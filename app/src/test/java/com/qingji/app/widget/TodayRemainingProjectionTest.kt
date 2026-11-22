package com.qingji.app.widget

import com.qingji.app.domain.ExerciseCompensation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 阶段 C9：今日剩余独立投影与口径测试。 */
class TodayRemainingProjectionTest {

    @Test
    fun `schema版本独立于大快照`() {
        assertEquals(2, TodayRemainingWidgetData.SCHEMA_VERSION)
        assertTrue(TodayRemainingWidgetData.SCHEMA_VERSION != WidgetData.SCHEMA_VERSION)
    }

    @Test
    fun `热量计算与domain口径一致`() {
        val goal = 2200; val eaten = 1120; val kcal = 400.0
        val bonus = ExerciseCompensation.bonusKcal(kcal, true, 50)
        assertEquals(200, bonus)
        val remaining = ExerciseCompensation.remainingWithBonus(goal, eaten, kcal, true, 50)
        assertEquals(goal - eaten + bonus, remaining)
        val d = TodayRemainingWidgetData(
            goalKcal = goal, eatenKcal = eaten, exerciseKcal = kcal.toInt(),
            bonusKcal = bonus, remainingKcal = remaining,
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS, hasSnapshot = true
        )
        assertEquals(remaining, d.remainingKcal)
        assertTrue(d.canShowRemaining)
    }

    @Test
    fun `目标未设置时不可展示且不伪造2000`() {
        val d = TodayRemainingWidgetData(goalKcal = 0, goalConfigured = false,
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS, hasSnapshot = true)
        assertEquals(0, d.goalKcal)
        assertFalse(d.canShowRemaining)
    }

    @Test
    fun `局部失败与负数状态`() {
        val failed = TodayRemainingWidgetData(profileState = ItemState.FAILED, hasSnapshot = true)
        assertFalse(failed.canShowRemaining)
        val over = TodayRemainingWidgetData(remainingKcal = -300, goalConfigured = true,
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS, hasSnapshot = true)
        assertTrue(over.canShowRemaining)
        assertTrue(over.remainingKcal < 0)
    }

    @Test
    fun `stale快照不可当实时数据`() {
        val d = TodayRemainingWidgetData(stale = true, hasSnapshot = true,
            profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
            exerciseState = ItemState.SUCCESS)
        assertFalse(d.canShowRemaining)
    }
}
