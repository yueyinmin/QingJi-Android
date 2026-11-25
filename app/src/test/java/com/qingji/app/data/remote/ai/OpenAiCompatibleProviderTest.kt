package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(timeoutSec: Int = 5) = AiConfig(
        provider = "custom",
        baseUrl = server.url("/v1").toString(),
        textModel = "text-model",
        visionModel = "vision-model",
        timeoutSec = timeoutSec,
        enabled = true
    )

    private fun successBody(content: String = "你好，这是 AI 回复") = """
        {
          "choices": [
            {"message": {"role": "assistant", "content": "$content"}}
          ],
          "usage": {"prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20}
        }
    """.trimIndent()

    private fun simpleRequest(cfg: AiConfig = config()) = AiRequest(
        config = cfg,
        key = "secret-key",
        messages = listOf(
            AiMessage.system("只回答事实"),
            AiMessage.user("你好")
        )
    )

    @Test
    fun generate_plainText_sendsExpectedRequestAndParsesUsage() = runTest {
        server.enqueue(MockResponse().setBody(successBody()))

        val response = OpenAiCompatibleProvider().generate(simpleRequest())

        assertEquals("你好，这是 AI 回复", response.content)
        assertEquals("text-model", response.model)
        assertEquals(12, response.usage?.promptTokens)
        assertEquals(8, response.usage?.completionTokens)
        assertEquals(20, response.usage?.totalTokens)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer secret-key", recorded.getHeader("Authorization"))
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("text-model", body.getString("model"))
        assertEquals(0.2, body.getDouble("temperature"), 0.0)
        assertFalse(body.has("response_format"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("只回答事实", messages.getJSONObject(0).getString("content"))
    }

    @Test
    fun generate_imageMessage_usesVisionModelAndImageUrlPart() = runTest {
        server.enqueue(MockResponse().setBody(successBody("识别完成")))

        val request = AiRequest(
            config = config(),
            key = "secret-key",
            messages = listOf(AiMessage.userImage("识别这份食物", "QUJD", "image/png"))
        )
        val response = OpenAiCompatibleProvider().generate(request)

        assertEquals("识别完成", response.content)
        assertEquals("vision-model", response.model)

        val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("vision-model", body.getString("model"))
        val parts = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("text", parts.getJSONObject(0).getString("type"))
        assertEquals("image_url", parts.getJSONObject(1).getString("type"))
        assertEquals(
            "data:image/png;base64,QUJD",
            parts.getJSONObject(1).getJSONObject("image_url").getString("url")
        )
    }

    @Test
    fun generate_responseFormatFallback_retriesOnceWithoutField() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"response_format unsupported"}"""))
        server.enqueue(MockResponse().setBody(successBody()))

        var reportedModel = ""
        val provider = OpenAiCompatibleProvider(onResponseFormatFallback = { reportedModel = it })
        val request = simpleRequest().copy(forceJsonObject = true)

        val response = provider.generate(request)

        assertEquals("你好，这是 AI 回复", response.content)
        assertEquals("text-model", reportedModel)
        assertEquals(2, server.requestCount)

        val first = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        val second = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertTrue(first.has("response_format"))
        assertFalse(second.has("response_format"))
    }

    @Test
    fun generate_401_throwsInvalidApiKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        try {
            OpenAiCompatibleProvider().generate(simpleRequest())
            fail("Expected AiError.InvalidApiKey")
        } catch (e: AiError.InvalidApiKey) {
            assertEquals("INVALID_API_KEY", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun generate_429_throwsRateLimitedWithRetryAfter() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "7")
                .setBody("""{"error":"slow down"}""")
        )

        try {
            OpenAiCompatibleProvider().generate(simpleRequest())
            fail("Expected AiError.RateLimited")
        } catch (e: AiError.RateLimited) {
            assertEquals(7L, e.retryAfterSec)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun generate_5xx_throwsModelUnavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("service unavailable"))

        try {
            OpenAiCompatibleProvider().generate(simpleRequest())
            fail("Expected AiError.ModelUnavailable")
        } catch (e: AiError.ModelUnavailable) {
            assertEquals("MODEL_UNAVAILABLE", e.code)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun generate_timeout_throwsTimeout() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(successBody())
                .setBodyDelay(800, TimeUnit.MILLISECONDS)
        )
        val fastClient = OkHttpClient.Builder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .build()
        val provider = OpenAiCompatibleProvider(clientFactory = { fastClient })

        try {
            provider.generate(simpleRequest())
            fail("Expected AiError.Timeout")
        } catch (e: AiError.Timeout) {
            assertEquals("TIMEOUT", e.code)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun generate_plainTextResponse_isAllowedWhenJsonNotForced() = runTest {
        server.enqueue(MockResponse().setBody("这是一段普通分析回复"))

        val response = OpenAiCompatibleProvider().generate(simpleRequest())

        assertEquals("这是一段普通分析回复", response.content)
        assertNull(response.usage)
    }

    @Test
    fun generate_invalidJson_throwsInvalidResponse() = runTest {
        server.enqueue(MockResponse().setBody("{not-json"))

        try {
            OpenAiCompatibleProvider().generate(simpleRequest().copy(forceJsonObject = true))
            fail("Expected AiError.InvalidResponse")
        } catch (e: AiError.InvalidResponse) {
            assertEquals("INVALID_RESPONSE", e.code)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun generate_disconnect_throwsNetworkError() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        try {
            OpenAiCompatibleProvider().generate(simpleRequest())
            fail("Expected AiError.Network")
        } catch (e: AiError.Network) {
            assertEquals("NETWORK", e.code)
            assertTrue(e.retryable)
        }
    }

    @Test
    fun generate_cancellation_stopsRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody(successBody())
                .setBodyDelay(1500, TimeUnit.MILLISECONDS)
        )
        val provider = OpenAiCompatibleProvider()

        val job = launch(Dispatchers.Default) {
            provider.generate(simpleRequest())
        }
        delay(100)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    @Test
    fun testConnection_success_declaresChatAndImageCapabilities() = runTest {
        server.enqueue(MockResponse().setBody(successBody("ok")))
        val provider = OpenAiCompatibleProvider()
        val cfg = config()

        val result = provider.testConnection(cfg, "secret-key")

        assertTrue(result.success)
        assertTrue(result.capabilities.chat)
        assertTrue(result.capabilities.imageInput)
        assertTrue(result.capabilities.streaming)
        assertTrue(result.capabilities.toolCalling)
        assertTrue(result.capabilities.streamingToolCalling)
        assertTrue(provider.capabilities(cfg).chat)
    }

    @Test
    fun testConnection_failure_returnsNoneCapabilities() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("forbidden"))
        val provider = OpenAiCompatibleProvider()
        val cfg = config()

        val result = provider.testConnection(cfg, "bad-key")

        assertFalse(result.success)
        assertFalse(result.capabilities.chat)
        assertFalse(provider.capabilities(cfg).chat)
    }

    @Test
    fun generate_missingBaseUrl_throwsConfigurationError() = runTest {
        val cfg = config().copy(baseUrl = "")
        try {
            OpenAiCompatibleProvider().generate(simpleRequest(cfg))
            fail("Expected AiError.Configuration")
        } catch (e: AiError.Configuration) {
            assertEquals("CONFIGURATION", e.code)
        }
    }

    @Test
    fun stream_textEmitsTextDeltasAndCompleted() = runTest {
        val sse = """
            data: {"choices":[{"delta":{"content":"你"},"finish_reason":null}]}

            data: {"choices":[{"delta":{"content":"好"},"finish_reason":null}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse)
        )

        val events = OpenAiCompatibleProvider().stream(simpleRequest()).toList()

        assertEquals(
            listOf("你", "好"),
            events.filterIsInstance<AiStreamEvent.TextDelta>().map { it.text }
        )
        val completed = events.filterIsInstance<AiStreamEvent.Completed>()
        assertEquals(1, completed.size)
        assertEquals(12, completed[0].usage?.totalTokens)

        val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        assertTrue(body.getBoolean("stream"))
    }

    @Test
    fun stream_toolCall_emitsStartArgumentsAndComplete() = runTest {
        val sse = """
            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"get_today_summary","arguments":"{\"date\""}}]},"finish_reason":null}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":\"2026-11-25\"}"}}]},"finish_reason":"tool_calls"}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse)
        )

        val events = OpenAiCompatibleProvider().stream(simpleRequest()).toList()

        val started = events.filterIsInstance<AiStreamEvent.ToolCallStarted>()
        val deltas = events.filterIsInstance<AiStreamEvent.ToolArgumentsDelta>()
        val completed = events.filterIsInstance<AiStreamEvent.ToolCallCompleted>()
        assertEquals(1, started.size)
        assertEquals("call_1", started[0].callId)
        assertEquals("get_today_summary", started[0].name)
        assertEquals("{\"date\":\"2026-11-25\"}", deltas.joinToString("") { it.fragment })
        assertEquals(1, completed.size)
        assertEquals("call_1", completed[0].callId)
    }

    @Test
    fun stream_vendorVariant_messageAndNoSpace_isAccepted() = runTest {
        val sse = """
            : ping

            data:{"choices":[{"message":{"content":"变体回复"},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse)
        )

        val events = OpenAiCompatibleProvider().stream(simpleRequest()).toList()

        assertEquals(
            listOf("变体回复"),
            events.filterIsInstance<AiStreamEvent.TextDelta>().map { it.text }
        )
        assertTrue(events.any { it is AiStreamEvent.Completed })
    }

    @Test
    fun stream_errorEvent_emitsStructuredFailure() = runTest {
        val sse = """
            data: {"error":{"message":"model not found"}}

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse)
        )

        val events = OpenAiCompatibleProvider().stream(simpleRequest()).toList()
        val failed = events.filterIsInstance<AiStreamEvent.Failed>()

        assertEquals(1, failed.size)
        assertTrue(failed[0].error is AiError.InvalidResponse)
        assertTrue(failed[0].error.message?.contains("model not found") == true)
    }

    @Test
    fun stream_httpError_emitsFailedEvent() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val events = OpenAiCompatibleProvider().stream(simpleRequest()).toList()

        val failed = events.filterIsInstance<AiStreamEvent.Failed>()
        assertEquals(1, failed.size)
        assertTrue(failed[0].error is AiError.InvalidApiKey)
    }

    @Test
    fun generate_withTools_sendsSchemaAndParsesToolCalls() = runTest {
        val toolResponse = """
            {
              "choices":[{"message":{"content":"","tool_calls":[
                {"id":"call_1","type":"function","function":{"name":"get_today_summary","arguments":"{\"date\":\"2026-11-25\"}"}}
              ]},"finish_reason":"tool_calls"}],
              "usage":{"prompt_tokens":20,"completion_tokens":5,"total_tokens":25}
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(toolResponse))
        val tool = AiToolDefinition(
            name = "get_today_summary",
            description = "读取今日摘要",
            parametersJson = """{"type":"object","properties":{"date":{"type":"string"}},"required":["date"]}"""
        )
        val request = simpleRequest().copy(tools = listOf(tool), toolChoice = AiToolChoice.Required)

        val response = OpenAiCompatibleProvider().generate(request)

        assertEquals(1, response.toolCalls.size)
        assertEquals("call_1", response.toolCalls[0].id)
        assertEquals("get_today_summary", response.toolCalls[0].name)
        assertEquals("{\"date\":\"2026-11-25\"}", response.toolCalls[0].arguments)
        assertEquals("tool_calls", response.finishReason)
        assertEquals(20, response.usage?.promptTokens)

        val body = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
        val sentTool = body.getJSONArray("tools").getJSONObject(0)
        assertEquals("function", sentTool.getString("type"))
        assertEquals(tool.name, sentTool.getJSONObject("function").getString("name"))
        assertEquals("required", body.getString("tool_choice"))
    }

    @Test
    fun generate_toolResultMessages_keepCallIdMapping() = runTest {
        server.enqueue(MockResponse().setBody(successBody()))
        val request = simpleRequest().copy(
            messages = listOf(
                AiMessage.assistantToolCalls(
                    listOf(AiToolCall("call_1", "get_today_summary", "{\"date\":\"2026-11-25\"}"))
                ),
                AiMessage.toolResult("call_1", "今日摄入 1800 千卡")
            )
        )

        OpenAiCompatibleProvider().generate(request)

        val messages = JSONObject(server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8())
            .getJSONArray("messages")
        assertEquals("assistant", messages.getJSONObject(0).getString("role"))
        assertEquals(
            "get_today_summary",
            messages.getJSONObject(0).getJSONArray("tool_calls").getJSONObject(0)
                .getJSONObject("function").getString("name")
        )
        assertEquals("tool", messages.getJSONObject(1).getString("role"))
        assertEquals("call_1", messages.getJSONObject(1).getString("tool_call_id"))
    }
}
