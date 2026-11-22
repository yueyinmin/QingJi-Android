package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 日记隐私锁状态机测试（阶段 B）。 */
class DiaryPrivacyControllerTest {

    private fun enabledController(available: Boolean = true) =
        DiaryPrivacyController({ available }).also { it.onEnabledChanged(true) }

    @Test
    fun `初次安装默认Disabled且内容不遮蔽`() {
        val c = DiaryPrivacyController()
        assertEquals(DiaryPrivacyState.Disabled, c.state)
        assertFalse(c.shouldMask())
    }

    @Test
    fun `开启后进入日记页为Locked且遮蔽`() {
        val c = enabledController()
        assertEquals(DiaryPrivacyState.Locked, c.state)
        c.enterDiaryArea()
        assertEquals(DiaryPrivacyState.Locked, c.state)
        assertTrue(c.shouldMask())
    }

    @Test
    fun `验证成功解锁内容可见`() {
        val c = enabledController()
        c.enterDiaryArea()
        val gen = c.beginAuthentication()
        assertTrue(c.onAuthenticated(gen))
        assertEquals(DiaryPrivacyState.Unlocked, c.state)
        assertFalse(c.shouldMask())
    }

    @Test
    fun `失败与取消均保持锁定`() {
        val c = enabledController()
        c.enterDiaryArea()
        val g1 = c.beginAuthentication()
        assertTrue(c.onAuthenticationFailed(g1))
        assertEquals(DiaryPrivacyState.Locked, c.state)
        val g2 = c.beginAuthentication()
        assertTrue(c.onCanceled(g2))
        assertEquals(DiaryPrivacyState.Locked, c.state)
    }

    @Test
    fun `离开日记区域立即锁定`() {
        val c = enabledController()
        c.enterDiaryArea()
        val g = c.beginAuthentication()
        c.onAuthenticated(g)
        assertEquals(DiaryPrivacyState.Unlocked, c.state)
        c.leaveDiaryArea()
        assertEquals(DiaryPrivacyState.Locked, c.state)
        assertTrue(c.shouldMask())
    }

    @Test
    fun `日记内部切换不重新锁定`() {
        val c = enabledController()
        c.enterDiaryArea()
        c.onAuthenticated(c.beginAuthentication())
        c.enterDiaryArea() // 模拟内部再进入（编辑页返回等）
        assertEquals(DiaryPrivacyState.Unlocked, c.state)
    }

    @Test
    fun `迟到认证回调被generation丢弃`() {
        val c = enabledController()
        c.enterDiaryArea()
        val oldGen = c.beginAuthentication()
        c.leaveDiaryArea()        // 离开 → generation 自增
        c.enterDiaryArea()        // 再进入 → Locked
        assertFalse("离开前的在途回调不能解锁", c.onAuthenticated(oldGen))
        assertEquals(DiaryPrivacyState.Locked, c.state)
    }

    @Test
    fun `生物不可用时保持Unavailable且内容遮蔽`() {
        val c = enabledController(available = false)
        assertEquals(DiaryPrivacyState.Unavailable, c.state)
        c.enterDiaryArea()
        val gen = c.beginAuthentication()
        assertEquals("能力不足时不得开始认证", -1L, gen)
        assertFalse("伪造成功回调不得解锁", c.onAuthenticated(gen))
        assertEquals(DiaryPrivacyState.Unavailable, c.state)
        assertTrue(c.shouldMask())
    }

    @Test
    fun `后台遮罩不改变解锁会话`() {
        val c = enabledController()
        c.enterDiaryArea()
        c.onAuthenticated(c.beginAuthentication())
        c.onEnterBackground()
        assertTrue(c.shouldMask())          // 后台遮蔽
        c.onForeground()
        assertFalse(c.shouldMask())         // 回前台仍在会话中
        assertEquals(DiaryPrivacyState.Unlocked, c.state)
    }

    @Test
    fun `进程重建resetSession必须回到Locked`() {
        val c = enabledController()
        c.enterDiaryArea()
        c.onAuthenticated(c.beginAuthentication())
        c.resetSession()
        assertEquals(DiaryPrivacyState.Locked, c.state)
        assertTrue(c.shouldMask())
    }

    @Test
    fun `关闭隐私锁后不遮蔽且不再锁定`() {
        val c = enabledController()
        c.onEnabledChanged(false)
        assertEquals(DiaryPrivacyState.Disabled, c.state)
        c.enterDiaryArea()
        assertFalse(c.shouldMask())
    }
}
