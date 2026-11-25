package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.domain.privacy.SecretRedactor
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
 * OpenAI 兼容协议适配器。
 *
 * 支持：普通生成、图片输入、SSE 文本流、Tool Calling、流式工具参数、
 * 工具结果回传、错误映射、response_format 降级与请求取消。
 */
class OpenAiCompatibleProvider(
    private val clientFactory: (AiConfig) -> OkHttpClient = { defaultClient(it) },
    private val onResponseFormatFallback: (String) -> Unit = {}
) : AiProvider {

    private val capabilityCache = ConcurrentHashMap<String, AiProviderCapabilities>()

    override suspend fun generate(request: AiRequest): AiResponse {
        val model = modelFor(request)
        validate(request.config, request.key, model)

        val body = buildBodyJson(
            request = request,
            includeResponseFormat = request.forceJsonObject,
            stream = false
        )
        val http = call(
            config = request.config,
            key = request.key,
            bodyJson = body,
            allowFallback = request.forceJsonObject
        )
        val parsed = parseResponse(http.body, requireJson = request.forceJsonObject)
        markCapability(request.config, model, request.messages.hasImagePart())
        return AiResponse(
            content = parsed.content,
            model = model,
            usage = parsed.usage,
            toolCalls = parsed.toolCalls,
            finishReason = parsed.finishReason
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun stream(request: AiRequest): Flow<AiStreamEvent> = callbackFlow {
        val model = modelFor(request)
        var activeCall: Call? = null

        try {
            validate(request.config, request.key, model)
            val body = buildBodyJson(
                request = request,
                includeResponseFormat = false,
                stream = true
            )
            val client = clientFactory(request.config)
            val httpRequest = Request.Builder()
                .url(endpoint(request.config))
                .addHeader("Authorization", "Bearer ${request.key}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            activeCall = client.newCall(httpRequest)
            var streamUsage: TokenUsage? = null

            withContext(Dispatchers.IO) {
                activeCall!!.execute().use { response ->
                    if (response.code !in 200..299) {
                        throw mapHttpError(
                            HttpResult(response.code, response.body?.string().orEmpty())
                        )
                    }
                    val source = response.body?.source()
                        ?: throw AiError.StreamInterrupted()

                    val accumulators = linkedMapOf<Int, ToolCallAccumulator>()

                    while (!isClosedForSend) {
                        val line = source.readUtf8Line() ?: break
                        val payload = line.removePrefix("data:").trim()
                        if (payload.isEmpty() || !line.startsWith("data:")) continue
                        if (payload == "[DONE]") break

                        val event = try {
                            JSONObject(payload)
                        } catch (_: JSONException) {
                            continue
                        }

                        if (event.has("error")) {
                            val detail = event.optJSONObject("error")?.optString("message")
                                ?: event.optString("error")
                            // 阶段 K：流式错误同样来自上游，脱敏后再展示
                            trySend(
                                AiStreamEvent.Failed(
                                    AiError.InvalidResponse(SecretRedactor.redact(detail, 120))
                                )
                            )
                            break
                        }

                        event.optJSONObject("usage")?.let { streamUsage = parseUsage(it) }
                        val choice = event.optJSONArray("choices")?.optJSONObject(0) ?: continue
                        // 大多数供应商用 delta；少数兼容实现直接给 message。
                        val delta = choice.optJSONObject("delta") ?: choice.optJSONObject("message")

                        delta?.optString("content")?.takeIf { it.isNotEmpty() }
                            ?.let { trySend(AiStreamEvent.TextDelta(it)) }

                        delta?.optJSONArray("tool_calls")?.let { calls ->
                            for (i in 0 until calls.length()) {
                                val callJson = calls.optJSONObject(i) ?: continue
                                val index = callJson.optInt("index", i)
                                val function = callJson.optJSONObject("function")
                                val id = callJson.optString("id").takeIf { it.isNotBlank() }
                                val name = function?.optString("name")?.takeIf { it.isNotBlank() }
                                val args = function?.optString("arguments")?.takeIf { it.isNotEmpty() }

                                val acc = accumulators.getOrPut(index) { ToolCallAccumulator(index) }
                                if (id != null && acc.id.isBlank()) acc.id = id
                                if (name != null && acc.name.isBlank()) acc.name = name
                                if (acc.id.isBlank()) acc.id = "tool_$index"

                                if (!acc.started && acc.name.isNotBlank()) {
                                    trySend(AiStreamEvent.ToolCallStarted(acc.id, acc.name))
                                    acc.started = true
                                }
                                if (args != null) {
                                    acc.arguments.append(args)
                                    trySend(AiStreamEvent.ToolArgumentsDelta(acc.id, args))
                                }
                            }
                        }

                        val finishReason = choice.optString("finish_reason")
                        if (finishReason == "tool_calls") {
                            completeToolCalls(accumulators) { trySend(it) }
                        }
                    }

                    completeToolCalls(accumulators) { trySend(it) }
                }
            }

            markCapability(request.config, model, request.messages.hasImagePart())
            trySend(AiStreamEvent.Completed(usage = streamUsage))
            close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiError) {
            trySend(AiStreamEvent.Failed(e))
            close()
        } catch (e: IOException) {
            trySend(AiStreamEvent.Failed(mapThrowable(e)))
            close()
        } catch (e: Exception) {
            trySend(AiStreamEvent.Failed(mapThrowable(e)))
            close()
        }

        awaitClose { activeCall?.cancel() }
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
            parseResponse(http.body, requireJson = false)
            val capabilities = AiProviderCapabilities(
                chat = true,
                streaming = true,
                toolCalling = true,
                streamingToolCalling = true,
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
            // 阶段 K：这条文案会显示在配置页，同样先脱敏
            ConnectionResult(
                false,
                SecretRedactor.redact(e.message ?: e.code, 120),
                AiProviderCapabilities.NONE
            )
        } catch (e: Exception) {
            val error = mapThrowable(e)
            capabilityCache[cacheKey(config)] = AiProviderCapabilities.NONE
            ConnectionResult(
                false,
                SecretRedactor.redact(error.message ?: error.code, 120),
                AiProviderCapabilities.NONE
            )
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

    private fun buildBodyJson(
        request: AiRequest,
        includeResponseFormat: Boolean,
        stream: Boolean
    ): String {
        val model = modelFor(request)
        return JSONObject().apply {
            put("model", model)
            put("temperature", request.temperature)
            request.maxTokens?.let { put("max_tokens", it) }
            if (includeResponseFormat) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            if (stream) put("stream", true)
            if (request.tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    request.tools.forEach { tool ->
                        put(
                            JSONObject()
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject()
                                        .put("name", tool.name)
                                        .put("description", tool.description)
                                        .put("parameters", JSONObject(tool.parametersJson))
                                )
                        )
                    }
                })
                put("tool_choice", toolChoiceJson(request.toolChoice))
            }
            put("messages", JSONArray().apply {
                request.messages.forEach { message -> put(messageToJson(message)) }
            })
        }.toString()
    }

    private fun toolChoiceJson(choice: AiToolChoice): Any = when (choice) {
        AiToolChoice.Auto -> "auto"
        AiToolChoice.None -> "none"
        AiToolChoice.Required -> "required"
        is AiToolChoice.Named -> JSONObject()
            .put("type", "function")
            .put("function", JSONObject().put("name", choice.name))
    }

    private fun messageToJson(message: AiMessage): JSONObject {
        val textParts = message.parts.filterIsInstance<AiContentPart.Text>()
        val imageParts = message.parts.filterIsInstance<AiContentPart.ImageBase64>()

        if (message.toolCallId != null) {
            return JSONObject()
                .put("role", "tool")
                .put("tool_call_id", message.toolCallId)
                .put("content", textParts.joinToString("\n") { it.text })
        }

        if (message.toolCalls.isNotEmpty()) {
            val obj = JSONObject()
                .put("role", message.role)
                .put("content", textParts.firstOrNull()?.text.orEmpty())
            obj.put("tool_calls", JSONArray().apply {
                message.toolCalls.forEach { call ->
                    put(
                        JSONObject()
                            .put("id", call.id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject()
                                    .put("name", call.name)
                                    .put("arguments", call.arguments)
                            )
                    )
                }
            })
            return obj
        }

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

    private fun parseResponse(raw: String, requireJson: Boolean): ParsedResponse {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) throw AiError.InvalidResponse("AI 返回内容为空")

        val root = try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            if (requireJson) {
                // 阶段 K：这段正文可能包含服务商回显的请求内容，脱敏后再进提示
                throw AiError.InvalidResponse("返回内容不是合法 JSON：${SecretRedactor.redact(trimmed, 80)}")
            }
            return ParsedResponse(content = trimmed)
        }

        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val content = message?.optString("content")?.takeIf { it.isNotBlank() }
            ?: root.optString("content").takeIf { it.isNotBlank() }
            ?: trimmed
        val usage = root.optJSONObject("usage")?.let { parseUsage(it) }
        val toolCalls = parseToolCalls(message?.optJSONArray("tool_calls"))
        val finishReason = root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optString("finish_reason")
            ?.takeIf { it.isNotBlank() }

        return ParsedResponse(content, usage, toolCalls, finishReason)
    }

    private fun parseToolCalls(array: JSONArray?): List<AiToolCall> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val function = obj.optJSONObject("function") ?: return@mapNotNull null
            AiToolCall(
                id = obj.optString("id").takeIf { it.isNotBlank() } ?: "tool_$i",
                name = function.optString("name"),
                arguments = function.optString("arguments")
            )
        }
    }

    private fun parseUsage(obj: JSONObject): TokenUsage = TokenUsage(
        promptTokens = obj.optIntOrNull("prompt_tokens"),
        completionTokens = obj.optIntOrNull("completion_tokens"),
        totalTokens = obj.optIntOrNull("total_tokens")
    )

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun mapHttpError(result: HttpResult): AiError {
        // 阶段 K：上游正文可能回显 Key / Token，先脱敏再进错误提示
        val detail = SecretRedactor.redact(result.body, 120)
        return when (result.code) {
            401, 403 -> AiError.InvalidApiKey()
            402 -> AiError.InsufficientBalance()
            429 -> AiError.RateLimited(result.retryAfterSec)
            in 500..599 -> AiError.ModelUnavailable("服务端 ${result.code}：$detail")
            400 -> AiError.InvalidResponse("请求参数错误：$detail")
            else -> AiError.InvalidResponse("请求失败(${result.code})：$detail")
        }
    }

    private fun mapThrowable(e: Throwable): AiError = when (e) {
        is SocketTimeoutException, is InterruptedIOException -> AiError.Timeout()
        is IOException -> AiError.Network(e)
        is JSONException -> AiError.InvalidResponse(e.message.orEmpty())
        is AiError -> e
        else -> AiError.Unknown(e.message?.take(120).orEmpty())
    }

    private fun markCapability(config: AiConfig, model: String, hasImage: Boolean) {
        if (model.isBlank()) return
        val previous = capabilities(config)
        capabilityCache[cacheKey(config)] = previous.copy(
            chat = true,
            streaming = true,
            toolCalling = true,
            streamingToolCalling = true,
            imageInput = previous.imageInput || hasImage
        )
    }

    private fun completeToolCalls(
        accumulators: Map<Int, ToolCallAccumulator>,
        emit: (AiStreamEvent) -> Unit
    ) {
        accumulators.values.sortedBy { it.index }.forEach { acc ->
            if (!acc.completed && acc.started) {
                emit(AiStreamEvent.ToolCallCompleted(acc.id))
                acc.completed = true
            }
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

    private data class ParsedResponse(
        val content: String,
        val usage: TokenUsage? = null,
        val toolCalls: List<AiToolCall> = emptyList(),
        val finishReason: String? = null
    )

    private class ToolCallAccumulator(val index: Int) {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
        var started: Boolean = false
        var completed: Boolean = false
    }

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
