package com.qingji.app.ai.nudge

import com.qingji.app.domain.WeightPoint
import com.qingji.app.domain.nudge.NudgeFacts
import com.qingji.app.domain.nudge.NudgePolicy
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 规则引擎的唯一输入来源。
 *
 * 抽成接口有两个目的：
 * 1. 规则测试不需要 Room 和 Repository；
 * 2. 以后接 Health Connect / 设备数据时，只换实现不动规则。
 *
 * 实现方只允许「读」——J 阶段写工具仍然全禁，提醒本身也不改任何业务数据。
 */
fun interface NudgeFactsSource {
    suspend fun snapshot(now: LocalDateTime): NudgeFacts
}

/** 用户设置的读取口；真实实现是 DataStore，测试里给假值。 */
fun interface NudgePolicySource {
    suspend fun policy(): NudgePolicy
}

/** 供 UI 展示「下次什么时候可以再提醒」用的时间计算，规则层与界面共用同一套判断。 */
object NudgeTiming {
    /** 从 [now] 起，还要多久才走出静默时段；不在静默期返回 null。 */
    fun silentUntil(now: LocalDateTime, policy: NudgePolicy): LocalDateTime? {
        val minuteOfDay = now.hour * 60 + now.minute
        if (!policy.isSilent(minuteOfDay)) return null
        val today = now.toLocalDate()
        return if (policy.silentStartMinute < policy.silentEndMinute) {
            // 同日窗口，例如 12:00～14:00
            today.atStartOfDay().plusMinutes(policy.silentEndMinute.toLong())
        } else {
            // 跨午夜窗口：当前在「晚上那一段」→ 明天结束；在「凌晨那一段」→ 今天结束
            val endDay: LocalDate =
                if (minuteOfDay >= policy.silentStartMinute) today.plusDays(1) else today
            endDay.atStartOfDay().plusMinutes(policy.silentEndMinute.toLong())
        }
    }
}
