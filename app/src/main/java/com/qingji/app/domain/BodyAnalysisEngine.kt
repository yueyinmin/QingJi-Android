package com.qingji.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * H 阶段：身体数据可信度标签。
 *
 * 和训练分析里的 [TrustLevel] 分开定义，是因为这里要多出两类：
 *   · AI_INFERRED —— 模型自己的推断（例如「可能是水分波动」），永远不能当成记录；
 *   · SUGGESTION —— 给用户的建议（例如「保持每周 0.5kg」），属于意见不是事实。
 *
 * 规则层本身只会产出 RECORDED / CALCULATED / MISSING 三种；
 * 另外两种是写给模型看的口径约定，模型在解释时必须自己标注。
 */
enum class BodyTrust { RECORDED, CALCULATED, AI_INFERRED, MISSING, SUGGESTION }

/** 一条体重记录（只含分析需要的字段，不把 Room 实体泄漏到规则层）。 */
data class WeightPoint(
    val date: LocalDate,
    val weightKg: Double,
    val bodyFatPct: Double? = null
)

/** 一条围度记录。五项都可能缺，缺的那项算不出来就是 null。 */
data class MeasurePoint(
    val date: LocalDate,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val hipCm: Double? = null,
    val thighCm: Double? = null,
    val armCm: Double? = null
)

/** 计算 BMI / 目标进度需要的最小档案。 */
data class BodyProfile(
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val gender: String = "male",
    val birthday: LocalDate? = null,
    /** 每日蛋白目标（克），综合报告判断「蛋白达标天数」用；0 表示没设 */
    val proteinGoalG: Int = 0
)

/** 体重方向：只有连续记录够多时才敢给结论，否则 UNKNOWN。 */
enum class WeightDirection { LOSING, GAINING, STABLE, UNKNOWN }

data class WeightTrend(
    val latestDate: LocalDate?,
    val latestKg: Double?,
    /** 最新一次相对 7 天前最近一条记录的变化；没有可比记录就是 null */
    val change7dKg: Double?,
    /** 最新一次相对 28 天前最近一条记录的变化；没有可比记录就是 null */
    val change28dKg: Double?,
    val average7dKg: Double?,
    /** 整个统计窗口内的平均值（窗口长度见 [BodyAnalysis.windowDays]） */
    val averageWindowKg: Double?,
    /** 每周变化速度，单位 kg/周；记录跨度不足 14 天时不给 */
    val weeklyRateKg: Double?,
    val direction: WeightDirection,
    val trust: BodyTrust
)

data class BmiSection(
    val value: Double?,
    val category: String?,
    val heightCm: Double?,
    val weightKg: Double?,
    val trust: BodyTrust
)

data class BodyFatSection(
    val latestPct: Double?,
    val latestDate: LocalDate?,
    /** 最新体脂相对 14 天前最近一条的变化（百分点），不够就 null */
    val change14dPct: Double?,
    val trust: BodyTrust
)

data class MeasureSection(
    val latest: MeasurePoint?,
    /** 最新与窗口内最早 waist 记录相隔的天数；不足 7 天不算变化 */
    val waistSpanDays: Int?,
    val waistChangeCm: Double?,
    val trust: BodyTrust
)

data class CompletenessSection(
    val windowDays: Int,
    /** 窗口内实际有体重记录的天数（同一天多条只算一天） */
    val weightDays: Int,
    /** 建议最少记录天数：每 7 天 2 次，至少 3 天 */
    val requiredWeightDays: Int,
    val measureDays: Int,
    /** 相邻两次体重记录的平均间隔天数 */
    val averageGapDays: Int?,
    /** 充足 / 偏少 / 不足 */
    val level: String,
    val trust: BodyTrust
)

data class GoalSection(
    val startWeightKg: Double?,
    val targetWeightKg: Double?,
    val currentWeightKg: Double?,
    /** 当前距目标还差多少 kg；正数=还要减，负数=还要增 */
    val remainingKg: Double?,
    /** 已完成进度百分比，可能为负（往反方向走了） */
    val progressPct: Double?,
    /** 按当前每周速度估算还需几周；速度不够或方向不对就是 null */
    val estimatedWeeks: Double?,
    val trust: BodyTrust
)

/**
 * 一份身体数据分析报告。
 *
 * [facts] 只放本地能算出来的数字陈述（每条都能在 records 里对得上），
 * [warnings] 放数据不足 / 口径不清的提醒。两者都不含建议——建议由模型解释时给，
 * 并且必须标成 SUGGESTION，写不写身体数据由用户自己决定。
 */
data class BodyAnalysis(
    val generatedFor: LocalDate,
    val windowDays: Int,
    val weight: WeightTrend,
    val bmi: BmiSection,
    val bodyFat: BodyFatSection,
    val measures: MeasureSection,
    val completeness: CompletenessSection,
    val goal: GoalSection,
    val facts: List<String>,
    val warnings: List<String>,
    val insufficientData: Boolean
)

/**
 * H 阶段规则层：体重 / BMI / 体脂 / 围度 / 目标进度全部在本地算。
 *
 * 三条硬规矩：
 * 1. 模型不参与任何计算——它只能解释这里给出的数字；
 * 2. 缺数据一律 null + MISSING + 一条 warning，不用默认值糊过去；
 * 3. 只读。本类没有任何写库入口，身体数据只能由用户在页面上录。
 */
object BodyAnalysisEngine {

    /** 少于 3 天体重记录就认为趋势不可靠 */
    const val MIN_WEIGHT_DAYS = 3

    /** 每周变化绝对值小于 0.15kg 视为稳定，避免把水分波动说成减脂 */
    const val STABLE_RATE_KG_PER_WEEK = 0.15

    /** 估算进度至少要有这个速度，否则不给周数 */
    private const val MIN_RATE_FOR_ESTIMATE = 0.05

    fun analyze(
        weights: List<WeightPoint>,
        measures: List<MeasurePoint>,
        profile: BodyProfile?,
        today: LocalDate,
        windowDays: Int = 28
    ): BodyAnalysis {
        val days = windowDays.coerceIn(7, 180)
        val from = today.minusDays(days - 1L)
        val warnings = mutableListOf<String>()
        val facts = mutableListOf<String>()

        // 未来日期（误录 / 回填）不参与分析，和 Widget 的 latestEffective 口径保持一致
        val history = weights
            .filter { it.date <= today && it.weightKg in 20.0..400.0 }
            .sortedWith(compareBy({ it.date }))
        val droppedBadWeight = weights.count { it.date <= today && it.weightKg !in 20.0..400.0 }
        if (droppedBadWeight > 0) {
            warnings.add("有 $droppedBadWeight 条体重记录数值异常（不在 20~400kg），已排除")
        }

        val window = history.filter { it.date >= from }
        val latest = history.lastOrNull()

        // ---------- 体重趋势 ----------
        val latestDate = latest?.date
        val change7d = latest?.let { l ->
            history.lastOrNull { it.date <= l.date.minusDays(7) }
                ?.let { roundTo(l.weightKg - it.weightKg, 1) }
        }
        val change28d = latest?.let { l ->
            history.lastOrNull { it.date <= l.date.minusDays(28) }
                ?.let { roundTo(l.weightKg - it.weightKg, 1) }
        }
        val avg7d = average(window.filter { it.date > today.minusDays(7) })
        val avgWindow = average(window)
        val weeklyRate = weeklyRate(window)
        val direction = when {
            weeklyRate == null -> WeightDirection.UNKNOWN
            abs(weeklyRate) < STABLE_RATE_KG_PER_WEEK -> WeightDirection.STABLE
            weeklyRate < 0 -> WeightDirection.LOSING
            else -> WeightDirection.GAINING
        }

        if (latest != null) {
            facts.add("最新体重 ${format(latest.weightKg, 1)} kg（${latest.date} 记录）")
            if (latest.date < from) {
                warnings.add("近 $days 天没有体重记录，展示的是 ${latest.date} 的记录")
            }
        } else {
            warnings.add("还没有任何体重记录，体重趋势与 BMI 都算不出来")
        }
        change7d?.let { facts.add("比 7 天前${signed(it)} kg") }
        change28d?.let { facts.add("比 28 天前${signed(it)} kg") }
        weeklyRate?.let {
            facts.add("近 ${windowDays} 天平均每周${signed(it)} kg（按 ${window.first().date} 到 ${window.last().date} 的斜率计算）")
        }
        if (weeklyRate == null && window.size >= 2) {
            warnings.add("体重记录跨度不足 14 天，暂不判断减脂/增重速度")
        }

        val weightTrend = WeightTrend(
            latestDate = latestDate,
            latestKg = latest?.let { roundTo(it.weightKg, 1) },
            change7dKg = change7d,
            change28dKg = change28d,
            average7dKg = avg7d,
            averageWindowKg = avgWindow,
            weeklyRateKg = weeklyRate,
            direction = direction,
            trust = if (latest != null) BodyTrust.CALCULATED else BodyTrust.MISSING
        )

        // ---------- BMI ----------
        val height = profile?.heightCm?.takeIf { it in 100.0..250.0 }
        val currentWeight = latest?.weightKg
        val bmiValue = if (height != null && currentWeight != null) {
            NutritionCalculator.bmi(currentWeight, height).takeIf { it > 0 }
        } else null
        if (height == null) {
            warnings.add("档案里身高没填或不合理（100~250cm），BMI 算不出来")
        } else if (currentWeight == null) {
            warnings.add("没有体重记录，BMI 算不出来")
        }
        bmiValue?.let {
            val rounded = roundTo(it, 1)
            facts.add("BMI ${format(rounded, 1)}（${NutritionCalculator.bmiCategory(it)}），按身高 ${format(height!!, 1)} cm 与最新体重计算")
        }
        val bmi = BmiSection(
            value = bmiValue?.let { roundTo(it, 1) },
            category = bmiValue?.let { NutritionCalculator.bmiCategory(it) },
            heightCm = height,
            weightKg = currentWeight?.let { roundTo(it, 1) },
            trust = if (bmiValue != null) BodyTrust.CALCULATED else BodyTrust.MISSING
        )

        // ---------- 体脂率 ----------
        val fatHistory = history.filter { it.bodyFatPct != null && it.bodyFatPct in 1.0..75.0 }
        val fatLatest = fatHistory.lastOrNull()
        val fatChange = fatLatest?.let { l ->
            fatHistory.lastOrNull { it.date <= l.date.minusDays(14) }
                ?.let { roundTo(l.bodyFatPct!! - it.bodyFatPct!!, 1) }
        }
        if (fatLatest == null) {
            warnings.add("没有任何体脂率记录，体脂变化无法判断（不会用体重去猜体脂）")
        } else {
            facts.add("最近一次体脂率 ${format(fatLatest.bodyFatPct!!, 1)}%（${fatLatest.date} 记录）")
            fatChange?.let { facts.add("体脂率比 14 天前${signed(it)} 个百分点") }
        }
        val bodyFat = BodyFatSection(
            latestPct = fatLatest?.let { roundTo(it.bodyFatPct!!, 1) },
            latestDate = fatLatest?.date,
            change14dPct = fatChange,
            trust = if (fatLatest != null) BodyTrust.RECORDED else BodyTrust.MISSING
        )

        // ---------- 围度 ----------
        val measureHistory = measures
            .filter { it.date <= today }
            .sortedWith(compareBy({ it.date }))
        val measureWindow = measureHistory.filter { it.date >= from }
        val latestMeasure = measureWindow.lastOrNull() ?: measureHistory.lastOrNull()
        val waistRecords = measureWindow.filter { it.waistCm != null && it.waistCm in 30.0..200.0 }
        val waistSpan = if (waistRecords.size >= 2) {
            ChronoUnit.DAYS.between(waistRecords.first().date, waistRecords.last().date).toInt()
        } else null
        val waistChange = if (waistSpan != null && waistSpan >= 7) {
            roundTo(waistRecords.last().waistCm!! - waistRecords.first().waistCm!!, 1)
        } else null
        if (latestMeasure == null) {
            warnings.add("没有围度记录，腰围变化无法判断")
        } else {
            latestMeasure.waistCm?.let { facts.add("最近一次腰围 ${format(it, 1)} cm（${latestMeasure.date} 记录）") }
            waistChange?.let { facts.add("腰围比 $waistSpan 天前${signed(it)} cm") }
            if (waistSpan != null && waistSpan < 7) {
                warnings.add("围度记录只隔了 $waistSpan 天，暂不比较腰围变化")
            }
        }
        val measuresSection = MeasureSection(
            latest = latestMeasure,
            waistSpanDays = waistSpan,
            waistChangeCm = waistChange,
            trust = if (latestMeasure != null) BodyTrust.RECORDED else BodyTrust.MISSING
        )

        // ---------- 记录完整度 ----------
        val weightDates = window.map { it.date }.distinct().sorted()
        val measureDates = measureWindow.map { it.date }.distinct()
        val requiredDays = maxOf(MIN_WEIGHT_DAYS, (days + 6) / 7 * 2)
        val level = when {
            weightDates.size >= requiredDays -> "充足"
            weightDates.size >= MIN_WEIGHT_DAYS -> "偏少"
            else -> "不足"
        }
        val avgGap = if (weightDates.size >= 2) {
            roundTo(
                ChronoUnit.DAYS.between(weightDates.first(), weightDates.last()).toDouble() /
                    (weightDates.size - 1),
                1
            )
        } else null
        facts.add("近 $days 天体重记了 ${weightDates.size} 天（建议至少 $requiredDays 天），围度记了 ${measureDates.size} 天")
        if (level != "充足") {
            warnings.add("体重记录$level：近 $days 天只有 ${weightDates.size} 天，趋势结论可能被单日波动带偏")
        }
        val completeness = CompletenessSection(
            windowDays = days,
            weightDays = weightDates.size,
            requiredWeightDays = requiredDays,
            measureDays = measureDates.size,
            averageGapDays = avgGap?.let { roundTo(it, 0).toInt() },
            level = level,
            trust = if (weightDates.isNotEmpty()) BodyTrust.CALCULATED else BodyTrust.MISSING
        )

        // ---------- 目标进度 ----------
        val startWeight = profile?.startWeightKg?.takeIf { it in 20.0..400.0 }
        val targetWeight = profile?.targetWeightKg?.takeIf { it in 20.0..400.0 }
        val remaining = if (currentWeight != null && targetWeight != null) {
            roundTo(currentWeight - targetWeight, 1)
        } else null
        val totalDelta = if (startWeight != null && targetWeight != null) {
            roundTo(startWeight - targetWeight, 1)
        } else null
        val progress = if (startWeight != null && currentWeight != null && totalDelta != null && abs(totalDelta) >= 0.1) {
            roundTo((startWeight - currentWeight) / totalDelta * 100, 1)
        } else null
        val estimatedWeeks = if (weeklyRate != null && targetWeight != null && currentWeight != null &&
            abs(weeklyRate) >= MIN_RATE_FOR_ESTIMATE
        ) {
            val weeks = (targetWeight - currentWeight) / weeklyRate
            if (weeks > 0 && weeks < 520) roundTo(weeks, 1) else null
        } else null

        when {
            targetWeight == null -> warnings.add("档案里没有目标体重，目标进度算不出来")
            startWeight == null -> warnings.add("档案里没有起始体重，完成百分比算不出来")
        }
        if (remaining != null) {
            facts.add(if (remaining > 0) "距目标体重 ${format(targetWeight!!, 1)} kg 还差 ${format(remaining, 1)} kg"
            else "已低于目标体重 ${format(-remaining, 1)} kg")
        }
        progress?.let { facts.add("目标完成度 ${format(it, 0)}%") }
        if (remaining != null && weeklyRate != null && remaining * weeklyRate > 0 && abs(weeklyRate) >= MIN_RATE_FOR_ESTIMATE) {
            warnings.add("当前体重变化方向和目标相反，进度估算不适用")
        }
        estimatedWeeks?.let { facts.add("按当前速度预计还需 ${format(it, 1)} 周") }
        val goal = GoalSection(
            startWeightKg = startWeight,
            targetWeightKg = targetWeight,
            currentWeightKg = currentWeight?.let { roundTo(it, 1) },
            remainingKg = remaining,
            progressPct = progress,
            estimatedWeeks = estimatedWeeks,
            trust = if (remaining != null) BodyTrust.CALCULATED else BodyTrust.MISSING
        )

        val insufficient = latest == null || weightDates.size < MIN_WEIGHT_DAYS

        return BodyAnalysis(
            generatedFor = today,
            windowDays = days,
            weight = weightTrend,
            bmi = bmi,
            bodyFat = bodyFat,
            measures = measuresSection,
            completeness = completeness,
            goal = goal,
            facts = facts,
            warnings = warnings.distinct(),
            insufficientData = insufficient
        )
    }

    /** 相邻两次记录跨度 ≥14 天才算速度，避免两三天的水重被当成趋势。 */
    private fun weeklyRate(window: List<WeightPoint>): Double? {
        if (window.size < 3) return null
        val spanDays = ChronoUnit.DAYS.between(window.first().date, window.last().date)
        if (spanDays < 14) return null
        val delta = window.last().weightKg - window.first().weightKg
        return roundTo(delta / (spanDays / 7.0), 2)
    }

    private fun average(points: List<WeightPoint>): Double? =
        if (points.isEmpty()) null else roundTo(points.sumOf { it.weightKg } / points.size, 1)

    private fun signed(value: Double): String =
        (if (value > 0) "+" else "") + format(value, 1)

    private fun format(value: Double, digits: Int): String {
        val r = roundTo(value, digits)
        return if (digits <= 0) r.toInt().toString()
        else String.format(java.util.Locale.US, "%.${digits}f", r)
    }

    private fun roundTo(value: Double, digits: Int): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        var factor = 1.0
        repeat(digits) { factor *= 10 }
        return kotlin.math.round(value * factor) / factor
    }
}
