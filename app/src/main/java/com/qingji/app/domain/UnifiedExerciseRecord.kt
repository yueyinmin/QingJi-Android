package com.qingji.app.domain

import com.qingji.app.data.local.entity.ExerciseLog
import java.time.LocalDate

/**
 * 统一运动领域视图（统一运动体系 V2.1 / B1）。
 * 供运动历史、日记、统计、Widget 读取；五种来源归一，一场训练只算一场。
 *
 * 来源区分：
 * - QUICK_LOG：独立 ExerciseLog（sourceId 空）
 * - SESSION：正式 WorkoutSession（有 uuid）
 * - DIARY_PROJECTION：正式训练同步到 exercise_log 的投影（sourceId=session.uuid）——**必须跳过，不重复统计**
 * - 会话内力量/有氧明细由 items 表达，不单独成为一条 record。
 */
data class UnifiedExerciseRecord(
    val id: String,                 // "exlog:<id>" 或 "session:<uuid>"
    val source: Source,
    val date: LocalDate,
    val type: ExerciseRecordType,
    val name: String,
    val durationSec: Long,
    val kcal: Double,
    val distanceMeters: Double? = null,
    val items: List<WorkoutItem> = emptyList(),
    val mode: WorkoutMode = WorkoutMode.NORMAL,
    val note: String = ""
) {
    enum class Source { QUICK_LOG, SESSION }

    companion object {
        /**
         * 从独立 ExerciseLog 构造。
         *
         * 【去重语义，G2 复核修正】不只看 sourceId 是否为空，而是看它是否指向一个
         * 真实存在的 WorkoutSession——只有"sourceId 非空 且 该 uuid 属于正式训练会话"
         * 才是训练日记投影（TrainingRepository.upsertTrainingDiaryLog 写入），必须跳过。
         * 这样历史导入/其他来源带 sourceId 但无对应 session 的独立运动不会被误删。
         *
         * @param sessionUuids 当前所有正式训练会话 uuid 集合（由调用方 Repository 提供）
         */
        fun fromExerciseLog(
            log: ExerciseLog,
            sessionUuids: Set<String> = emptySet()
        ): UnifiedExerciseRecord? {
            if (log.sourceId.isNotBlank() && log.sourceId in sessionUuids) return null // 日记投影
            val type = TrainingTypeResolver.resolve(
                persistedRecordType = null, name = log.type,
                exerciseType = null, category = null
            ).type
            return UnifiedExerciseRecord(
                id = "exlog:${log.id}",
                source = Source.QUICK_LOG,
                date = log.date,
                type = type,
                name = log.type,
                durationSec = log.minutes * 60L,
                kcal = log.kcal,
                distanceMeters = if (log.distanceKm > 0) log.distanceKm * 1000 else null,
                note = ""
            )
        }
    }
}
