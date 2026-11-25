package com.qingji.app.data.local.ai

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** AI 库 v1：会话。对应迁移报告 qingji_ai.db 的 ai_conversation。 */
@Entity(
    tableName = "ai_conversation",
    indices = [Index("entryPage"), Index("updatedAt")]
)
data class AiConversationEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val entryPage: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long = 0L,
    val schemaVersion: Int = 1
)

/** AI 库 v1：消息。toolCallsJson 保存工具执行记录，content 保存正文。 */
@Entity(
    tableName = "ai_message",
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "sequence"], unique = true)
    ]
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val sequence: Int,
    val role: String,
    val content: String = "",
    val messageType: String = "text",
    val toolCallsJson: String = "",
    val tokenUsageJson: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val status: String = "success",
    val createdAt: Long
)

/** AI 库 v1 表定义齐全；以下表阶段 D 尚未读写，但 v1 一次建齐，避免后续 v2 改 schema。 */
@Entity(
    tableName = "ai_tool_call",
    indices = [Index(value = ["conversationId", "callId"], unique = true)]
)
data class AiToolCallEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String = "",
    val callId: String,
    val toolName: String,
    val argumentsJson: String = "",
    val resultJson: String = "",
    val status: String = "",
    val durationMs: Long = 0L,
    val errorCode: String = ""
)

@Entity(
    tableName = "ai_proposal",
    indices = [Index(value = ["status", "expiresAt"])]
)
data class AiProposalEntity(
    @PrimaryKey val id: String,
    val proposalType: String,
    val title: String = "",
    val reason: String = "",
    val beforeJson: String = "",
    val afterJson: String = "",
    val targetTable: String = "",
    val targetRecordId: String = "",
    val baseVersion: Int = 0,
    val status: String,
    val expiresAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "ai_token_usage")
data class AiTokenUsageEntity(
    @PrimaryKey val id: String,
    val conversationId: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val inputTokens: Int?,
    val outputTokens: Int?,
    val taskType: String = "",
    val requestedAt: Long
)
