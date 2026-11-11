package com.qingji.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * ============================================================
 * 轻断食时间计算（v2.0）
 * ------------------------------------------------------------
 * 只做一件事：给定「现在几点」和「进食窗口」，算出
 * 现在是不是断食中、已经断了多久、还剩多久。
 *
 * 为什么不建后台服务：断食状态完全由当前时间推出来，
 * 不需要定时器、不需要后台跑。App 打开时算一次就够，
 * 还省电。
 *
 * 纯函数，方便单元测试（跨零点这种边界最容易算错）。
 * ============================================================
 */
object FastingRules {

    /** 常见方案：断食小时数 */
    val PLANS = listOf(16, 14, 12, 18, 20)

    fun planLabel(hours: Int): String = "$hours:${24 - hours}"

    fun planSubtitle(hours: Int): String = "${hours}h 禁食"

    /**
     * 断食阶段说明（常见代谢科普，不是医疗建议）。
     * 按已断食小时数给一句人话 + 一句解释。
     */
    fun phase(elapsedHours: Double): Pair<String, String> = when {
        elapsedHours < 4 -> "消化吸收" to "刚吃完，身体正在处理血糖和营养"
        elapsedHours < 8 -> "血糖回落" to "胰岛素下降，身体开始动用储备"
        elapsedHours < 12 -> "脂肪动员" to "脂肪分解加速，酮体开始上升"
        elapsedHours < 16 -> "酮体上升" to "以脂肪供能为主，饥饿感通常会减轻"
        else -> "深度断食" to "酮体维持较高水平，注意补水和电解质"
    }

    /**
     * 算当前断食状态。
     *
     * @param now 现在的时间点（只用到一天内的时刻）
     * @param planHours 断食小时数（16 / 14 / 12）
     * @param eatStartMinute 进食窗口开始（当天第几分钟）
     * @param eatEndMinute 进食窗口结束（当天第几分钟）
     * @param enabled 断食功能是否开启
     */
    fun status(
        now: LocalTime,
        planHours: Int,
        eatStartMinute: Int,
        eatEndMinute: Int,
        enabled: Boolean
    ): FastingStatus {
        val nowMinute = now.hour * 60 + now.minute
        val windowStart = eatStartMinute.coerceIn(0, 1439)
        val windowEnd = eatEndMinute.coerceIn(0, 1439)
        val safePlan = planHours.coerceIn(1, 23)

        if (!enabled) {
            return FastingStatus(
                enabled = false,
                active = false,
                elapsedMinutes = 0,
                remainMinutes = 0,
                planHours = safePlan,
                eatStartMinute = windowStart,
                eatEndMinute = windowEnd
            )
        }

        // v1.0.3：支持跨零点进食窗口（如 22:00-06:00，end <= start 视为跨天）。
        // 跨零点时进食段 = [start, 24:00) + [0, end)，断食段 = [end, start)。
        val crossesMidnight = windowEnd <= windowStart

        // 断食总时长（分钟）：窗口的补集
        val fastingTotal = if (crossesMidnight) {
            24 * 60 - (windowStart - windowEnd)
        } else {
            24 * 60 - (windowEnd - windowStart)
        }

        val inWindow = if (crossesMidnight) {
            nowMinute >= windowStart || nowMinute < windowEnd
        } else {
            nowMinute in windowStart until windowEnd
        }
        if (inWindow) {
            // 正在进食窗口里：不算断食中，告诉你下一段断食几点开始
            return FastingStatus(
                enabled = true,
                active = false,
                elapsedMinutes = 0,
                remainMinutes = 0,
                planHours = safePlan,
                eatStartMinute = windowStart,
                eatEndMinute = windowEnd
            )
        }

        // 在断食区间：从最近的进食结束点算起（跨零点时结束点在今天凌晨）
        val elapsed = if (crossesMidnight) {
            // 断食段 [end, start)：end 在今天凌晨，now 在 end..start 之间
            nowMinute - windowEnd
        } else if (nowMinute >= windowEnd) {
            nowMinute - windowEnd
        } else {
            (24 * 60 - windowEnd) + nowMinute
        }
        val remain = (fastingTotal - elapsed).coerceAtLeast(0)

        return FastingStatus(
            enabled = true,
            active = true,
            elapsedMinutes = elapsed.coerceAtMost(fastingTotal),
            remainMinutes = remain,
            planHours = safePlan,
            eatStartMinute = windowStart,
            eatEndMinute = windowEnd
        )
    }

    /** 把「当天第几分钟」格式化成 07:40。 */
    fun minuteLabel(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 分钟数格式化成 09:32。 */
    fun durationLabel(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 完成度 0～1，用来画圆环。 */
    fun progress(status: FastingStatus): Float {
        if (!status.active) return 0f
        val total = status.elapsedMinutes + status.remainMinutes
        if (total <= 0) return 0f
        return (status.elapsedMinutes.toFloat() / total).coerceIn(0f, 1f)
    }

    // ============================================================
    // 自由断食（2026-11-11 专项）
    // ------------------------------------------------------------
    // 与上面的「方案断食」是两条独立的路：
    //   · 方案断食：由「进食窗口 + 当前时间」推状态，没有开始时刻；
    //   · 自由断食：只记一个开始时间戳，断了多久 = 现在 - 开始。
    // 所以这里只做时间算术，不碰 status()、不改任何现有规则。
    // 全部按时间戳算，天然支持跨日、跨多日，不需要「当天」假设。
    // ============================================================

    /** 自由断食已进行的分钟数；开始时间在未来时返回 0（不出现负数）。 */
    fun freeElapsedMinutes(startMillis: Long, nowMillis: Long): Int =
        ((nowMillis - startMillis) / 60_000L).toInt().coerceAtLeast(0)

    /** 落库时的 planHours：四舍五入到小时并夹到 1..23，兼容旧字段语义。 */
    fun freePlanHours(minutes: Int): Int = ((minutes + 30) / 60).coerceIn(1, 23)

    /**
     * 开始时间是否可用。null = 没问题，否则是一句给用户看的话。
     *
     * 上限 72 小时：再长基本是日期选错或忘了结束，不该直接写进历史。
     * 未来时间也不接受 —— 那样瞬间时长会变成 0，看着像 bug。
     */
    fun freeStartIssue(startMillis: Long, nowMillis: Long): String? = when {
        startMillis <= 0L -> "还没选开始时间"
        startMillis > nowMillis -> "开始时间不能晚于现在"
        nowMillis - startMillis > 72L * 60 * 60 * 1000 -> "开始时间超过 72 小时了，确认一下日期有没有选错"
        else -> null
    }

    /** 结束时不足 1 分钟不值得记一条，返回提示语；null = 正常可记录。 */
    fun freeEndIssue(minutes: Int): String? =
        if (minutes < 1) "这段只有不到 1 分钟，没有记录" else null

    /** 自由断食时长文案：14 小时 20 分 / 45 分钟。 */
    fun freeDurationLabel(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        val h = m / 60
        val rest = m % 60
        return when {
            h <= 0 -> "$rest 分钟"
            rest == 0 -> "$h 小时"
            else -> "$h 小时 $rest 分"
        }
    }

    // ============================================================
    // 断食成就（2026-11-11 专项，口径：连续断食天数）
    // ------------------------------------------------------------
    // 「连续」的判定只有一条：从今天往前，一天接一天都有断食记录。
    // 两个刻意的选择：
    //   1. **今天还没记不算断**——从昨天开始数。不然早上打开 App 看到
    //      「连续 0 天」会以为昨天白断了；
    //   2. 只要有记录就算（方案记录在保存设置时落库、自由断食在结束时落库），
    //      不要求 completed=true——那个字段方案记录从来不会写，拿来判定
    //      会让「一直按方案断食」的人永远 0 天。
    // ============================================================

    /** 里程碑：天数 → 名字。和成就页一样只放少量、够得着的档位。 */
    val STREAK_MILESTONES: List<Pair<Int, String>> = listOf(
        3 to "起步",
        7 to "一周",
        14 to "两周",
        30 to "一月",
        60 to "两月",
        100 to "百日"
    )

    /**
     * 连续断食天数。
     *
     * @param fastingDates 有断食记录的日期集合（不必连续、可含未来日期，这里只用今天及以前）
     * @param today 今天
     */
    fun consecutiveDays(fastingDates: Set<LocalDate>, today: LocalDate): Int {
        if (fastingDates.isEmpty()) return 0
        // 今天还没记就从昨天数：白天没到断食结束时间不该把连续记录打回 0
        var cursor = if (today in fastingDates) today else today.minusDays(1)
        var count = 0
        while (cursor in fastingDates) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** 下一个还没达成的里程碑；全达成返回 null。 */
    fun nextMilestone(days: Int): Pair<Int, String>? =
        STREAK_MILESTONES.firstOrNull { days < it.first }

    /** 当前已达到的最高里程碑；一个都没有返回 null。 */
    fun reachedMilestone(days: Int): Pair<Int, String>? =
        STREAK_MILESTONES.lastOrNull { days >= it.first }

    /** 成就卡片上那句话：「再坚持 3 天解锁『一周』」/「全部解锁了」。 */
    fun nextMilestoneText(days: Int): String {
        val next = nextMilestone(days) ?: return "里程碑都解锁了，稳住就行"
        val gap = next.first - days
        return "再坚持 $gap 天解锁「${next.second}」"
    }

    // ============================================================
    // 断食日历（2026-11-11 专项）
    // ------------------------------------------------------------
    // 只做「某个月有哪几天」这一件事，纯函数，界面不自己算闰月和月初星期。
    // 周一开头（国内习惯），前后补 null 对齐整周，行数按需 5～6 行。
    // ============================================================

    /** 月历网格：[null] 表示补位空格。 */
    fun monthCells(month: YearMonth): List<LocalDate?> {
        val days = month.lengthOfMonth()
        val lead = month.atDay(1).dayOfWeek.value - 1   // 周一=1 … 周日=7
        val cells = ArrayList<LocalDate?>(42)
        repeat(lead) { cells.add(null) }
        for (d in 1..days) cells.add(month.atDay(d))
        while (cells.size % 7 != 0) cells.add(null)
        return cells
    }

    /** 某天在日历上显示成什么状态，界面据此上色。 */
    enum class DayMark {
        /** 这天还没到（未来），不可点 */
        FUTURE,
        /** 有断食记录 */
        RECORDED,
        /** 没有记录 */
        EMPTY
    }

    fun dayMark(date: LocalDate, today: LocalDate, recordedDates: Set<LocalDate>): DayMark = when {
        date.isAfter(today) -> DayMark.FUTURE
        date in recordedDates -> DayMark.RECORDED
        else -> DayMark.EMPTY
    }
}

/** 一次算出来的断食状态。 */
data class FastingStatus(
    val enabled: Boolean,
    val active: Boolean,
    /** 已经断食多少分钟 */
    val elapsedMinutes: Int,
    /** 还剩多少分钟到可进食 */
    val remainMinutes: Int,
    val planHours: Int,
    val eatStartMinute: Int,
    val eatEndMinute: Int
) {
    /** 已断食小时数（带小数），给阶段判断用 */
    val elapsedHours: Double get() = elapsedMinutes / 60.0
}
