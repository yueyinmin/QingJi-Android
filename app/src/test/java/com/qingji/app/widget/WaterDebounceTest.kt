package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 喝水防误触状态机单测（W-04）：
 * inFlight 防并发 + lastSuccessAt 防误触 + 写失败立即重试。
 */
class WaterDebounceTest {

    @Test
    fun `首次点击放行`() {
        assertTrue(WaterGuard.canStart(WaterGuardState(), 1000L))
    }

    @Test
    fun `正在写入时拦截重复点击`() {
        val inFlight = WaterGuardState(inFlight = true, lastSuccessAt = 0L)
        assertFalse(WaterGuard.canStart(inFlight, 2000L))
    }

    @Test
    fun `成功后600ms内连点被拦截`() {
        val afterSuccess = WaterGuardState(false, 1000L)
        assertFalse(WaterGuard.canStart(afterSuccess, 1599L))
        assertTrue(WaterGuard.canStart(afterSuccess, 1600L)) // 恰好等于窗口放行
    }

    @Test
    fun `写失败释放执行权且不消耗成功窗口`() {
        // 上次成功 1000ms；本次 1200ms 开始写、失败 → lastSuccessAt 保持 1000
        val started = WaterGuard.onStart(WaterGuardState(false, 1000L))
        assertTrue(started.inFlight)
        val afterFail = WaterGuard.onFailure(started)
        assertFalse(afterFail.inFlight)
        assertEquals(1000L, afterFail.lastSuccessAt)
        // 距离上次成功 200ms（<600）——按"成功窗口"仍会被拦，
        // 但 inFlight 已释放：若这是首次写失败（lastSuccessAt=0）则可立即重试
        val firstFail = WaterGuard.onFailure(WaterGuard.onStart(WaterGuardState()))
        assertTrue(WaterGuard.canStart(firstFail, 10L))
    }

    @Test
    fun `写成功推进成功时间`() {
        val started = WaterGuard.onStart(WaterGuardState())
        val success = WaterGuard.onSuccess(5000L)
        assertEquals(5000L, success.lastSuccessAt)
        assertFalse(success.inFlight)
        assertFalse(WaterGuard.canStart(success, 5599L))
        assertTrue(WaterGuard.canStart(success, 5600L))
    }

    @Test
    fun `并发场景两个点击只有一个能通过`() {
        var state = WaterGuardState()
        val first = WaterGuard.canStart(state, 3000L)
        assertTrue(first)
        state = WaterGuard.onStart(state) // 第一个占用执行权
        val second = WaterGuard.canStart(state, 3001L) // 几乎同时的第二个
        assertFalse(second)
    }
}
