package com.qingji.app.widget

import android.content.Context
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * 小组件外观/设置快照的进程级轻量缓存（工单 B2 + V3 最终缺陷修复 P1）。
 *
 * 一致性模型（generation）：
 * - 每次 invalidate() 原子递增 generation（不清缓存，旧 Entry 因代次不符永不命中）；
 * - get()/warmUp() 开始回源时记录当时的 generation；
 * - 回源完成写缓存前，只有 generation 未变化才允许写入——旧在途读取绝不覆盖新值；
 * - 回源失败返回安全默认值但不缓存，下次调用可重新回源；
 * - CancellationException 单独重抛，不破坏结构化并发。
 *
 * get() 与 warmUp() 共用同一套 generation + mutex 机制。
 * 不新增持久化设置源，不改 SettingsStore 键名/格式。
 */
object WidgetSettingsCache {
    private data class Entry(val generation: Long, val settings: QingJiSettings)

    @Volatile
    private var cached: Entry? = null

    /**
     * 代次用 AtomicLong：invalidate 只需一次原子自增，不碰 cached，
     * 因此不再需要 synchronized 与协程 Mutex 两把锁——消除「校验后、写入前」竞态窗口。
     * 旧 loader 写回的 Entry 因 generation 不匹配，被 get 快路径/锁内二次检查拒绝。
     */
    private val generation = AtomicLong(0L)

    /** 保证 get 与 warmUp 的回源-校验-写入原子，避免两者互相覆盖。 */
    private val mutex = Mutex()

    /**
     * 测试注入点：替换回源函数后可用确定性的假数据源验证并发语义（CompletableDeferred gate）。
     * 生产始终为 null（直接读 SettingsStore）。
     */
    @Volatile
    internal var loaderForTest: (suspend () -> QingJiSettings)? = null

    private suspend fun loadFromSource(context: Context): QingJiSettings =
        loaderForTest?.invoke() ?: SettingsStore.observe(context.applicationContext).first()

    /** 冷启动预热：与 get 共用 generation 机制，旧预热不得覆盖较新缓存。 */
    suspend fun warmUp(context: Context) {
        val gen = generation.get()
        val loaded = try {
            loadFromSource(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return // 预热失败不缓存，首次读取会再试
        }
        mutex.withLock {
            if (gen == generation.get()) cached = Entry(gen, loaded)
        }
    }

    /**
     * 读取当前设置快照。命中缓存直接返回；未命中回源后仅在 generation 未变时回填。
     * 回源失败返回默认快照，但不写入缓存（下次可重试）。
     */
    suspend fun get(context: Context): QingJiSettings {
        // 快路径也必须校验代次：invalidate/reset 之后，旧代次 Entry 不得再被命中。
        // 否则并发测试里上一用例的在途回填会被下一用例读到（表现为 loader 未被调用 → gate 死锁，
        // 或读到旧值）。这是与 generation 模型一致的防御，不改变写入侧语义。
        cached?.let { entry -> if (entry.generation == generation.get()) return entry.settings }
        val gen = generation.get()
        return mutex.withLock {
            cached?.let { entry ->
                if (entry.generation == generation.get()) return@withLock entry.settings
            }
            val loaded = try {
                loadFromSource(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock QingJiSettings() // 失败不缓存
            }
            if (gen == generation.get()) cached = Entry(gen, loaded)
            loaded
        }
    }

    /**
     * 设置写入/数据变更后调用：先使旧 generation 失效，再清空缓存。
     * 任何在途的旧读取完成后都会因 generation 不匹配而被丢弃。
     */
    fun invalidate() {
        // 只做原子自增：cached 不主动清空。旧 Entry 因代次不符永远不会被命中，
        // 新读取因代次不符一定回源。单一原子操作 + 读取侧 generation 校验 = 无双锁竞态。
        generation.incrementAndGet()
    }

    /** 测试清理注入点。 */
    internal fun resetForTest() {
        generation.incrementAndGet()
        cached = null
        loaderForTest = null
    }
}
