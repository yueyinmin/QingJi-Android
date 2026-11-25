package com.qingji.app.data.remote.ai

/**
 * 模型能力声明。
 *
 * B01 约定：
 * - chat / imageInput 由适配器结合配置与连接测试声明。
 * - streaming / toolCalling / streamingToolCalling / jsonSchema 默认关闭，
 *   等 B02 / B03 真实验证后再开启。
 * - 不允许仅凭 provider 字符串推断能力。
 */
data class AiProviderCapabilities(
    val chat: Boolean,
    val streaming: Boolean = false,
    val toolCalling: Boolean = false,
    val streamingToolCalling: Boolean = false,
    val jsonSchema: Boolean = false,
    val imageInput: Boolean,
    val audioInput: Boolean = false
) {
    companion object {
        /** 完全不可用，用于配置缺失或连接失败时的明确表达。 */
        val NONE = AiProviderCapabilities(
            chat = false,
            imageInput = false
        )
    }
}
