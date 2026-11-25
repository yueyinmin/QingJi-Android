package com.qingji.app.data.remote.ai

import com.qingji.app.data.local.entity.AiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AI 服务提供商抽象。
 *
 * B01：普通生成、连接测试、能力声明。
 * B02：SSE 流式文本与流式工具参数。
 * B03：工具调用请求/结果回传。
 */
interface AiProvider {
    /**
     * 一次性生成文本结果。图片输入通过 [AiMessage.userImage] 传入。
     * 失败时抛出具体的 [AiError] 子类。
     */
    suspend fun generate(request: AiRequest): AiResponse

    /**
     * 流式生成。默认实现用于兼容 B01 的简单 Provider 测试替身；
     * OpenAI 兼容适配器会覆盖它。
     */
    fun stream(request: AiRequest): Flow<AiStreamEvent> = flow {
        throw AiError.ModelUnavailable("当前 Provider 不支持流式输出")
    }

    /** 连接测试；必须返回实测能力，不能只返回 success/failure。 */
    suspend fun testConnection(config: AiConfig, key: String): ConnectionResult

    /** 基于配置与连接测试缓存声明能力。 */
    fun capabilities(config: AiConfig): AiProviderCapabilities
}
