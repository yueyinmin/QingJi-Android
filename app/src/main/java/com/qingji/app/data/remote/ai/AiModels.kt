package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig

/** AI 请求的输入片段。B01 只声明文本与图片，音频留给后续阶段。 */
sealed interface AiContentPart {
    data class Text(val text: String) : AiContentPart

    data class ImageBase64(
        val base64: String,
        val mime: String = "image/jpeg"
    ) : AiContentPart
}

/** 统一消息模型，屏蔽具体供应商的 messages 结构差异。 */
data class AiMessage(
    val role: String,
    val parts: List<AiContentPart>
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
    }
}

/**
 * B01 的 Provider 请求模型。
 *
 * 这里不包含 stream、tools、tool_choice 等字段：
 * SSE 与 Tool Calling 分别属于 B02 / B03，本阶段只建立可稳定演进的基础抽象。
 */
data class AiRequest(
    val config: AiConfig,
    val key: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.2,
    val maxTokens: Int? = null,
    val forceJsonObject: Boolean = false
)

/** Token 统计。供应商不返回时字段保持 null，不伪造 0。 */
data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

/** Provider 的普通生成结果。 */
data class AiResponse(
    val content: String,
    val model: String? = null,
    val usage: TokenUsage? = null
)

/** 连接测试结果，同时返回实测能力，不靠供应商名称猜测。 */
data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val capabilities: AiProviderCapabilities
)
