package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import com.qingji.app.ai.agent.TrustLabel
import com.qingji.app.data.ai.AiChatStore
import com.qingji.app.data.ai.AiConversationSummary
import com.qingji.app.data.ai.PersistedChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

private class FakeAiChatStore : AiChatStore {
    private val conversationsFlow = MutableStateFlow<List<AiConversationSummary>>(emptyList())
    private val messages = LinkedHashMap<String, MutableList<PersistedChatMessage>>()
    private val messageFlows = LinkedHashMap<String, MutableStateFlow<List<PersistedChatMessage>>>()

    override fun observeConversations(): Flow<List<AiConversationSummary>> = conversationsFlow

    override fun observeMessages(conversationId: String): Flow<List<PersistedChatMessage>> =
        messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun ensureConversation(entryPage: String): AiConversationSummary =
        conversationsFlow.value.firstOrNull { it.entryPage == entryPage } ?: createConversation(entryPage)

    override suspend fun createConversation(entryPage: String): AiConversationSummary {
        val summary = AiConversationSummary(
            id = UUID.randomUUID().toString(),
            title = "新对话",
            entryPage = entryPage,
            updatedAt = System.currentTimeMillis()
        )
        conversationsFlow.value = listOf(summary) + conversationsFlow.value
        messages[summary.id] = mutableListOf()
        messageFlows[summary.id] = MutableStateFlow(emptyList())
        return summary
    }

    override suspend fun appendUserMessage(id: String, conversationId: String, text: String) {
        add(
            PersistedChatMessage(id, conversationId, nextSequence(conversationId), "user", text)
        )
    }

    override suspend fun appendAssistantMessage(id: String, conversationId: String, result: AgentResult) {
        add(
            PersistedChatMessage(
                id = id,
                conversationId = conversationId,
                sequence = nextSequence(conversationId),
                role = "assistant",
                text = result.answer,
                conclusions = result.conclusions,
                tools = result.toolExecutions,
                warnings = result.warnings
            )
        )
    }

    override suspend fun appendErrorMessage(id: String, conversationId: String, text: String) {
        add(
            PersistedChatMessage(
                id,
                conversationId,
                nextSequence(conversationId),
                "assistant",
                text,
                isError = true
            )
        )
    }

    override suspend fun deleteMessage(id: String) {
        messages.forEach { (conversationId, list) ->
            if (list.removeAll { it.id == id }) refresh(conversationId)
        }
    }

    override suspend fun renameConversation(id: String, title: String) {
        conversationsFlow.value = conversationsFlow.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = System.currentTimeMillis()) else it
        }
    }

    override suspend fun deleteConversation(id: String) {
        conversationsFlow.value = conversationsFlow.value.filterNot { it.id == id }
        messages.remove(id)
        messageFlows.remove(id)
    }

    private fun add(message: PersistedChatMessage) {
        messages.getOrPut(message.conversationId) { mutableListOf() }.add(message)
        refresh(message.conversationId)
        conversationsFlow.value = conversationsFlow.value.map {
            if (it.id == message.conversationId) it.copy(updatedAt = System.currentTimeMillis()) else it
        }
    }

    private fun refresh(conversationId: String) {
        messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value =
            messages[conversationId].orEmpty().toList()
    }

    private fun nextSequence(conversationId: String): Int =
        (messages[conversationId]?.maxOfOrNull { it.sequence } ?: 0) + 1
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiChatViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val date = LocalDate.parse("2026-11-25")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun send_success_persistsUserAndAssistantMessages() = runTest {
        var seenQuestion = ""
        var seenContext = ""
        val store = FakeAiChatStore()
        val vm = AiChatViewModel(
            app,
            testStore = store,
            testRunner = { question, context ->
                seenQuestion = question
                seenContext = context
                AgentResult(
                    answer = "今天热量稳定。",
                    conclusions = listOf(
                        AgentConclusion("今日约 1800 kcal", TrustLabel.FACT, "今天")
                    ),
                    rounds = 1,
                    toolExecutions = listOf(
                        AgentToolExecution("c1", "get_today_summary", "{}", true)
                    )
                )
            },
            testClock = { date }
        )

        vm.setContext("饮食页")
        vm.onInputChange("我今天吃得怎么样？")
        vm.send()

        val state = vm.uiState.value
        assertEquals("我今天吃得怎么样？", seenQuestion)
        assertEquals("饮食页", seenContext)
        assertFalse(state.isRunning)
        assertEquals(2, state.messages.size)
        assertEquals(ChatRole.USER, state.messages[0].role)
        assertEquals(ChatRole.ASSISTANT, state.messages[1].role)
        assertEquals("今天热量稳定。", state.messages[1].text)
        assertEquals(TrustLabel.FACT, state.messages[1].conclusions[0].trust)
        assertEquals("get_today_summary", state.messages[1].tools[0].name)
        assertTrue(state.conversations.size >= 1)
    }

    @Test
    fun send_failure_showsStructuredErrorAndRetryReusesQuestion() = runTest {
        var attempts = 0
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testRunner = { _, _ ->
                attempts++
                if (attempts == 1) throw IllegalStateException("network down")
                AgentResult("好了", emptyList(), 1, emptyList())
            },
            testClock = { date }
        )

        vm.send("今天训练怎么样？")
        assertTrue(vm.uiState.value.messages.last().isError)
        assertEquals("network down", vm.uiState.value.messages.last().text)

        vm.retry()
        assertFalse(vm.uiState.value.messages.last().isError)
        assertEquals("好了", vm.uiState.value.messages.last().text)
        assertEquals(2, attempts)
    }

    @Test
    fun newConversation_selectConversation_restoresMessages() = runTest {
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testRunner = { q, _ -> AgentResult(q, emptyList(), 1, emptyList()) },
            testClock = { date }
        )
        vm.send("问题一")
        val firstId = vm.uiState.value.activeConversationId!!

        vm.newConversation()
        val secondId = vm.uiState.value.activeConversationId!!
        assertTrue(firstId != secondId)
        assertEquals(1, vm.uiState.value.messages.size)

        vm.send("问题二")
        vm.selectConversation(firstId)

        assertEquals(firstId, vm.uiState.value.activeConversationId)
        assertEquals("问题一", vm.uiState.value.messages[0].text)
        assertEquals(2, vm.uiState.value.conversations.size)
    }

    @Test
    fun clear_createsNewConversationAndKeepsContext() = runTest {
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testRunner = { q, _ -> AgentResult(q, emptyList(), 1, emptyList()) },
            testClock = { date }
        )
        vm.setContext("训练页")
        vm.send("我的计划")
        val beforeClearId = vm.uiState.value.activeConversationId

        vm.clear()

        assertEquals("训练页", vm.uiState.value.context)
        assertEquals(1, vm.uiState.value.messages.size)
        assertEquals("welcome", vm.uiState.value.messages[0].id)
        assertTrue(vm.uiState.value.activeConversationId != beforeClearId)
    }

    @Test
    fun send_streamingRunner_updatesPendingTextBeforePersist() = runTest {
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testStreamRunner = { _, _, onDelta ->
                onDelta("正在")
                onDelta("查记录。")
                AgentResult("正在查记录。", emptyList(), 1, emptyList())
            },
            testClock = { date }
        )

        vm.send("今天怎么样")

        assertEquals("正在查记录。", vm.uiState.value.messages.last().text)
        assertFalse(vm.uiState.value.messages.last().pending)
    }

    @Test
    fun renameConversation_updatesChipTitle() = runTest {
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testRunner = { q, _ -> AgentResult(q, emptyList(), 1, emptyList()) },
            testClock = { date }
        )
        vm.send("问题")
        val id = vm.uiState.value.activeConversationId!!

        vm.renameConversation(id, "今日饮食复盘")

        assertEquals("今日饮食复盘", vm.uiState.value.conversations.first { it.id == id }.title)
    }

    @Test
    fun attachImage_andVoiceResult_prepareInputWithoutWritingDb() = runTest {
        val vm = AiChatViewModel(
            app,
            testStore = FakeAiChatStore(),
            testRunner = { q, _ -> AgentResult(q, emptyList(), 1, emptyList()) },
            testClock = { date }
        )

        vm.attachImage("YWJj")
        vm.onVoiceResult("看看这张图")

        assertEquals("YWJj", vm.uiState.value.attachedImageBase64)
        assertEquals("看看这张图", vm.uiState.value.input)
    }
}
