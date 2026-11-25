package com.qingji.app.ai.nudge

import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.WeightPoint
import com.qingji.app.domain.nudge.NudgeFacts
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * 从业务库读事实：只走现有 Repository 查询，不新增表、不改任何数据。
 *
 * 三条纪律：
 * - 每个数字都来自用户自己的记录，缺就是缺（0 / 空表），不填默认值；
 * - 查询失败直接抛出，由 NudgeManager 兜底成「这次不提醒」，绝不拿半截数据下结论；
 * - 只看必要范围：饮食看今天、体重看近 28 天、训练看今天。
 */
class RepositoryNudgeFactsSource(
    private val repo: QingJiRepository,
    private val training: TrainingRepository
) : NudgeFactsSource {

    override suspend fun snapshot(now: LocalDateTime): NudgeFacts {
        val today = now.toLocalDate()

        val foodLogs = repo.observeFoodLogs(today).first()
        val profile = repo.observeProfile().first()
        val weightLogs = repo.observeWeightLogs().first()
        val activePlan = training.observeActivePlan().first()
        val sessions = training.observeSessions().first()
        val plannedToday = if (activePlan == null) emptyList() else training.todayPlanExercises()

        val weightPoints = weightLogs
            .filter { it.date >= today.minusDays(27) && it.date <= today }
            .sortedBy { it.date }
            .map { WeightPoint(date = it.date, weightKg = it.weightKg, bodyFatPct = it.bodyFat) }

        return NudgeFacts(
            now = now,
            today = today,
            foodLogCountToday = foodLogs.size,
            mealCountToday = foodLogs.map { it.mealType }.distinct().size,
            kcalToday = foodLogs.sumOf { it.kcal },
            proteinTodayG = foodLogs.sumOf { it.proteinG },
            proteinGoalG = profile?.proteinGoalG ?: 0,
            hasActivePlan = activePlan != null,
            plannedExerciseCountToday = plannedToday.size,
            trainedToday = sessions.any { it.date == today },
            weightPoints = weightPoints,
            targetWeightKg = profile?.targetWeight ?: 0.0,
            heightCm = profile?.heightCm ?: 0.0
        )
    }
}
