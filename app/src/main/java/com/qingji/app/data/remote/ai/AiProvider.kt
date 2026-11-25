package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig

/**
 * AI 服务提供商抽象。
 *
 * B01 只定义普通生成、连接测试与能力声明。
 * SSE 流式生成在 B02 扩展，Tool Calling 在 B03 扩展，不在本接口中放空实现。
 */
interface AiProvider {
    /**
     * 一次性生成文本结果。图片输入通过 [AiMessage.userImage] 传入。
     * 失败时抛出具体的 [AiError] 子类。
     */
    suspend fun generate(request: AiRequest): AiResponse

    /** 连接测试；必须返回实测能力，不能只返回 success/failure。 */
    suspend fun testConnection(config: AiConfig, key: String): ConnectionResult

    /** 基于配置与连接测试缓存声明能力。 */
    fun capabilities(config: AiConfig): AiProviderCapabilities
}
