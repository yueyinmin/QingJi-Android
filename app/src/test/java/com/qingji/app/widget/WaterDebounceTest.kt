package com.qingji.app.widget

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
}
