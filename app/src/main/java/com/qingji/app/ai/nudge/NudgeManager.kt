package com.qingji.app.ai.nudge

import com.qingji.app.data.ai.NudgeStore
import com.qingji.app.domain.nudge.NudgeCandidate
import com.qingji.app.domain.nudge.NudgeDecision
import com.qingji.app.domain.nudge.NudgeOutcome
import com.qingji.app.domain.nudge.NudgeRecord
import com.qingji.app.domain.nudge.NudgeRuleEngine
import com.qingji.app.domain.nudge.NudgeSkipReason
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * J 阶段：主动管家。
 *
 * 只做三件事，且顺序固定：
 * 1. 读用户设置与本地事实（缺数据就闭嘴）；
 * 2. 交给 [NudgeRuleEngine] 判断该不该提醒；
 * 3. 该提醒就落库（唯一索引防重），然后把本地文案交给界面。
 *
 * **这里不会调用任何模型。** 提醒文案由规则层用本地数字拼出来，
 * 用户点「让管家看看」才把事实带进对话，走已有的只读工具。
 * 这条边界就是路线图里「禁止无条件云端请求」的落地点。
 */
class NudgeManager(
    private val store: NudgeStore,
    private val factsSource: NudgeFactsSource,
    private val policySource: NudgePolicySource,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** 频控只看最近这些小时的历史，不把整表读进内存 */
    private val historyWindowHours: Long = 24L * 14
) {

    /** 只看不落库：界面预览、调试、测试都用它。 */
    suspend fun peek(): NudgeDecision {
        val nowValue = now()
        val policy = policySource.policy()
        val facts = runCatching { factsSource.snapshot(nowValue) }.getOrElse { error ->
            return NudgeDecision.Skip(
                NudgeSkipReason.INSUFFICIENT_DATA,
                "读取本地数据失败：${error.message.orEmpty()}"
            )
        }
        val history = runCatching {
            store.since(nowValue.toEpochMillis() - historyWindowHours * 60L * 60L * 1000L)
        }.getOrDefault(emptyList())
        return NudgeRuleEngine.evaluate(facts, policy, history.map { it.toHistoryItem() })
    }

    /** 判定 + 落库（真正「发出」一条提醒）。 */
    suspend fun check(): NudgeDecision {
        val decision = peek()
        if (decision !is NudgeDecision.Show) return decision
        return if (store.insert(decision.candidate.toRecord(now()))) {
            decision
        } else {
            // 并发或重复调用：别看返回值是 Show，其实今天这条已经发过了。
            NudgeDecision.Skip(NudgeSkipReason.COOLDOWN, "同一天同类提醒已存在")
        }
    }

    /**
     * 同 [check]，但把落库后的记录整条返回，界面直接拿来展示。
     * 返回 null = 没触发，或者今天这条已经在库里了。
     */
    suspend fun checkRecord(): NudgeRecord? {
        val decision = peek()
        if (decision !is NudgeDecision.Show) return null
        val record = decision.candidate.toRecord(now())
        return if (store.insert(record)) record else null
    }

    fun observeHistory(limit: Int = 20): Flow<List<NudgeRecord>> = store.observeRecent(limit)

    suspend fun recent(limit: Int = 20): List<NudgeRecord> = store.recent(limit)

    /** 用户点「知道了」：只改反馈状态，不删记录（历史里还能看到「当时忽略过」）。 */
    suspend fun dismiss(id: String) {
        store.updateOutcome(id, NudgeOutcome.DISMISSED, now().toEpochMillis())
    }

    /** 用户点「去处理」：记成 ACTED，便于以后评估提醒有没有用。 */
    suspend fun markActed(id: String) {
        store.updateOutcome(id, NudgeOutcome.ACTED, now().toEpochMillis())
    }

    suspend fun delete(id: String) = store.delete(id)

    suspend fun clearHistory() = store.clearAll()

    /**
     * 「让管家看看」要发出去的问题。
     *
     * 只带本地算好的数字，不带原始记录明细：模型拿不到它不需要的东西，
     * 也避免把一整天的饮食明细塞进上下文。
     */
    fun analysisPrompt(record: NudgeRecord): String {
        val facts = record.facts.entries.joinToString("，") { "${it.key}=${it.value}" }
        val factPart = if (facts.isBlank()) "" else "（本地数据：$facts）"
        return buildString {
            append("帮我看一下这条提醒：")
            append(record.title)
            append("——")
            append(record.body)
            append(factPart)
            append(" 请先用本地记录核对，再说建议。")
        }
    }

    private fun NudgeCandidate.toRecord(nowValue: LocalDateTime) = NudgeRecord(
        id = newId(),
        kind = kind,
        reason = reason,
        title = title,
        body = body,
        facts = facts,
        needsAnalysis = needsAnalysis,
        dateKey = nowValue.toLocalDate().toString(),
        outcome = NudgeOutcome.SHOWN,
        shownAt = nowValue.toEpochMillis(),
        updatedAt = nowValue.toEpochMillis()
    )

    private fun LocalDateTime.toEpochMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
