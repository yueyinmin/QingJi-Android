package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 喝水防误触合并判定单测（阶段 2）：600ms 内连点只放行一次。 */
class WaterDebounceTest {

    private val window = 600L

    @Test
    fun `首次点击放行`() {
        assertTrue(WaterDebounce.shouldAllow(1000L, 0L, window))
    }

    @Test
    fun `窗口内连点被拦截`() {
        assertFalse(WaterDebounce.shouldAllow(1500L, 1000L, window))
        assertFalse(WaterDebounce.shouldAllow(1599L, 1000L, window))
    }

    @Test
    fun `恰好等于窗口放行`() {
        assertTrue(WaterDebounce.shouldAllow(1600L, 1000L, window))
    }

    @Test
    fun `超过窗口放行`() {
        assertTrue(WaterDebounce.shouldAllow(2000L, 1000L, window))
    }

    @Test
    fun `写库成功后才推进时间戳`() {
        assertEquals(1000L, WaterDebounce.nextAfterWrite(true, 0L, 1000L))
    }

    @Test
    fun `写库失败清除窗口允许立即重试`() {
        // 上一次成功在 1000ms；这次 1100ms 写失败 → 清零窗口，
        // 下一次 1200ms 立即放行（不被上次成功后的 600ms 吞掉）
        val afterFail = WaterDebounce.nextAfterWrite(false, 1000L, 1100L)
        assertEquals(0L, afterFail)
        assertTrue(WaterDebounce.shouldAllow(1200L, afterFail, window))
    }
}
