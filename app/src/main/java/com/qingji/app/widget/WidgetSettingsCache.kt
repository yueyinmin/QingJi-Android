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
 * 可注入性（最终验收收尾 P1）：核心状态机下沉到 [CacheStore] 实例，
 * 测试为每个用例 new 一个独立实例并注入自己的 loader——彻底消除跨测试共享 object loader 污染。
 * 对外仍保留 [WidgetSettingsCache] object 单例，生产行为与调用方零改动。
 */
object WidgetSettingsCache {
    private data class Entry(val generation: Long, val settings: QingJiSettings)

    /**
     * 可独立实例化的缓存状态机。生产由 object 持有单例；测试每例独立 new。
     * @param loader 回源函数；生产 = SettingsStore；测试 = 各自注入的确定性假源。
     */
    internal class CacheStore(
        private val loader: suspend (Context) -> QingJiSettings
    ) {
        @Volatile
        private var cached: Entry? = null

        /** 代次用 AtomicLong：invalidate 只原子自增、不碰 cached，消除双锁校验-写入竞态窗口。 */
        private val generation = AtomicLong(0L)

        /** 保证 get 与 warmUp 的回源-校验-写入原子。 */
        private val mutex = Mutex()

        suspend fun warmUp(context: Context) {
            val gen = generation.get()
            val loaded = try {
                loader(context)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return // 预热失败不缓存，首次读取会再试
            }
            mutex.withLock {
                if (gen == generation.get()) cached = Entry(gen, loaded)
            }
        }

        suspend fun get(context: Context): QingJiSettings {
            // 快路径也必须校验代次：旧代次 Entry 不得命中。
            cached?.let { entry -> if (entry.generation == generation.get()) return entry.settings }
            val gen = generation.get()
            return mutex.withLock {
                cached?.let { entry ->
                    if (entry.generation == generation.get()) return@withLock entry.settings
                }
                val loaded = try {
                    loader(context)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withLock QingJiSettings() // 失败不缓存
                }
                if (gen == generation.get()) cached = Entry(gen, loaded)
                loaded
            }
        }

        fun invalidate() {
            generation.incrementAndGet()
        }
    }

    /** 生产单例：loader 固定读 SettingsStore，与既有键名/格式一致。 */
    private val store = CacheStore { context ->
        SettingsStore.observe(context.applicationContext).first()
    }

    suspend fun warmUp(context: Context) = store.warmUp(context)
    suspend fun get(context: Context): QingJiSettings = store.get(context)
    fun invalidate() = store.invalidate()
}
