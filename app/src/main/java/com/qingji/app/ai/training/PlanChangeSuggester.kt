package com.qingji.app.ai.training

import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.TrainingAnalysis
import org.json.JSONObject

/**
 * 计划差异建议（只生成，不执行）。
 *
 * 用户确认后由 ProposalManager 写主库；任何路径都不得绕过提案直接改计划。
 */
data class PlanChangeSuggestion(
    val proposalType: String,
    val targetRecordId: String,
    val baseVersion: Long,
    val title: String,
    val reason: String,
    val beforeJson: String,
    val afterJson: String
)

object PlanChangeSuggester {

    fun suggest(
        analysis: TrainingAnalysis,
        plans: List<TrainingPlan>
    ): PlanChangeSuggestion? {
        val active = plans.firstOrNull { it.isActive }
        val inactive = plans.firstOrNull { !it.isActive }
        val strength = analysis.summary(ExerciseRecordType.STRENGTH)
            ?: return null

        // 近 28 天力量训练过密且几乎无间隔：建议先停用当前计划。
        if (active != null && strength.sessions >= 6 && (strength.minGapDays ?: Int.MAX_VALUE) <= 1) {
            return PlanChangeSuggestion(
                proposalType = TYPE_DEACTIVATE,
                targetRecordId = active.uuid,
                baseVersion = active.version,
                title = "暂停当前训练计划",
                reason = "近 ${analysis.windowDays} 天力量训练 ${strength.sessions} 次，" +
                    "最短间隔 ${strength.minGapDays} 天，恢复信号不足；建议先暂停计划。",
                beforeJson = stateJson(active),
                afterJson = stateJson(active.copy(isActive = false, version = active.version + 1))
            )
        }

        // 近 28 天没有力量训练，且有现成未启用计划：建议启用。
        if (strength.sessions == 0 && inactive != null) {
            return PlanChangeSuggestion(
                proposalType = TYPE_ACTIVATE,
                targetRecordId = inactive.uuid,
                baseVersion = inactive.version,
                title = "重新启用训练计划",
                reason = "近 ${analysis.windowDays} 天没有力量训练记录；" +
                    "建议重新启用「${inactive.name}」，从轻量开始。",
                beforeJson = stateJson(inactive),
                afterJson = stateJson(inactive.copy(isActive = true, version = inactive.version + 1))
            )
        }

        return null
    }

    private fun stateJson(plan: TrainingPlan): String = JSONObject().apply {
        put("uuid", plan.uuid)
        put("name", plan.name)
        put("isActive", plan.isActive)
        put("version", plan.version)
    }.toString()

    private const val TYPE_ACTIVATE = "PLAN_ACTIVATE"
    private const val TYPE_DEACTIVATE = "PLAN_DEACTIVATE"
}
