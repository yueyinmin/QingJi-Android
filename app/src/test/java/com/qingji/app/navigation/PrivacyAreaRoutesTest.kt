package com.qingji.app.navigation

import com.qingji.app.ui.navigation.QingJiRoutes
import com.qingji.app.ui.navigation.QingJiTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 隐私锁路由覆盖回归测试。
 * 缺陷背景：睡眠页挂了 DiaryPrivacyGate，但「日记区域」路由集合漏了 Sleep，
 * 导致 _inDiaryArea=false，用户输密码认证成功后 onAuthenticated 拒绝解锁 → 一直锁着。
 * 进展 Tab 的三张日记卡同理。
 */
class PrivacyAreaRoutesTest {

    @Test
    fun `睡眠页路由在隐私区域内`() {
        assertTrue(QingJiRoutes.privacyAreaRoutes.contains(QingJiRoutes.Sleep))
    }

    @Test
    fun `进展页路由在隐私区域内`() {
        assertTrue(QingJiRoutes.privacyAreaRoutes.contains(QingJiTab.Progress.route))
    }

    @Test
    fun `日记Tab与编辑页仍在隐私区域内`() {
        assertTrue(QingJiRoutes.privacyAreaRoutes.contains(QingJiTab.Diary.route))
        assertTrue(QingJiRoutes.privacyAreaRoutes.contains(QingJiRoutes.DiaryEdit))
    }

    @Test
    fun `无关页面不在隐私区域`() {
        assertFalse(QingJiRoutes.privacyAreaRoutes.contains(QingJiTab.Home.route))
        assertFalse(QingJiRoutes.privacyAreaRoutes.contains(QingJiTab.Mine.route))
        assertFalse(QingJiRoutes.privacyAreaRoutes.contains(QingJiRoutes.Profile))
    }

    @Test
    fun `所有挂隐私门的页面路由都被覆盖`() {
        // DiaryScreen(Diary tab) / DiaryEditScreen(DiaryEdit) /
        // ProgressCardRenderer 三卡(Progress tab) / SleepScreen(Sleep) —— 逐一核对
        val required = setOf(
            QingJiTab.Diary.route,
            QingJiRoutes.DiaryEdit,
            QingJiTab.Progress.route,
            QingJiRoutes.Sleep
        )
        assertTrue(required.all { it in QingJiRoutes.privacyAreaRoutes })
    }
}
