package com.qingji.app.data.ai

import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemorySource
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import kotlinx.coroutines.flow.Flow

/** 长期记忆存储抽象：规则层不依赖 Room。 */
interface MemoryStore {
    fun observeActive(): Flow<List<MemoryRecord>>
    fun observeAll(): Flow<List<MemoryRecord>>

    suspend fun all(): List<MemoryRecord>

    /** 只取生效中的指定类别，limit 用来卡住候选数量（真正注入还要过字数预算）。 */
    suspend fun activeByCategories(categories: Set<MemoryCategory>, limit: Int): List<MemoryRecord>

    suspend fun findById(id: String): MemoryRecord?
    suspend fun findByDedupeKey(dedupeKey: String): MemoryRecord?
    suspend fun findBySourceRef(sourceRef: String): List<MemoryRecord>

    /** 返回 false 表示已存在同类别同内容（幂等，不报错）。 */
    suspend fun insert(record: MemoryRecord): Boolean

    suspend fun updateContent(
        id: String,
        content: String,
        category: MemoryCategory,
        truth: MemoryTruth,
        source: MemorySource,
        dedupeKey: String,
        updatedAt: Long
    )

    suspend fun updateStatus(id: String, status: MemoryStatus, updatedAt: Long)

    suspend fun upgradeTruth(id: String, truth: MemoryTruth, source: MemorySource, updatedAt: Long)

    /** 用户显式删除单条；不动任何正式业务数据。 */
    suspend fun delete(id: String)

    /** 阶段 K：按来源批量删除（删会话时用户勾了「连记忆一起删」）。 */
    suspend fun deleteBySourceRefs(sourceRefs: List<String>)

    /** 阶段 K：来源被删但保留记忆，只标失效。 */
    suspend fun invalidateBySourceRefs(sourceRefs: List<String>, now: Long)

    /** 阶段 K：全量自检，把来源已不存在的记忆标失效；返回处理条数。 */
    suspend fun invalidateDangling(now: Long): Int

    suspend fun clearAll()

    suspend fun expireOverdue(now: Long): Int
}
