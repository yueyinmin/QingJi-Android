package com.qingji.app.ai

import com.qingji.app.data.remote.ai.AiError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 本地工具执行器。
 *
 * 规则：
 * - 只执行注册过的只读工具。
 * - 参数先做 JSON Schema 最小校验（required 字段）。
 * - 并发数受 maxConcurrency 限制，默认串行安全值 1。
 * - 每个请求始终按 callId 返回结果，顺序与输入一致。
 * - 单个工具失败不影响其他工具结果。
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val maxConcurrency: Int = 1,
    private val defaultTimeoutMs: Long = 10_000
) {
    suspend fun execute(requests: List<ToolRequest>): List<ToolResult> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val seenCallIds = ConcurrentHashMap.newKeySet<String>()

        requests.map { request ->
            async {
                val start = System.currentTimeMillis()
                if (!seenCallIds.add(request.callId)) {
                    return@async failed(
                        request,
                        AiError.ToolArgument("tool", "重复 callId：${request.callId}"),
                        start
                    )
                }

                val tool = registry.find(request.name)
                    ?: return@async failed(
                        request,
                        AiError.ToolArgument(request.name, "未注册的工具"),
                        start
                    )

                if (!tool.readOnly) {
                    return@async failed(
                        request,
                        AiError.PermissionDenied(request.name),
                        start
                    )
                }

                val argumentError = validateArguments(tool, request.argumentsJson)
                if (argumentError != null) return@async failed(request, argumentError, start)

                try {
                    val result = withTimeout(defaultTimeoutMs) {
                        semaphore.withPermit { tool.execute(request.argumentsJson) }
                    }
                    ToolResult(
                        callId = request.callId,
                        ok = true,
                        resultJson = result,
                        durationMs = System.currentTimeMillis() - start
                    )
                } catch (e: TimeoutCancellationException) {
                    failed(request, AiError.Timeout(), start)
                } catch (e: AiError) {
                    failed(request, e, start)
                } catch (e: Exception) {
                    failed(
                        request,
                        AiError.ToolArgument(request.name, e.message?.take(120).orEmpty()),
                        start
                    )
                }
            }
        }.map { it.await() }
    }

    private fun validateArguments(tool: AiTool, argumentsJson: String): AiError? {
        val args = try {
            JSONObject(argumentsJson)
        } catch (e: JSONException) {
            return AiError.ToolArgument(tool.name, "参数不是合法 JSON")
        }

        val schema = try {
            JSONObject(tool.parametersJson)
        } catch (e: JSONException) {
            return AiError.ToolArgument(tool.name, "工具 Schema 不是合法 JSON")
        }

        val required = schema.optJSONArray("required") ?: JSONArray()
        for (i in 0 until required.length()) {
            val key = required.optString(i)
            if (key.isNotBlank() && !args.has(key)) {
                return AiError.ToolArgument(tool.name, "缺少必填参数：$key")
            }
        }
        return null
    }

    private fun failed(request: ToolRequest, error: AiError, start: Long) = ToolResult(
        callId = request.callId,
        ok = false,
        resultJson = "",
        error = error,
        durationMs = System.currentTimeMillis() - start
    )
}
