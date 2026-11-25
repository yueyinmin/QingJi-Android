package com.qingji.app.ai.agent

import com.qingji.app.ai.ToolExecutor
import com.qingji.app.ai.ToolRegistry
import com.qingji.app.ai.ToolRequest
import com.qingji.app.ai.ToolResult
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.AiMessage
import com.qingji.app.data.remote.ai.AiProvider
import com.qingji.app.data.remote.ai.AiRequest
import com.qingji.app.data.remote.ai.AiResponse
import com.qingji.app.data.remote.ai.AiStreamEvent
import com.qingji.app.data.remote.ai.AiToolCall
import com.qingji.app.data.remote.ai.TokenUsage
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

/**
 * C02 只读 Agent。
 *
 * 能力：
 * - ContextBuilder 最小化上下文
 * - 单轮/多轮 tool_call 循环
 * - 多工具按 callId 执行与回传
 * - 轮次/单轮工具数/总工具数上限
 * - callId 与参数指纹去重
 * - 只读白名单校验
 * - 取消透传
 *
 * 不实现写工具；模型要求写库时只能输出 SUGGESTION。
 */
class QingJiAgent(
    private val provider: AiProvider,
    private val registry: ToolRegistry,
    private val executor: ToolExecutor,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val responseParser: AgentResponseParser = AgentResponseParser()
) {
    /** 一次性生成。保留原有调用方与测试契约。 */
    suspend fun run(request: AgentRequest): AgentResult =
        runWith(request) { provider.generate(it) }

    /**
     * 流式生成。工具调用轮次仍走 generate 语义（由 stream 事件拼装），
     * 最终 answer 片段通过 [onText] 回调给 UI，立即显示。
     */
    suspend fun runStreaming(
        request: AgentRequest,
        onText: suspend (String) -> Unit
    ): AgentResult = runWith(request) { streamOnce(it, onText) }

    private suspend fun streamOnce(
        request: AiRequest,
        onText: suspend (String) -> Unit
    ): AiResponse {
        val filter = AnswerStreamFilter()
        val rawBuffer = StringBuilder()
        val calls = LinkedHashMap<String, AiToolCall>()
        var usage: TokenUsage? = null
        var streamError: AiError? = null

        provider.stream(request).collect { event ->
            when (event) {
                is AiStreamEvent.TextDelta -> {
                    rawBuffer.append(event.text)
                    val visible = filter.accept(event.text)
                    if (visible.isNotEmpty()) onText(visible)
                }
                is AiStreamEvent.ToolCallStarted -> calls.putIfAbsent(
                    event.callId,
                    AiToolCall(event.callId, event.name, "")
                )
                is AiStreamEvent.ToolArgumentsDelta -> {
                    val previous = calls[event.callId]
                    val name = previous?.name.orEmpty()
                    calls[event.callId] = AiToolCall(
                        event.callId,
                        name,
                        (previous?.arguments.orEmpty()) + event.fragment
                    )
                }
                is AiStreamEvent.ToolCallCompleted -> Unit
                is AiStreamEvent.Completed -> usage = event.usage
                is AiStreamEvent.Failed -> streamError = event.error
            }
        }

        streamError?.let { throw it }
        var raw = rawBuffer.toString()
        if (raw.isBlank()) raw = filter.finish()
        return AiResponse(
            content = raw,
            usage = usage,
            toolCalls = calls.values.toList(),
            finishReason = if (calls.isEmpty()) "stop" else "tool_calls"
        )
    }

    private suspend fun runWith(
        request: AgentRequest,
        generate: suspend (AiRequest) -> AiResponse
    ): AgentResult {
        if (request.question.isBlank()) throw AiError.InvalidResponse("用户问题不能为空")

        val maxRounds = request.maxRounds.coerceAtLeast(1)
        val maxToolsPerRound = request.maxToolsPerRound.coerceAtLeast(1)
        val maxTotalTools = request.maxTotalTools.coerceAtLeast(1)

        val readonlyDefinitions = registry.all()
            .filter { it.readOnly }
            .map { com.qingji.app.data.remote.ai.AiToolDefinition(it.name, it.description, it.parametersJson) }
        val initial = contextBuilder.build(request, readonlyDefinitions)
        val messages = initial.messages.toMutableList()
        val executions = mutableListOf<AgentToolExecution>()
        val warnings = mutableListOf<String>()
        val seenCallIds = HashSet<String>()
        val seenFingerprints = HashSet<String>()
        var totalTools = 0
        // 阶段 L：多轮工具调用会把用量分散在每次响应里，这里逐轮累加
        var totalUsage: TokenUsage? = null

        for (round in 1..maxRounds) {
            val response = generate(initial.copy(messages = messages.toList()))
            val calls = response.toolCalls
            totalUsage = addUsage(totalUsage, response.usage)

            if (calls.isEmpty()) {
                val parsed = responseParser.parse(
                    response.content,
                    hasToolEvidence = executions.any { it.ok }
                )
                return AgentResult(
                    answer = parsed.answer,
                    conclusions = parsed.conclusions,
                    rounds = round,
                    toolExecutions = executions,
                    warnings = warnings + parsed.warnings,
                    usage = totalUsage
                )
            }

            if (calls.size > maxToolsPerRound) {
                throw AiError.InvalidResponse("单轮工具调用数 ${calls.size} 超过上限 $maxToolsPerRound")
            }
            totalTools += calls.size
            if (totalTools > maxTotalTools) {
                throw AiError.InvalidResponse("单任务工具调用总数超过上限 $maxTotalTools")
            }

            val prepared = prepareCalls(calls, round, seenCallIds, seenFingerprints, warnings)
            val accepted = prepared.filter { it.includeInProtocol }

            if (accepted.isNotEmpty()) {
                messages.add(AiMessage.assistantToolCalls(accepted.map { it.call }))

                val executed = executor.execute(accepted.mapNotNull { it.toolRequest })
                    .associateBy { it.callId }

                accepted.forEach { item ->
                    val result = item.errorResult ?: executed[item.call.id]
                        ?: ToolResult(
                            callId = item.call.id,
                            ok = false,
                            resultJson = "",
                            error = AiError.ToolArgument(item.call.name, "工具未返回结果")
                        )
                    messages.add(AiMessage.toolResult(item.call.id, toolResultContent(item.call.name, result)))
                    executions.add(
                        AgentToolExecution(
                            callId = item.call.id,
                            name = item.call.name,
                            argumentsJson = item.call.arguments,
                            ok = result.ok,
                            errorCode = result.error?.code,
                            durationMs = result.durationMs,
                            warning = item.warning
                        )
                    )
                }
            } else {
                warnings.add("本轮工具调用全部无效，已要求模型改用可用工具或回答 UNKNOWN")
                messages.add(
                    AiMessage.user("上一轮工具调用全部无效。只能使用已注册只读工具；否则输出 UNKNOWN 结论。")
                )
            }
        }

        throw AiError.InvalidResponse("Agent 达到最大交互轮数 $maxRounds，未生成最终答案")
    }

    /**
     * 阶段 L：两轮 Token 用量相加。
     * 任一字段缺失就保持 null —— 不把「上游没给」当 0 累加，
     * 否则会得出「明明没返回数据，总量却是 0」的假数字。
     */
    private fun addUsage(acc: TokenUsage?, next: TokenUsage?): TokenUsage? {
        if (next == null) return acc
        if (acc == null) return next
        fun sum(a: Int?, b: Int?): Int? = if (a == null || b == null) null else a + b
        return TokenUsage(
            promptTokens = sum(acc.promptTokens, next.promptTokens),
            completionTokens = sum(acc.completionTokens, next.completionTokens),
            totalTokens = sum(acc.totalTokens, next.totalTokens)
        )
    }

    private fun prepareCalls(
        calls: List<AiToolCall>,
        round: Int,
        seenCallIds: MutableSet<String>,
        seenFingerprints: MutableSet<String>,
        warnings: MutableList<String>
    ): List<PreparedCall> {
        val prepared = mutableListOf<PreparedCall>()

        calls.forEachIndexed { index, original ->
            val callId = original.id.takeIf { it.isNotBlank() } ?: "agent_r${round}_${index}"
            if (callId != original.id) {
                warnings.add("工具调用缺少 callId，已生成 $callId")
            }

            if (!seenCallIds.add(callId)) {
                warnings.add("重复 callId：$callId，已跳过")
                prepared.add(
                    PreparedCall(
                        call = original.copy(id = callId),
                        includeInProtocol = false,
                        warning = "重复 callId"
                    )
                )
                return@forEachIndexed
            }

            val fingerprint = "${original.name}|${canonicalArguments(original.arguments)}"
            if (!seenFingerprints.add(fingerprint)) {
                prepared.add(
                    PreparedCall(
                        call = original.copy(id = callId),
                        includeInProtocol = true,
                        errorResult = errorResult(
                            callId,
                            original.name,
                            AiError.ToolArgument(original.name, "重复参数指纹，未执行")
                        ),
                        warning = "重复参数指纹"
                    )
                )
                return@forEachIndexed
            }

            val tool = registry.find(original.name)
            if (tool == null) {
                prepared.add(
                    PreparedCall(
                        call = original.copy(id = callId),
                        includeInProtocol = true,
                        errorResult = errorResult(
                            callId,
                            original.name,
                            AiError.ToolArgument(original.name, "工具未注册")
                        ),
                        warning = "工具未注册"
                    )
                )
                return@forEachIndexed
            }

            if (!tool.readOnly) {
                prepared.add(
                    PreparedCall(
                        call = original.copy(id = callId),
                        includeInProtocol = true,
                        errorResult = errorResult(
                            callId,
                            original.name,
                            AiError.PermissionDenied(original.name)
                        ),
                        warning = "写入工具被拒绝"
                    )
                )
                return@forEachIndexed
            }

            prepared.add(
                PreparedCall(
                    call = original.copy(id = callId),
                    toolRequest = ToolRequest(callId, original.name, original.arguments)
                )
            )
        }

        return prepared
    }

    private fun canonicalArguments(arguments: String): String = try {
        JSONObject(arguments).toString()
    } catch (_: Exception) {
        arguments.trim()
    }

    private fun errorResult(callId: String, name: String, error: AiError) = ToolResult(
        callId = callId,
        ok = false,
        resultJson = "",
        error = error
    )

    private fun toolResultContent(toolName: String, result: ToolResult): String {
        if (!result.ok) {
            val error = result.error ?: AiError.Unknown("工具执行失败")
            return JSONObject()
                .put("tool", toolName)
                .put("callId", result.callId)
                .put("ok", false)
                .put("source", "UNKNOWN")
                .put(
                    "error",
                    JSONObject()
                        .put("code", error.code)
                        .put("message", error.message ?: error.code)
                        .put("toolName", toolName)
                        .put("callId", result.callId)
                        .put("retryable", error.retryable)
                )
                .toString()
        }

        return try {
            JSONObject(result.resultJson).put("callId", result.callId).toString()
        } catch (_: Exception) {
            JSONObject()
                .put("callId", result.callId)
                .put("ok", true)
                .put("source", "FACT")
                .put("raw", result.resultJson)
                .toString()
        }
    }

    private data class PreparedCall(
        val call: AiToolCall,
        val toolRequest: ToolRequest? = null,
        val errorResult: ToolResult? = null,
        val includeInProtocol: Boolean = true,
        val warning: String? = null
    )
}
