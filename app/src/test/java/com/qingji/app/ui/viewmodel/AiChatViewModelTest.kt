package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import com.qingji.app.ai.agent.TrustLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun send_success_addsUserAndAssistantMessages() = runTest {
        var seenQuestion = ""
        var seenContext = ""
        val vm = AiChatViewModel(
            app,
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
        assertEquals(3, state.messages.size)
        assertEquals(ChatRole.USER, state.messages[1].role)
        assertEquals(ChatRole.ASSISTANT, state.messages[2].role)
        assertEquals("今天热量稳定。", state.messages[2].text)
        assertEquals(TrustLabel.FACT, state.messages[2].conclusions[0].trust)
        assertEquals("get_today_summary", state.messages[2].tools[0].name)
    }

    @Test
    fun send_failure_showsStructuredErrorAndRetryReusesQuestion() = runTest {
        var attempts = 0
        val vm = AiChatViewModel(
            app,
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
    fun clear_resetsConversationButKeepsContext() = runTest {
        val vm = AiChatViewModel(
            app,
            testRunner = { q, _ -> AgentResult(q, emptyList(), 1, emptyList()) },
            testClock = { date }
        )
        vm.setContext("训练页")
        vm.send("我的计划")

        vm.clear()

        assertEquals("训练页", vm.uiState.value.context)
        assertEquals(1, vm.uiState.value.messages.size)
        assertEquals("welcome", vm.uiState.value.messages[0].id)
    }
}
