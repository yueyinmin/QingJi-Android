package com.qingji.app.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * QJ-PROGRESS-V2：七种进度条绘制纯计算测试。
 * 覆盖低进度准确性、循环接缝、气泡生命周期、样式 key 容错。
 */
class StyledProgressBarMathTest {

    // ---------- 样式 key ----------
    @Test
    fun `七种样式 key 唯一且默认 simple`() {
        val keys = ProgressBarStyle.entries.map { it.key }
        assertEquals(7, keys.size)
        assertEquals(7, keys.toSet().size)
        assertEquals("simple", ProgressBarStyle.DEFAULT_KEY)
        assertEquals(ProgressBarStyle.SIMPLE, ProgressBarStyle.fromKey(null))
        assertEquals(ProgressBarStyle.SIMPLE, ProgressBarStyle.fromKey("unknown"))
        ProgressBarStyle.entries.forEach { style ->
            assertEquals(style, ProgressBarStyle.fromKey(style.key))
        }
    }

    // ---------- 低进度准确性 ----------
    @Test
    fun `五档进度填充宽度与真实值一致`() {
        val w = 200f
        assertEquals(0f, fillWidth(0f, w), 0.01f)
        assertEquals(1f, fillWidth(0.0001f, w), 0.01f) // 极低进度只给 1px 可见，不放大成 barHeight
        assertEquals(100f, fillWidth(0.5f, w), 0.01f)
        assertEquals(198f, fillWidth(0.99f, w), 0.01f)
        assertEquals(w, fillWidth(1f, w), 0.01f)
    }

    @Test
    fun `越界进度夹紧不越轨道`() {
        assertEquals(0f, fillWidth(-0.3f, 200f), 0.01f)
        assertEquals(200f, fillWidth(1.5f, 200f), 0.01f)
        assertEquals(null, fillRect(0f, 200f, 6f))
        assertEquals(200f, fillRect(1.4f, 200f, 6f)!!.right, 0.01f)
    }

    @Test
    fun `弹簧过冲值不导致绘制越界`() {
        assertEquals(0f, safeProgress(-0.2f))
        assertEquals(1f, safeProgress(1.2f))
        assertEquals(100f, fillWidth(1.2f, 100f), 0.01f)
    }

    // ---------- COMET ----------
    @Test
    fun `彗星短进度下光点数量受限`() {
        assertEquals(0, cometCount(0f, 200f, 10f))
        assertEquals(0, cometCount(0.01f, 200f, 10f))
        assertTrue(cometCount(0.5f, 200f, 10f) >= 1)
        assertEquals(3, cometCount(1f, 200f, 10f))
    }

    // ---------- SHIMMER / STRIPE / AURORA 接缝 ----------
    @Test
    fun `循环相位 0 与 1 等价`() {
        assertEquals(0f, loopPhase(0f), 0.01f)
        assertEquals(0f, loopPhase(1f), 0.01f)
        assertEquals(0.5f, loopPhase(2.5f), 0.01f)
    }

    @Test
    fun `扫描高光接缝在轨道外`() {
        val w = 200f
        assertEquals(-w, shimmerX(0f, w), 0.01f)
        assertEquals(-w, shimmerX(1f, w), 0.01f) // 回到轨道外，不跳色
    }

    @Test
    fun `斜纹偏移循环连续`() {
        val spacing = 20f
        assertEquals(-spacing, stripeOffset(0f, spacing), 0.01f)
        assertEquals(-spacing, stripeOffset(1f, spacing), 0.01f)
    }

    @Test
    fun `极光渐变首尾同色不跳色`() {
        val theme = Color(0xFF4CAF50)
        val first = auroraColor(0f, theme)
        val last = auroraColor(1f, theme)
        assertTrue(abs(first.red - last.red) < 0.001f)
        assertTrue(abs(first.green - last.green) < 0.001f)
        assertTrue(abs(first.blue - last.blue) < 0.001f)
    }

    // ---------- LIQUID 气泡 ----------
    @Test
    fun `气泡固定种子可复现且数量正确`() {
        assertEquals(0, buildProgressBubbles(0).size)
        val a = buildProgressBubbles(12)
        assertEquals(a, buildProgressBubbles(12))
        assertTrue(a.all { it.speed in 1..3 })
    }

    @Test
    fun `气泡整数倍率周期连续`() {
        val spec = buildProgressBubbles(4).first()
        val w = 200f; val h = 10f
        val at0 = progressBubbleFrame(spec, 0f, w, h, 0f)!!
        val at1 = progressBubbleFrame(spec, 1f, w, h, 0f)!!
        assertEquals(at0.x, at1.x, 0.5f)
        assertEquals(at0.y, at1.y, 0.5f)
        assertEquals(at0.alpha, at1.alpha, 0.01f)
    }

    @Test
    fun `气泡不越界且接近液面淡出`() {
        val spec = buildProgressBubbles(4).first()
        val w = 200f; val h = 10f
        listOf(0.1f, 0.3f, 0.6f, 0.9f).forEach { phase ->
            val f = progressBubbleFrame(spec, phase, w, h, 0f) ?: return@forEach
            assertTrue(f.x >= f.radius - 0.1f)
            assertTrue(f.x <= w - f.radius + 0.1f)
            assertTrue(f.y in 0f..h)
            assertTrue(f.alpha in 0f..1f)
        }
        val late = spec.copy(birth = 0.99f)
        val alpha = progressBubbleFrame(late, 0f, w, h, 0f)!!.alpha
        assertTrue("接近液面应淡出", alpha < 0.3f)
    }

    // ---------- PULSE ----------
    @Test
    fun `双光环相位错开且透明度低`() {
        val rings = pulseRings(0f, 20f)
        assertEquals(2, rings.size)
        assertTrue(rings.all { it.alpha <= 0.3f })
        assertTrue(abs(rings[0].radius - rings[1].radius) > 0.01f)
    }
}
