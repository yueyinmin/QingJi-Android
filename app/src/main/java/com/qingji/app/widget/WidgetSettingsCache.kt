package com.qingji.app.widget

import android.content.Context
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 小组件外观/设置快照的进程级轻量缓存（工单 B2 + V3 最终缺陷修复 P1）。
 *
 * 一致性模型（generation）：
 * - 每次 invalidate() 递增 generation 并清空缓存；
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

    @Volatile
    private var generation: Long = 0L

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
        val gen = generation
        val loaded = try {
            loadFromSource(context)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return // 预热失败不缓存，首次读取会再试
        }
        mutex.withLock {
            if (gen == generation) cached = Entry(gen, loaded)
        }
    }

    /**
     * 读取当前设置快照。命中缓存直接返回；未命中回源后仅在 generation 未变时回填。
     * 回源失败返回默认快照，但不写入缓存（下次可重试）。
     */
    suspend fun get(context: Context): QingJiSettings {
        cached?.let { return it.settings }
        val gen = generation
        return mutex.withLock {
            cached?.let { return@withLock it.settings }
            val loaded = try {
                loadFromSource(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock QingJiSettings() // 失败不缓存
            }
            if (gen == generation) cached = Entry(gen, loaded)
            loaded
        }
    }

    /**
     * 设置写入/数据变更后调用：先使旧 generation 失效，再清空缓存。
     * 任何在途的旧读取完成后都会因 generation 不匹配而被丢弃。
     */
    fun invalidate() {
        synchronized(this) {
            generation += 1
            cached = null
        }
    }

    /** 测试清理注入点。 */
    internal fun resetForTest() {
        synchronized(this) {
            generation += 1
            cached = null
        }
        loaderForTest = null
    }
}
