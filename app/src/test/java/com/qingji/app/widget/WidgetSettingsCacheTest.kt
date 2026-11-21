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
 * 工单 E1.10 / B2：外观设置变更后缓存不得永久显示旧值，且命中缓存不重复回源。
 * 用注入的确定性 loader 验证缓存状态机本身，不依赖 Robolectric 下 DataStore 的磁盘时序。
 * DataStore 的键/格式不变，这里只测「缓存 → 失效 → 回源」的语义。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetSettingsCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WidgetSettingsCache.resetForTest()
    }

    @Test
    fun `invalidate_forces_refresh_of_appearance`() = runBlocking {
        var sourceAlpha = 55
        var loadCount = 0
        WidgetSettingsCache.loaderForTest = {
            loadCount += 1
            QingJiSettings(widgetAlpha = sourceAlpha, widgetShowWater = false)
        }

        val first = WidgetSettingsCache.get(context)
        assertEquals(55, first.widgetAlpha)
        assertEquals(false, first.widgetShowWater)
        assertEquals(1, loadCount)

        // 命中缓存：回源值已变但不 invalidate，缓存仍是旧值、不重复回源
        sourceAlpha = 90
        assertEquals(55, WidgetSettingsCache.get(context).widgetAlpha)
        assertEquals(1, loadCount)

        // 设置写入路径会 invalidate，之后必须回源读到新值，不会永久停留在旧外观
        WidgetSettingsCache.invalidate()
        assertEquals(90, WidgetSettingsCache.get(context).widgetAlpha)
        assertEquals(2, loadCount)
    }

    @Test
    fun `loader_failure_falls_back_to_default_settings`() = runBlocking {
        WidgetSettingsCache.loaderForTest = { throw IllegalStateException("disk error") }
        val s = WidgetSettingsCache.get(context)
        // 回源失败时回落到与 SettingsStore 同源的默认值，不伪造用户设置、不崩。
        assertEquals(QingJiSettings(), s)
    }
    // ---------- V3 最终缺陷修复：确定性并发与失败缓存测试 ----------

    @Test
    fun `回源失败返回默认但不缓存_下次重新回源`() = runBlocking {
        var fail = true
        var loads = 0
        WidgetSettingsCache.loaderForTest = {
            loads += 1
            if (fail) throw IllegalStateException("temp failure")
            QingJiSettings(widgetAlpha = 77)
        }
        assertEquals(QingJiSettings(), WidgetSettingsCache.get(context))
        assertEquals(1, loads)
        fail = false
        // 失败值未进缓存 → 下次必须重新回源拿到真实值
        assertEquals(77, WidgetSettingsCache.get(context).widgetAlpha)
        assertEquals(2, loads)
    }

    @Test
    fun `CancellationException必须向上传播`() = runBlocking {
        WidgetSettingsCache.loaderForTest = { throw CancellationException("cancelled") }
        try {
            WidgetSettingsCache.get(context)
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
        WidgetSettingsCache.loaderForTest = {
            if (first) {
                first = false
                entered.complete(Unit)
                gate.await() // 第一次回源暂停
                QingJiSettings(widgetAlpha = 11)
            } else QingJiSettings(widgetAlpha = 99)
        }
        val stale = async { WidgetSettingsCache.get(context) }
        entered.await() // 等第一次回源真正进入 loader
        WidgetSettingsCache.invalidate()
        gate.complete(Unit)
        val inFlight = stale.await().widgetAlpha
        assertTrue("在途读取只能是旧值或已失效后的新值", inFlight == 11 || inFlight == 99)
        // 核心保证：在途旧读完成后，缓存里不能留旧值——下一次 get 必须拿到新值
        assertEquals(99, WidgetSettingsCache.get(context).widgetAlpha)
    }

    @Test
    fun `warmUp回源暂停期间invalidate_旧预热不得覆盖缓存`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var first = true
        WidgetSettingsCache.loaderForTest = {
            if (first) {
                first = false
                entered.complete(Unit)
                gate.await(); QingJiSettings(widgetAlpha = 22)
            } else QingJiSettings(widgetAlpha = 88)
        }
        val warm = async { WidgetSettingsCache.warmUp(context) }
        entered.await()
        WidgetSettingsCache.invalidate()
        gate.complete(Unit)
        warm.await()
        assertEquals(88, WidgetSettingsCache.get(context).widgetAlpha)
    }

    @Test
    fun `get与warmUp并发最终缓存不回退`() = runBlocking {
        var value = 33
        WidgetSettingsCache.loaderForTest = { QingJiSettings(widgetAlpha = value) }
        coroutineScope {
            listOf(async { WidgetSettingsCache.get(context) }, async { WidgetSettingsCache.warmUp(context) }).awaitAll()
        }
        assertEquals(33, WidgetSettingsCache.get(context).widgetAlpha)
        value = 44
        WidgetSettingsCache.invalidate()
        coroutineScope {
            listOf(async { WidgetSettingsCache.get(context) }, async { WidgetSettingsCache.warmUp(context) }).awaitAll()
        }
        assertEquals(44, WidgetSettingsCache.get(context).widgetAlpha)
    }

    @Test
    fun `连续两次写入失效后读到最后一次成功值`() = runBlocking {
        var alpha = 10
        WidgetSettingsCache.loaderForTest = { QingJiSettings(widgetAlpha = alpha) }
        WidgetSettingsCache.get(context)
        alpha = 20; WidgetSettingsCache.invalidate()
        alpha = 30; WidgetSettingsCache.invalidate()
        assertEquals(30, WidgetSettingsCache.get(context).widgetAlpha)
    }

}
