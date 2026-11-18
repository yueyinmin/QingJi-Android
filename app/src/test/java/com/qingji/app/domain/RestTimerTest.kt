package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.4 健康体系：组间休息倒计时状态机测试。
 * 全部用固定时间戳，不碰系统时钟——结果可复现。
 */
class RestTimerTest {

    private val t0 = 1_000_000_000L  // 任取一个 epoch 毫秒起点

    @Test
    fun `未计时时剩余为0且不算结束`() {
        val st = RestTimerState()
        assertEquals(0L, st.remainingSeconds(t0))
        assertFalse(st.isFinished(t0))
    }

    @Test
    fun `倒计时内剩余秒数向上取整`() {
        // 总 90 秒，过了 30 秒（含 500ms 零头）→ 剩 60 秒（500ms 向上取整成 1 秒）
        val st = RestTimerState(endsAt = t0 + 90_000L, totalMs = 90_000L)
        val now = t0 + 30_500L
        assertEquals(60L, st.remainingSeconds(now))
        assertFalse(st.isFinished(now))
    }

    @Test
    fun `走完后isFinished为真且剩余为0`() {
        val st = RestTimerState(endsAt = t0 + 60_000L, totalMs = 60_000L)
        val now = t0 + 60_100L
        assertEquals(0L, st.remainingSeconds(now))
        assertTrue(st.isFinished(now))
    }

    @Test
    fun `暂停冻结剩余继续时从冻结处接着走`() {
        val st = RestTimerState(endsAt = t0 + 90_000L, totalMs = 90_000L)
        val pausedAt = t0 + 20_000L
        val paused = st.pausedAt(pausedAt)
        assertTrue(paused.paused)
        assertEquals(70_000L, paused.pausedRemainingMs)
        // 暂停中无论 now 怎么走，剩余都冻结在 70 秒
        assertEquals(70L, paused.remainingSeconds(pausedAt + 10_000L))
        // 继续：从当下接着走 70 秒
        val resumed = paused.resumedAt(pausedAt + 10_000L)
        assertFalse(resumed.paused)
        assertEquals(pausedAt + 10_000L + 70_000L, resumed.endsAt)
        assertEquals(70L, resumed.remainingSeconds(pausedAt + 10_000L))
    }

    @Test
    fun `重复暂停或对未计时的暂停继续是无操作`() {
        val idle = RestTimerState()
        assertEquals(idle, idle.pausedAt(t0))
        assertEquals(idle, idle.resumedAt(t0))
        val running = RestTimerState(endsAt = t0 + 60_000L, totalMs = 60_000L)
        assertEquals(running, running.resumedAt(t0))
    }

    @Test
    fun `边界时间刚好等于结束时刻算走完`() {
        val st = RestTimerState(endsAt = t0, totalMs = 60_000L)
        assertTrue(st.isFinished(t0))
        assertEquals(0L, st.remainingSeconds(t0))
    }
}
