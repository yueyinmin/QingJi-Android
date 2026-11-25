package com.qingji.app.domain.nudge

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * J 阶段：主动提醒的规则引擎。
 *
 * 三条硬约束（对应路线图验收「本地规则先判断，数据充分才调 AI」）：
 * 1. 只做本地判断，这里不出现任何网络/模型调用；
 * 2. 数据不够就不说话（宁可沉默，不猜）；
 * 3. 一天最多一条，同类还有冷却时间，静默时段一律闭嘴。
 *
 * 纯函数：同样的 facts + policy + history 永远得到同样的结论，方便单测。
 */
object NudgeRuleEngine {

    /** 各规则的触发时间点（小时）。晚于这个点才判断「今天是不是没做」。 */
    private const val RECORD_CHECK_HOUR = 20
    private const val PLAN_CHECK_HOUR = 20
    private const val PROTEIN_CHECK_HOUR = 19

    /** 蛋白达标线：低于目标 60% 才提醒，避免每顿都唠叨。 */
    private const val PROTEIN_ALERT_RATIO = 0.6

    /** 体重方向相反的门槛：7 天均值相对前 20 天漂移超过 0.4kg 才算。 */
    private const val WEIGHT_DRIFT_KG = 0.4

    /** 停滞判定：14 天内最高最低相差不超过 0.3kg。 */
    private const val WEIGHT_PLATEAU_RANGE_KG = 0.3

    /** 离目标不到 0.5kg 就不再说「偏离」，否则永远收不了尾。 */
    private const val WEIGHT_NEAR_TARGET_KG = 0.5

    /** 停滞提醒要求离目标还有 1kg 以上。 */
    private const val WEIGHT_PLATEAU_MIN_GAP_KG = 1.0

    /** 体重记录超过 3 天没更新，就不判方向（数据太旧）。 */
    private const val WEIGHT_STALE_DAYS = 3L

    fun evaluate(
        facts: NudgeFacts,
        policy: NudgePolicy,
        history: List<NudgeHistoryItem>
    ): NudgeDecision {
        if (!policy.enabled) {
            return NudgeDecision.Skip(NudgeSkipReason.DISABLED, "用户关闭了主动提醒")
        }

        val minuteOfDay = facts.now.hour * 60 + facts.now.minute
        if (policy.isSilent(minuteOfDay)) {
            return NudgeDecision.Skip(
                NudgeSkipReason.SILENT_HOURS,
                "静默时段 ${formatMinute(policy.silentStartMinute)}～${formatMinute(policy.silentEndMinute)}"
            )
        }

        val todayKey = facts.today.toString()
        val shownToday = history.count { it.dateKey == todayKey }
        if (shownToday >= policy.dailyLimit.coerceAtLeast(0)) {
            return NudgeDecision.Skip(
                NudgeSkipReason.DAILY_LIMIT,
                "今天已经提醒过 $shownToday 条，上限 ${policy.dailyLimit}"
            )
        }

        val evaluation = buildCandidates(facts, policy.disabledKinds)
        if (evaluation.candidates.isEmpty()) {
            // 一条规则都没能做出判断 = 数据不够；有规则判断过且都正常 = 确实没事。
            return NudgeDecision.Skip(
                if (evaluation.judgedKinds.isEmpty()) {
                    NudgeSkipReason.INSUFFICIENT_DATA
                } else {
                    NudgeSkipReason.NO_TRIGGER
                },
                evaluation.insufficientReasons.joinToString("；")
            )
        }

        // 冷却过滤：今天已发过的、以及同类还没过冷却期的一律跳过。
        val ready = evaluation.candidates.filter { candidate ->
            val last = history
                .filter { it.kind == candidate.kind }
                .maxByOrNull { it.shownAt }
            last == null || (
                last.dateKey != todayKey &&
                    facts.now.toEpochMillis() - last.shownAt >=
                    policy.cooldownFor(candidate.kind) * 60L * 60L * 1000L
                )
        }

        if (ready.isEmpty()) {
            return NudgeDecision.Skip(
                NudgeSkipReason.COOLDOWN,
                "候选 ${evaluation.candidates.size} 条都在冷却期内"
            )
        }

        // candidates 已按优先级降序排好，取第一条即可（同优先级按枚举声明顺序）。
        return NudgeDecision.Show(ready.first())
    }

    private data class Evaluation(
        val candidates: List<NudgeCandidate>,
        /** 真正做出判断（触发或确认正常）的类别；用来区分「没事」和「数据不够」 */
        val judgedKinds: Set<NudgeKind>,
        val insufficientReasons: List<String>
    )

    private fun buildCandidates(facts: NudgeFacts, disabled: Set<NudgeKind>): Evaluation {
        val candidates = mutableListOf<NudgeCandidate>()
        val judged = mutableSetOf<NudgeKind>()
        val insufficient = mutableListOf<String>()

        fun allowed(kind: NudgeKind) = kind !in disabled

        // 1) 计划未完成
        if (allowed(NudgeKind.PLAN_NOT_DONE)) {
            judged += NudgeKind.PLAN_NOT_DONE
            when {
                !facts.hasActivePlan -> Unit // 没启用计划，不算「未完成」
                facts.plannedExerciseCountToday <= 0 -> Unit // 休息日
                facts.trainedToday -> Unit
                facts.now.hour < PLAN_CHECK_HOUR -> Unit
                else -> candidates += NudgeCandidate(
                    kind = NudgeKind.PLAN_NOT_DONE,
                    reason = NudgeReason.PLAN_SKIPPED,
                    title = "今天的训练还没记",
                    body = "今天计划里有 ${facts.plannedExerciseCountToday} 个动作，" +
                        "但还没看到训练记录。练完了记得记一下。",
                    needsAnalysis = false,
                    facts = mapOf(
                        "plannedExercises" to facts.plannedExerciseCountToday.toString()
                    )
                )
            }
        }

        // 2) 蛋白不足
        if (allowed(NudgeKind.PROTEIN_LOW)) {
            judged += NudgeKind.PROTEIN_LOW
            when {
                facts.proteinGoalG <= 0 -> {
                    judged -= NudgeKind.PROTEIN_LOW
                    insufficient += "没设蛋白目标，不判断蛋白"
                }
                facts.mealCountToday < 2 -> {
                    judged -= NudgeKind.PROTEIN_LOW
                    insufficient += "今天只记录了 ${facts.mealCountToday} 餐，不判断蛋白"
                }
                facts.now.hour < PROTEIN_CHECK_HOUR -> Unit
                facts.proteinTodayG >= facts.proteinGoalG * PROTEIN_ALERT_RATIO -> Unit
                else -> {
                    val gap = (facts.proteinGoalG - facts.proteinTodayG).coerceAtLeast(0.0)
                    candidates += NudgeCandidate(
                        kind = NudgeKind.PROTEIN_LOW,
                        reason = NudgeReason.PROTEIN_BELOW_TARGET,
                        title = "蛋白还差一截",
                        body = "今天蛋白 ${fmt(facts.proteinTodayG)}g，目标 ${facts.proteinGoalG}g，" +
                            "还差约 ${fmt(gap)}g。",
                        needsAnalysis = true,
                        facts = mapOf(
                            "proteinG" to fmt(facts.proteinTodayG),
                            "proteinGoalG" to facts.proteinGoalG.toString(),
                            "gapG" to fmt(gap)
                        )
                    )
                }
            }
        }

        // 3) 记录不完整
        if (allowed(NudgeKind.RECORD_INCOMPLETE)) {
            judged += NudgeKind.RECORD_INCOMPLETE
            when {
                facts.now.hour < RECORD_CHECK_HOUR -> Unit
                facts.foodLogCountToday > 1 -> Unit
                else -> candidates += NudgeCandidate(
                    kind = NudgeKind.RECORD_INCOMPLETE,
                    reason = NudgeReason.FOOD_LOG_MISSING,
                    title = "今天还没怎么记",
                    body = if (facts.foodLogCountToday == 0) {
                        "今天还没有饮食记录。补上之后，热量和蛋白才算得准。"
                    } else {
                        "今天只记了 ${facts.foodLogCountToday} 条饮食（约 ${fmt(facts.kcalToday)} kcal），" +
                            "要不要补一下？"
                    },
                    needsAnalysis = false,
                    facts = mapOf(
                        "foodLogCount" to facts.foodLogCountToday.toString(),
                        "kcal" to fmt(facts.kcalToday)
                    )
                )
            }
        }

        // 4) 体重偏离
        if (allowed(NudgeKind.WEIGHT_DEVIATION)) {
            when (val weightCase = evaluateWeight(facts)) {
                is WeightCase.Candidate -> {
                    judged += NudgeKind.WEIGHT_DEVIATION
                    candidates += weightCase.candidate
                }
                is WeightCase.Insufficient -> insufficient += weightCase.reason
                WeightCase.NoTrigger -> judged += NudgeKind.WEIGHT_DEVIATION
            }
        }

        return Evaluation(
            candidates = candidates.sortedByDescending { it.kind.priority },
            judgedKinds = judged,
            insufficientReasons = insufficient
        )
    }

    private sealed interface WeightCase {
        data class Candidate(val candidate: NudgeCandidate) : WeightCase
        data class Insufficient(val reason: String) : WeightCase
        data object NoTrigger : WeightCase
    }

    private fun evaluateWeight(facts: NudgeFacts): WeightCase {
        if (facts.targetWeightKg <= 0.0) return WeightCase.Insufficient("没设目标体重，不判断体重偏离")
        val points = facts.weightPoints.sortedBy { it.date }
        val latest = points.lastOrNull() ?: return WeightCase.Insufficient("没有体重记录")
        if (ChronoUnit.DAYS.between(latest.date, facts.today) > WEIGHT_STALE_DAYS) {
            return WeightCase.Insufficient("最新体重记录超过 $WEIGHT_STALE_DAYS 天，不判断趋势")
        }

        val gap = latest.weightKg - facts.targetWeightKg
        if (abs(gap) <= WEIGHT_NEAR_TARGET_KG) return WeightCase.NoTrigger

        val recent = points.filter { ChronoUnit.DAYS.between(it.date, facts.today) <= 6 }
        val baseline = points.filter {
            val days = ChronoUnit.DAYS.between(it.date, facts.today)
            days in 7..27
        }

        // 方向：需要近期和基线各至少 2 条，否则不敢说「在往回走」。
        if (recent.size >= 2 && baseline.size >= 2) {
            val drift = recent.map { it.weightKg }.average() - baseline.map { it.weightKg }.average()
            val wantDown = facts.targetWeightKg < latest.weightKg
            val wrongDirection = (wantDown && drift >= WEIGHT_DRIFT_KG) ||
                (!wantDown && drift <= -WEIGHT_DRIFT_KG)
            if (wrongDirection) {
                return WeightCase.Candidate(
                    NudgeCandidate(
                        kind = NudgeKind.WEIGHT_DEVIATION,
                        reason = NudgeReason.WEIGHT_WRONG_DIRECTION,
                        title = if (wantDown) "体重在往上走" else "体重在往下掉",
                        body = "近 7 天平均 ${fmt(recent.map { it.weightKg }.average())}kg，" +
                            "比前 3 周平均${if (drift > 0) "高" else "低"} ${fmt(abs(drift))}kg；" +
                            "目标是 ${fmt(facts.targetWeightKg)}kg。",
                        needsAnalysis = true,
                        facts = mapOf(
                            "latestKg" to fmt(latest.weightKg),
                            "recent7dAvgKg" to fmt(recent.map { it.weightKg }.average()),
                            "baselineAvgKg" to fmt(baseline.map { it.weightKg }.average()),
                            "driftKg" to fmt(drift),
                            "targetKg" to fmt(facts.targetWeightKg)
                        )
                    )
                )
            }
        }

        // 停滞：14 天内记录够多且几乎没动。
        val last14 = points.filter { ChronoUnit.DAYS.between(it.date, facts.today) <= 13 }
        if (last14.size >= 4) {
            val max = last14.maxOf { it.weightKg }
            val min = last14.minOf { it.weightKg }
            if (max - min <= WEIGHT_PLATEAU_RANGE_KG && abs(gap) > WEIGHT_PLATEAU_MIN_GAP_KG) {
                return WeightCase.Candidate(
                    NudgeCandidate(
                        kind = NudgeKind.WEIGHT_DEVIATION,
                        reason = NudgeReason.WEIGHT_PLATEAU,
                        title = "体重卡住了",
                        body = "近 14 天体重在 ${fmt(min)}～${fmt(max)}kg 之间，基本没动；" +
                            "离目标 ${fmt(facts.targetWeightKg)}kg 还有 ${fmt(abs(gap))}kg。",
                        needsAnalysis = true,
                        facts = mapOf(
                            "minKg" to fmt(min),
                            "maxKg" to fmt(max),
                            "gapKg" to fmt(abs(gap)),
                            "targetKg" to fmt(facts.targetWeightKg)
                        )
                    )
                )
            }
        }

        if (recent.size < 2 || baseline.size < 2) {
            return WeightCase.Insufficient("体重记录不足以判断趋势（近 7 天 ${recent.size} 条，基线 ${baseline.size} 条）")
        }
        return WeightCase.NoTrigger
    }

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun formatMinute(minute: Int): String =
        String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60)

    private fun fmt(value: Double): String = String.format(Locale.CHINA, "%.1f", value)
}
