package com.qingji.app.domain

import java.time.LocalTime

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
    val PLANS = listOf(16, 14, 12)

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

        // 进食窗口合法（结束晚于开始）才按窗口算，否则按方案小时数直接推
        val fastingTotal = if (windowEnd > windowStart) {
            24 * 60 - (windowEnd - windowStart)
        } else {
            safePlan * 60
        }

        val inWindow = windowEnd > windowStart && nowMinute in windowStart until windowEnd
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

        // 在断食区间：从「昨天/今天的进食结束」算起
        val elapsed = if (nowMinute >= windowEnd) {
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
