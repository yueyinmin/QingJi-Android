package com.qingji.app.ai.nudge

import com.qingji.app.data.ai.NudgeStore
import com.qingji.app.domain.nudge.NudgeDecision
import com.qingji.app.domain.nudge.NudgeKind
import com.qingji.app.domain.nudge.NudgeOutcome
import com.qingji.app.domain.nudge.NudgePolicy
import com.qingji.app.domain.nudge.NudgeRecord
import com.qingji.app.domain.nudge.NudgeSkipReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 阶段 J 管家流程测试。
 *
 * 重点：只落一条（唯一约束兜底）、数据源挂掉不崩、用户能关掉、
 * 以及「让管家看看」带出去的信息只含本地算好的数字。
 */
class NudgeManagerTest {

    private val today = LocalDate.of(2026, 11, 25)
    private val nowValue = LocalDateTime.of(today, java.time.LocalTime.of(21, 0))

    private class FakeStore : NudgeStore {
        val rows = mutableListOf<NudgeRecord>()

        override fun observeRecent(limit: Int): Flow<List<NudgeRecord>> =
            flowOf(rows.sortedByDescending { it.shownAt }.take(limit))

        override suspend fun recent(limit: Int): List<NudgeRecord> =
            rows.sortedByDescending { it.shownAt }.take(limit)

        override suspend fun since(since: Long): List<NudgeRecord> =
            rows.filter { it.shownAt >= since }

        /** 模拟真实唯一索引：同类同一天第二次写入失败。 */
        override suspend fun insert(record: NudgeRecord): Boolean {
            if (rows.any { it.kind == record.kind && it.dateKey == record.dateKey }) return false
            rows += record
            return true
        }

        override suspend fun updateOutcome(id: String, outcome: NudgeOutcome, updatedAt: Long) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(outcome = outcome, updatedAt = updatedAt)
        }

        override suspend fun delete(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun clearAll() = rows.clear()
    }

    private fun manager(
        store: NudgeStore = FakeStore(),
        policy: NudgePolicy = NudgePolicy(),
        facts: () -> com.qingji.app.domain.nudge.NudgeFacts = { emptyFacts() }
    ): NudgeManager {
        var seq = 0
        return NudgeManager(
            store = store,
            factsSource = { facts() },
            policySource = { policy },
            now = { nowValue },
            newId = { "nudge-${++seq}" }
        )
    }

    /** 21 点、今天什么都没记：必然触发「记录不完整」。 */
    private fun emptyFacts() = com.qingji.app.domain.nudge.NudgeFacts(
        now = nowValue,
        today = today
    )

    @Test
    fun `触发一次会落一条记录`() = runTest {
        val store = FakeStore()
        val decision = manager(store).check()

        assertTrue(decision is NudgeDecision.Show)
        assertEquals(1, store.rows.size)
        assertEquals(NudgeKind.RECORD_INCOMPLETE, store.rows.first().kind)
        assertEquals("2026-11-25", store.rows.first().dateKey)
        assertEquals(NudgeOutcome.SHOWN, store.rows.first().outcome)
    }

    @Test
    fun `同一天再检查不会发第二条`() = runTest {
        val store = FakeStore()
        val manager = manager(store)

        assertTrue(manager.check() is NudgeDecision.Show)
        val second = manager.check()

        // 默认每天 1 条：第二次直接被额度拦下
        assertEquals(NudgeSkipReason.DAILY_LIMIT, (second as NudgeDecision.Skip).reason)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `额度放宽到两条时同类仍然不重复`() = runTest {
        val store = FakeStore()
        val manager = manager(store, policy = NudgePolicy(dailyLimit = 2))

        assertTrue(manager.check() is NudgeDecision.Show)
        val second = manager.check()

        assertEquals(NudgeSkipReason.COOLDOWN, (second as NudgeDecision.Skip).reason)
        assertEquals(1, store.rows.size)
    }

    @Test
    fun `checkRecord返回落库后的整条记录`() = runTest {
        val store = FakeStore()
        val record = manager(store).checkRecord()

        assertNotNull(record)
        assertEquals(store.rows.first().id, record!!.id)
        assertEquals(NudgeKind.RECORD_INCOMPLETE, record.kind)
        assertTrue(record.body.isNotBlank())
        // 第二次同样的检查拿不到记录（今天已经发过）
        assertNull(manager(store).checkRecord())
    }

    @Test
    fun `总开关关闭时不落库`() = runTest {
        val store = FakeStore()
        val decision = manager(store, policy = NudgePolicy(enabled = false)).check()

        assertEquals(NudgeSkipReason.DISABLED, (decision as NudgeDecision.Skip).reason)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `读取本地数据失败时不崩也不打扰`() = runTest {
        val store = FakeStore()
        val broken = manager(store) { error("数据库锁住了") }
        val decision = broken.check()

        assertEquals(NudgeSkipReason.INSUFFICIENT_DATA, (decision as NudgeDecision.Skip).reason)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `忽略后状态改成DISMISSED但记录还在`() = runTest {
        val store = FakeStore()
        val manager = manager(store)
        val record = manager.checkRecord()!!

        manager.dismiss(record.id)

        assertEquals(1, store.rows.size)
        assertEquals(NudgeOutcome.DISMISSED, store.rows.first().outcome)
    }

    @Test
    fun `去分析时状态改成ACTED`() = runTest {
        val store = FakeStore()
        val manager = manager(store)
        val record = manager.checkRecord()!!

        manager.markActed(record.id)

        assertEquals(NudgeOutcome.ACTED, store.rows.first().outcome)
    }

    @Test
    fun `给AI的问题只带本地算好的数字`() = runTest {
        val store = FakeStore()
        val manager = manager(store)
        val record = manager.checkRecord()!!

        val prompt = manager.analysisPrompt(record)

        assertTrue(prompt.contains("今天还没怎么记"))
        assertTrue(prompt.contains("foodLogCount=0"))
        // 不做因果推断、不要求替用户拍板
        assertTrue(prompt.contains("先用本地记录核对"))
    }

    @Test
    fun `清空历史不影响当天去重规则之外的数据`() = runTest {
        val store = FakeStore()
        val manager = manager(store)
        manager.checkRecord()

        manager.clearHistory()

        assertFalse(store.rows.isNotEmpty())
        // 清空后可以重新触发（等同于用户主动重置了历史）
        assertNotNull(manager.checkRecord())
    }
}
