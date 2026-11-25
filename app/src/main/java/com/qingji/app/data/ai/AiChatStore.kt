package com.qingji.app.data.ai

import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import kotlinx.coroutines.flow.Flow

data class AiConversationSummary(
    val id: String,
    val title: String,
    val entryPage: String,
    val updatedAt: Long
)

data class PersistedChatMessage(
    val id: String,
    val conversationId: String,
    val sequence: Int,
    val role: String,
    val text: String,
    val conclusions: List<AgentConclusion> = emptyList(),
    val tools: List<AgentToolExecution> = emptyList(),
    val isError: Boolean = false,
    val warnings: List<String> = emptyList()
)

/** AI 会话存储抽象，UI 不直接依赖 Room。 */
interface AiChatStore {
    fun observeConversations(): Flow<List<AiConversationSummary>>
    fun observeMessages(conversationId: String): Flow<List<PersistedChatMessage>>

    /** 长会话只加载最近 limit 条，避免一次性把全部消息读进内存。 */
    fun observeMessagesPaged(conversationId: String, limit: Int): Flow<List<PersistedChatMessage>> =
        observeMessages(conversationId)

    suspend fun countMessages(conversationId: String): Int = 0

    suspend fun ensureConversation(entryPage: String): AiConversationSummary
    suspend fun createConversation(entryPage: String): AiConversationSummary
    suspend fun appendUserMessage(id: String, conversationId: String, text: String)
    suspend fun appendAssistantMessage(id: String, conversationId: String, result: AgentResult)
    suspend fun appendErrorMessage(id: String, conversationId: String, text: String)
    suspend fun deleteMessage(id: String)
    suspend fun renameConversation(id: String, title: String)
    suspend fun deleteConversation(id: String)
}
