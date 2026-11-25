package com.qingji.app.domain.nudge

import com.qingji.app.domain.WeightPoint
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * J 阶段：主动 AI 管家的四类提醒。
 *
 * 这里的每一条都必须是「本地数据能判定的事实」，不是模型猜的：
 * - 记录不完整：今天几乎没写记录；
 * - 蛋白不足：今天吃了两餐以上，但蛋白离目标差得多；
 * - 计划未完成：今天计划里有训练，但一条训练都没记；
 * - 体重偏离：近 7 天在往目标的反方向走，或者卡住不动。
 *
 * 优先级数字越大越先提醒（一天最多一条，所以顺序很重要）。
 */
enum class NudgeKind(val key: String, val priority: Int, val label: String) {
    WEIGHT_DEVIATION("weight_deviation", 90, "体重偏离"),
    PLAN_NOT_DONE("plan_not_done", 70, "计划未完成"),
    PROTEIN_LOW("protein_low", 60, "蛋白不足"),
    RECORD_INCOMPLETE("record_incomplete", 40, "记录不完整");

    companion object {
        fun fromKey(key: String): NudgeKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 触发细节（同一种提醒内部还要分情况，文案和给 AI 的提示都不一样）。
 *
 * 用枚举而不是自由文本，是为了让规则层可断言、也让文案可统一维护。
 */
enum class NudgeReason(val key: String) {
    /** 今天饮食记录 0～1 条 */
    FOOD_LOG_MISSING("food_log_missing"),

    /** 蛋白低于目标的 60% */
    PROTEIN_BELOW_TARGET("protein_below_target"),

    /** 今天有计划安排但没练 */
    PLAN_SKIPPED("plan_skipped"),

    /** 近期体重在往目标反方向走 */
    WEIGHT_WRONG_DIRECTION("weight_wrong_direction"),

    /** 体重连续两周几乎不动，离目标还有距离 */
    WEIGHT_PLATEAU("weight_plateau");

    companion object {
        fun fromKey(key: String): NudgeReason? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 本地事实快照：规则层唯一输入。
 *
 * 全部来自业务库只读查询，不含任何模型输出；`null` / `0` 代表「没这个数据」，
 * 规则层遇到缺数据一律不提醒，不用默认值硬凑。
 */
data class NudgeFacts(
    val now: LocalDateTime,
    val today: LocalDate,
    /** 今天饮食记录条数（一条食物算一条） */
    val foodLogCountToday: Int = 0,
    /** 今天已记录的餐次数量（早/午/晚/加餐各自出现才算一次） */
    val mealCountToday: Int = 0,
    val kcalToday: Double = 0.0,
    val proteinTodayG: Double = 0.0,
    /** 0 = 用户没设蛋白目标，规则层直接不判蛋白 */
    val proteinGoalG: Int = 0,
    val hasActivePlan: Boolean = false,
    /** 今天计划里的动作数；0 = 休息日或没有计划 */
    val plannedExerciseCountToday: Int = 0,
    val trainedToday: Boolean = false,
    /** 近 28 天体重记录（按日期升序，来自 weight_log） */
    val weightPoints: List<WeightPoint> = emptyList(),
    /** 0 = 没设目标体重 */
    val targetWeightKg: Double = 0.0,
    /** 身高，仅用于把体重变化换算成合理量级（可缺，缺了不影响判定） */
    val heightCm: Double = 0.0
) {
    /** 最近一条体重记录的日期；没有记录就是 null */
    val latestWeightDate: LocalDate? get() = weightPoints.lastOrNull()?.date

    val latestWeightKg: Double? get() = weightPoints.lastOrNull()?.weightKg
}

/** 一条已经发过的提醒（频控输入 + 历史列表展示）。 */
data class NudgeHistoryItem(
    val id: String,
    val kind: NudgeKind,
    /** 发送当天的 yyyy-MM-dd，用于「今天已经提醒过」判断 */
    val dateKey: String,
    val shownAt: Long,
    val outcome: NudgeOutcome = NudgeOutcome.SHOWN
)

enum class NudgeOutcome { SHOWN, DISMISSED, ACTED }

/** 规则引擎算出来的一条候选提醒（还没落库）。 */
data class NudgeCandidate(
    val kind: NudgeKind,
    val reason: NudgeReason,
    /** 一句话标题，纯本地生成，不经过模型 */
    val title: String,
    /** 正文：只陈述本地数字，不做因果推断 */
    val body: String,
    /** 是否需要更进一步的分析（true 时 UI 才给「让管家看看」入口） */
    val needsAnalysis: Boolean,
    /** 关键数字，方便测试断言与写进 AI 上下文 */
    val facts: Map<String, String> = emptyMap()
)

/** 用户设置 + 频控参数，统一由 [NudgePolicy] 承载，规则层只读。 */
data class NudgePolicy(
    val enabled: Boolean = true,
    /** 用户单独关掉的类别 */
    val disabledKinds: Set<NudgeKind> = emptySet(),
    /** 每天最多几条，默认 1 条；上限 2 条，避免打扰 */
    val dailyLimit: Int = 1,
    /** 静默时段（分钟数，0 = 00:00）；支持跨午夜，如 22:00 → 08:00 */
    val silentStartMinute: Int = 22 * 60,
    val silentEndMinute: Int = 8 * 60,
    /** 同类提醒的冷却小时数；体重偏离要更久，避免天天念 */
    val cooldownHours: Int = 20
) {
    fun cooldownFor(kind: NudgeKind): Int =
        if (kind == NudgeKind.WEIGHT_DEVIATION) maxOf(cooldownHours, 72) else cooldownHours

    /** 是否在静默时段内（跨午夜也算）。 */
    fun isSilent(minuteOfDay: Int): Boolean {
        if (silentStartMinute == silentEndMinute) return false
        return if (silentStartMinute < silentEndMinute) {
            minuteOfDay in silentStartMinute until silentEndMinute
        } else {
            minuteOfDay >= silentStartMinute || minuteOfDay < silentEndMinute
        }
    }
}

/** 不提醒的原因，全部可解释（UI 与日志都直接用它）。 */
enum class NudgeSkipReason {
    /** 用户关了总开关 */
    DISABLED,

    /** 处在静默时段 */
    SILENT_HOURS,

    /** 今天的额度已经用完 */
    DAILY_LIMIT,

    /** 命中规则但该类还在冷却，或今天已发过 */
    COOLDOWN,

    /** 数据不够，不敢下判断 */
    INSUFFICIENT_DATA,

    /** 数据够，但今天状态正常，没什么可提醒 */
    NO_TRIGGER
}

sealed interface NudgeDecision {
    data class Show(val candidate: NudgeCandidate) : NudgeDecision

    data class Skip(
        val reason: NudgeSkipReason,
        /** 给日志/调试看的一句话；UI 不直接展示 */
        val detail: String = ""
    ) : NudgeDecision
}

/**
 * 一条已经落库的提醒（历史列表 + 频控 + 用户反馈）。
 *
 * 把 [candidate] 摊平成字段而不是嵌一层，是为了让存储层转换简单、
 * 也让「以前发过什么」在数据库里一眼可读。
 */
data class NudgeRecord(
    val id: String,
    val kind: NudgeKind,
    val reason: NudgeReason,
    val title: String,
    val body: String,
    val facts: Map<String, String> = emptyMap(),
    val needsAnalysis: Boolean = false,
    /** 发送当天 yyyy-MM-dd */
    val dateKey: String,
    val outcome: NudgeOutcome = NudgeOutcome.SHOWN,
    val shownAt: Long,
    val updatedAt: Long
) {
    fun toHistoryItem(): NudgeHistoryItem =
        NudgeHistoryItem(id = id, kind = kind, dateKey = dateKey, shownAt = shownAt, outcome = outcome)
}

