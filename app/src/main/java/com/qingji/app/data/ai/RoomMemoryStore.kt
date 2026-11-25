package com.qingji.app.data.ai

import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMemoryEntity
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemorySource
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 实现：只做实体↔领域模型转换与字段校验，业务规则在 MemoryManager。 */
class RoomMemoryStore(private val db: AiDatabase) : MemoryStore {

    private val dao get() = db.aiMemoryDao()

    override fun observeActive(): Flow<List<MemoryRecord>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<MemoryRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<MemoryRecord> = dao.all().map { it.toDomain() }

    override suspend fun activeByCategories(
        categories: Set<MemoryCategory>,
        limit: Int
    ): List<MemoryRecord> =
        if (categories.isEmpty()) emptyList()
        else dao.activeByCategories(categories.map { it.key }, limit).map { it.toDomain() }

    override suspend fun findById(id: String): MemoryRecord? = dao.findById(id)?.toDomain()

    override suspend fun findByDedupeKey(dedupeKey: String): MemoryRecord? =
        dao.findByDedupeKey(dedupeKey)?.toDomain()

    override suspend fun findBySourceRef(sourceRef: String): List<MemoryRecord> =
        dao.findBySourceRef(sourceRef).map { it.toDomain() }

    override suspend fun insert(record: MemoryRecord): Boolean =
        dao.insert(record.toEntity()) != -1L

    override suspend fun updateContent(
        id: String,
        content: String,
        category: MemoryCategory,
        truth: MemoryTruth,
        source: MemorySource,
        dedupeKey: String,
        updatedAt: Long
    ) = dao.updateContent(id, content, category.key, truth.name, source.name, dedupeKey, updatedAt)

    override suspend fun updateStatus(id: String, status: MemoryStatus, updatedAt: Long) =
        dao.updateStatus(id, status.name, updatedAt)

    override suspend fun upgradeTruth(
        id: String,
        truth: MemoryTruth,
        source: MemorySource,
        updatedAt: Long
    ) = dao.upgradeTruth(id, truth.name, source.name, updatedAt)

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun clearAll() = dao.clearAll()

    override suspend fun expireOverdue(now: Long): Int = dao.expireOverdue(now)

    private fun AiMemoryEntity.toDomain() = MemoryRecord(
        id = id,
        category = MemoryCategory.fromKey(category) ?: MemoryCategory.PREFERENCE,
        content = content,
        truthType = runCatching { MemoryTruth.valueOf(truthType) }.getOrDefault(MemoryTruth.INFERRED),
        source = runCatching { MemorySource.valueOf(source) }.getOrDefault(MemorySource.AI_INFERRED),
        sourceRef = sourceRef,
        confidence = confidence,
        status = runCatching { MemoryStatus.valueOf(status) }.getOrDefault(MemoryStatus.ACTIVE),
        expiresAt = expiresAt,
        dedupeKey = dedupeKey,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MemoryRecord.toEntity() = AiMemoryEntity(
        id = id,
        category = category.key,
        content = content,
        truthType = truthType.name,
        source = source.name,
        sourceRef = sourceRef,
        confidence = confidence,
        status = status.name,
        expiresAt = expiresAt,
        dedupeKey = dedupeKey,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
