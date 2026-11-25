package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** B01 抽象层的纯 JVM 测试，不触网、不依赖 Android 框架。 */
class AiProviderAbstractionTest {

    private val config = AiConfig(
        provider = "custom",
        baseUrl = "https://example.invalid/v1",
        textModel = "test-model",
        enabled = true
    )

    @Test
    fun userTextMessage_hasSingleTextPart() {
        val message = AiMessage.user("今天吃了什么")
        assertEquals("user", message.role)
        assertEquals(1, message.parts.size)
        assertEquals("今天吃了什么", (message.parts[0] as AiContentPart.Text).text)
    }

    @Test
    fun userImageMessage_keepsTextAndImageParts() {
        val message = AiMessage.userImage("识别这张照片", "QUJD", "image/png")
        assertEquals(2, message.parts.size)
        assertEquals("识别这张照片", (message.parts[0] as AiContentPart.Text).text)
        val image = message.parts[1] as AiContentPart.ImageBase64
        assertEquals("QUJD", image.base64)
        assertEquals("image/png", image.mime)
    }

    @Test
    fun request_usesB01Defaults() {
        val request = AiRequest(config = config, key = "key", messages = listOf(AiMessage.user("hi")))
        assertEquals(0.2, request.temperature, 0.0)
        assertNull(request.maxTokens)
        assertFalse(request.forceJsonObject)
    }

    @Test
    fun tokenUsage_allowsUnknownValues() {
        val usage = TokenUsage(promptTokens = null, completionTokens = null, totalTokens = null)
        assertNull(usage.promptTokens)
        assertNull(usage.completionTokens)
        assertNull(usage.totalTokens)
    }

    @Test
    fun capabilities_canDeclareChatOnlyWithoutFutureFeatures() {
        val capabilities = AiProviderCapabilities(chat = true, imageInput = false)
        assertTrue(capabilities.chat)
        assertFalse(capabilities.streaming)
        assertFalse(capabilities.toolCalling)
        assertFalse(capabilities.streamingToolCalling)
        assertFalse(capabilities.jsonSchema)
        assertFalse(capabilities.imageInput)
        assertFalse(capabilities.audioInput)
        assertFalse(AiProviderCapabilities.NONE.chat)
    }

    @Test
    fun errors_exposeStableCodesAndRetryability() {
        assertTrue(AiError.Network().retryable)
        assertFalse(AiError.InvalidApiKey().retryable)
        assertFalse(AiError.InsufficientBalance().retryable)
        assertTrue(AiError.RateLimited(30).retryable)
        assertEquals("RATE_LIMITED", AiError.RateLimited().code)
        assertEquals("TOOL_ARGUMENT", AiError.ToolArgument("get_today_summary", "bad date").code)
    }

    @Test
    fun networkError_keepsOriginalCause() {
        val cause = IllegalStateException("socket closed")
        val error = AiError.Network(cause)
        assertSame(cause, error.cause)
        assertEquals("连不上 AI 服务，请检查网络", error.message)
    }
}
