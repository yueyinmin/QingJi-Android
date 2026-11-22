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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 工单 E1.10 / B2 / V3 最终验收收尾 P1：
 * 外观设置变更后缓存不得永久显示旧值，且命中缓存不重复回源。
 *
 * 隔离方式（收尾 P1）：每个用例 @Before 新建独立 [WidgetSettingsCache.CacheStore] 并注入自己的 loader，
 * 不再使用跨测试共享的 object loader。所有 async 子协程在用例结束前 awaitAll 完成；
 * 挂起 gate 一律 complete，杜绝遗留挂起。全部原始断言保留，不跳过、不降低。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsCacheTest {

    private lateinit var context: Context
    private lateinit var store: WidgetSettingsCache.CacheStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 初始 loader 由各用例覆盖；独立实例确保无任何跨用例共享状态。
        store = WidgetSettingsCache.CacheStore { QingJiSettings() }
    }

    @Test
    fun `invalidate_forces_refresh_of_appearance`() = runBlocking {
        var sourceAlpha = 55
        var loadCount = 0
        store = WidgetSettingsCache.CacheStore {
            loadCount += 1
            QingJiSettings(widgetAlpha = sourceAlpha, widgetShowWater = false)
        }

        val first = store.get(context)
        assertEquals(55, first.widgetAlpha)
        assertEquals(false, first.widgetShowWater)
        assertEquals(1, loadCount)

        sourceAlpha = 90
        assertEquals(55, store.get(context).widgetAlpha)
        assertEquals(1, loadCount)

        store.invalidate()
        assertEquals(90, store.get(context).widgetAlpha)
        assertEquals(2, loadCount)
    }

    @Test
    fun `loader_failure_falls_back_to_default_settings`() = runBlocking {
        store = WidgetSettingsCache.CacheStore { throw IllegalStateException("disk error") }
        val s = store.get(context)
        assertEquals(QingJiSettings(), s)
    }

    @Test
    fun `回源失败返回默认但不缓存_下次重新回源`() = runBlocking {
        var fail = true
        var loads = 0
        store = WidgetSettingsCache.CacheStore {
            loads += 1
            if (fail) throw IllegalStateException("temp failure")
            QingJiSettings(widgetAlpha = 77)
        }
        assertEquals(QingJiSettings(), store.get(context))
        assertEquals(1, loads)
        fail = false
        assertEquals(77, store.get(context).widgetAlpha)
        assertEquals(2, loads)
    }

    @Test
    fun `CancellationException必须向上传播`() = runBlocking {
        store = WidgetSettingsCache.CacheStore { throw CancellationException("cancelled") }
        try {
            store.get(context)
            org.junit.Assert.fail("取消异常应传播")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun `get回源暂停期间invalidate_旧读取完成不得覆盖新值`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var first = true
        store = WidgetSettingsCache.CacheStore {
            if (first) {
                first = false
                entered.complete(Unit)
                gate.await()
                QingJiSettings(widgetAlpha = 11)
            } else QingJiSettings(widgetAlpha = 99)
        }
        val stale = async { store.get(context) }
        entered.await()
        store.invalidate()
        gate.complete(Unit)
        val inFlight = stale.await().widgetAlpha
        assertTrue("在途读取只能是旧值或已失效后的新值", inFlight == 11 || inFlight == 99)
        assertEquals(99, store.get(context).widgetAlpha)
    }

    @Test
    fun `warmUp回源暂停期间invalidate_旧预热不得覆盖缓存`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var first = true
        store = WidgetSettingsCache.CacheStore {
            if (first) {
                first = false
                entered.complete(Unit)
                gate.await()
                QingJiSettings(widgetAlpha = 22)
            } else QingJiSettings(widgetAlpha = 88)
        }
        val warm = async { store.warmUp(context) }
        entered.await()
        store.invalidate()
        gate.complete(Unit)
        warm.await()
        assertEquals(88, store.get(context).widgetAlpha)
    }

    @Test
    fun `get与warmUp并发最终缓存不回退`() = runBlocking {
        var value = 33
        store = WidgetSettingsCache.CacheStore { QingJiSettings(widgetAlpha = value) }
        coroutineScope {
            listOf(async { store.get(context) }, async { store.warmUp(context) }).awaitAll()
        }
        assertEquals(33, store.get(context).widgetAlpha)
        value = 44
        store.invalidate()
        coroutineScope {
            listOf(async { store.get(context) }, async { store.warmUp(context) }).awaitAll()
        }
        assertEquals(44, store.get(context).widgetAlpha)
    }

    @Test
    fun `连续两次写入失效后读到最后一次成功值`() = runBlocking {
        var alpha = 10
        store = WidgetSettingsCache.CacheStore { QingJiSettings(widgetAlpha = alpha) }
        store.get(context)
        alpha = 20; store.invalidate()
        alpha = 30; store.invalidate()
        assertEquals(30, store.get(context).widgetAlpha)
    }
}
