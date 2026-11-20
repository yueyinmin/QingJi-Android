package com.qingji.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * ============================================================
 * 自律管理：周期计时引擎（SD-004，纯函数、可单测）
 * ------------------------------------------------------------
 * 设计铁律：
 * 1. 以「日期」为单位算天数，绝不用每秒递增的计数器去存天数；
 * 2. 当前周期天数 = 今天 - 周期开始日 + 1；
 * 3. 历史最佳 = 所有已结束周期和当前周期里的最大天数；
 * 4. 不允许出现负数天数；
 * 5. 跨天 / 跨月 / 跨年 / 闰年 / 时区统一交给 LocalDate 处理
 *    （LocalDate 是本地时区的日历日，天然正确）；
 * 6. 未打卡、未记录绝不在这里被当成偏离 —— 引擎只算「从某天到某天有多少天」。
 * ============================================================
 */

object SelfDisciplineEngine {

    /**
     * 单个周期的天数。
     * @param startAt 周期开始日（含）
     * @param endAt   周期结束日（含）；null 表示进行中，按 today 算
     * @param today   本地今天，默认 LocalDate.now()
     */
    fun periodDays(
        startAt: LocalDate,
        endAt: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): Int {
        val end = endAt ?: today
        // end 早于 start（数据异常）按 0 处理，绝不给负数
        if (end.isBefore(startAt)) return 0
        return ChronoUnit.DAYS.between(startAt, end).toInt() + 1
    }

    /**
     * 当前进行中周期已坚持天数。没有进行中周期返回 0。
     */
    fun currentPeriodDays(
        activeStartAt: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (activeStartAt == null) return 0
        return periodDays(activeStartAt, null, today)
    }

    /**
     * 历史最佳周期天数：所有周期里的最大天数（含进行中）。无周期返回 0。
     */
    fun bestPeriodDays(
        periods: List<PeriodInput>,
        today: LocalDate = LocalDate.now()
    ): Int = periods.maxOfOrNull { periodDays(it.startAt, it.endAt, today) } ?: 0

    /**
     * 全部符合目标天数：统计所有打卡里 status = on_target 的条数。
     * 未打卡的天不计入，符合「没记录不能自动算成功」。
     */
    fun onTargetDays(
        checkinStatuses: List<String>
    ): Int = checkinStatuses.count { it == SelfDisciplineContracts.STATUS_ON_TARGET }

    /**
     * 目标偏离次数：统计所有打卡里 status = off_target 的条数。
     */
    fun deviationCount(
        checkinStatuses: List<String>
    ): Int = checkinStatuses.count { it == SelfDisciplineContracts.STATUS_OFF_TARGET }

    /**
     * 连续记录天数：从今天往回数，连续有打卡（on_target / off_target 都算，skip 不算）的天数。
     * 今天没记录就从昨天算起。
     */
    fun currentStreak(
        recordedDates: Set<LocalDate>,
        today: LocalDate = LocalDate.now()
    ): Int {
        var day = today
        if (day !in recordedDates) day = day.minusDays(1)
        var count = 0
        while (day in recordedDates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** 轻量周期输入，避免引擎依赖 Room 实体。 */
    data class PeriodInput(
        val startAt: LocalDate,
        val endAt: LocalDate?
    )
}
