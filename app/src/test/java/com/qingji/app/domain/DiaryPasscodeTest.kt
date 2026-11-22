package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 数字密码安全原语与失败限制测试（阶段 C 逻辑层）。 */
class DiaryPasscodeTest {

    @Test
    fun `密码格式规则前后端一致`() {
        assertTrue(DiaryPasscode.isValidFormat("1234"))
        assertTrue(DiaryPasscode.isValidFormat("12345678"))
        assertFalse(DiaryPasscode.isValidFormat("123"))
        assertFalse(DiaryPasscode.isValidFormat("123456789"))
        assertFalse(DiaryPasscode.isValidFormat("12ab"))
    }

    @Test
    fun `正确密码验证成功且不含明文`() {
        val stored = DiaryPasscode.create("2468")
        assertTrue(DiaryPasscode.verify("2468", stored))
        assertFalse(stored.saltB64.contains("2468"))
        assertFalse(stored.hashB64.contains("2468"))
        assertEquals(DiaryPasscode.ITERATIONS, stored.iterations)
    }

    @Test
    fun `错误密码验证失败`() {
        val stored = DiaryPasscode.create("1357")
        assertFalse(DiaryPasscode.verify("1358", stored))
        assertFalse(DiaryPasscode.verify("0000", stored))
    }

    @Test
    fun `相同密码不同盐产生不同hash`() {
        val a = DiaryPasscode.create("8888")
        val b = DiaryPasscode.create("8888")
        assertNotEquals(a.saltB64, b.saltB64)
        assertNotEquals(a.hashB64, b.hashB64)
        assertTrue(DiaryPasscode.verify("8888", a))
        assertTrue(DiaryPasscode.verify("8888", b))
    }

    @Test
    fun `失败次数超限触发冷却且冷却中不可尝试`() {
        var now = 1000L
        val guard = DiaryPasscodeGuard(maxAttempts = 3, cooldownMs = 5000L, clock = { now })
        assertTrue(guard.canAttempt())
        guard.onFailure(); guard.onFailure(); guard.onFailure()
        assertFalse("3次失败后应冷却", guard.canAttempt())
        assertEquals(5000L, guard.cooldownRemainingMs)
        now = 6001L
        assertTrue("冷却结束后恢复", guard.canAttempt())
        guard.onSuccess()
        assertEquals(3, guard.remainingAttempts)
    }

    @Test
    fun `成功即清零失败计数`() {
        val guard = DiaryPasscodeGuard(maxAttempts = 5, clock = { 100L })
        guard.onFailure(); guard.onFailure()
        assertEquals(3, guard.remainingAttempts)
        guard.onSuccess()
        assertEquals(5, guard.remainingAttempts)
    }
}
