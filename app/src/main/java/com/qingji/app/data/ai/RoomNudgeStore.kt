package com.qingji.app.data.ai

import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiNudgeEntity
import com.qingji.app.domain.nudge.NudgeKind
import com.qingji.app.domain.nudge.NudgeOutcome
import com.qingji.app.domain.nudge.NudgeReason
import com.qingji.app.domain.nudge.NudgeRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Room 实现：只做实体↔领域模型转换，业务判断在 NudgeManager / 规则层。 */
class RoomNudgeStore(private val db: AiDatabase) : NudgeStore {

    private val dao get() = db.aiNudgeDao()

    override fun observeRecent(limit: Int): Flow<List<NudgeRecord>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override suspend fun recent(limit: Int): List<NudgeRecord> =
        dao.recent(limit).map { it.toDomain() }

    override suspend fun since(since: Long): List<NudgeRecord> =
        dao.since(since).map { it.toDomain() }

    override suspend fun insert(record: NudgeRecord): Boolean =
        dao.insert(record.toEntity()) != -1L

    override suspend fun updateOutcome(id: String, outcome: NudgeOutcome, updatedAt: Long) =
        dao.updateOutcome(id, outcome.name, updatedAt)

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun clearAll() = dao.clearAll()

    private fun AiNudgeEntity.toDomain() = NudgeRecord(
        id = id,
        kind = NudgeKind.fromKey(kind) ?: NudgeKind.RECORD_INCOMPLETE,
        reason = NudgeReason.fromKey(reason) ?: NudgeReason.FOOD_LOG_MISSING,
        title = title,
        body = body,
        facts = decodeFacts(factsJson),
        needsAnalysis = needsAnalysis,
        dateKey = dateKey,
        outcome = runCatching { NudgeOutcome.valueOf(outcome) }.getOrDefault(NudgeOutcome.SHOWN),
        shownAt = shownAt,
        updatedAt = updatedAt
    )

    private fun NudgeRecord.toEntity() = AiNudgeEntity(
        id = id,
        kind = kind.key,
        reason = reason.key,
        dateKey = dateKey,
        title = title,
        body = body,
        factsJson = encodeFacts(facts),
        needsAnalysis = needsAnalysis,
        outcome = outcome.name,
        shownAt = shownAt,
        updatedAt = updatedAt
    )

    /** 关键数字用扁平 JSON 存：值全部是字符串，不需要额外的类型约定。 */
    private fun encodeFacts(facts: Map<String, String>): String {
        if (facts.isEmpty()) return "{}"
        val json = JSONObject()
        facts.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    /** 解析失败一律当成空表：历史记录坏了不该让整个页面崩。 */
    private fun decodeFacts(raw: String): Map<String, String> = runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { key -> json.optString(key) }
    }.getOrDefault(emptyMap())
}
