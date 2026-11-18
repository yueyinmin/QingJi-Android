package com.qingji.app.domain

import com.qingji.app.data.local.entity.DailyHealthSummaryEntity
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.1 · 每日健康聚合纯逻辑（工单 10.2，可单测）
 * ------------------------------------------------------------
 * 聚合口径（全部来自原始记录，不碰缓存）：
 * · 饮食：FoodLog 当日 kcal / 蛋白 / 碳水 / 脂肪 求和；
 * · 运动消耗：统一账本按来源分类（力量 / 有氧 / 步行），总消耗 = 全部来源之和
 *   （MANUAL_EXERCISE、DEVICE_IMPORTED 等其他来源计入总消耗，不单列，工单 10.2）；
 * · 力量训练：当日场次次数 / 时长求和 / 训练容量 = Σ重量×次数（TrainingMath.sessionVolume），
 *   **容量 kg 与消耗 kcal 是两个独立概念，绝不混算**；
 * · 睡眠：归属当日（起床日期）的所有记录时长求和 + 条数；
 * · 签名：各源轻量聚合值的拼接串，原始数据一变签名就变 → 缓存自动失效重建。
 * 无数据的日期聚合出来是全 0 行 + 有效签名（缓存放心存）；UI 层负责「没记录显示空态」。
 * ============================================================
 */
object DailyHealthAggregator {

    /** 账本一行（从 ExerciseSessionCalorieEntity 提炼的纯数据视图，便于单测）。 */
    data class LedgerRow(val sourceType: String, val caloriesGross: Double)

    /** 聚合输入（全部原始记录当日切片）。 */
    data class RawDay(
        val foodLogCount: Int,
        val foodKcal: Double,
        val foodProteinG: Double,
        val foodCarbG: Double,
        val foodFatG: Double,
        val ledger: List<LedgerRow>,
        val workoutCount: Int,
        val workoutDurationMinutes: Int,
        /** 当日所有场次全部组的 (重量kg, 次数) 对，容量 = Σ w×r */
        val workoutSets: List<Pair<Double, Int>>,
        val sleepRecordCount: Int,
        val sleepDurationMinutes: Int
    )

    /** 聚合成一行缓存值（不含 id，由仓库决定 upsert）。 */
    fun aggregate(date: LocalDate, raw: RawDay, now: Long): DailyHealthSummaryEntity {
        val strength = raw.ledger.filter { it.sourceType == "STRENGTH_TRAINING" }.sumOf { it.caloriesGross }
        val cardio = raw.ledger.filter { it.sourceType == "CARDIO" }.sumOf { it.caloriesGross }
        val walking = raw.ledger.filter { it.sourceType == "WALKING" }.sumOf { it.caloriesGross }
        val totalBurn = raw.ledger.sumOf { it.caloriesGross }
        val volumeKg = raw.workoutSets.sumOf { (w, r) -> if (w > 0.0) w * r else 0.0 }
        return DailyHealthSummaryEntity(
            date = date,
            calorieIntake = raw.foodKcal,
            proteinG = raw.foodProteinG,
            carbohydratesG = raw.foodCarbG,
            fatG = raw.foodFatG,
            exerciseCalories = totalBurn,
            strengthCalories = strength,
            cardioCalories = cardio,
            walkingCalories = walking,
            workoutDurationMinutes = raw.workoutDurationMinutes,
            workoutCount = raw.workoutCount,
            workoutVolumeKg = volumeKg,
            sleepDurationMinutes = raw.sleepDurationMinutes,
            sleepRecordCount = raw.sleepRecordCount,
            signature = signatureOf(raw),
            lastCalculatedAt = now
        )
    }

    /**
     * 签名：把各源的「条数 + 关键求和」拼成一串。
     * 轻量（DAO 一条聚合 SQL 就能算出来），原始记录任何增删改都会让它变化。
     */
    fun signatureOf(raw: RawDay): String = listOf(
        raw.foodLogCount,
        kotlin.math.round(raw.foodKcal * 10) / 10,
        kotlin.math.round(raw.foodProteinG * 10) / 10,
        raw.ledger.size,
        kotlin.math.round(raw.ledger.sumOf { it.caloriesGross } * 10) / 10,
        raw.workoutCount,
        raw.workoutSets.size,
        raw.sleepRecordCount,
        raw.sleepDurationMinutes
    ).joinToString("|")
}
