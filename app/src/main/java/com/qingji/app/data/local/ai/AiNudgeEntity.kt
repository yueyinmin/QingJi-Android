package com.qingji.app.data.local.ai

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 库 v3：主动提醒的发送记录。
 *
 * 只存「发过什么、什么时候发的、用户怎么处理」，不存模型输出正文之外的推理过程。
 * 三个用途：
 * 1. 去重 —— (kind, dateKey) 唯一索引，同一天同一种提醒只会落一条；
 * 2. 冷却 —— 按 shownAt 判断同类提醒间隔；
 * 3. 历史 —— 「管家提醒」页展示最近记录，用户可回看。
 *
 * 提醒文案本身就是本地模板生成的（[title]/[body]），模型只在用户主动追问时参与，
 * 所以这张表不含任何需要保密的内容。
 */
@Entity(
    tableName = "ai_nudge",
    indices = [
        Index("kind"),
        Index("dateKey"),
        Index(value = ["kind", "dateKey"], unique = true)
    ]
)
data class AiNudgeEntity(
    @PrimaryKey val id: String,
    /** weight_deviation / plan_not_done / protein_low / record_incomplete */
    val kind: String,
    /** 触发细节，见 NudgeReason */
    val reason: String,
    /** 发送当天 yyyy-MM-dd，去重与「今天发过没」都看它 */
    val dateKey: String,
    val title: String,
    val body: String,
    /** 关键数字（JSON 字符串），只用于展示与写进 AI 上下文，不做计算 */
    val factsJson: String = "{}",
    /** 是否建议用户进一步分析（UI 决定要不要给入口） */
    val needsAnalysis: Boolean = false,
    /** SHOWN / DISMISSED / ACTED */
    val outcome: String = "SHOWN",
    val shownAt: Long,
    val updatedAt: Long
)
