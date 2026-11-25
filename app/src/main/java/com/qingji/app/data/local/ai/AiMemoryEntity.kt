package com.qingji.app.data.local.ai

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 库 v2：长期记忆。
 *
 * 边界（见《长期记忆数据边界设计》）：
 * - 只存经过确认或可追溯的结构化信息，不存聊天全文、不存工具临时结果；
 * - 每条记忆必须有来源（[source] + [sourceRef]），可失效、可删除；
 * - INFERRED 不能被系统自动升级成 FACT，必须用户确认（接口在规则层把关）。
 *
 * [dedupeKey] = 类别 + 归一化内容的 SHA-256，唯一索引用来防重复写入。
 */
@Entity(
    tableName = "ai_memory",
    indices = [
        Index("category"),
        Index("status"),
        Index(value = ["dedupeKey"], unique = true)
    ]
)
data class AiMemoryEntity(
    @PrimaryKey val id: String,
    /** preference / training / nutrition / goal / event / inference */
    val category: String,
    val content: String,
    /** USER_FACT / CONFIRMED / INFERRED */
    val truthType: String,
    /** USER_INPUT / BUSINESS_RECORD / USER_CONFIRMED / AI_INFERRED */
    val source: String,
    /** 来源记录 id 或会话消息 id；空串表示没有可追溯来源（仅用户口述时可能为空） */
    val sourceRef: String = "",
    /** 仅 INFERRED 有意义；不能当成 FACT 的概率来用 */
    val confidence: Double? = null,
    /** ACTIVE / INVALIDATED / EXPIRED */
    val status: String = "ACTIVE",
    /** 到期时间戳（毫秒）；0 = 不过期 */
    val expiresAt: Long = 0L,
    val dedupeKey: String,
    val createdAt: Long,
    val updatedAt: Long
)
