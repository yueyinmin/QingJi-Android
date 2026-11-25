package com.qingji.app.ai.privacy

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.ai.AiConversationEntity
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMemoryEntity
import com.qingji.app.data.local.ai.AiNudgeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 K：一键清除 AI 数据。
 *
 * 只放一个用例：Robolectric 下每个测试方法会新建 Application，而 DataStore
 * 同一个文件只允许一个活跃实例，多个方法会互相打架。一个方法里把「统计 → 清空」
 * 走完整就够了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiPrivacyCleanerTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        // 内存库随进程结束释放，这里不持有引用，避免跨用例串数据
    }

    @Test
    fun `清除后 AI 库全空且回报删除前的规模`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.aiChatDao()
            dao.insertConversation(
                AiConversationEntity(id = "c1", title = "问一句", entryPage = "首页", createdAt = 1, updatedAt = 2)
            )
            db.aiMemoryDao().insert(
                AiMemoryEntity(
                    id = "m1", category = "nutrition", content = "不吃辣",
                    truthType = "USER_FACT", source = "USER_INPUT",
                    dedupeKey = "k1", createdAt = 1, updatedAt = 1
                )
            )
            db.aiNudgeDao().insert(
                AiNudgeEntity(
                    id = "n1", kind = "record_incomplete", reason = "NO_FOOD_LOG",
                    factsJson = "{}", title = "今天还没记", body = "记一条吧",
                    dateKey = "2026-11-25", shownAt = 1_000L, updatedAt = 1_000L
                )
            )

            val before = AiPrivacyCleaner.inspect(db)
            assertEquals(1, before.conversations)
            assertEquals(1, before.memories)
            assertEquals(1, before.nudges)

            val report = AiPrivacyCleaner.clearAll(db, app)

            assertEquals(1, report.conversations)
            assertEquals(1, report.memories)
            assertEquals(1, report.nudges)
            assertTrue(report.summary().contains("1 个会话"))

            // 清完之后整库为空
            val after = AiPrivacyCleaner.inspect(db)
            assertEquals(0, after.conversations)
            assertEquals(0, after.messages)
            assertEquals(0, after.memories)
            assertEquals(0, after.nudges)
            assertTrue(after.isEmpty)
            assertTrue(after.summary().contains("没有可清除"))
        } finally {
            db.close()
        }
    }
}
