package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.remote.AiClient
import com.qingji.app.data.remote.AiException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class AiClientFacadeTest {

    private val config = AiConfig(
        provider = "custom",
        baseUrl = "https://example.invalid/v1",
        textModel = "text-model",
        visionModel = "vision-model",
        enabled = true
    )

    private class FakeProvider(
        private val response: AiResponse? = null,
        private val error: AiError? = null,
        private val connection: ConnectionResult? = null
    ) : AiProvider {
        var lastRequest: AiRequest? = null

        override suspend fun generate(request: AiRequest): AiResponse {
            lastRequest = request
            error?.let { throw it }
            return response ?: AiResponse("")
        }

        override suspend fun testConnection(config: AiConfig, key: String): ConnectionResult =
            connection ?: ConnectionResult(true, "ok", capabilities(config))

        override fun capabilities(config: AiConfig): AiProviderCapabilities =
            AiProviderCapabilities(chat = true, imageInput = true)
    }

    @Test
    fun recognizeText_parsesJsonAndForcesJsonObject() = runTest {
        val json = """
            {"meal_type":"lunch","items":[{"name":"米饭","grams":150,"kcal":174.0,
            "protein_g":3.9,"carb_g":38.0,"fat_g":0.6,"confidence":0.8}],
            "total_kcal":999,"advice":"","need_clarify":false}
        """.trimIndent()
        val provider = FakeProvider(response = AiResponse(json))
        val client = AiClient(provider)

        val result = client.recognizeText(config, "key", "中午吃了米饭", "用户忌口：花生")

        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("lunch", parsed.mealType)
        assertEquals(1, parsed.items.size)
        assertEquals("米饭", parsed.items[0].name)
        assertEquals(174.0, parsed.totalKcal, 0.0)
        assertTrue(provider.lastRequest!!.forceJsonObject)
        assertTrue(provider.lastRequest!!.messages[0].parts.toString().contains("用户忌口：花生"))
    }

    @Test
    fun analyze_stripsCodeFenceAndKeepsPlainText() = runTest {
        val provider = FakeProvider(response = AiResponse("```json\n这是一段分析\n```"))
        val client = AiClient(provider)

        val result = client.analyze(config, "key", "今日 1800 kcal")

        assertTrue(result.isSuccess)
        assertEquals("这是一段分析", result.getOrThrow())
        assertFalse(provider.lastRequest!!.forceJsonObject)
    }

    @Test
    fun testConnection_failureMapsToLegacyException() = runTest {
        val provider = FakeProvider(
            connection = ConnectionResult(false, "Key 无效", AiProviderCapabilities.NONE)
        )
        val client = AiClient(provider)

        val result = client.testConnection(config, "bad-key")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiException)
        assertEquals("Key 无效", result.exceptionOrNull()?.message)
    }

    @Test
    fun recognizeText_errorMapsAiErrorToLegacyException() = runTest {
        val provider = FakeProvider(error = AiError.InvalidApiKey())
        val client = AiClient(provider)

        val result = client.recognizeText(config, "key", "米饭")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AiException)
    }
}
