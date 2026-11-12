package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自由断食的时间规则（2026-11-11 专项）。
 *
 * 这些是纯算术，不依赖 Android / DataStore，所以在电脑上直接跑。
 * 重点盯三类边界：跨日、开始时间非法、时长太短。
 */
class FastingRulesFreeTest {

    private val hour = 60 * 60 * 1000L
    private val minute = 60 * 1000L

    @Test
    fun `同日计时按分钟算`() {
        val start = 1_700_000_000_000L
        assertEquals(90, FastingRules.freeElapsedMinutes(start, start + 90 * minute))
    }

    @Test
    fun `跨日计时不会归零`() {
        // 昨晚 20:00 开始，今早 10:00 结束 → 14 小时
        val start = 1_700_000_000_000L
        assertEquals(14 * 60, FastingRules.freeElapsedMinutes(start, start + 14 * hour))
    }

    @Test
    fun `跨多日也照实算`() {
        val start = 1_700_000_000_000L
        assertEquals(50 * 60, FastingRules.freeElapsedMinutes(start, start + 50 * hour))
    }

    @Test
    fun `开始时间在未来时返回0而不是负数`() {
        val now = 1_700_000_000_000L
        assertEquals(0, FastingRules.freeElapsedMinutes(now + hour, now))
    }

    @Test
    fun `落库小时数四舍五入并夹在1到23`() {
        assertEquals(14, FastingRules.freePlanHours(14 * 60))
        assertEquals(15, FastingRules.freePlanHours(14 * 60 + 40)) // 14h40m 进位
        assertEquals(1, FastingRules.freePlanHours(1))
        assertEquals(23, FastingRules.freePlanHours(30 * 60))      // 超一天也不越过旧字段上限
    }

    @Test
    fun `开始时间非法时给出可读原因`() {
        val now = 1_700_000_000_000L
        assertNotNull(FastingRules.freeStartIssue(0L, now))
        // 未来允许（预约），只有约到 7 天外才拒绝
        assertNull(FastingRules.freeStartIssue(now + hour, now))
        assertNotNull(FastingRules.freeStartIssue(now + 8 * 24 * hour, now))
        assertNull(FastingRules.freeStartIssue(now + 7 * 24 * hour, now))
        // 2026-11-12：今天以前的日期直接拒绝
        assertNotNull(FastingRules.freeStartIssue(now - 12 * hour, now))
        assertNotNull(FastingRules.freeStartIssue(now - 73 * hour, now))
        // 今天 0 点起允许
        val midnight = java.time.Instant.ofEpochMilli(now)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertNull(FastingRules.freeStartIssue(midnight, now))
    }

    @Test
    fun `预约状态判定与倒计时文案`() {
        val now = 1_700_000_000_000L
        assertTrue(FastingRules.freeIsScheduled(now + hour, now))
        assertTrue(!FastingRules.freeIsScheduled(now - hour, now))
        assertTrue(FastingRules.freeScheduleLabel(now + 3 * hour, now).contains("还有 3 小时"))
        assertTrue(FastingRules.freeScheduleLabel(now + hour, now).contains("开始"))
    }

    @Test
    fun `过去时间现在一律拒绝`() {
        // 2026-11-12 起：自由断食开始时间不允许选今天以前，72 小时规则随之退役
        val now = 1_700_000_000_000L
        assertNotNull(FastingRules.freeStartIssue(now - 72 * hour, now))
        assertNotNull(FastingRules.freeStartIssue(now - 12 * hour, now))
    }

    @Test
    fun `不足一分钟不记录`() {
        assertNotNull(FastingRules.freeEndIssue(0))
        assertNull(FastingRules.freeEndIssue(1))
    }

    @Test
    fun `时长文案带小时和分钟`() {
        assertEquals("45 分钟", FastingRules.freeDurationLabel(45))
        assertEquals("14 小时", FastingRules.freeDurationLabel(14 * 60))
        assertEquals("14 小时 20 分", FastingRules.freeDurationLabel(14 * 60 + 20))
        assertTrue(FastingRules.freeDurationLabel(0).contains("0 分钟"))
    }
}
