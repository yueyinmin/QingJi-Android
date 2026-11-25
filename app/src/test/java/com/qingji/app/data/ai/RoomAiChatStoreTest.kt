package com.qingji.app.data.ai

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import com.qingji.app.ai.agent.TrustLabel
import com.qingji.app.data.local.ai.AiDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomAiChatStoreTest {

    @Test
    fun conversationAndMessagesSurviveStoreRecreation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val store = RoomAiChatStore(db)
            val conversation = store.createConversation("首页")
            store.appendUserMessage("u1", conversation.id, "我今天吃得怎么样？")
            store.appendAssistantMessage(
                id = "a1",
                conversationId = conversation.id,
                result = AgentResult(
                    answer = "今天热量稳定。",
                    conclusions = listOf(AgentConclusion("约 1800 kcal", TrustLabel.FACT, "今天")),
                    rounds = 1,
                    toolExecutions = listOf(
                        AgentToolExecution("c1", "get_today_summary", "{}", true)
                    ),
                    warnings = listOf("数据来自本地记录")
                )
            )

            val recreated = RoomAiChatStore(db)
            val conversations = recreated.conversationsOnce()
            val messages = recreated.messagesOnce(conversation.id)

            assertEquals(1, conversations.size)
            assertEquals("我今天吃得怎么样？", conversations[0].title)
            assertEquals(2, messages.size)
            assertEquals("user", messages[0].role)
            assertEquals("assistant", messages[1].role)
            assertEquals("今天热量稳定。", messages[1].text)
            assertEquals(TrustLabel.FACT, messages[1].conclusions[0].trust)
            assertEquals("get_today_summary", messages[1].tools[0].name)
            assertTrue(messages[1].warnings.contains("数据来自本地记录"))
        } finally {
            db.close()
        }
    }
}
