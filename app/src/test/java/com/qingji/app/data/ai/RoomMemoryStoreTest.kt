package com.qingji.app.data.ai

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemorySource
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 阶段 I：AI 库 v2 记忆表的真实读写（Robolectric + 内存库）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomMemoryStoreTest {

    private fun openDb(): AiDatabase {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun record(
        id: String = "m1",
        content: String = "我不吃香菜",
        dedupeKey: String = "key-1",
        category: MemoryCategory = MemoryCategory.NUTRITION,
        status: MemoryStatus = MemoryStatus.ACTIVE,
        expiresAt: Long = 0L,
        sourceRef: String = ""
    ) = MemoryRecord(
        id = id,
        category = category,
        content = content,
        truthType = MemoryTruth.USER_FACT,
        source = MemorySource.USER_INPUT,
        sourceRef = sourceRef,
        confidence = null,
        status = status,
        expiresAt = expiresAt,
        dedupeKey = dedupeKey,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `写入后能按类别读到且重复键被拒`() = runBlocking {
        val db = openDb()
        try {
            val store = RoomMemoryStore(db)
            assertTrue(store.insert(record()))
            assertFalse("同 dedupeKey 再写应被忽略", store.insert(record(id = "m2")))
            assertEquals(1, store.all().size)

            val nutrition = store.activeByCategories(setOf(MemoryCategory.NUTRITION), 10)
            assertEquals(1, nutrition.size)
            val training = store.activeByCategories(setOf(MemoryCategory.TRAINING), 10)
            assertTrue(training.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `过期扫描只动到期且生效中的记录`() = runBlocking {
        val db = openDb()
        try {
            val store = RoomMemoryStore(db)
            store.insert(record(id = "expired", dedupeKey = "k1", expiresAt = 500L))
            store.insert(record(id = "future", dedupeKey = "k2", expiresAt = 9_000L))

            val changed = store.expireOverdue(1_000L)
            assertEquals(1, changed)
            assertEquals(MemoryStatus.EXPIRED, store.findById("expired")!!.status)
            assertEquals(MemoryStatus.ACTIVE, store.findById("future")!!.status)
        } finally {
            db.close()
        }
    }

    @Test
    fun `用户确认能把推断升级为已确认`() = runBlocking {
        val db = openDb()
        try {
            val store = RoomMemoryStore(db)
            val inferred = record(id = "inf", content = "可能喜欢早上训练").copy(
                category = MemoryCategory.TRAINING,
                dedupeKey = "k-inf",
                truthType = MemoryTruth.INFERRED,
                source = MemorySource.AI_INFERRED,
                confidence = 0.6
            )
            store.insert(inferred)

            store.upgradeTruth("inf", MemoryTruth.CONFIRMED, MemorySource.USER_CONFIRMED, 2000L)
            val updated = store.findById("inf")!!
            assertEquals(MemoryTruth.CONFIRMED, updated.truthType)
            assertEquals(MemorySource.USER_CONFIRMED, updated.source)
        } finally {
            db.close()
        }
    }

    @Test
    fun `删除会话不会删除长期记忆`() = runBlocking {
        val db = openDb()
        try {
            val chat = RoomAiChatStore(db)
            val conversation = chat.createConversation("首页")
            chat.appendUserMessage("u1", conversation.id, "记住我不吃香菜")
            val store = RoomMemoryStore(db)
            store.insert(record(sourceRef = "u1"))

            chat.deleteConversation(conversation.id)

            assertNotNull(store.findById("m1"))
            assertEquals(1, store.all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `恢复出厂会清空记忆表`() = runBlocking {
        val db = openDb()
        try {
            val store = RoomMemoryStore(db)
            store.insert(record())
            db.aiChatDao().clearAll()
            assertTrue(store.all().isEmpty())
        } finally {
            db.close()
        }
    }
}
