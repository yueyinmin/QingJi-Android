package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * OpenAI 兼容协议适配器（B01）。
 *
 * 只实现普通生成、图片输入、连接测试、错误映射、response_format 降级和请求取消。
 * SSE 与 Tool Calling 分别由 B02 / B03 扩展，不在本类提前放空实现。
 */
class OpenAiCompatibleProvider(
    private val clientFactory: (AiConfig) -> OkHttpClient = { defaultClient(it) },
    private val onResponseFormatFallback: (String) -> Unit = {}
) : AiProvider {

    private val capabilityCache = ConcurrentHashMap<String, AiProviderCapabilities>()

    override suspend fun generate(request: AiRequest): AiResponse {
        val model = modelFor(request)
        validate(request.config, request.key, model)

        val body = buildBodyJson(request, includeResponseFormat = request.forceJsonObject)
        val http = call(
            config = request.config,
            key = request.key,
            bodyJson = body,
            allowFallback = request.forceJsonObject
        )
        val parsed = parseResponse(http.body)
        markCapability(request.config, model, request.messages.hasImagePart())
        return AiResponse(content = parsed.first, model = model, usage = parsed.second)
    }

    override suspend fun testConnection(config: AiConfig, key: String): ConnectionResult {
        return try {
            validate(config, key, config.textModel.trim())
            val body = JSONObject().apply {
                put("model", config.textModel.trim())
                put("max_tokens", 10)
                put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
            }.toString()

            val http = call(config, key, body, allowFallback = false)
            parseResponse(http.body)
            val capabilities = AiProviderCapabilities(
                chat = true,
                streaming = false,
                toolCalling = false,
                streamingToolCalling = false,
                jsonSchema = false,
                imageInput = config.visionModel.isNotBlank(),
                audioInput = false
            )
            capabilityCache[cacheKey(config)] = capabilities
            ConnectionResult(true, "连接成功：${config.textModel.trim()}", capabilities)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            capabilityCache[cacheKey(config)] = AiProviderCapabilities.NONE
            ConnectionResult(false, e.message ?: e.code, AiProviderCapabilities.NONE)
        } catch (e: Exception) {
            val error = mapThrowable(e)
            capabilityCache[cacheKey(config)] = AiProviderCapabilities.NONE
            ConnectionResult(false, error.message ?: error.code, AiProviderCapabilities.NONE)
        }
    }

    override fun capabilities(config: AiConfig): AiProviderCapabilities =
        capabilityCache[cacheKey(config)] ?: AiProviderCapabilities.NONE

    private fun validate(config: AiConfig, key: String, model: String) {
        if (config.baseUrl.isBlank()) throw AiError.Configuration("还没填 Base URL，去设置 → AI 服务配置")
        if (key.isBlank()) throw AiError.Configuration("还没填 API Key，去设置 → AI 服务配置")
        if (model.isBlank()) throw AiError.Configuration("还没填模型名，去设置 → AI 服务配置")
    }

    private fun modelFor(request: AiRequest): String =
        if (request.messages.hasImagePart()) request.config.visionModel.trim()
        else request.config.textModel.trim()

    private fun List<AiMessage>.hasImagePart(): Boolean =
        any { message -> message.parts.any { it is AiContentPart.ImageBase64 } }

    private fun buildBodyJson(request: AiRequest, includeResponseFormat: Boolean): String {
        val model = modelFor(request)
        return JSONObject().apply {
            put("model", model)
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
            if (includeResponseFormat) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            put("messages", JSONArray().apply {
                request.messages.forEach { message -> put(messageToJson(message)) }
            })
        }.toString()
    }

    private fun messageToJson(message: AiMessage): JSONObject {
        val textParts = message.parts.filterIsInstance<AiContentPart.Text>()
        val imageParts = message.parts.filterIsInstance<AiContentPart.ImageBase64>()
        if (imageParts.isEmpty() && textParts.size == 1) {
            return JSONObject().put("role", message.role).put("content", textParts[0].text)
        }

        val content = JSONArray()
        textParts.forEach { part ->
            content.put(JSONObject().put("type", "text").put("text", part.text))
        }
        imageParts.forEach { part ->
            val dataUrl = "data:${part.mime};base64,${part.base64}"
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUrl))
            )
        }
        return JSONObject().put("role", message.role).put("content", content)
    }

    private suspend fun call(
        config: AiConfig,
        key: String,
        bodyJson: String,
        allowFallback: Boolean
    ): HttpResult {
        var result = post(config, key, bodyJson)

        if (result.code == 400 && allowFallback && JSONObject(bodyJson).has("response_format")) {
            onResponseFormatFallback(config.textModel.trim())
            val withoutFormat = JSONObject(bodyJson).apply { remove("response_format") }.toString()
            result = post(config, key, withoutFormat)
        }

        if (result.code !in 200..299) throw mapHttpError(result)
        return result
    }

    private suspend fun post(
        config: AiConfig,
        key: String,
        bodyJson: String
    ): HttpResult = suspendCancellableCoroutine { continuation ->
        val client = clientFactory(config)
        val request = Request.Builder()
            .url(endpoint(config))
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()
        val call = client.newCall(request)

        continuation.invokeOnCancellation { runCatching { call.cancel() } }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(mapThrowable(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = try {
                        it.body?.string().orEmpty()
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(mapThrowable(e))
                        return
                    }
                    if (continuation.isActive) {
                        continuation.resume(
                            HttpResult(
                                code = it.code,
                                body = body,
                                retryAfterSec = it.header("Retry-After")?.toLongOrNull()
                            )
                        )
                    }
                }
            }
        })
    }

    private fun endpoint(config: AiConfig): String {
        val raw = config.baseUrl.trim().trimEnd('/')
        return if (raw.endsWith("/chat/completions")) raw else "$raw/chat/completions"
    }

    private fun parseResponse(raw: String): Pair<String, TokenUsage?> {
        val root = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw AiError.InvalidResponse("返回内容不是合法 JSON：${raw.take(80)}")
        }

        val content = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("content").takeIf { it.isNotBlank() }
            ?: throw AiError.InvalidResponse("返回内容缺少 content")

        val usageObject = root.optJSONObject("usage")
        val usage = usageObject?.let {
            TokenUsage(
                promptTokens = it.optIntOrNull("prompt_tokens"),
                completionTokens = it.optIntOrNull("completion_tokens"),
                totalTokens = it.optIntOrNull("total_tokens")
            )
        }
        return content to usage
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun mapHttpError(result: HttpResult): AiError = when (result.code) {
        401, 403 -> AiError.InvalidApiKey()
        402 -> AiError.InsufficientBalance()
        429 -> AiError.RateLimited(result.retryAfterSec)
        in 500..599 -> AiError.ModelUnavailable("服务端 ${result.code}：${result.body.take(120)}")
        400 -> AiError.InvalidResponse("请求参数错误：${result.body.take(120)}")
        else -> AiError.InvalidResponse("请求失败(${result.code})：${result.body.take(120)}")
    }

    private fun mapThrowable(e: Throwable): AiError = when (e) {
        is SocketTimeoutException, is InterruptedIOException -> AiError.Timeout()
        is IOException -> AiError.Network(e)
        is JSONException -> AiError.InvalidResponse(e.message.orEmpty())
        is AiError -> e
        else -> AiError.Unknown(e.message?.take(120).orEmpty())
    }

    private fun markCapability(config: AiConfig, model: String, hasImage: Boolean) {
        val previous = capabilities(config)
        if (!previous.chat && model.isNotBlank()) {
            capabilityCache[cacheKey(config)] = previous.copy(
                chat = true,
                imageInput = previous.imageInput || hasImage
            )
        }
    }

    private fun cacheKey(config: AiConfig): String =
        listOf(
            config.baseUrl.trim(),
            config.textModel.trim(),
            config.visionModel.trim()
        ).joinToString("|")

    private data class HttpResult(
        val code: Int,
        val body: String,
        val retryAfterSec: Long? = null
    )

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(config: AiConfig): OkHttpClient {
            val seconds = config.timeoutSec.coerceIn(5, 120).toLong()
            return OkHttpClient.Builder()
                .connectTimeout(seconds, TimeUnit.SECONDS)
                .readTimeout(seconds, TimeUnit.SECONDS)
                .writeTimeout(seconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
