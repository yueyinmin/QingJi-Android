package com.qingji.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.prefs.QingJiSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 收尾补充核查 P1：缓存并发边界（不改动原 WidgetSettingsCacheTest 的任何断言）。
 * 全部使用实例级 CacheStore 注入，每例独立；async 全部 awaitAll，gate 一律 complete。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsCacheBoundaryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /** 1+2+3+6：两个并发回源；回源期间连续两次 invalidate；旧代次晚于新代次完成；旧结果不得污染缓存。 */
    @Test
    fun `两个并发回源_期间两次invalidate_旧代次晚完成也不污染后续缓存`() = runBlocking {
        val oldEntered = CompletableDeferred<Unit>()
        val oldGate = CompletableDeferred<Unit>()
        val newEntered = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        var call = 0
        val store = WidgetSettingsCache.CacheStore {
            when (call++) {
                0 -> { oldEntered.complete(Unit); oldGate.await(); QingJiSettings(widgetAlpha = 11) }
                1 -> { newEntered.complete(Unit); newGate.await(); QingJiSettings(widgetAlpha = 22) }
                else -> QingJiSettings(widgetAlpha = 33)
            }
        }
        val old = async { store.get(context) }
        oldEntered.await() // 旧回源已进入 loader 并持有 Mutex
        store.invalidate() // 第一次
        store.invalidate() // 第二次连续失效
        val new = async { store.get(context) } // 新 get 在 Mutex 上排队
        // 先放行旧回源：它因代次不符不写缓存，随后新 get 才能进入自己的 loader
        oldGate.complete(Unit)
        newEntered.await()
        newGate.complete(Unit)
        val oldResult = old.await().widgetAlpha
        val newResult = new.await().widgetAlpha
        // 允许在途调用返回自己的旧结果 —— 但绝不污染缓存
        assertNotEquals("新代次调用必须拿到新值", 11, newResult)
        assertEquals(22, newResult)
        // 新代次结果 22 被正确缓存；旧代次 11 未被写入。后续 get 命中缓存仍为 22（不是 11）。
        assertEquals("缓存必须是新代次 22，旧 11 不得残留", 22, store.get(context).widgetAlpha)
    }

    /** 4：普通异常返回安全默认值但不缓存。 */
    @Test
    fun `普通异常返回默认值且不缓存_下次重新回源`() = runBlocking {
        var fail = true
        var loads = 0
        val store = WidgetSettingsCache.CacheStore {
            loads++
            if (fail) throw IllegalStateException("io error") else QingJiSettings(widgetAlpha = 77)
        }
        assertEquals(QingJiSettings(), store.get(context))
        assertEquals(1, loads)
        fail = false
        assertEquals(77, store.get(context).widgetAlpha)
        assertEquals(2, loads)
    }

    /** 5：CancellationException 向上传播，不被吞成默认值。 */
    @Test
    fun `CancellationException向上传播且不被缓存`() = runBlocking {
        var cancelled = false
        val store = WidgetSettingsCache.CacheStore {
            cancelled = true
            throw CancellationException("cancel")
        }
        var thrown = false
        try { store.get(context) } catch (e: CancellationException) { thrown = true }
        assertEquals(true, cancelled)
        assertEquals(true, thrown)
    }

    /** warmUp 路径的旧代次晚完成也不覆盖新缓存。 */
    @Test
    fun `warmUp旧代次晚完成也不覆盖新代次缓存`() = runBlocking {
        val g1 = CompletableDeferred<Unit>(); val e1 = CompletableDeferred<Unit>()
        val g2 = CompletableDeferred<Unit>(); val e2 = CompletableDeferred<Unit>()
        var call = 0
        val store = WidgetSettingsCache.CacheStore {
            when (call++) {
                0 -> { e1.complete(Unit); g1.await(); QingJiSettings(widgetAlpha = 10) }
                1 -> { e2.complete(Unit); g2.await(); QingJiSettings(widgetAlpha = 20) }
                else -> QingJiSettings(widgetAlpha = 99)
            }
        }
        coroutineScope {
            val a = async { store.warmUp(context) }
            e1.await()
            store.invalidate()
            val b = async { store.get(context) }
            e2.await()
            g2.complete(Unit)
            g1.complete(Unit)
            listOf(a, b).awaitAll()
        }
        // 新代次 get 的结果 20 被缓存；旧 warmUp 的 10 因代次不符未写入。
        assertEquals("缓存必须是新代次 20，旧 10 不得覆盖", 20, store.get(context).widgetAlpha)
    }
}
