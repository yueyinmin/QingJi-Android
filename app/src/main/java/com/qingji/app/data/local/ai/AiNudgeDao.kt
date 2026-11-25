package com.qingji.app.data.local.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 主动提醒 DAO（AI 库 v3）。
 *
 * 写入靠 (kind, dateKey) 唯一索引兜底：并发或重复检查时只有第一条能进去，
 * 这样「一天同一种提醒只发一次」不依赖调用方加锁。
 */
@Dao
interface AiNudgeDao {

    /** 重复写入忽略；返回 -1 表示今天这条已经发过。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(nudge: AiNudgeEntity): Long

    @Query("SELECT * FROM ai_nudge ORDER BY shownAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiNudgeEntity>>

    @Query("SELECT * FROM ai_nudge ORDER BY shownAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<AiNudgeEntity>

    /** 阶段 L：整段导出用（备份 aiData 段）。 */
    @Query("SELECT * FROM ai_nudge ORDER BY shownAt DESC")
    suspend fun all(): List<AiNudgeEntity>

    /** 频控只需要最近一段时间的记录，不把整表读进内存。 */
    @Query("SELECT * FROM ai_nudge WHERE shownAt >= :since ORDER BY shownAt DESC")
    suspend fun since(since: Long): List<AiNudgeEntity>

    @Query("SELECT * FROM ai_nudge WHERE kind = :kind AND dateKey = :dateKey LIMIT 1")
    suspend fun findByDay(kind: String, dateKey: String): AiNudgeEntity?

    @Query("UPDATE ai_nudge SET outcome = :outcome, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOutcome(id: String, outcome: String, updatedAt: Long)

    @Query("DELETE FROM ai_nudge WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ai_nudge")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM ai_nudge")
    suspend fun countAll(): Int
}
