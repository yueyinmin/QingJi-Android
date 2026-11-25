package com.qingji.app.ai.proposal

import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiProposalEntity
import com.qingji.app.data.local.entity.AiOperationLogEntity
import com.qingji.app.data.local.entity.AiProposalExecutionEntity
import java.security.MessageDigest

object ProposalStatus {
    const val PENDING = "PENDING_CONFIRMATION"
    const val EXECUTING = "EXECUTING"
    const val EXECUTED = "EXECUTED"
    const val REJECTED = "REJECTED"
    const val STALE = "STALE"
    const val FAILED = "FAILED"
    const val EXPIRED = "EXPIRED"
}

sealed class ProposalResult(val status: String) {
    data class Done(val value: String, val executionId: Long? = null) : ProposalResult(value)
    data class Error(val value: String, val reason: String) : ProposalResult(value)
}

/**
 * 提案状态机。主库 ai_proposal_execution 是唯一权威；AI 库只展示。
 *
 * 阶段 E 纪律：写工具不注册进 ToolRegistry，本类只由显式用户确认入口调用。
 */
class ProposalManager(
    private val mainDb: AppDatabase,
    private val aiDb: AiDatabase,
    private val executors: List<ProposalExecutor>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val executionDao get() = mainDb.aiProposalDao()
    private val displayDao get() = aiDb.aiChatDao()

    /** 同一提案固定一个幂等键；重复确认不会重复执行。 */
    fun idempotencyKey(proposal: AiProposalEntity): String {
        val raw = listOf(
            proposal.id,
            proposal.proposalType,
            proposal.targetTable,
            proposal.targetRecordId,
            proposal.baseVersion.toString(),
            proposal.afterJson
        ).joinToString("|")
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun createProposal(
        id: String,
        proposalType: String,
        title: String,
        reason: String,
        beforeJson: String,
        afterJson: String,
        targetTable: String,
        targetRecordId: String,
        baseVersion: Long,
        ttlMs: Long = DEFAULT_TTL_MS
    ): AiProposalEntity {
        val now = clock()
        val entity = AiProposalEntity(
            id = id,
            proposalType = proposalType,
            title = title,
            reason = reason,
            beforeJson = beforeJson,
            afterJson = afterJson,
            targetTable = targetTable,
            targetRecordId = targetRecordId,
            baseVersion = baseVersion.toInt(),
            status = ProposalStatus.PENDING,
            expiresAt = if (ttlMs > 0) now + ttlMs else 0L,
            createdAt = now,
            updatedAt = now
        )
        displayDao.insertProposal(entity)
        return entity
    }

    suspend fun reject(proposalId: String): ProposalResult {
        val proposal = displayDao.proposal(proposalId)
            ?: return ProposalResult.Error(ProposalStatus.FAILED, "提案不存在")
        displayDao.updateProposalStatus(proposalId, ProposalStatus.REJECTED, clock())
        return ProposalResult.Done(ProposalStatus.REJECTED)
    }

    /** 用户确认入口。成功/失败/STALE 都由主库记录决定。 */
    suspend fun confirm(proposalId: String): ProposalResult {
        val proposal = displayDao.proposal(proposalId)
            ?: return ProposalResult.Error(ProposalStatus.FAILED, "提案不存在")

        val now = clock()
        if (proposal.status != ProposalStatus.PENDING) {
            return existingResult(proposal)
        }
        if (proposal.expiresAt > 0 && now > proposal.expiresAt) {
            displayDao.updateProposalStatus(proposalId, ProposalStatus.EXPIRED, now)
            return ProposalResult.Done(ProposalStatus.EXPIRED)
        }

        val key = idempotencyKey(proposal)
        executionDao.findByIdempotencyKey(key)?.let { return resultOf(it) }

        val executor = executors.firstOrNull { proposal.proposalType in it.supportedTypes }
            ?: return failWithoutExecution(proposal, key, "NO_EXECUTOR", "提案类型未注册执行器")

        if (!executor.targetExists(proposal) ||
            executor.currentVersion(proposal) != proposal.baseVersion.toLong()
        ) {
            return markStale(proposal, key, "版本不匹配或目标不存在")
        }

        val createdAt = clock()
        val record = AiProposalExecutionEntity(
            proposalId = proposal.id,
            proposalType = proposal.proposalType,
            targetTable = proposal.targetTable,
            targetRecordId = proposal.targetRecordId,
            baseVersion = proposal.baseVersion.toLong(),
            status = ProposalStatus.EXECUTING,
            idempotencyKey = key,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        val executionId = try {
            executionDao.insertExecution(record)
        } catch (e: Exception) {
            val existing = executionDao.findByIdempotencyKey(key)
            if (existing != null) return resultOf(existing)
            throw e
        }

        val ok = try {
            mainDb.withTransaction {
                val success = executor.execute(proposal)
                if (!success) throw VersionConflict()
                val finishedAt = clock()
                executionDao.markFinished(executionId, ProposalStatus.EXECUTED, finishedAt, finishedAt)
                executionDao.insertOperationLog(
                    AiOperationLogEntity(
                        proposalId = proposal.id,
                        action = proposal.proposalType,
                        result = ProposalStatus.EXECUTED,
                        detailsJson = proposal.afterJson,
                        idempotencyKey = key,
                        executedAt = finishedAt
                    )
                )
                true
            }
        } catch (_: VersionConflict) {
            false
        } catch (e: Exception) {
            // 事务已回滚；FAILED 必须写在事务外，否则会一起被回滚掉。
            executionDao.updateStatus(
                id = executionId,
                status = ProposalStatus.FAILED,
                errorCode = "EXECUTION_FAILED",
                errorMessage = e.message?.take(200).orEmpty(),
                updatedAt = clock()
            )
            displayDao.updateProposalStatus(proposalId, ProposalStatus.FAILED, clock())
            return ProposalResult.Error(ProposalStatus.FAILED, e.message ?: "执行失败")
        }

        if (!ok) {
            executionDao.updateStatus(
                id = executionId,
                status = ProposalStatus.STALE,
                errorCode = "VERSION_CONFLICT",
                errorMessage = "确认时数据已变化",
                updatedAt = clock()
            )
            displayDao.updateProposalStatus(proposalId, ProposalStatus.STALE, clock())
            return ProposalResult.Done(ProposalStatus.STALE)
        }

        displayDao.updateProposalStatus(proposalId, ProposalStatus.EXECUTED, clock())
        return ProposalResult.Done(ProposalStatus.EXECUTED, executionId)
    }

    /** 启动扫描：超时的 EXECUTING 不能永久卡住。 */
    suspend fun recoverExecuting(maxAgeMs: Long = DEFAULT_EXECUTING_MAX_AGE_MS) {
        val cutoff = clock() - maxAgeMs
        executionDao.findStaleExecuting(ProposalStatus.EXECUTING, cutoff, 100).forEach { record ->
            val proposal = displayDao.proposal(record.proposalId)
            val executor = executors.firstOrNull { record.proposalType in it.supportedTypes }
            val changed = if (proposal != null && executor != null) {
                executor.alreadyExecuted(proposal)
            } else {
                false
            }
            val status = if (changed) ProposalStatus.EXECUTED else ProposalStatus.FAILED
            val code = if (changed) "" else "RECOVERY_FAILED"
            executionDao.updateStatus(record.id, status, code, "", clock())
            if (proposal != null) displayDao.updateProposalStatus(proposal.id, status, clock())
        }
    }

    /** 主库权威 → AI 库展示单向对账；PENDING 提案做过期与版本预检。 */
    suspend fun reconcile() {
        displayDao.allProposals().forEach { proposal ->
            executionDao.latestByProposal(proposal.id)?.let { record ->
                if (record.status != proposal.status) {
                    displayDao.updateProposalStatus(proposal.id, record.status, clock())
                }
                return@forEach
            }
            if (proposal.status != ProposalStatus.PENDING) return@forEach
            val now = clock()
            if (proposal.expiresAt > 0 && now > proposal.expiresAt) {
                displayDao.updateProposalStatus(proposal.id, ProposalStatus.EXPIRED, now)
                return@forEach
            }
            val executor = executors.firstOrNull { proposal.proposalType in it.supportedTypes }
                ?: return@forEach
            if (!executor.targetExists(proposal) ||
                executor.currentVersion(proposal) != proposal.baseVersion.toLong()
            ) {
                displayDao.updateProposalStatus(proposal.id, ProposalStatus.STALE, now)
            }
        }
    }

    private suspend fun existingResult(proposal: AiProposalEntity): ProposalResult {
        val key = idempotencyKey(proposal)
        executionDao.findByIdempotencyKey(key)?.let { return resultOf(it) }
        return ProposalResult.Done(proposal.status)
    }

    private fun resultOf(record: AiProposalExecutionEntity): ProposalResult =
        ProposalResult.Done(record.status, record.id)

    private suspend fun failWithoutExecution(
        proposal: AiProposalEntity,
        key: String,
        code: String,
        reason: String
    ): ProposalResult {
        val now = clock()
        executionDao.insertExecution(
            AiProposalExecutionEntity(
                proposalId = proposal.id,
                proposalType = proposal.proposalType,
                targetTable = proposal.targetTable,
                targetRecordId = proposal.targetRecordId,
                baseVersion = proposal.baseVersion.toLong(),
                status = ProposalStatus.FAILED,
                idempotencyKey = key,
                errorCode = code,
                errorMessage = reason,
                createdAt = now,
                updatedAt = now
            )
        )
        displayDao.updateProposalStatus(proposal.id, ProposalStatus.FAILED, now)
        return ProposalResult.Error(ProposalStatus.FAILED, reason)
    }

    private suspend fun markStale(
        proposal: AiProposalEntity,
        key: String,
        reason: String
    ): ProposalResult {
        val now = clock()
        val existing = executionDao.findByIdempotencyKey(key)
        if (existing == null) {
            executionDao.insertExecution(
                AiProposalExecutionEntity(
                    proposalId = proposal.id,
                    proposalType = proposal.proposalType,
                    targetTable = proposal.targetTable,
                    targetRecordId = proposal.targetRecordId,
                    baseVersion = proposal.baseVersion.toLong(),
                    status = ProposalStatus.STALE,
                    idempotencyKey = key,
                    errorCode = "VERSION_CONFLICT",
                    errorMessage = reason,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        displayDao.updateProposalStatus(proposal.id, ProposalStatus.STALE, now)
        return ProposalResult.Done(ProposalStatus.STALE)
    }

    private class VersionConflict : RuntimeException("CAS version conflict")

    companion object {
        const val DEFAULT_TTL_MS = 24L * 60 * 60 * 1000
        const val DEFAULT_EXECUTING_MAX_AGE_MS = 10L * 60 * 1000
    }
}
