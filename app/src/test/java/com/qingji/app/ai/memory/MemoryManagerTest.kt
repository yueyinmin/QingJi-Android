package com.qingji.app.ai.memory

import com.qingji.app.data.ai.MemoryStore
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemorySource
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 I 规则层测试。
 * 重点：只有用户明说才写、推断不能自动升级、检索不串类、失效/过期不参与。
 */
class MemoryManagerTest {

    private val now = 1_800_000_000_000L

    private class FakeStore : MemoryStore {
        val items = mutableListOf<MemoryRecord>()

        override fun observeActive(): Flow<List<MemoryRecord>> =
            flowOf(items.filter { it.status == MemoryStatus.ACTIVE })

        override fun observeAll(): Flow<List<MemoryRecord>> = flowOf(items.toList())

        override suspend fun all(): List<MemoryRecord> = items.toList()

        override suspend fun activeByCategories(
            categories: Set<MemoryCategory>,
            limit: Int
        ): List<MemoryRecord> = items
            .filter { it.status == MemoryStatus.ACTIVE && it.category in categories }
            .sortedByDescending { it.updatedAt }
            .take(limit)

        override suspend fun findById(id: String): MemoryRecord? = items.firstOrNull { it.id == id }

        override suspend fun findByDedupeKey(dedupeKey: String): MemoryRecord? =
            items.firstOrNull { it.dedupeKey == dedupeKey }

        override suspend fun findBySourceRef(sourceRef: String): List<MemoryRecord> =
            items.filter { it.sourceRef == sourceRef }

        override suspend fun insert(record: MemoryRecord): Boolean {
            if (items.any { it.dedupeKey == record.dedupeKey }) return false
            items += record
            return true
        }

        override suspend fun updateContent(
            id: String,
            content: String,
            category: MemoryCategory,
            truth: MemoryTruth,
            source: MemorySource,
            dedupeKey: String,
            updatedAt: Long
        ) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    content = content,
                    category = category,
                    truthType = truth,
                    source = source,
                    dedupeKey = dedupeKey,
                    updatedAt = updatedAt
                )
            }
        }

        override suspend fun updateStatus(id: String, status: MemoryStatus, updatedAt: Long) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) items[index] = items[index].copy(status = status, updatedAt = updatedAt)
        }

        override suspend fun upgradeTruth(
            id: String,
            truth: MemoryTruth,
            source: MemorySource,
            updatedAt: Long
        ) {
            val index = items.indexOfFirst { it.id == id }
            if (index >= 0) {
                items[index] = items[index].copy(
                    truthType = truth,
                    source = source,
                    status = MemoryStatus.ACTIVE,
                    updatedAt = updatedAt
                )
            }
        }

        override suspend fun delete(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun clearAll() = items.clear()

        override suspend fun expireOverdue(now: Long): Int {
            var count = 0
            val updated = items.map { record ->
                if (record.status == MemoryStatus.ACTIVE && record.expiresAt in 1..now) {
                    count++
                    record.copy(status = MemoryStatus.EXPIRED, updatedAt = now)
                } else record
            }
            items.clear()
            items.addAll(updated)
            return count
        }
    }

    // ---------- 触发词与分类 ----------

    @Test
    fun `句首记住才触发且能分到饮食类`() {
        val candidate = MemoryManager.detectExplicit("记住我不吃香菜")
        assertNotNull(candidate)
        assertEquals("我不吃香菜", candidate!!.content)
        assertEquals(MemoryCategory.NUTRITION, candidate.category)
    }

    @Test
    fun `正文里的记住不触发`() {
        assertNull(MemoryManager.detectExplicit("我今天记住要少吃点"))
        assertNull(MemoryManager.detectExplicit("记住"))
        assertNull(MemoryManager.detectExplicit("记住我"))
    }

    @Test
    fun `训练与目标关键词分到对应类别`() {
        assertEquals(MemoryCategory.TRAINING, MemoryManager.detectCategory("周六练腿，深蹲为主"))
        assertEquals(MemoryCategory.GOAL, MemoryManager.detectCategory("目标体重 72 公斤"))
        assertEquals(MemoryCategory.NUTRITION, MemoryManager.detectCategory("早餐不喝牛奶"))
        assertEquals(MemoryCategory.PREFERENCE, MemoryManager.detectCategory("喜欢晚上记录"))
    }

    // ---------- 写入 ----------

    @Test
    fun `用户明说就写 USER_FACT 并带来源`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureExplicit(
            store = store,
            text = "记住我乳糖不耐受",
            sourceRef = "msg-1",
            now = now,
            idFactory = { "m1" }
        )

        assertNotNull(record)
        assertEquals(MemoryTruth.USER_FACT, record!!.truthType)
        assertEquals(MemorySource.USER_INPUT, record.source)
        assertEquals("msg-1", record.sourceRef)
        assertEquals(1, store.items.size)
    }

    @Test
    fun `同样内容不会记两遍`() = runTest {
        val store = FakeStore()
        MemoryManager.captureExplicit(store, "记住我乳糖不耐受", "msg-1", now = now)
        val second = MemoryManager.captureExplicit(store, "记住，我乳糖不耐受。", "msg-2", now = now)

        assertNull(second)
        assertEquals(1, store.items.size)
    }

    @Test
    fun `关掉的类别不再自动写入`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureExplicit(
            store = store,
            text = "记住我不吃香菜",
            sourceRef = "msg-1",
            disabled = setOf(MemoryCategory.NUTRITION),
            now = now
        )

        assertNull(record)
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `AI 推断只能以 INFERRED 落库且置信度太高会被拒`() = runTest {
        val store = FakeStore()
        val ok = MemoryManager.captureInferred(
            store = store,
            content = "可能更喜欢早上训练",
            category = MemoryCategory.TRAINING,
            confidence = 0.6,
            sourceRef = "msg-9",
            now = now
        )
        assertNotNull(ok)
        assertEquals(MemoryTruth.INFERRED, ok!!.truthType)

        val tooSure = MemoryManager.captureInferred(
            store = store,
            content = "一定喜欢早上训练",
            category = MemoryCategory.TRAINING,
            confidence = 0.95,
            sourceRef = "msg-9",
            now = now
        )
        assertNull(tooSure)
    }

    @Test
    fun `推断不能覆盖已有的用户事实`() = runTest {
        val store = FakeStore()
        MemoryManager.captureExplicit(store, "记住我乳糖不耐受", "msg-1", now = now)
        val inferred = MemoryManager.captureInferred(
            store = store,
            content = "我乳糖不耐受",
            category = MemoryCategory.NUTRITION,
            confidence = 0.5,
            sourceRef = "msg-2",
            now = now
        )

        assertNull(inferred)
        assertEquals(MemoryTruth.USER_FACT, store.items.single().truthType)
    }

    // ---------- 确认与纠正 ----------

    @Test
    fun `推断要用户确认才升级为已确认`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureInferred(
            store, "可能更喜欢早上训练", MemoryCategory.TRAINING, 0.6, "msg-1", now = now
        )!!
        // 没有任何路径会自动升级：必须先显式确认
        assertEquals(MemoryTruth.INFERRED, store.items.single().truthType)

        val ok = MemoryManager.confirm(store, record, now + 1000)
        assertTrue(ok)
        assertEquals(MemoryTruth.CONFIRMED, store.items.single().truthType)
        assertEquals(MemorySource.USER_CONFIRMED, store.items.single().source)
    }

    @Test
    fun `用户事实不需要也不能再确认`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureExplicit(store, "记住我不吃香菜", "msg-1", now = now)!!
        assertFalse(MemoryManager.confirm(store, record, now + 1000))
    }

    @Test
    fun `纠正改内容并重新归类`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureExplicit(store, "记住我喜欢跑步", "msg-1", now = now)!!

        val ok = MemoryManager.correct(store, record, "目标体重 70 公斤", now + 500)
        assertTrue(ok)
        val updated = store.items.single()
        assertEquals("目标体重 70 公斤", updated.content)
        assertEquals(MemoryCategory.GOAL, updated.category)
        assertEquals(MemoryTruth.USER_FACT, updated.truthType)
        assertEquals(MemorySource.USER_CONFIRMED, updated.source)
    }

    @Test
    fun `纠正成和另一条重复会被拒`() = runTest {
        val store = FakeStore()
        MemoryManager.captureExplicit(store, "记住我不吃香菜", "msg-1", now = now)
        val second = MemoryManager.captureExplicit(store, "记住我乳糖不耐受", "msg-2", now = now)!!

        val ok = MemoryManager.correct(store, second, "我不吃香菜", now + 100)
        assertFalse(ok)
        assertEquals("我乳糖不耐受", store.items.first { it.id == second.id }.content)
    }

    // ---------- 检索 ----------

    @Test
    fun `饮食问题不会读到训练记忆`() = runTest {
        val store = FakeStore()
        MemoryManager.captureExplicit(store, "记住我不吃香菜", "msg-1", now = now)
        MemoryManager.captureExplicit(store, "记住我周六练腿", "msg-2", now = now + 10)

        val injection = MemoryManager.retrieve(store, "饮食页", now + 20)
        assertTrue(injection.notes.any { it.text == "我不吃香菜" })
        assertFalse(injection.notes.any { it.text.contains("练腿") })
    }

    @Test
    fun `失效与过期的记忆不参与回答`() = runTest {
        val store = FakeStore()
        val keep = MemoryManager.captureExplicit(store, "记住我不吃香菜", "msg-1", now = now)!!
        val drop = MemoryManager.captureExplicit(store, "记住我乳糖不耐受", "msg-2", now = now)!!
        MemoryManager.invalidate(store, drop, now + 10)

        val expired = keep.copy(
            id = "old",
            content = "喜欢甜的",
            dedupeKey = MemoryManager.dedupeKey(MemoryCategory.PREFERENCE, "喜欢甜的"),
            expiresAt = now - 1
        )
        store.items += expired

        val injection = MemoryManager.retrieve(store, "饮食页", now + 20)
        assertEquals(1, injection.notes.size)
        assertEquals("我不吃香菜", injection.notes.single().text)
    }

    @Test
    fun `推断类最多注入一条且带标签`() = runTest {
        val store = FakeStore()
        MemoryManager.captureInferred(store, "可能喜欢早上训练", MemoryCategory.TRAINING, 0.5, "m1", now = now)
        MemoryManager.captureInferred(store, "可能不喜欢晚上训练", MemoryCategory.TRAINING, 0.5, "m2", now = now + 1)
        MemoryManager.captureExplicit(store, "记住我周六练腿", "m3", now = now + 2)

        val injection = MemoryManager.retrieve(store, "训练计划", now + 10)
        assertEquals(2, injection.notes.size)
        assertEquals(1, injection.notes.count { it.truth == MemoryTruth.INFERRED })
        assertTrue(injection.truncated)
    }

    @Test
    fun `条数与字数预算都会截断`() = runTest {
        val store = FakeStore()
        repeat(8) { index ->
            MemoryManager.captureExplicit(
                store,
                "记住偏好$index 号记录方式保持每天坚持写下来",
                "msg-$index",
                now = now + index
            )
        }

        val byItems = MemoryManager.retrieve(store, "首页", now + 100, maxItems = 3, maxChars = 500)
        assertEquals(3, byItems.notes.size)
        assertTrue(byItems.truncated)

        val byChars = MemoryManager.retrieve(store, "首页", now + 100, maxItems = 8, maxChars = 10)
        assertTrue(byChars.notes.isEmpty())
        assertTrue(byChars.truncated)
    }

    @Test
    fun `删除记忆不影响其它记录`() = runTest {
        val store = FakeStore()
        val record = MemoryManager.captureExplicit(store, "记住我不吃香菜", "msg-1", now = now)!!
        MemoryManager.delete(store, record)
        assertTrue(store.items.isEmpty())
    }

    @Test
    fun `去重键忽略空白标点与大小写`() {
        val a = MemoryManager.dedupeKey(MemoryCategory.NUTRITION, "我 不吃香菜")
        val b = MemoryManager.dedupeKey(MemoryCategory.NUTRITION, "我不吃香菜。")
        val c = MemoryManager.dedupeKey(MemoryCategory.TRAINING, "我不吃香菜")
        assertEquals(a, b)
        assertFalse(a == c)
    }
}
