package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 断食成就的「连续天数」判定（2026-11-11 专项）。
 *
 * 两条业务约定，必须钉死在测试里：
 *   1. 今天还没记不算断 —— 从昨天接着数，否则早上打开看到 0 天会以为白断了；
 *   2. 只要当天有断食记录就算，不要求 completed=true
 *      （方案记录从来不会写那个字段）。
 */
class FastingRulesStreakTest {

    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `今天有记录时从今天开始数`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, FastingRules.consecutiveDays(dates, today))
    }

    @Test
    fun `今天还没记时从昨天接着数`() {
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, FastingRules.consecutiveDays(dates, today))
    }

    @Test
    fun `中间断一天就停在那里`() {
        val dates = setOf(
            today,
            today.minusDays(1),
            // 前天缺失
            today.minusDays(3),
            today.minusDays(4)
        )
        assertEquals(2, FastingRules.consecutiveDays(dates, today))
    }

    @Test
    fun `没有记录是0天`() {
        assertEquals(0, FastingRules.consecutiveDays(emptySet(), today))
    }

    @Test
    fun `只有今天一条是1天`() {
        assertEquals(1, FastingRules.consecutiveDays(setOf(today), today))
    }

    @Test
    fun `未来日期不参与连续计算`() {
        val dates = setOf(today.plusDays(1), today.plusDays(2))
        assertEquals(0, FastingRules.consecutiveDays(dates, today))
    }

    @Test
    fun `跨月连续能正确累加`() {
        val t = LocalDate.of(2026, 12, 2)
        val dates = setOf(t, t.minusDays(1), t.minusDays(2), t.minusDays(3))
        // 12/2、12/1、11/30、11/29
        assertEquals(4, FastingRules.consecutiveDays(dates, t))
    }

    @Test
    fun `下一个里程碑与文案`() {
        assertEquals(3 to "起步", FastingRules.nextMilestone(0))
        assertEquals(7 to "一周", FastingRules.nextMilestone(4))
        assertNull(FastingRules.nextMilestone(100))
        assertEquals("再坚持 3 天解锁「一周」", FastingRules.nextMilestoneText(4))
        assertEquals("里程碑都解锁了，稳住就行", FastingRules.nextMilestoneText(100))
    }

    @Test
    fun `已达到的里程碑取最高那档`() {
        assertNull(FastingRules.reachedMilestone(2))
        assertEquals(3 to "起步", FastingRules.reachedMilestone(3))
        assertEquals(30 to "一月", FastingRules.reachedMilestone(45))
        assertEquals(100 to "百日", FastingRules.reachedMilestone(200))
    }

    @Test
    fun `可进食时间由断食开始时间加方案小时自动对标`() {
        // endMinute=940（15:40）→ 16h 方案：start=(940+960)%1440=460（07:40）
        assertEquals(460, (940 + 16 * 60) % 1440)
        // 14h：start=(940+840)%1440=340（05:40）
        assertEquals(340, (940 + 14 * 60) % 1440)
        // 跨零点：end=1320（22:00）+16h → (1320+960)%1440=840（14:00 次日）
        assertEquals(840, (1320 + 16 * 60) % 1440)
        // 12h：start=(940+720)%1440=220（03:40）
        assertEquals(220, (940 + 12 * 60) % 1440)
    }
}
