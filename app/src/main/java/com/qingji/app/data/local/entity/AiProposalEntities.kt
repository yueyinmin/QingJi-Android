package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 提案执行权威记录（主库 qingji.db）。
 *
 * 幂等键唯一索引是防止重复确认/网络重试重复写入的最后一道门；
 * AI 库只存展示状态，不存权威幂等键。
 */
@Entity(
    tableName = "ai_proposal_execution",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["proposalId"]),
        Index(value = ["status"])
    ]
)
data class AiProposalExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val proposalId: String,
    val proposalType: String,
    @ColumnInfo(defaultValue = "") val targetTable: String = "",
    @ColumnInfo(defaultValue = "") val targetRecordId: String = "",
    val baseVersion: Long = 0L,
    val status: String,
    val idempotencyKey: String,
    @ColumnInfo(defaultValue = "") val errorCode: String = "",
    @ColumnInfo(defaultValue = "") val errorMessage: String = "",
    val createdAt: Long,
    val executedAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** 正式 AI 操作审计，只记录结果，不参与业务权威判定。 */
@Entity(
    tableName = "ai_operation_log",
    indices = [
        Index(value = ["proposalId"]),
        Index(value = ["idempotencyKey"])
    ]
)
data class AiOperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val proposalId: String = "",
    val action: String,
    val result: String,
    @ColumnInfo(defaultValue = "") val errorCode: String = "",
    @ColumnInfo(defaultValue = "") val detailsJson: String = "",
    @ColumnInfo(defaultValue = "") val idempotencyKey: String = "",
    val executedAt: Long
)
