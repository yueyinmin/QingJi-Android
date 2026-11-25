package com.qingji.app.ai.agent

import com.qingji.app.ai.AiTool
import com.qingji.app.ai.ToolExecutor
import com.qingji.app.ai.ToolRegistry
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.remote.ai.AiContentPart
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.AiProviderCapabilities
import com.qingji.app.data.remote.ai.ConnectionResult
import com.qingji.app.data.remote.ai.AiMessage
import com.qingji.app.data.remote.ai.AiProvider
import com.qingji.app.data.remote.ai.AiRequest
import com.qingji.app.data.remote.ai.AiResponse
import com.qingji.app.data.remote.ai.AiToolCall
import com.qingji.app.data.remote.ai.AiToolChoice
import com.qingji.app.data.remote.ai.AiToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class QingJiAgentTest {

    private val date = LocalDate.parse("2026-11-25")

    private val config = AiConfig(
        provider = "test",
        baseUrl = "https://example.invalid/v1",
        textModel = "test-model",
        visionModel = "vision-model",
        enabled = true
    )

    private fun agentRequest() = AgentRequest(
        config = config,
        key = "key",
        question = "我今天吃得怎么样？",
        taskType = "daily_analysis",
        date = date,
        page = "首页",
        recentSummary = "最近体重稳定"
    )

    private fun fakeTool(
        toolName: String,
        result: String = """{"tool":"$toolName","source":"FACT","data":{"ok":true}}""",
        readOnlyFlag: Boolean = true
    ) = object : AiTool {
        override val name = toolName
        override val description = "$toolName 测试工具"
        override val parametersJson = "{}"
        override val readOnly = readOnlyFlag
        override suspend fun execute(argumentsJson: String) = result
    }

    private fun textOf(message: AiMessage): String =
        (message.parts.first() as AiContentPart.Text).text

    private class QueueProvider(
        private val responses: List<AiResponse>,
        private val delayMs: Long = 0
    ) : AiProvider {
        val requests = mutableListOf<AiRequest>()
        private var index = 0

        override suspend fun generate(request: AiRequest): AiResponse {
            requests.add(request)
            if (delayMs > 0) delay(delayMs)
            val response = responses[index.coerceAtMost(responses.lastIndex)]
            index++
            return response
        }

        override suspend fun testConnection(
            config: com.qingji.app.data.local.entity.AiConfig,
            key: String
        ): ConnectionResult = ConnectionResult(true, "ok", capabilities(config))

        override fun capabilities(config: com.qingji.app.data.local.entity.AiConfig): AiProviderCapabilities =
            AiProviderCapabilities(chat = true, toolCalling = true, imageInput = false)
    }

    private fun finalJson(
        answer: String = "今天热量偏稳。",
        trust: String = "FACT"
    ) = """{"answer":"$answer","conclusions":[{"text":"今天热量约 1800 千卡","trust":"$trust","dataRange":"今天"}]}"""

    @Test
    fun contextBuilder_buildsMinimalContextWithTrustRules() {
        val toolDef = AiToolDefinition("get_today_summary", "读取今日摘要", "{}")
        val aiRequest = ContextBuilder().build(agentRequest(), listOf(toolDef))
        val system = textOf(aiRequest.messages[0])

        assertTrue(system.contains("2026-11-25"))
        assertTrue(system.contains("daily_analysis"))
        assertTrue(system.contains("get_today_summary"))
        assertTrue(system.contains("FACT"))
        assertTrue(system.contains("INFERRED"))
        assertTrue(system.contains("只输出 JSON"))
        assertEquals("用户问题：我今天吃得怎么样？", textOf(aiRequest.messages[1]))
        assertTrue(aiRequest.forceJsonObject)
        assertTrue(aiRequest.toolChoice is AiToolChoice.Auto)
        assertSame(toolDef, aiRequest.tools.single())
    }

    @Test
    fun agent_noToolCall_returnsParsedResult() = runTest {
        val provider = QueueProvider(listOf(AiResponse(finalJson(trust = "INFERRED"))))
        val agent = QingJiAgent(provider, ToolRegistry(), ToolExecutor(ToolRegistry()))

        val result = agent.run(agentRequest())

        assertEquals("今天热量偏稳。", result.answer)
        assertEquals(1, result.conclusions.size)
        assertEquals(TrustLabel.INFERRED, result.conclusions[0].trust)
        assertTrue(result.toolExecutions.isEmpty())
        assertEquals(1, result.rounds)
    }

    @Test
    fun agent_singleToolCall_executesAndFeedsResultBack() = runTest {
        val registry = ToolRegistry().apply { register(fakeTool("get_today_summary")) }
        val provider = QueueProvider(
            listOf(
                AiResponse(
                    "",
                    toolCalls = listOf(AiToolCall("call_1", "get_today_summary", "{}"))
                ),
                AiResponse(finalJson())
            )
        )
        val agent = QingJiAgent(provider, registry, ToolExecutor(registry))

        val result = agent.run(agentRequest())

        assertEquals(2, result.rounds)
        assertEquals(1, result.toolExecutions.size)
        assertTrue(result.toolExecutions[0].ok)
        assertEquals(TrustLabel.FACT, result.conclusions[0].trust)

        val secondRequest = provider.requests[1]
        assertTrue(secondRequest.messages[2].toolCalls.isNotEmpty())
        assertEquals("call_1", secondRequest.messages[2].toolCalls[0].id)
        assertEquals("call_1", secondRequest.messages[3].toolCallId)
        assertEquals("call_1", JSONObject(textOf(secondRequest.messages[3])).getString("callId"))
    }

    @Test
    fun agent_multipleToolCalls_keepCallIdMapping() = runTest {
        val registry = ToolRegistry().apply {
            register(fakeTool("tool_a", """{"tool":"tool_a","source":"FACT","data":{"v":"A"}}"""))
            register(fakeTool("tool_b", """{"tool":"tool_b","source":"FACT","data":{"v":"B"}}"""))
        }
        val provider = QueueProvider(
            listOf(
                AiResponse(
                    "",
                    toolCalls = listOf(
                        AiToolCall("a", "tool_a", "{}"),
                        AiToolCall("b", "tool_b", "{}")
                    )
                ),
                AiResponse(finalJson())
            )
        )
        val agent = QingJiAgent(provider, registry, ToolExecutor(registry, maxConcurrency = 2))

        val result = agent.run(agentRequest())

        assertEquals(listOf("a", "b"), result.toolExecutions.map { it.callId })
        val toolMessages = provider.requests[1].messages.filter { it.toolCallId != null }
        assertEquals(listOf("a", "b"), toolMessages.map { it.toolCallId })
    }

    @Test
    fun agent_unknownAndWriteTools_areRejectedAsStructuredErrors() = runTest {
        val registry = ToolRegistry().apply {
            register(fakeTool("write_food_log", readOnlyFlag = false))
        }
        val provider = QueueProvider(
            listOf(
                AiResponse(
                    "",
                    toolCalls = listOf(
                        AiToolCall("unknown", "not_registered", "{}"),
                        AiToolCall("write", "write_food_log", "{}")
                    )
                ),
                AiResponse(finalJson(trust = "UNKNOWN"))
            )
        )
        val agent = QingJiAgent(provider, registry, ToolExecutor(registry))

        val result = agent.run(agentRequest())

        assertTrue(result.toolExecutions.all { !it.ok })
        assertEquals(
            listOf("TOOL_ARGUMENT", "PERMISSION_DENIED"),
            result.toolExecutions.map { it.errorCode }
        )
        assertFalse(provider.requests[0].tools.any { it.name == "write_food_log" })
    }

    @Test
    fun agent_duplicateCallId_isSkippedAndWarned() = runTest {
        val registry = ToolRegistry().apply { register(fakeTool("tool_a")) }
        val provider = QueueProvider(
            listOf(
                AiResponse(
                    "",
                    toolCalls = listOf(
                        AiToolCall("same", "tool_a", "{}"),
                        AiToolCall("same", "tool_a", "{}")
                    )
                ),
                AiResponse(finalJson(trust = "UNKNOWN"))
            )
        )
        val agent = QingJiAgent(provider, registry, ToolExecutor(registry))

        val result = agent.run(agentRequest())

        assertEquals(1, result.toolExecutions.size)
        assertTrue(result.warnings.any { it.contains("重复 callId") })
    }

    @Test
    fun agent_maxRounds_limitIsEnforced() = runTest {
        val registry = ToolRegistry().apply { register(fakeTool("tool_a")) }
        val provider = QueueProvider((1..10).map {
            AiResponse("", toolCalls = listOf(AiToolCall("call_$it", "tool_a", "{}")))
        })
        val agent = QingJiAgent(provider, registry, ToolExecutor(registry))

        try {
            agent.run(agentRequest().copy(maxRounds = 2, maxTotalTools = 10))
            fail("Expected max rounds error")
        } catch (e: AiError.InvalidResponse) {
            assertTrue(e.message?.contains("最大交互轮数") == true)
        }
    }

    @Test
    fun parser_invalidTrustLabel_downgradesToUnknown() {
        val parsed = AgentResponseParser().parse(
            """{"answer":"x","conclusions":[{"text":"y","trust":"FACT","dataRange":"今天"}]}""",
            hasToolEvidence = false
        )

        assertEquals(TrustLabel.UNKNOWN, parsed.conclusions[0].trust)
        assertTrue(parsed.warnings.any { it.contains("没有工具证据") })
    }

    @Test
    fun agent_cancellation_propagates() = runBlocking {
        val provider = QueueProvider(listOf(AiResponse(finalJson())), delayMs = 2000)
        val agent = QingJiAgent(provider, ToolRegistry(), ToolExecutor(ToolRegistry()))

        val job = launch(Dispatchers.Default) { agent.run(agentRequest()) }
        delay(100)
        job.cancel()

        assertTrue(job.isCancelled)
    }
}
