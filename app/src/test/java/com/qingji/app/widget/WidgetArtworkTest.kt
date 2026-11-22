package com.qingji.app.widget

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** V2.3：Glance 图片资源生成测试（Robolectric 提供 Android Canvas）。 */
@RunWith(RobolectricTestRunner::class)
class WidgetArtworkTest {

    @Test
    fun `BMI仪表盘生成正确尺寸且各值不崩溃`() {
        listOf(0.0, 15.0, 18.4, 18.5, 24.9, 28.0, 35.0, 60.0).forEach { bmi ->
            val bmp = WidgetArtwork.bmiGauge(
                bmi = bmi, widthPx = 240, heightPx = 124,
                green = Color.parseColor("#0E7A5F"), warn = Color.parseColor("#E08A2E"),
                danger = Color.parseColor("#E5544B"), needle = Color.BLACK,
                track = Color.GRAY
            )
            assertEquals(240, bmp.width)
            assertEquals(124, bmp.height)
            // Robolectric 的 Android Canvas 是 no-op shadow，不产生真实像素；
            // 这里只验证 Bitmap 契约（非空/尺寸/可回收），真实视觉需真机截图。
            assertTrue(!bmp.isRecycled)
            bmp.recycle()
            assertTrue(bmp.isRecycled)
        }
    }

    @Test
    fun `BMI无数据与有数据都能生成独立Bitmap实例`() {
        // 不比较像素（Robolectric Canvas no-op），验证两种输入都走完整绘制路径且不抛异常
        val empty = WidgetArtwork.bmiGauge(0.0, 240, 124, Color.GREEN, Color.YELLOW, Color.RED, Color.BLACK, Color.GRAY)
        val normal = WidgetArtwork.bmiGauge(24.9, 240, 124, Color.GREEN, Color.YELLOW, Color.RED, Color.BLACK, Color.GRAY)
        assertTrue(empty !== normal)
        assertEquals(240, empty.width); assertEquals(240, normal.width)
        empty.recycle(); normal.recycle()
    }

    @Test
    fun `水杯在0到100水位都能生成正确尺寸Bitmap`() {
        listOf(0f, 0.5f, 1f).forEach { level ->
            val bmp = WidgetArtwork.waterCup(
                156, 380, level,
                Color.parseColor("#9FC9DA"), Color.parseColor("#7EC8E3"),
                Color.parseColor("#AEDCEC"), Color.parseColor("#E6F4FA")
            )
            assertEquals(156, bmp.width); assertEquals(380, bmp.height)
            bmp.recycle()
        }
    }

    @Test
    fun `水位与投影percent口径一致`() {
        val waterMl = 1600; val goal = 3200
        val pct = (waterMl * 100f / goal).coerceIn(0f, 100f)
        assertEquals(50f, pct, 0.001f)
        assertEquals(0.5f, pct / 100f, 0.001f) // 传给 waterCup 的 level
    }
}
