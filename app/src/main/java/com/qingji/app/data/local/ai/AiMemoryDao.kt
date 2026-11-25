package com.qingji.app.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 长期记忆 DAO（AI 库 v2）。
 *
 * 查询一律把「已失效 / 已过期」排除在注入之外；只有管理页需要看全部，
 * 所以额外提供不带状态过滤的 [observeAll]。
 */
@Dao
interface AiMemoryDao {

    @Query("SELECT * FROM ai_memory WHERE status = 'ACTIVE' ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<AiMemoryEntity>>

    @Query("SELECT * FROM ai_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AiMemoryEntity>>

    @Query("SELECT * FROM ai_memory ORDER BY updatedAt DESC")
    suspend fun all(): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE status = 'ACTIVE' AND category IN (:categories) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun activeByCategories(categories: List<String>, limit: Int): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AiMemoryEntity?

    @Query("SELECT * FROM ai_memory WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): AiMemoryEntity?

    @Query("SELECT * FROM ai_memory WHERE sourceRef = :sourceRef ORDER BY updatedAt DESC")
    suspend fun findBySourceRef(sourceRef: String): List<AiMemoryEntity>

    /** 重复写入直接忽略：唯一索引冲突时返回 -1，由调用方当作「已经记过」。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(memory: AiMemoryEntity): Long

    @Query(
        "UPDATE ai_memory SET content = :content, category = :category, truthType = :truthType, " +
            "source = :source, dedupeKey = :dedupeKey, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateContent(
        id: String,
        content: String,
        category: String,
        truthType: String,
        source: String,
        dedupeKey: String,
        updatedAt: Long
    )

    @Query("UPDATE ai_memory SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    /** 用户确认 AI 推断：INFERRED → CONFIRMED，来源改成用户确认。 */
    @Query("UPDATE ai_memory SET truthType = :truthType, source = :source, status = 'ACTIVE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun upgradeTruth(id: String, truthType: String, source: String, updatedAt: Long)

    @Query("DELETE FROM ai_memory WHERE id = :id")
    suspend fun deleteById(id: String)

    // ---------- 阶段 K：来源失效与按来源清理 ----------

    /** 用户选择「连记忆一起删」时使用；调用方必须保证非空（空列表会生成非法 SQL）。 */
    @Query("DELETE FROM ai_memory WHERE sourceRef IN (:sourceRefs)")
    suspend fun deleteBySourceRefs(sourceRefs: List<String>)

    /** 只失效不删除：会话删了，但这条记忆作为历史仍可查看。 */
    @Query("UPDATE ai_memory SET status = 'INVALIDATED', updatedAt = :now WHERE sourceRef IN (:sourceRefs)")
    suspend fun invalidateBySourceRefs(sourceRefs: List<String>, now: Long)

    @Query("SELECT COUNT(*) FROM ai_memory WHERE sourceRef IN (:sourceRefs)")
    suspend fun countBySourceRefs(sourceRefs: List<String>): Int

    /**
     * 阶段 K：来源已不存在的记忆统一标失效。
     * 只处理 sourceRef 形如 UUID（36 位、带连字符）的记录，避免误伤未来可能写入的
     * 「业务记录 id」；已删除的会话其消息也一并消失，所以用 ai_message 做存在性判断。
     */
    @Query(
        "UPDATE ai_memory SET status = 'INVALIDATED', updatedAt = :now " +
            "WHERE status = 'ACTIVE' AND sourceRef <> '' AND length(sourceRef) = 36 " +
            "AND sourceRef NOT IN (SELECT id FROM ai_message)"
    )
    suspend fun invalidateDanglingSourceRefs(now: Long): Int

    @Query("SELECT COUNT(*) FROM ai_memory")
    suspend fun countAll(): Int

    @Query("DELETE FROM ai_memory")
    suspend fun clearAll()

    /** 到期的记忆自动标 EXPIRED；只动 ACTIVE 的，已失效的不再改。 */
    @Query("UPDATE ai_memory SET status = 'EXPIRED', updatedAt = :now WHERE status = 'ACTIVE' AND expiresAt > 0 AND expiresAt <= :now")
    suspend fun expireOverdue(now: Long): Int
}
