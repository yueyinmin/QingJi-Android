package com.qingji.app.ui.components

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QJ-WATERCUP-REFACTOR-V2：P0 文字测量缓存核心证据。
 *
 * 真实 TextMeasurer（Robolectric + Android FontFamilyResolver）验证：
 *  1. 短文字不缩字号、长文字自适应且不超宽；
 *  2. 相同输入结果一致（可安全缓存在 remember 中）；
 *  3. 文字/几何变化才产生不同结果。
 * WaterCup 里 measureCupText 被 remember(capacityText, geometry, density) 包裹，
 * 动画帧状态不进 key，因此波浪/气泡不会触发重复测量。
 */
@RunWith(RobolectricTestRunner::class)
class WaterCupTextMeasureTest {

    private val density = Density(2f, 1f)
    private lateinit var measurer: TextMeasurer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        measurer = TextMeasurer(
            createFontFamilyResolver(context),
            density,
            LayoutDirection.Ltr,
            1
        )
    }

    private fun measure(text: String?, width: Float, height: Float): CupTextLayout? =
        measureCupText(measurer, text, cupGeometry(width, height, density), density)

    @Test
    fun `短文字在宽杯中不再缩字号`() {
        val result = measure("500/2000 ml", 320f, 760f)
        assertTrue(result != null)
        assertTrue(kotlin.math.abs(10f - result!!.fontSizeSp) < 0.01f)
        assertTrue(result.layout.size.width <= result.available + 1f)
    }

    @Test
    fun `长文字自动缩字号`() {
        val result = measure("1234567890123456789012345678901234567890 ml", 40f, 200f)
        assertTrue(result != null)
        assertTrue("字号应缩小", result!!.fontSizeSp < 10f)
        assertTrue("字号不得低于 7sp", result.fontSizeSp >= 7f)
    }

    @Test
    fun `一般长文字缩字号后不超出可用宽度`() {
        val result = measure("12345678901234567890 ml", 80f, 320f)
        assertTrue(result != null)
        assertTrue("文字不得超出杯体可用宽度", result!!.layout.size.width <= result.available + 2f)
    }

    @Test
    fun `相同输入测量结果一致可安全缓存`() {
        val a = measure("800/2500 ml", 240f, 600f)!!
        val b = measure("800/2500 ml", 240f, 600f)!!
        assertTrue(kotlin.math.abs(a.fontSizeSp - b.fontSizeSp) < 0.01f)
        assertTrue(kotlin.math.abs(a.layout.size.width - b.layout.size.width) < 0.01f)
        assertTrue(kotlin.math.abs(a.layout.size.height - b.layout.size.height) < 0.01f)
    }

    @Test
    fun `不同文字或尺寸触发不同测量结果`() {
        val a = measure("100 ml", 240f, 600f)!!
        val b = measure("9999 ml", 240f, 600f)!!
        val c = measure("100 ml", 120f, 600f)!!
        assertTrue("不同文字宽度不同", a.layout.size.width != b.layout.size.width)
        assertTrue("不同尺寸可用宽度不同", a.available != c.available)
    }

    @Test
    fun `null 或空白文字不测量`() {
        assertTrue("null 文字应返回 null", measure(null, 240f, 600f) == null)
        assertTrue("空白文字应返回 null", measure("   ", 240f, 600f) == null)
    }
}
