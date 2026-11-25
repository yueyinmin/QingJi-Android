package com.qingji.app.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // ---------- 阶段 K：隐私层面的彻底删除 ----------

    /** 会话里的消息 id；记忆的 sourceRef 写的就是用户消息 id，用来反查来源。 */
    @Query("SELECT id FROM ai_message WHERE conversationId = :conversationId")
    suspend fun messageIdsOf(conversationId: String): List<String>

    @Query("DELETE FROM ai_message WHERE conversationId = :conversationId")
    suspend fun deleteMessagesOf(conversationId: String)

    @Query("DELETE FROM ai_tool_call WHERE conversationId = :conversationId")
    suspend fun deleteToolCallsOf(conversationId: String)

    @Query("DELETE FROM ai_token_usage WHERE conversationId = :conversationId")
    suspend fun deleteTokenUsageOf(conversationId: String)

    @Query("DELETE FROM ai_conversation WHERE id = :id")
    suspend fun deleteConversationRow(id: String)

    /**
     * 彻底删除一个会话：消息、工具调用、Token 记录、会话行一起删。
     * 提案表（ai_proposal）没有 conversationId 外键，且属于跨会话的展示镜像，这里不动。
     */
    @Transaction
    suspend fun deleteConversationDeep(id: String) {
        deleteToolCallsOf(id)
        deleteTokenUsageOf(id)
        deleteMessagesOf(id)
        deleteConversationRow(id)
    }

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
        clearMemories()
        clearNudges()
    }

    // ---------- 阶段 E：提案展示状态（权威仍在主库 ai_proposal_execution） ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposal(proposal: AiProposalEntity)

    @Query("SELECT * FROM ai_proposal WHERE id = :id LIMIT 1")
    suspend fun proposal(id: String): AiProposalEntity?

    @Query("SELECT * FROM ai_proposal WHERE status = :status")
    suspend fun proposalsByStatus(status: String): List<AiProposalEntity>

    @Query("SELECT * FROM ai_proposal")
    suspend fun allProposals(): List<AiProposalEntity>

    @Query("UPDATE ai_proposal SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProposalStatus(id: String, status: String, updatedAt: Long)

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

    /** 阶段 I：长期记忆也要清；删会话不等于删记忆，只有恢复出厂才整表清。 */
    @Query("DELETE FROM ai_memory")
    suspend fun clearMemories()

    /** 阶段 J：主动提醒的历史与频控记录同样属于 AI 数据，恢复出厂一起清。 */
    @Query("DELETE FROM ai_nudge")
    suspend fun clearNudges()

    // ---------- 阶段 K：数据规模统计（清除前给用户看，清除后回报条数） ----------

    @Query("SELECT COUNT(*) FROM ai_conversation")
    suspend fun countConversations(): Int

    @Query("SELECT COUNT(*) FROM ai_message")
    suspend fun countAllMessages(): Int

    @Query("SELECT COUNT(*) FROM ai_tool_call")
    suspend fun countAllToolCalls(): Int

    @Query("SELECT COUNT(*) FROM ai_token_usage")
    suspend fun countAllTokenUsage(): Int

    // ---------- 阶段 L：Token 用量落库 ----------

    /** 上游没返回 usage 时调用方不应插入任何行（见 RoomAiChatStore），不写 0 假数据。 */
    @Insert
    suspend fun insertTokenUsage(usage: AiTokenUsageEntity)
}
