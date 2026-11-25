package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qingji.app.data.local.entity.AiOperationLogEntity
import com.qingji.app.data.local.entity.AiProposalExecutionEntity

/** 主库提案执行记录与审计。AI 库只存展示状态，权威状态在这里。 */
@Dao
interface AiProposalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecution(record: AiProposalExecutionEntity): Long

    @Query("SELECT * FROM ai_proposal_execution WHERE idempotencyKey = :key LIMIT 1")
    suspend fun findByIdempotencyKey(key: String): AiProposalExecutionEntity?

    @Query("SELECT * FROM ai_proposal_execution WHERE proposalId = :proposalId ORDER BY id DESC LIMIT 1")
    suspend fun latestByProposal(proposalId: String): AiProposalExecutionEntity?

    @Query("SELECT * FROM ai_proposal_execution WHERE status = :status AND createdAt <= :before ORDER BY id ASC LIMIT :limit")
    suspend fun findStaleExecuting(status: String, before: Long, limit: Int): List<AiProposalExecutionEntity>

    @Query("UPDATE ai_proposal_execution SET status = :status, errorCode = :errorCode, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorCode: String, errorMessage: String, updatedAt: Long)

    /**
     * 阶段 L：恢复备份后，业务数据已整包换掉，卡在 EXECUTING 的台账不可能再有结果，
     * 统一标 STALE（而不是 FAILED）：语义是「作废」，不是「执行失败」。
     */
    @Query(
        "UPDATE ai_proposal_execution SET status = :status, errorCode = :errorCode, " +
            "errorMessage = :errorMessage, updatedAt = :updatedAt WHERE status = 'EXECUTING'"
    )
    suspend fun markExecutingStale(
        status: String,
        errorCode: String,
        errorMessage: String,
        updatedAt: Long
    ): Int

    @Query("UPDATE ai_proposal_execution SET status = :status, executedAt = :executedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFinished(id: Long, status: String, executedAt: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperationLog(log: AiOperationLogEntity)

    @Query("SELECT * FROM ai_operation_log WHERE idempotencyKey = :key ORDER BY id DESC LIMIT 1")
    suspend fun findLogByKey(key: String): AiOperationLogEntity?

    @Query("DELETE FROM ai_proposal_execution")
    suspend fun clearExecutions()

    @Query("DELETE FROM ai_operation_log")
    suspend fun clearLogs()
}
