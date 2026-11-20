package com.qingji.app.domain

/**
 * ============================================================
 * 训练场次热量结算（健康体系 V1.0 · P0）
 * ------------------------------------------------------------
 * 解决「力量/自重训练不计入运动消耗」：训练保存时按组数据分段估算整场消耗，
 * 写进统一运动消耗账本（exercise_session_calorie）。
 *
 * 口径（用户已拍板，2026-11-17）：
 * - 沿用现有运动模块公式：千卡 = MET × 体重kg × 时长(小时)，即 MET × 体重 ÷ 60 × 分钟。
 *   不用工单里的 MET×3.5×体重÷200×分钟（差约 5%，且和历史 ExerciseLog 不可比）。
 * - 组数据不拆表：继续读 workout_exercise.setsJson。
 * - 分段计时，不同时段不同强度参数：
 *     · 做功时间 = 次数 × 4 秒/次（夹在 8~120 秒），按动作 MET（随 RPE 取低/中/高档）；
 *     · 组间休息 = 组里记的 restSec（没记按 45 秒），一律用静息近似 MET（1.3），
 *       绝不按高强度 MET 全额算；
 *     · 动作切换与准备 = 每个动作 30 秒，也按静息 MET。
 * - 每组热量只是整场消耗的分摊展示，账本只记一条整场记录，不会二次计入。
 * - 未完成的组（次数和重量都是 0）不计。
 * - 估算总时长超过场次实际时长时，按实际时长等比封顶——宁少不多。
 * - 历史训练没有逐组数据时用「时长 × 中等强度 MET × 0.7」保守估算，标记 estimation。
 *
 * 所有数字都是估算，不是实测；界面上要带「估算」字样。
 * ============================================================
 */
object SessionCalorieCalculator {

    /** 计算版本号。参数或口径变了就 +1，账本里每条记录都带着它，历史不会被悄悄重写。 */
    const val VERSION = "session-calorie-v1"

    /** 结算方式标记（写进账本 calculationMethod 列）。 */
    const val METHOD_SEGMENTED = "met_segmented"            // 有逐组数据的正常结算
    const val METHOD_ESTIMATION_HISTORY = "estimation_history"          // 历史场次按组数据补算
    const val METHOD_ESTIMATION_DURATION = "estimation_history_duration" // 只有时长，最保守
    const val METHOD_USER_DIRECT = "user_direct"            // 用户直接填千卡（独立运动模块）
    const val METHOD_EXERCISE_LOG = "exercise_log"          // 镜像自 exercise_log 的既有记录

    // ---- 估算节奏参数（全部显式标记为估算，不冒充实测） ----
    const val SECONDS_PER_REP = 4.0          // 一次动作向心+离心约 4 秒
    const val MIN_WORK_SECONDS = 8.0
    const val MAX_WORK_SECONDS = 120.0
    const val DEFAULT_REST_SECONDS = 45.0    // 组里没记休息时的默认值
    const val REST_MET = 1.3                 // 组间休息 ≈ 静坐（Compendium 约 1.3）
    const val SETUP_SECONDS_PER_EXERCISE = 30.0
    const val HISTORY_CONSERVATIVE_FACTOR = 0.7   // 只有时长时再打七折，宁少不多

    /** 每分钟千卡（现有口径）：MET × 体重 ÷ 60。 */
    fun kcalPerMinute(met: Double, weightKg: Double): Double = met * weightKg / 60.0

    /** 一场训练里的一个动作条目（已过滤掉无效组）。 */
    data class StrengthEntry(
        val exerciseUuid: String,
        val name: String,
        /** 动作 MET 三档（来自 exercise_calorie_config，查不到就用兜底值） */
        val metLow: Double,
        val metModerate: Double,
        val metHigh: Double,
        val sets: List<WorkoutSet>
    )

    /** 一组的估算结果（分摊展示用）。 */
    data class SetCalorie(
        val entryIndex: Int,
        val setIndex: Int,
        val workKcal: Double,
        val restKcal: Double
    ) {
        val totalKcal: Double get() = workKcal + restKcal
    }

    data class SessionResult(
        /** 整场总消耗（未舍入；账本存这个值，UI 展示才四舍五入） */
        val totalKcal: Double,
        /** 估算的做功+休息+切换总秒数（封顶后） */
        val estimatedSeconds: Double,
        /** 封顶比例：1.0 = 估算时长没超场次实际时长 */
        val scale: Double,
        val method: String,
        val sets: List<SetCalorie>
    )

    /**
     * 结算一场力量/自重训练。
     * @param weightKg 计算用体重（沿用现有口径：档案起始体重，兜底 70kg）
     * @param durationMinutes 场次实际时长（workout_session.durationMinutes，0 = 没有可靠时长）
     */
    fun settleStrengthSession(
        entries: List<StrengthEntry>,
        weightKg: Double,
        durationMinutes: Int,
        method: String = METHOD_SEGMENTED
    ): SessionResult {
        val safeWeight = weightKg.coerceAtLeast(20.0)
        val setResults = mutableListOf<SetCalorie>()
        var workKcalTotal = 0.0
        var restKcalTotal = 0.0
        var setupKcalTotal = 0.0
        var estimatedSeconds = 0.0

        entries.forEachIndexed { ei, entry ->
            val validSets = entry.sets.filter { it.reps > 0 || it.weightKg > 0.0 }
            // 动作切换与准备时间：只给有有效组的动作算，空动作不收这笔
            if (validSets.isEmpty()) return@forEachIndexed
            setupKcalTotal += kcalPerMinute(REST_MET, safeWeight) * SETUP_SECONDS_PER_EXERCISE / 60.0
            estimatedSeconds += SETUP_SECONDS_PER_EXERCISE
            validSets.forEachIndexed { si, set ->
                // RPE 决定强度档：≥8 高强度、≤5 轻松、其余中等；没填按中等
                val met = when {
                    set.rpe >= 8 -> entry.metHigh
                    set.rpe in 1..5 -> entry.metLow
                    else -> entry.metModerate
                }.coerceAtLeast(0.5)
                val workSeconds = (set.reps * SECONDS_PER_REP)
                    .coerceIn(MIN_WORK_SECONDS, MAX_WORK_SECONDS)
                val workKcal = kcalPerMinute(met, safeWeight) * workSeconds / 60.0
                // 最后一组后面没有「组间休息」（要么切换动作要么结束），不计
                val restSeconds = if (si == validSets.lastIndex) {
                    0.0
                } else {
                    (if (set.restSec > 0) set.restSec else DEFAULT_REST_SECONDS).toDouble()
                }
                val restKcal = kcalPerMinute(REST_MET, safeWeight) * restSeconds / 60.0

                workKcalTotal += workKcal
                restKcalTotal += restKcal
                estimatedSeconds += workSeconds + restSeconds
                setResults += SetCalorie(ei, si, workKcal, restKcal)
            }
        }

        // 场次实际时长封顶：估算出来的时间比实际还长，说明中间有歇/漏记，等比压缩
        val actualSeconds = durationMinutes * 60.0
        val scale = if (actualSeconds > 0 && estimatedSeconds > actualSeconds) {
            actualSeconds / estimatedSeconds
        } else {
            1.0
        }
        val total = (workKcalTotal + restKcalTotal + setupKcalTotal) * scale
        return SessionResult(
            totalKcal = total,
            estimatedSeconds = estimatedSeconds * scale,
            scale = scale,
            method = method,
            sets = setResults
        )
    }

    /**
     * 历史场次只有时长、没有逐组数据时的保守估算。
     * 用中等力量 MET 再打七折；时长也没有就返回 0（账本里标记「不可计算」，不造数）。
     */
    fun estimateFromDurationOnly(durationMinutes: Int, weightKg: Double, moderateMet: Double): Double {
        if (durationMinutes <= 0) return 0.0
        val safeWeight = weightKg.coerceAtLeast(20.0)
        return kcalPerMinute(moderateMet, safeWeight) * durationMinutes * HISTORY_CONSERVATIVE_FACTOR
    }

    /** UI 展示用：保留 1 位小数再四舍五入到整数，避免「0 千卡」的误导。 */
    fun displayKcal(value: Double): Int {
        if (value <= 0.0) return 0
        return kotlin.math.round(value).toInt().coerceAtLeast(1)
    }

    // ============================================================
    // 实时预览（训练进行页的「每组热量」展示用，不写库）
    // 口径与 settleStrengthSession 完全一致，只是不做时长封顶
    //（进行中还没到结束，实际时长未知）。
    // ============================================================

    /**
     * 一个动作下面每一组的估算热量（做功 + 组间休息，最后一组没有休息段）。
     * 每组数字随次数（做功时间）、RPE（强度档）和休息时长自然变化，
     * 不是「每个动作一个固定值」。
     */
    fun previewSetKcalList(
        metLow: Double,
        metModerate: Double,
        metHigh: Double,
        weightKg: Double,
        sets: List<WorkoutSet>
    ): List<Double> {
        val safeWeight = weightKg.coerceAtLeast(20.0)
        val valid = sets.filter { it.reps > 0 || it.weightKg > 0.0 }
        return valid.mapIndexed { si, set ->
            val met = when {
                set.rpe >= 8 -> metHigh
                set.rpe in 1..5 -> metLow
                else -> metModerate
            }.coerceAtLeast(0.5)
            val workSeconds = (set.reps * SECONDS_PER_REP)
                .coerceIn(MIN_WORK_SECONDS, MAX_WORK_SECONDS)
            val workKcal = kcalPerMinute(met, safeWeight) * workSeconds / 60.0
            val restSeconds = if (si == valid.lastIndex) {
                0.0
            } else {
                (if (set.restSec > 0) set.restSec else DEFAULT_REST_SECONDS).toDouble()
            }
            val restKcal = kcalPerMinute(REST_MET, safeWeight) * restSeconds / 60.0
            workKcal + restKcal
        }
    }

    /** 单个预览参数包：一次训练页预览共用，避免每个动作都传一长串参数。 */
    data class PreviewEntry(
        val metLow: Double,
        val metModerate: Double,
        val metHigh: Double,
        val sets: List<WorkoutSet>
    )

    /** 整场预览：所有动作的组热量 + 动作切换准备时间（静息 MET，只给有有效组的动作算）。 */
    fun previewSessionTotalKcal(entries: List<PreviewEntry>, weightKg: Double): Double {
        val safeWeight = weightKg.coerceAtLeast(20.0)
        var total = 0.0
        entries.forEach { entry ->
            val setKcalList = previewSetKcalList(entry.metLow, entry.metModerate, entry.metHigh, safeWeight, entry.sets)
            if (setKcalList.isEmpty()) return@forEach
            total += kcalPerMinute(REST_MET, safeWeight) * SETUP_SECONDS_PER_EXERCISE / 60.0
            total += setKcalList.sumOf { it }
        }
        return total
    }
}
