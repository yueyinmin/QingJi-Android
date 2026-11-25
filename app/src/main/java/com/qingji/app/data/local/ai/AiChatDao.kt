package com.qingji.app.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {

    @Query("SELECT * FROM ai_conversation WHERE deletedAt = 0 ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AiConversationEntity>>

    @Query("SELECT * FROM ai_conversation WHERE deletedAt = 0 ORDER BY updatedAt DESC")
    suspend fun conversations(): List<AiConversationEntity>

    @Query("SELECT * FROM ai_conversation WHERE id = :id AND deletedAt = 0")
    suspend fun conversation(id: String): AiConversationEntity?

    @Query("SELECT * FROM ai_conversation WHERE entryPage = :entryPage AND deletedAt = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestConversation(entryPage: String): AiConversationEntity?

    @Insert
    suspend fun insertConversation(conversation: AiConversationEntity)

    @Query("UPDATE ai_conversation SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationTitle(id: String, title: String, updatedAt: Long)

    @Query("UPDATE ai_conversation SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: String, updatedAt: Long)

    @Query("UPDATE ai_conversation SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteConversation(id: String, deletedAt: Long)

    @Query("SELECT * FROM ai_message WHERE conversationId = :conversationId ORDER BY sequence ASC")
    fun observeMessages(conversationId: String): Flow<List<AiMessageEntity>>

    @Query("SELECT * FROM ai_message WHERE conversationId = :conversationId ORDER BY sequence DESC LIMIT :limit")
    fun observeRecentMessages(conversationId: String, limit: Int): Flow<List<AiMessageEntity>>

    @Query("SELECT COUNT(*) FROM ai_message WHERE conversationId = :conversationId")
    suspend fun countMessages(conversationId: String): Int

    @Query("SELECT * FROM ai_message WHERE conversationId = :conversationId ORDER BY sequence ASC")
    suspend fun messages(conversationId: String): List<AiMessageEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM ai_message WHERE conversationId = :conversationId")
    suspend fun nextSequence(conversationId: String): Int

    @Insert
    suspend fun insertMessage(message: AiMessageEntity)

    @Query("DELETE FROM ai_message WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Transaction
    suspend fun appendMessage(message: AiMessageEntity) {
        insertMessage(message)
        touchConversation(message.conversationId, message.createdAt)
    }

    /** 恢复出厂时清空 AI 库；顺序先子表后主表。 */
    @Transaction
    suspend fun clearAll() {
        clearToolCalls()
        clearProposals()
        clearTokenUsage()
        clearMessages()
        clearConversations()
    }

    @Query("DELETE FROM ai_tool_call")
    suspend fun clearToolCalls()

    @Query("DELETE FROM ai_proposal")
    suspend fun clearProposals()

    @Query("DELETE FROM ai_token_usage")
    suspend fun clearTokenUsage()

    @Query("DELETE FROM ai_message")
    suspend fun clearMessages()

    @Query("DELETE FROM ai_conversation")
    suspend fun clearConversations()
}
