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
         * 【去重红线】带 sourceId 的日志是训练日记投影（TrainingRepository.upsertTrainingDiaryLog），
         * 必须返回 null，避免一场训练在历史/统计里出现两次。
         */
        fun fromExerciseLog(log: ExerciseLog): UnifiedExerciseRecord? {
            if (log.sourceId.isNotBlank()) return null // 日记投影，跳过
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
