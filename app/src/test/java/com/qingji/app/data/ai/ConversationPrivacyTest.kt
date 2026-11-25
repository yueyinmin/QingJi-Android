package com.qingji.app.data.ai

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.ai.AiConversationEntity
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMemoryEntity
import com.qingji.app.data.local.ai.AiMessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 K：删除会话必须是「真的删掉」，不是留个删除标记。
 *
 * 用户视角的承诺：
 * - 删了会话，消息、工具调用、Token 记录一起从本机消失；
 * - 其它会话一条不少；
 * - 记忆按用户选择处理：一起删，或者保留但标「来源已失效」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConversationPrivacyTest {

    private lateinit var db: AiDatabase
    private lateinit var store: RoomAiChatStore

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomAiChatStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- 造数据 ----------

    private suspend fun seedConversation(id: String, messageIds: List<String>) {
        val dao = db.aiChatDao()
        dao.insertConversation(
            AiConversationEntity(
                id = id,
                title = "饭后问一句",
                entryPage = "首页",
                createdAt = 1_000L,
                updatedAt = 2_000L
            )
        )
        messageIds.forEachIndexed { index, messageId ->
            dao.insertMessage(
                AiMessageEntity(
                    id = messageId,
                    conversationId = id,
                    sequence = index + 1,
                    role = if (index % 2 == 0) "user" else "assistant",
                    content = "内容 $index",
                    createdAt = 1_000L + index
                )
            )
        }
        // 工具调用与 Token 表当前没有写入方，用 SQL 直接造，验证删除路径确实覆盖它们
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO ai_tool_call (id, conversationId, messageId, callId, toolName, " +
                "argumentsJson, resultJson, status, durationMs, errorCode) " +
                "VALUES ('tc-$id', '$id', '', 'call-1', 'query_food_log', '{}', '{}', 'ok', 12, '')"
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO ai_token_usage (id, conversationId, providerId, modelId, " +
                "inputTokens, outputTokens, taskType, requestedAt) " +
                "VALUES ('tk-$id', '$id', 'p1', 'm1', 100, 200, 'chat', 3000)"
        )
    }

    private suspend fun seedMemory(id: String, sourceRef: String, status: String = "ACTIVE") {
        db.aiMemoryDao().insert(
            AiMemoryEntity(
                id = id,
                category = "nutrition",
                content = "不吃香菜",
                truthType = "USER_FACT",
                source = "USER_INPUT",
                sourceRef = sourceRef,
                status = status,
                dedupeKey = "key-$id",
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        )
    }

    private suspend fun messageCount(conversationId: String): Int = db.aiChatDao().countMessages(conversationId)

    // ---------- 用例 ----------

    @Test
    fun `删除会话后消息工具与用量记录都不在了`() = runBlocking {
        seedConversation("c1", listOf("m1", "m2"))
        seedConversation("c2", listOf("m3"))

        store.deleteConversation("c1", dropMemories = false)

        val dao = db.aiChatDao()
        assertNull(dao.conversation("c1"))
        assertEquals(0, messageCount("c1"))
        assertEquals(1, dao.countAllToolCalls()) // 只剩 c2 的那条
        assertEquals(1, dao.countAllTokenUsage())

        // 别的会话不受影响
        assertNotNull(dao.conversation("c2"))
        assertEquals(1, messageCount("c2"))
    }

    @Test
    fun `不勾连记忆时记忆保留但标来源失效`() = runBlocking {
        seedConversation("c1", listOf("m1", "m2"))
        seedMemory("mem1", sourceRef = "m1")

        store.deleteConversation("c1", dropMemories = false)

        val memory = db.aiMemoryDao().findById("mem1")
        assertNotNull(memory)
        assertEquals("INVALIDATED", memory!!.status)
    }

    @Test
    fun `勾了连记忆一起删时记忆直接消失`() = runBlocking {
        seedConversation("c1", listOf("m1", "m2"))
        seedMemory("mem1", sourceRef = "m1")

        store.deleteConversation("c1", dropMemories = true)

        assertNull(db.aiMemoryDao().findById("mem1"))
        assertEquals(0, db.aiMemoryDao().countAll())
    }

    @Test
    fun `删会话不动别的会话产生的记忆`() = runBlocking {
        seedConversation("c1", listOf("m1"))
        seedConversation("c2", listOf("m9"))
        seedMemory("mem1", sourceRef = "m1")
        seedMemory("mem2", sourceRef = "m9")

        store.deleteConversation("c1", dropMemories = true)

        assertNull(db.aiMemoryDao().findById("mem1"))
        assertEquals("ACTIVE", db.aiMemoryDao().findById("mem2")!!.status)
    }

    @Test
    fun `删除前能数出这段对话产生的记忆条数`() = runBlocking {
        seedConversation("c1", listOf("m1", "m2"))
        seedMemory("mem1", sourceRef = "m1")
        seedMemory("mem2", sourceRef = "m2")
        seedMemory("mem3", sourceRef = "other")

        assertEquals(2, store.countMemoriesFrom("c1"))
        assertEquals(0, store.countMemoriesFrom("不存在的会话"))
    }

    @Test
    fun `来源自检只失效找不到出处的记忆`() = runBlocking {
        val dao = db.aiChatDao()
        seedConversation("c1", listOf("m1"))
        seedMemory("mem-ok", sourceRef = "m1")
        seedMemory("mem-gone", sourceRef = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
        // 非 UUID 的 sourceRef（未来可能是业务记录 id）不参与自检
        seedMemory("mem-biz", sourceRef = "food_log:123")

        val changed = db.aiMemoryDao().invalidateDanglingSourceRefs(now = 5_000L)

        assertEquals(1, changed)
        assertEquals("ACTIVE", db.aiMemoryDao().findById("mem-ok")!!.status)
        assertEquals("INVALIDATED", db.aiMemoryDao().findById("mem-gone")!!.status)
        assertEquals("ACTIVE", db.aiMemoryDao().findById("mem-biz")!!.status)
        assertTrue(dao.countAllMessages() >= 1)
    }
}
