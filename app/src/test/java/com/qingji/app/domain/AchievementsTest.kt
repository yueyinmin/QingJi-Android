package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就规则（M6）。
 * 关键边界：
 * 1. 一条数据都没有时，所有徽章都必须是「未解锁」，不能靠 0 >= 0 误判；
 * 2. 连续天数从今天或昨天开始数，断了就停；
 * 3. 进度值不能超过 1（界面画进度条不能溢出）。
 */
class AchievementsTest {

    @Test
    fun `空数据时全部未解锁`() {
        val list = Achievements.list(AchievementStats())
        assertEquals(11, list.size)
        assertTrue(list.none { it.unlocked })
        assertTrue(list.all { it.progress == 0f })
    }

    @Test
    fun `记了第一条饮食就解锁开张`() {
        val list = Achievements.list(AchievementStats(foodLogCount = 1))
        val first = list.first { it.id == "first_bite" }
        assertTrue(first.unlocked)
        assertEquals("1 / 1", first.progressText)
    }

    @Test
    fun `进度最大不超过一`() {
        val list = Achievements.list(
            AchievementStats(foodLogCount = 500, weightLogCount = 99, diaryCount = 99)
        )
        assertTrue(list.all { it.progress <= 1f })
        // 只给了三类数字，所以只有对应的徽章该解锁（连续天数、减重没给，仍为未解锁）
        assertTrue(list.first { it.id == "food_100" }.unlocked)
        assertTrue(list.first { it.id == "diary_30" }.unlocked)
        assertTrue(list.first { it.id == "weigh_10" }.unlocked)
        assertFalse(list.first { it.id == "streak_7" }.unlocked)
        assertFalse(list.first { it.id == "lose_1kg" }.unlocked)
    }

    @Test
    fun `没减重时减重徽章不解锁`() {
        val list = Achievements.list(AchievementStats(weightLostKg = 0.4))
        val badge = list.first { it.id == "lose_1kg" }
        assertFalse(badge.unlocked)
        assertEquals(0, badge.current)
    }

    @Test
    fun `连续天数从今天往前数`() {
        val dates = setOf("2026-11-07", "2026-11-06", "2026-11-05")
        assertEquals(
            3,
            Achievements.streakDays(dates, today = "2026-11-07", yesterday = "2026-11-06")
        )
    }

    @Test
    fun `今天还没记时从昨天数`() {
        val dates = setOf("2026-11-06", "2026-11-05")
        assertEquals(
            2,
            Achievements.streakDays(dates, today = "2026-11-07", yesterday = "2026-11-06")
        )
    }

    @Test
    fun `中间断一天就停`() {
        val dates = setOf("2026-11-07", "2026-11-05", "2026-11-04")
        assertEquals(
            1,
            Achievements.streakDays(dates, today = "2026-11-07", yesterday = "2026-11-06")
        )
    }

    @Test
    fun `完全没记录时连续天数为零`() {
        assertEquals(
            0,
            Achievements.streakDays(emptySet(), today = "2026-11-07", yesterday = "2026-11-06")
        )
    }
}
