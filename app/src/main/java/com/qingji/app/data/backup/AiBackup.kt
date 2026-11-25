package com.qingji.app.data.backup

import com.qingji.app.data.local.ai.AiConversationEntity
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMemoryEntity
import com.qingji.app.data.local.ai.AiMessageEntity
import com.qingji.app.data.local.ai.AiNudgeEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================
 * 备份文件 v9 新增的 aiData 段（阶段 L）
 * ------------------------------------------------------------
 * 带什么：
 * - conversations / messages：用户与管家的对话原文；
 * - memories：长期记忆（含来源、可信度、失效状态）；
 * - nudges：管家提醒历史（顺带把频控计数带回去，换机后不会当天又重复提醒）。
 *
 * 不带什么：
 * - ai_tool_call / ai_token_usage：过程与用量数据，重新用起来就会重新产生；
 * - ai_proposal：它是「待确认的展示镜像」，跨设备没有意义，
 *   恢复后已确认/待确认的提案全部作废（见 [RESTORE_NOTE]）；
 * - API Key：从来不在备份里。
 *
 * 兼容规则（与其它段一致，优先不丢数据）：
 * - version >= 9 且文件里真的有 aiData 段：整段覆盖（先清 AI 库再灌）；
 * - version < 9（阶段 K 及以前导出的备份）：**AI 库一行都不动**，
 *   免得用户导个老备份把现在的对话和记忆清空。
 * ============================================================
 */
data class AiBackup(
    val conversations: List<AiConversationEntity> = emptyList(),
    val messages: List<AiMessageEntity> = emptyList(),
    val memories: List<AiMemoryEntity> = emptyList(),
    val nudges: List<AiNudgeEntity> = emptyList()
) {

    fun isEmpty(): Boolean =
        conversations.isEmpty() && messages.isEmpty() && memories.isEmpty() && nudges.isEmpty()

    companion object {
        /** 本段从第几版备份开始存在；用来判断「老备份不要动本机 AI 数据」。 */
        const val VERSION = 9

        /** 恢复后写在摘要里的一句话，让用户知道提案为什么没了。 */
        const val RESTORE_NOTE = "待确认的 AI 提案已作废"

        fun fromJson(obj: JSONObject?): AiBackup {
            if (obj == null) return AiBackup()

            val conversations = (obj.optJSONArray("conversations") ?: JSONArray())
                .mapObjects { o ->
                    val id = o.optString("id").trim()
                    if (id.isEmpty()) return@mapObjects null
                    AiConversationEntity(
                        id = id,
                        title = o.optString("title"),
                        entryPage = o.optString("entryPage"),
                        createdAt = o.optLong("createdAt"),
                        updatedAt = o.optLong("updatedAt")
                    )
                }

            val messages = (obj.optJSONArray("messages") ?: JSONArray()).mapObjects { o ->
                val id = o.optString("id").trim()
                val conversationId = o.optString("conversationId").trim()
                if (id.isEmpty() || conversationId.isEmpty()) return@mapObjects null
                AiMessageEntity(
                    id = id,
                    conversationId = conversationId,
                    sequence = o.optInt("sequence"),
                    role = o.optString("role"),
                    content = o.optString("content"),
                    messageType = o.optString("messageType", "text").ifBlank { "text" },
                    toolCallsJson = o.optString("toolCallsJson"),
                    tokenUsageJson = o.optString("tokenUsageJson"),
                    providerId = o.optString("providerId"),
                    modelId = o.optString("modelId"),
                    status = o.optString("status", "success").ifBlank { "success" },
                    createdAt = o.optLong("createdAt")
                )
            }

            val memories = (obj.optJSONArray("memories") ?: JSONArray()).mapObjects { o ->
                val id = o.optString("id").trim()
                val dedupeKey = o.optString("dedupeKey").trim()
                // dedupeKey 是唯一索引的一部分，缺了就没法安全落库（会给所有空串记忆互相冲突）
                if (id.isEmpty() || dedupeKey.isEmpty()) return@mapObjects null
                AiMemoryEntity(
                    id = id,
                    category = o.optString("category", "preference").ifBlank { "preference" },
                    content = o.optString("content"),
                    truthType = o.optString("truthType", "INFERRED").ifBlank { "INFERRED" },
                    source = o.optString("source", "AI_INFERRED").ifBlank { "AI_INFERRED" },
                    sourceRef = o.optString("sourceRef"),
                    confidence = if (o.has("confidence") && !o.isNull("confidence")) {
                        // optDouble 遇到坏值会给 NaN，落库前挡掉
                        o.optDouble("confidence").takeIf { !it.isNaN() }
                    } else {
                        null
                    },
                    status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" },
                    expiresAt = o.optLong("expiresAt"),
                    dedupeKey = dedupeKey,
                    createdAt = o.optLong("createdAt"),
                    updatedAt = o.optLong("updatedAt")
                )
            }

            val nudges = (obj.optJSONArray("nudges") ?: JSONArray()).mapObjects { o ->
                val id = o.optString("id").trim()
                val kind = o.optString("kind").trim()
                val dateKey = o.optString("dateKey").trim()
                if (id.isEmpty() || kind.isEmpty() || dateKey.isEmpty()) return@mapObjects null
                AiNudgeEntity(
                    id = id,
                    kind = kind,
                    reason = o.optString("reason"),
                    dateKey = dateKey,
                    title = o.optString("title"),
                    body = o.optString("body"),
                    factsJson = o.optString("factsJson", "{}").ifBlank { "{}" },
                    needsAnalysis = o.optBoolean("needsAnalysis"),
                    outcome = o.optString("outcome", "SHOWN").ifBlank { "SHOWN" },
                    shownAt = o.optLong("shownAt"),
                    updatedAt = o.optLong("updatedAt")
                )
            }

            return AiBackup(conversations, messages, memories, nudges)
        }

        /**
         * 数组 → 对象列表；单条坏了只跳过它，不牵连整段
         * （与 ParsedBackup 里 parseArray 的约定一致）。
         */
        private fun <T> JSONArray.mapObjects(block: (JSONObject) -> T?): List<T> {
            val out = ArrayList<T>(length())
            for (i in 0 until length()) {
                val obj = optJSONObject(i) ?: continue
                val item = try {
                    block(obj)
                } catch (_: Exception) {
                    null
                }
                if (item != null) out.add(item)
            }
            return out
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("conversations", JSONArray().apply {
            conversations.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("title", c.title)
                    put("entryPage", c.entryPage)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                })
            }
        })
        put("messages", JSONArray().apply {
            messages.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("conversationId", m.conversationId)
                    put("sequence", m.sequence)
                    put("role", m.role)
                    put("content", m.content)
                    put("messageType", m.messageType)
                    put("toolCallsJson", m.toolCallsJson)
                    put("tokenUsageJson", m.tokenUsageJson)
                    put("providerId", m.providerId)
                    put("modelId", m.modelId)
                    put("status", m.status)
                    put("createdAt", m.createdAt)
                })
            }
        })
        put("memories", JSONArray().apply {
            memories.forEach { mem ->
                put(JSONObject().apply {
                    put("id", mem.id)
                    put("category", mem.category)
                    put("content", mem.content)
                    put("truthType", mem.truthType)
                    put("source", mem.source)
                    put("sourceRef", mem.sourceRef)
                    put("confidence", mem.confidence ?: JSONObject.NULL)
                    put("status", mem.status)
                    put("expiresAt", mem.expiresAt)
                    put("dedupeKey", mem.dedupeKey)
                    put("createdAt", mem.createdAt)
                    put("updatedAt", mem.updatedAt)
                })
            }
        })
        put("nudges", JSONArray().apply {
            nudges.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id)
                    put("kind", n.kind)
                    put("reason", n.reason)
                    put("dateKey", n.dateKey)
                    put("title", n.title)
                    put("body", n.body)
                    put("factsJson", n.factsJson)
                    put("needsAnalysis", n.needsAnalysis)
                    put("outcome", n.outcome)
                    put("shownAt", n.shownAt)
                    put("updatedAt", n.updatedAt)
                })
            }
        })
    }

    /**
     * 写入 AI 库（调用方保证在事务里）：
     * 先清空 7 张表，再按「会话 → 消息 → 记忆 → 提醒」顺序灌入。
     * 记忆与提醒的唯一索引冲突用 IGNORE，坏一条不影响其它。
     */
    suspend fun restoreInto(db: AiDatabase) {
        val dao = db.aiChatDao()
        dao.clearAll()
        conversations.forEach { dao.insertConversation(it) }
        messages.forEach { dao.insertMessage(it) }
        memories.forEach { db.aiMemoryDao().insert(it) }
        nudges.forEach { db.aiNudgeDao().insert(it) }
    }
}
