package com.qingji.app.domain.nudge

import com.qingji.app.domain.WeightPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 阶段 J 规则引擎测试。
 *
 * 重点验三件事：该闭嘴的时候一定闭嘴（关掉/静默/额度/冷却/数据不足），
 * 该说的只说一条（优先级最高那条），以及所有数字都来自本地事实。
 */
class NudgeRuleEngineTest {

    private val today = LocalDate.of(2026, 11, 25)

    private fun at(hour: Int, minute: Int = 30): LocalDateTime =
        LocalDateTime.of(today, java.time.LocalTime.of(hour, minute))

    private fun facts(
        now: LocalDateTime = at(21),
        foodLogCount: Int = 0,
        mealCount: Int = 0,
        kcal: Double = 0.0,
        protein: Double = 0.0,
        proteinGoal: Int = 0,
        hasPlan: Boolean = false,
        plannedToday: Int = 0,
        trained: Boolean = false,
        weights: List<WeightPoint> = emptyList(),
        target: Double = 0.0
    ) = NudgeFacts(
        now = now,
        today = today,
        foodLogCountToday = foodLogCount,
        mealCountToday = mealCount,
        kcalToday = kcal,
        proteinTodayG = protein,
        proteinGoalG = proteinGoal,
        hasActivePlan = hasPlan,
        plannedExerciseCountToday = plannedToday,
        trainedToday = trained,
        weightPoints = weights,
        targetWeightKg = target,
        heightCm = 175.0
    )

    private fun evaluated(decision: NudgeDecision): NudgeCandidate =
        (decision as? NudgeDecision.Show)?.candidate
            ?: error("预期 Show，实际是 $decision")

    private fun skipped(decision: NudgeDecision): NudgeDecision.Skip =
        decision as? NudgeDecision.Skip ?: error("预期 Skip，实际是 $decision")

    // ---------- 一、该闭嘴的场景 ----------

    @Test
    fun `总开关关闭时一条都不发`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(foodLogCount = 0),
            NudgePolicy(enabled = false),
            emptyList()
        )
        assertEquals(NudgeSkipReason.DISABLED, skipped(decision).reason)
    }

    @Test
    fun `静默时段内不打扰`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(22, 30), foodLogCount = 0),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.SILENT_HOURS, skipped(decision).reason)
    }

    @Test
    fun `跨午夜的静默时段凌晨也生效`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(7, 10), foodLogCount = 0),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.SILENT_HOURS, skipped(decision).reason)
    }

    @Test
    fun `今天额度用完就不再发`() {
        val history = listOf(
            NudgeHistoryItem(
                id = "n1",
                kind = NudgeKind.RECORD_INCOMPLETE,
                dateKey = today.toString(),
                shownAt = at(20, 5).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), foodLogCount = 0),
            NudgePolicy(dailyLimit = 1),
            history
        )
        assertEquals(NudgeSkipReason.DAILY_LIMIT, skipped(decision).reason)
    }

    @Test
    fun `同类提醒今天已发过就跳过`() {
        val shownAt = at(20, 5).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = listOf(
            NudgeHistoryItem("n1", NudgeKind.RECORD_INCOMPLETE, today.toString(), shownAt)
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), foodLogCount = 0),
            NudgePolicy(dailyLimit = 2),
            history
        )
        assertEquals(NudgeSkipReason.COOLDOWN, skipped(decision).reason)
    }

    @Test
    fun `同类提醒还在冷却期跨天也不重发`() {
        // 昨天 21:30 发过一条，现在今天 03:30 之前都在 20 小时冷却里
        val shownAt = at(21).minusHours(19).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val history = listOf(
            NudgeHistoryItem("n1", NudgeKind.RECORD_INCOMPLETE, today.minusDays(1).toString(), shownAt)
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), foodLogCount = 0),
            NudgePolicy(dailyLimit = 2),
            history
        )
        assertEquals(NudgeSkipReason.COOLDOWN, skipped(decision).reason)
    }

    @Test
    fun `体重偏离的冷却更长`() {
        val weights = wrongDirectionWeights()
        val shownAt = at(21).minusHours(48).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val history = listOf(
            NudgeHistoryItem("n1", NudgeKind.WEIGHT_DEVIATION, today.minusDays(2).toString(), shownAt)
        )
        val decision = NudgeRuleEngine.evaluate(
            // 饮食记满三条，排除「记录不完整」干扰，只看体重这条的冷却
            facts(now = at(21), foodLogCount = 3, weights = weights, target = 65.0),
            NudgePolicy(dailyLimit = 2),
            history
        )
        assertEquals(NudgeSkipReason.COOLDOWN, skipped(decision).reason)
    }

    @Test
    fun `没有触发条件时安静`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(12), foodLogCount = 3, mealCount = 1),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(decision).reason)
    }

    // ---------- 二、四类规则各自的触发条件 ----------

    @Test
    fun `晚上八点后没记饮食才提记录不完整`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 0),
            NudgePolicy(),
            emptyList()
        )
        val candidate = evaluated(decision)
        assertEquals(NudgeKind.RECORD_INCOMPLETE, candidate.kind)
        assertEquals(NudgeReason.FOOD_LOG_MISSING, candidate.reason)
    }

    @Test
    fun `记录超过一条就不算不完整`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), foodLogCount = 2),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(decision).reason)
    }

    @Test
    fun `蛋白低于目标六成才提`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(19, 30), foodLogCount = 4, mealCount = 2, protein = 40.0, proteinGoal = 120),
            NudgePolicy(),
            emptyList()
        )
        val candidate = evaluated(decision)
        assertEquals(NudgeKind.PROTEIN_LOW, candidate.kind)
        assertTrue(candidate.body.contains("40.0"))
        assertTrue(candidate.body.contains("120"))
    }

    @Test
    fun `蛋白达标或者只吃一餐都不提`() {
        val enough = NudgeRuleEngine.evaluate(
            facts(now = at(19, 30), foodLogCount = 4, mealCount = 2, protein = 100.0, proteinGoal = 120),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(enough).reason)

        // 只记了一餐时不判蛋白：这条规则数据不够，其它规则判断过且正常 → 安静
        val oneMeal = NudgeRuleEngine.evaluate(
            facts(now = at(19, 30), foodLogCount = 1, mealCount = 1, protein = 10.0, proteinGoal = 120),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(oneMeal).reason)
    }

    @Test
    fun `没设蛋白目标就不判蛋白`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(19, 30), foodLogCount = 4, mealCount = 3, protein = 5.0, proteinGoal = 0),
            NudgePolicy(),
            emptyList()
        )
        // 没有任何蛋白候选；安静的理由里能看出是「目标没设」
        val skip = skipped(decision)
        assertEquals(NudgeSkipReason.NO_TRIGGER, skip.reason)
        assertTrue(skip.detail.contains("没设蛋白目标"))
    }

    @Test
    fun `有计划有安排但没练才算计划未完成`() {
        val hit = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), hasPlan = true, plannedToday = 5),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeKind.PLAN_NOT_DONE, evaluated(hit).kind)

        // 下面三种都只给计划这一类，饮食记够两条避免「记录不完整」混进来
        val restDay = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 2, hasPlan = true, plannedToday = 0),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(restDay).reason)

        val trained = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 2, hasPlan = true, plannedToday = 5, trained = true),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(trained).reason)

        val noPlan = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 2, hasPlan = false, plannedToday = 5),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(noPlan).reason)
    }

    @Test
    fun `体重往目标反方向走会提醒`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), weights = wrongDirectionWeights(), target = 65.0),
            NudgePolicy(),
            emptyList()
        )
        val candidate = evaluated(decision)
        assertEquals(NudgeKind.WEIGHT_DEVIATION, candidate.kind)
        assertEquals(NudgeReason.WEIGHT_WRONG_DIRECTION, candidate.reason)
        assertTrue(candidate.needsAnalysis)
    }

    @Test
    fun `体重两周不动会提醒停滞`() {
        val weights = listOf(
            WeightPoint(today.minusDays(13), 66.4),
            WeightPoint(today.minusDays(9), 66.5),
            WeightPoint(today.minusDays(5), 66.4),
            WeightPoint(today.minusDays(1), 66.5)
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), weights = weights, target = 65.0),
            NudgePolicy(),
            emptyList()
        )
        val candidate = evaluated(decision)
        assertEquals(NudgeReason.WEIGHT_PLATEAU, candidate.reason)
    }

    @Test
    fun `体重已经接近目标就不再念`() {
        val weights = listOf(
            WeightPoint(today.minusDays(1), 65.3)
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), foodLogCount = 3, weights = weights, target = 65.0),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeSkipReason.NO_TRIGGER, skipped(decision).reason)
    }

    @Test
    fun `体重记录太旧不判断趋势`() {
        val weights = listOf(
            WeightPoint(today.minusDays(10), 70.0),
            WeightPoint(today.minusDays(8), 70.5)
        )
        // 只开体重这一类，其它规则不参与 → 数据不够就是「数据不够」
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), weights = weights, target = 65.0),
            NudgePolicy(
                disabledKinds = setOf(
                    NudgeKind.RECORD_INCOMPLETE,
                    NudgeKind.PLAN_NOT_DONE,
                    NudgeKind.PROTEIN_LOW
                )
            ),
            emptyList()
        )
        assertEquals(NudgeSkipReason.INSUFFICIENT_DATA, skipped(decision).reason)
    }

    @Test
    fun `没设目标体重不判体重偏离`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(21), weights = wrongDirectionWeights(), target = 0.0),
            NudgePolicy(
                disabledKinds = setOf(
                    NudgeKind.RECORD_INCOMPLETE,
                    NudgeKind.PLAN_NOT_DONE,
                    NudgeKind.PROTEIN_LOW
                )
            ),
            emptyList()
        )
        assertEquals(NudgeSkipReason.INSUFFICIENT_DATA, skipped(decision).reason)
    }

    // ---------- 三、优先级与开关 ----------

    @Test
    fun `一天只发一条且优先计划未完成`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 0, hasPlan = true, plannedToday = 4),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeKind.PLAN_NOT_DONE, evaluated(decision).kind)
    }

    @Test
    fun `体重偏离优先级最高`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(
                now = at(20, 30),
                foodLogCount = 0,
                hasPlan = true,
                plannedToday = 4,
                weights = wrongDirectionWeights(),
                target = 65.0
            ),
            NudgePolicy(),
            emptyList()
        )
        assertEquals(NudgeKind.WEIGHT_DEVIATION, evaluated(decision).kind)
    }

    @Test
    fun `关掉某一类只影响这一类`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(now = at(20, 30), foodLogCount = 0, hasPlan = true, plannedToday = 4),
            NudgePolicy(disabledKinds = setOf(NudgeKind.PLAN_NOT_DONE)),
            emptyList()
        )
        assertEquals(NudgeKind.RECORD_INCOMPLETE, evaluated(decision).kind)
    }

    @Test
    fun `最高优先级被关掉时顺延到下一条`() {
        val decision = NudgeRuleEngine.evaluate(
            facts(
                now = at(20, 30),
                weights = wrongDirectionWeights(),
                target = 65.0,
                hasPlan = true,
                plannedToday = 3
            ),
            NudgePolicy(disabledKinds = setOf(NudgeKind.WEIGHT_DEVIATION)),
            emptyList()
        )
        assertEquals(NudgeKind.PLAN_NOT_DONE, evaluated(decision).kind)
    }

    @Test
    fun `体重在冷却时落到下一条候选`() {
        val shownAt = at(21).minusHours(2).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val history = listOf(
            NudgeHistoryItem("n1", NudgeKind.WEIGHT_DEVIATION, today.minusDays(1).toString(), shownAt)
        )
        val decision = NudgeRuleEngine.evaluate(
            facts(
                now = at(21),
                weights = wrongDirectionWeights(),
                target = 65.0,
                hasPlan = true,
                plannedToday = 3
            ),
            NudgePolicy(dailyLimit = 2),
            history
        )
        assertEquals(NudgeKind.PLAN_NOT_DONE, evaluated(decision).kind)
    }

    /**
     * 目标 65kg、近期在涨：近 7 天均值 66.7，基线（8～28 天）均值 66.0。
     * 最新记录是昨天，不算过期。
     */
    private fun wrongDirectionWeights(): List<WeightPoint> = listOf(
        WeightPoint(today.minusDays(13), 66.0),
        WeightPoint(today.minusDays(10), 66.0),
        WeightPoint(today.minusDays(7), 66.0),
        WeightPoint(today.minusDays(3), 66.6),
        WeightPoint(today.minusDays(1), 66.8)
    )
}
