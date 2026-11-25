package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig

/** AI 请求的输入片段。音频输入属于后续阶段，本模型暂不声明。 */
sealed interface AiContentPart {
    data class Text(val text: String) : AiContentPart

    data class ImageBase64(
        val base64: String,
        val mime: String = "image/jpeg"
    ) : AiContentPart
}

/** 工具调用返回项。arguments 是模型生成的原始 JSON 字符串，由上层校验。 */
data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

/** 工具定义，parametersJson 必须是 JSON Schema 对象字符串。 */
data class AiToolDefinition(
    val name: String,
    val description: String,
    val parametersJson: String
)

sealed interface AiToolChoice {
    data object Auto : AiToolChoice
    data object None : AiToolChoice
    data object Required : AiToolChoice
    data class Named(val name: String) : AiToolChoice
}

/** 统一消息模型，屏蔽具体供应商的 messages 结构差异。 */
data class AiMessage(
    val role: String,
    val parts: List<AiContentPart>,
    val toolCallId: String? = null,
    val toolCalls: List<AiToolCall> = emptyList()
) {
    companion object {
        fun system(text: String) = AiMessage(
            role = "system",
            parts = listOf(AiContentPart.Text(text))
        )

        fun user(text: String) = AiMessage(
            role = "user",
            parts = listOf(AiContentPart.Text(text))
        )

        fun userImage(
            text: String,
            base64: String,
            mime: String = "image/jpeg"
        ) = AiMessage(
            role = "user",
            parts = listOf(
                AiContentPart.Text(text),
                AiContentPart.ImageBase64(base64, mime)
            )
        )

        /** 把助手侧 tool_calls 回传给模型，用于第二阶段生成最终答案。 */
        fun assistantToolCalls(calls: List<AiToolCall>) = AiMessage(
            role = "assistant",
            parts = emptyList(),
            toolCalls = calls
        )

        /** 把工具执行结果按 callId 回传，防止结果串位。 */
        fun toolResult(callId: String, content: String) = AiMessage(
            role = "tool",
            parts = listOf(AiContentPart.Text(content)),
            toolCallId = callId
        )
    }
}

/** B02/B03 后的 Provider 请求模型。旧调用方无需传 tools / toolChoice。 */
data class AiRequest(
    val config: AiConfig,
    val key: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.2,
    val maxTokens: Int? = null,
    val forceJsonObject: Boolean = false,
    val tools: List<AiToolDefinition> = emptyList(),
    val toolChoice: AiToolChoice = AiToolChoice.Auto
)

/** Token 统计。供应商不返回时字段保持 null，不伪造 0。 */
data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

/** Provider 的普通生成结果。工具调用时 content 可能为空。 */
data class AiResponse(
    val content: String,
    val model: String? = null,
    val usage: TokenUsage? = null,
    val toolCalls: List<AiToolCall> = emptyList(),
    val finishReason: String? = null
)

/** 统一流式事件。 */
sealed interface AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent
    data class ToolCallStarted(val callId: String, val name: String) : AiStreamEvent
    data class ToolArgumentsDelta(val callId: String, val fragment: String) : AiStreamEvent
    data class ToolCallCompleted(val callId: String) : AiStreamEvent
    data class Completed(val usage: TokenUsage? = null) : AiStreamEvent
    data class Failed(val error: AiError) : AiStreamEvent
}

/** 连接测试结果，同时返回实测能力，不靠供应商名称猜测。 */
data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val capabilities: AiProviderCapabilities
)
