package com.qingji.app.data.ai

import com.qingji.app.domain.nudge.NudgeOutcome
import com.qingji.app.domain.nudge.NudgeRecord
import kotlinx.coroutines.flow.Flow

/**
 * 主动提醒的存储边界。
 *
 * 只做「读历史 / 写一条 / 改反馈」，触发频率、静默时段这些判断全在规则层，
 * 这样规则可以脱离 Room 单测，也方便以后换存储。
 */
interface NudgeStore {

    fun observeRecent(limit: Int): Flow<List<NudgeRecord>>

    suspend fun recent(limit: Int): List<NudgeRecord>

    /** 频控窗口内的记录（按 shownAt 倒序）。 */
    suspend fun since(since: Long): List<NudgeRecord>

    /** true = 落库成功；false = 今天同一种提醒已经存在（唯一索引拦下了）。 */
    suspend fun insert(record: NudgeRecord): Boolean

    suspend fun updateOutcome(id: String, outcome: NudgeOutcome, updatedAt: Long)

    suspend fun delete(id: String)

    suspend fun clearAll()
}
