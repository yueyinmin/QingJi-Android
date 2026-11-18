package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import java.time.LocalDate

/**
 * ============================================================
 * 统一运动消耗账本聚合 —— 纯函数，不碰安卓、不碰数据库
 * ------------------------------------------------------------
 * 背景（2026-11-18）：「近七天运动消耗」卡原来只读 exercise_log（有氧表），
 * 训练中心的力量训练结算写进的是 exercise_session_calorie（统一账本），
 * 两张表割裂——主人做完力量训练，卡片数字纹丝不动。
 *
 * 统一账本其实已经把两类都收进来了：
 *   - 力量训练场次：sourceType = STRENGTH_TRAINING
 *   - 独立有氧/步行/手动：镜像自 exercise_log（sourceId = exlog:<id>）
 * 所以统计口径改成「只读账本」这一处，力量和有氧都算进来，
 * 以后接设备也自动生效，不用再改卡片。
 *
 * 分工：总数 / 次数 / 分钟 / 分类占比由这里算；明细列表仍用 ExerciseLog，
 * 因为改/删入口要的是那条可编辑的记录（力量场次在训练中心改）。
 * ============================================================
 */
object ExerciseLedgerCalculator {

    /** 账本里sourceType 对应的展示分类。 */
    const val KIND_STRENGTH = "strength"
    const val KIND_CARDIO = "cardio"
    const val KIND_WALK = "walk"
    const val KIND_OTHER = "other"

    data class KindTotal(val kind: String, val kcal: Double)

    data class Summary(
        val count: Int,
        val minutes: Int,
        val kcal: Double,
        val label: String,
        /** 按来源类型拆开的消耗，界面可显示「力量 xx / 有氧 xx」 */
        val byKind: List<KindTotal>
    )

    /** 一条账本行归到哪个展示分类。 */
    fun kindOf(sourceType: String): String = when (sourceType.uppercase()) {
        "STRENGTH_TRAINING" -> KIND_STRENGTH
        "CARDIO" -> KIND_CARDIO
        "WALKING" -> KIND_WALK
        else -> KIND_OTHER
    }

    /**
     * @param rows 候选账本行（调用方给近 30 天全量即可）
     * @param range day / week / month，范围口径复用 ExerciseRangeCalculator，保证两处一致
     * @param today 归属今天，跨天边界统一用它，不调系统时钟（方便单测）
     */
    fun summarize(
        rows: List<ExerciseSessionCalorieEntity>,
        range: String,
        today: LocalDate
    ): Summary {
        val from = ExerciseRangeCalculator.rangeStart(range, today)
        val picked = rows.filter { it.date >= from && it.date <= today }
        val byKind = picked
            .groupBy { kindOf(it.sourceType) }
            .map { (kind, list) -> KindTotal(kind, list.sumOf { it.caloriesGross }) }
            .sortedByDescending { it.kcal }
        return Summary(
            count = picked.size,
            minutes = picked.sumOf { it.durationSeconds } / 60,
            kcal = picked.sumOf { it.caloriesGross },
            label = when (range) {
                ExerciseRangeCalculator.RANGE_DAY -> "今日"
                ExerciseRangeCalculator.RANGE_MONTH -> "近 30 天"
                else -> "近 7 天"
            },
            byKind = byKind
        )
    }

    /** 账本范围过滤后的行，按日期从旧到新，供明细展示/调试。 */
    fun filter(
        rows: List<ExerciseSessionCalorieEntity>,
        range: String,
        today: LocalDate
    ): List<ExerciseSessionCalorieEntity> {
        val from = ExerciseRangeCalculator.rangeStart(range, today)
        return rows.filter { it.date >= from && it.date <= today }.sortedBy { it.date }
    }
}
