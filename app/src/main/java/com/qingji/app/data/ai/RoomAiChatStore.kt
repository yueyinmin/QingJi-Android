package com.qingji.app.data.ai

import androidx.room.withTransaction
import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import com.qingji.app.ai.agent.TrustLabel
import com.qingji.app.data.local.ai.AiConversationEntity
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 基于 qingji_ai.db v1 的会话存储。 */
class RoomAiChatStore(
    private val database: AiDatabase
) : AiChatStore {

    private val dao = database.aiChatDao()

    override fun observeConversations(): Flow<List<AiConversationSummary>> =
        dao.observeConversations().map { rows ->
            rows.map {
                AiConversationSummary(
                    id = it.id,
                    title = it.title.ifBlank { "新对话" },
                    entryPage = it.entryPage,
                    updatedAt = it.updatedAt
                )
            }
        }

    override fun observeMessages(conversationId: String): Flow<List<PersistedChatMessage>> =
        dao.observeMessages(conversationId).map { rows -> rows.map { decode(it) } }

    suspend fun conversationsOnce(): List<AiConversationSummary> =
        dao.conversations().map { summary(it) }

    suspend fun messagesOnce(conversationId: String): List<PersistedChatMessage> =
        dao.messages(conversationId).map { decode(it) }

    override suspend fun ensureConversation(entryPage: String): AiConversationSummary {
        dao.latestConversation(entryPage)?.let { return summary(it) }
        return createConversation(entryPage)
    }

    override suspend fun createConversation(entryPage: String): AiConversationSummary {
        val now = System.currentTimeMillis()
        val entity = AiConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            entryPage = entryPage,
            createdAt = now,
            updatedAt = now
        )
        dao.insertConversation(entity)
        return summary(entity)
    }

    override suspend fun appendUserMessage(id: String, conversationId: String, text: String) {
        database.withTransaction {
            val conversation = dao.conversation(conversationId)
            val message = AiMessageEntity(
                id = id,
                conversationId = conversationId,
                sequence = dao.nextSequence(conversationId),
                role = "user",
                content = text,
                messageType = "user_text",
                status = "success",
                createdAt = System.currentTimeMillis()
            )
            dao.insertMessage(message)
            if (conversation?.title.isNullOrBlank()) {
                dao.updateConversationTitle(
                    conversationId,
                    text.take(TITLE_LENGTH),
                    System.currentTimeMillis()
                )
            } else {
                dao.touchConversation(conversationId, System.currentTimeMillis())
            }
        }
    }

    override suspend fun appendAssistantMessage(id: String, conversationId: String, result: AgentResult) {
        val content = JSONObject()
            .put("answer", result.answer)
            .put("conclusions", JSONArray().apply {
                result.conclusions.forEach { conclusion ->
                    put(
                        JSONObject()
                            .put("text", conclusion.text)
                            .put("trust", conclusion.trust.name)
                            .put("dataRange", conclusion.dataRange.orEmpty())
                    )
                }
            })
            .put("warnings", JSONArray().apply { result.warnings.forEach { put(it) } })
            .toString()

        append(
            AiMessageEntity(
                id = id,
                conversationId = conversationId,
                sequence = dao.nextSequence(conversationId),
                role = "assistant",
                content = content,
                messageType = "assistant_result",
                toolCallsJson = encodeTools(result.toolExecutions),
                status = "success",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun appendErrorMessage(id: String, conversationId: String, text: String) {
        append(
            AiMessageEntity(
                id = id,
                conversationId = conversationId,
                sequence = dao.nextSequence(conversationId),
                role = "assistant",
                content = text,
                messageType = "assistant_error",
                status = "error",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteMessage(id: String) {
        dao.deleteMessage(id)
    }

    override suspend fun deleteConversation(id: String) {
        dao.softDeleteConversation(id, System.currentTimeMillis())
    }

    private suspend fun append(message: AiMessageEntity) {
        database.withTransaction {
            dao.insertMessage(message)
            dao.touchConversation(message.conversationId, message.createdAt)
        }
    }

    private fun summary(entity: AiConversationEntity) = AiConversationSummary(
        id = entity.id,
        title = entity.title.ifBlank { "新对话" },
        entryPage = entity.entryPage,
        updatedAt = entity.updatedAt
    )

    private fun decode(entity: AiMessageEntity): PersistedChatMessage {
        if (entity.messageType != "assistant_result") {
            return PersistedChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                sequence = entity.sequence,
                role = entity.role,
                text = entity.content,
                isError = entity.messageType == "assistant_error",
                tools = decodeTools(entity.toolCallsJson)
            )
        }

        return try {
            val root = JSONObject(entity.content)
            val conclusions = mutableListOf<AgentConclusion>()
            val conclusionArray = root.optJSONArray("conclusions") ?: JSONArray()
            for (i in 0 until conclusionArray.length()) {
                val obj = conclusionArray.optJSONObject(i) ?: continue
                conclusions.add(
                    AgentConclusion(
                        text = obj.optString("text"),
                        trust = TrustLabel.from(obj.optString("trust")) ?: TrustLabel.UNKNOWN,
                        dataRange = obj.optString("dataRange").takeIf { it.isNotBlank() }
                    )
                )
            }
            val warnings = mutableListOf<String>()
            val warningArray = root.optJSONArray("warnings") ?: JSONArray()
            for (i in 0 until warningArray.length()) warnings.add(warningArray.optString(i))

            PersistedChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                sequence = entity.sequence,
                role = entity.role,
                text = root.optString("answer"),
                conclusions = conclusions,
                tools = decodeTools(entity.toolCallsJson),
                warnings = warnings
            )
        } catch (_: Exception) {
            PersistedChatMessage(
                id = entity.id,
                conversationId = entity.conversationId,
                sequence = entity.sequence,
                role = entity.role,
                text = entity.content,
                tools = decodeTools(entity.toolCallsJson)
            )
        }
    }

    private fun encodeTools(tools: List<AgentToolExecution>): String = JSONArray().apply {
        tools.forEach { tool ->
            put(
                JSONObject()
                    .put("callId", tool.callId)
                    .put("name", tool.name)
                    .put("argumentsJson", tool.argumentsJson)
                    .put("ok", tool.ok)
                    .put("errorCode", tool.errorCode.orEmpty())
                    .put("durationMs", tool.durationMs)
                    .put("warning", tool.warning.orEmpty())
            )
        }
    }.toString()

    private fun decodeTools(json: String): List<AgentToolExecution> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                AgentToolExecution(
                    callId = obj.optString("callId"),
                    name = obj.optString("name"),
                    argumentsJson = obj.optString("argumentsJson"),
                    ok = obj.optBoolean("ok"),
                    errorCode = obj.optString("errorCode").takeIf { it.isNotBlank() },
                    durationMs = obj.optLong("durationMs"),
                    warning = obj.optString("warning").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val TITLE_LENGTH = 18
    }
}
