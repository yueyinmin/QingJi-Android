package com.qingji.app.ui.components

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QJ-WATERCUP-REFACTOR-V2：WaterCup 纯计算逻辑测试。
 * 覆盖水位边界、气泡生命周期/周期连续性、杯壁约束、文字自适应、小尺寸约束。
 */
class WaterCupMathTest {

    private val density = Density(2f, 1f)
    private val geometry = cupGeometry(78f * 2, 190f * 2, density) // px

    // ---------- 水位 ----------
    @Test
    fun `安全水位 clamp 越界值`() {
        assertEquals(0f, safeLevel(-0.5f))
        assertEquals(0f, safeLevel(0f))
        assertEquals(0.5f, safeLevel(0.5f))
        assertEquals(1f, safeLevel(1f))
        assertEquals(1f, safeLevel(1.5f), 0f)
    }

    @Test
    fun `五档水位的水面 Y 单调上升且在杯内`() {
        val ys = listOf(0f, 0.01f, 0.5f, 0.99f, 1f).map { waterTopY(geometry, it) }
        assertTrue(ys.zipWithNext().all { (a, b) -> b <= a })
        assertTrue(ys.all { it >= geometry.top - 1f && it <= geometry.bottom + 1f })
        assertEquals(geometry.bottom, ys.first(), 0.01f)
        assertEquals(geometry.top, ys.last(), 0.01f)
    }

    @Test
    fun `振幅 clamp 不超杯高八分之一`() {
        val maxAmp = (geometry.bottom - geometry.top) * 0.08f
        assertEquals(maxAmp, safeAmplitude(9999f, geometry), 0.01f)
        assertEquals(0f, safeAmplitude(-1f, geometry), 0.01f)
    }

    // ---------- 气泡 ----------
    @Test
    fun `四档气泡数量正确且关闭时为空`() {
        assertEquals(0, buildBubbleSpecs(0).size)
        assertEquals(4, buildBubbleSpecs(1).size)
        assertEquals(9, buildBubbleSpecs(2).size)
        assertEquals(16, buildBubbleSpecs(3).size)
    }

    @Test
    fun `气泡固定种子可复现`() {
        val a = buildBubbleSpecs(2)
        val b = buildBubbleSpecs(2)
        assertEquals(a, b)
    }

    @Test
    fun `气泡采样周期连续没有接缝跳变`() {
        // phase=0 与 phase=1 在数学上等价（cycle 取模 1），
        // 因此任何倍率下气泡位置/透明度应连续。
        val spec = buildBubbleSpecs(2).first()
        val waterTop = waterTopY(geometry, 0.5f)
        val at0 = bubbleFrameAt(spec, 0f, geometry, waterTop, density)!!
        val at1 = bubbleFrameAt(spec, 1f, geometry, waterTop, density)!!
        assertEquals(at0.x, at1.x, 0.5f)
        assertEquals(at0.y, at1.y, 0.5f)
        assertEquals(at0.alpha, at1.alpha, 0.01f)
    }

    @Test
    fun `气泡始终在杯壁内且不穿出水面`() {
        val waterTop = waterTopY(geometry, 0.35f)
        listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { phase ->
            buildBubbleSpecs(2).take(4).forEach { spec ->
                val frame = bubbleFrameAt(spec, phase, geometry, waterTop, density) ?: return@forEach
                assertTrue("气泡 x 越界: ${frame.x}", frame.x >= geometry.centerX - geometry.topHalf - 1f)
                assertTrue("气泡 x 越界: ${frame.x}", frame.x <= geometry.centerX + geometry.topHalf + 1f)
                assertTrue("气泡穿出水面: ${frame.y} < $waterTop", frame.y >= waterTop - frame.radius)
                assertTrue(frame.alpha in 0f..1f)
            }
        }
    }

    @Test
    fun `接近水面时气泡淡出`() {
        val spec = BubbleSpec(0.5f, 2f, 1, 0f, 0f, 0.9f)
        val waterTop = waterTopY(geometry, 0.5f)
        // 相位推进使 cycle 接近 1：构造 birth=0.99 + phase 小步进
        val alphaHigh = bubbleFrameAt(spec, 0f, geometry, waterTop, density)!!.alpha
        val late = spec.copy(birth = 0.995f)
        val alphaLow = bubbleFrameAt(late, 0f, geometry, waterTop, density)!!.alpha
        assertTrue("接近水面应更透明", alphaLow < alphaHigh)
        assertTrue(alphaLow < 0.2f)
    }

    @Test
    fun `低水位气泡行程被压缩不越界`() {
        val waterTop = waterTopY(geometry, 0.01f)
        val frame = bubbleFrameAt(buildBubbleSpecs(2).first(), 0.5f, geometry, waterTop, density)
        // 极低水位时可用行程 <= 半径，允许返回 null（不画气泡）
        if (frame != null) {
            assertTrue(frame.y in waterTop..geometry.bottom)
        }
    }

    // ---------- 杯底高光 ----------
    @Test
    fun `小尺寸下高光宽度与长度自动收窄`() {
        val big = footHighlight(cupGeometry(200f, 400f, density))
        val small = footHighlight(cupGeometry(30f, 60f, density))
        assertTrue(small.width <= big.width)
        assertTrue(small.endX - small.startX < big.endX - big.startX)
        assertTrue(small.alpha in 0f..1f)
    }

    // ---------- 几何/文字 ----------
    @Test
    fun `几何参数对称且锥形成立`() {
        assertEquals(geometry.width / 2f, geometry.centerX, 0.01f)
        assertTrue(geometry.topHalf > geometry.bottomHalf)
        assertTrue(geometry.top < geometry.bottom)
        assertTrue(geometry.textAvailable > 0f)
    }

}
