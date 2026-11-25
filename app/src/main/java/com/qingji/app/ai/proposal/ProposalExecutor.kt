package com.qingji.app.ai.proposal

import com.qingji.app.data.local.ai.AiProposalEntity
import com.qingji.app.data.repository.TrainingRepository

/**
 * 提案执行器。阶段 E 只接训练计划类，且**不注册到 Agent 工具表**；
 * 写工具继续禁用，等 E 验收通过再开放。
 */
interface ProposalExecutor {
    val supportedTypes: Set<String>

    /** 返回 false = CAS 失败，提案标 STALE。 */
    suspend fun execute(proposal: AiProposalEntity): Boolean

    /** 崩溃恢复时判断业务是否已经落库。 */
    suspend fun alreadyExecuted(proposal: AiProposalEntity): Boolean

    /** 目标是否仍存在；不存在时 PENDING 提案直接 STALE。 */
    suspend fun targetExists(proposal: AiProposalEntity): Boolean

    /** 当前版本，用于确认前的 STALE 预检。 */
    suspend fun currentVersion(proposal: AiProposalEntity): Long?
}

class PlanProposalExecutor(
    private val trainingRepository: TrainingRepository
) : ProposalExecutor {

    override val supportedTypes = setOf(
        TYPE_ACTIVATE, TYPE_DEACTIVATE, TYPE_DELETE
    )

    override suspend fun execute(proposal: AiProposalEntity): Boolean {
        val uuid = proposal.targetRecordId
        val base = proposal.baseVersion.toLong()
        return when (proposal.proposalType) {
            TYPE_ACTIVATE -> trainingRepository.casSetActiveForProposal(uuid, true, base)
            TYPE_DEACTIVATE -> trainingRepository.casSetActiveForProposal(uuid, false, base)
            TYPE_DELETE -> trainingRepository.casDeleteForProposal(uuid, base)
            else -> false
        }
    }

    override suspend fun alreadyExecuted(proposal: AiProposalEntity): Boolean {
        val current = currentVersion(proposal) ?: return proposal.proposalType == TYPE_DELETE
        return if (proposal.proposalType == TYPE_DELETE) false else current > proposal.baseVersion
    }

    override suspend fun targetExists(proposal: AiProposalEntity): Boolean =
        currentVersion(proposal) != null

    override suspend fun currentVersion(proposal: AiProposalEntity): Long? =
        trainingRepository.planVersion(proposal.targetRecordId)

    companion object {
        const val TYPE_ACTIVATE = "PLAN_ACTIVATE"
        const val TYPE_DEACTIVATE = "PLAN_DEACTIVATE"
        const val TYPE_DELETE = "PLAN_DELETE"
    }
}
