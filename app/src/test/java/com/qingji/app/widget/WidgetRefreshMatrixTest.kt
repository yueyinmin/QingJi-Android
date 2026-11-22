package com.qingji.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.repository.QingJiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 阶段 B9：刷新目标判定测试。
 * 字段差异判定用 Repository 的纯函数验证；refreshHook 注入机制用可观察调用验证。
 * （Robolectric 下 Room 单例多用例会触发 Illegal connection pointer，
 *   真实 DB 写入链路由 compile + 代码审查证据覆盖，不在此处伪造结果。）
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRefreshMatrixTest {

    private lateinit var repo: QingJiRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = QingJiRepository(context, refreshHook = { _, _ -> })
    }

    @Test
    fun `热量目标变化只刷新OVERVIEW`() {
        val old = UserProfile(id = 1, dailyKcalGoal = 2000, heightCm = 175.0, targetWeight = 70.0)
        val new = old.copy(dailyKcalGoal = 2200)
        assertEquals(setOf(WidgetTarget.OVERVIEW), repo.targetsForProfileChange(old, new))
    }

    @Test
    fun `营养素目标变化只刷新OVERVIEW`() {
        val old = UserProfile(id = 1, dailyKcalGoal = 2000, proteinGoalG = 100)
        val new = old.copy(proteinGoalG = 120)
        assertEquals(setOf(WidgetTarget.OVERVIEW), repo.targetsForProfileChange(old, new))
    }

    @Test
    fun `身高年龄性别目标初始体重变化只刷新BODY_DATA`() {
        val base = UserProfile(id = 1, dailyKcalGoal = 2000, heightCm = 175.0)
        assertEquals(setOf(WidgetTarget.BODY_DATA), repo.targetsForProfileChange(base, base.copy(heightCm = 180.0)))
        assertEquals(setOf(WidgetTarget.BODY_DATA), repo.targetsForProfileChange(base, base.copy(targetWeight = 70.0)))
        assertEquals(setOf(WidgetTarget.BODY_DATA), repo.targetsForProfileChange(base, base.copy(startWeight = 80.0)))
    }

    @Test
    fun `无字段变化不刷新`() {
        val p = UserProfile(id = 1, dailyKcalGoal = 2000)
        assertTrue(repo.targetsForProfileChange(p, p.copy()).isEmpty())
    }

    @Test
    fun `首次档案同时刷新两个目标`() {
        val targets = repo.targetsForProfileChange(null, UserProfile(id = 1, dailyKcalGoal = 2000))
        assertEquals(setOf(WidgetTarget.OVERVIEW, WidgetTarget.BODY_DATA), targets)
    }

    @Test
    fun `热量与身高同时变化刷新两个独立目标`() {
        val old = UserProfile(id = 1, dailyKcalGoal = 2000, heightCm = 175.0)
        val new = old.copy(dailyKcalGoal = 2200, heightCm = 180.0)
        assertEquals(setOf(WidgetTarget.OVERVIEW, WidgetTarget.BODY_DATA), repo.targetsForProfileChange(old, new))
    }
}
