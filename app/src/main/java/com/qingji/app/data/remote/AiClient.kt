package com.qingji.app.data.remote

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.AiMessage
import com.qingji.app.data.remote.ai.AiPrompts
import com.qingji.app.data.remote.ai.AiProvider
import com.qingji.app.data.remote.ai.AiRecognitionParser
import com.qingji.app.data.remote.ai.AiRequest
import com.qingji.app.data.remote.ai.OpenAiCompatibleProvider
import kotlin.coroutines.cancellation.CancellationException

/** AI 返回的一个食物条目（保留旧包名，兼容现有界面与备份代码）。 */
data class AiFoodItem(
    val name: String,
    val amountText: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
    val confidence: Double
)

/** 一次识别的完整结果。 */
data class AiRecognitionResult(
    val mealType: String,
    val items: List<AiFoodItem>,
    val totalKcal: Double,
    val advice: String,
    val needClarify: Boolean
)

/** 旧错误类型；新代码使用 [AiError]，这里只做兼容。 */
class AiException(message: String) : Exception(message)

/**
 * AI 客户端兼容门面。
 *
 * B01 起网络协议由 [AiProvider] / [OpenAiCompatibleProvider] 实现，
 * 本类只保留旧的公开方法签名，避免影响历史调用方。
 * 新代码请直接通过 QingJiRepository 或 AiProvider 调用。
 */
@Deprecated("请通过 QingJiRepository 或 AiProvider 调用")
class AiClient(
    private val provider: AiProvider = OpenAiCompatibleProvider()
) {
    suspend fun recognizeText(
        config: AiConfig,
        key: String,
        input: String,
        extraRule: String = ""
    ): Result<AiRecognitionResult> = try {
        validateText(config, key)
        val systemContent = if (extraRule.isBlank()) {
            AiPrompts.SYSTEM_PROMPT
        } else {
            AiPrompts.SYSTEM_PROMPT + "\n\n用户偏好（优先遵守）：\n" + extraRule
        }
        val response = provider.generate(
            AiRequest(
                config = config,
                key = key,
                messages = listOf(AiMessage.system(systemContent), AiMessage.user(input)),
                temperature = 0.1,
                forceJsonObject = true
            )
        )
        Result.success(AiRecognitionParser.parse(response.content))
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: AiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
    }

    suspend fun recognizeImage(
        config: AiConfig,
        key: String,
        base64Jpeg: String,
        extraRule: String = ""
    ): Result<AiRecognitionResult> = try {
        validateVision(config, key)
        val systemContent = if (extraRule.isBlank()) {
            AiPrompts.VISION_PROMPT
        } else {
            AiPrompts.VISION_PROMPT + "\n\n用户偏好（优先遵守）：\n" + extraRule
        }
        val response = provider.generate(
            AiRequest(
                config = config,
                key = key,
                messages = listOf(
                    AiMessage.system(systemContent),
                    AiMessage.userImage("识别这份食物并估算份量", base64Jpeg)
                ),
                temperature = 0.1,
                forceJsonObject = false
            )
        )
        Result.success(AiRecognitionParser.parse(response.content))
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: AiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
    }

    suspend fun analyze(config: AiConfig, key: String, facts: String): Result<String> = try {
        validateText(config, key)
        val response = provider.generate(
            AiRequest(
                config = config,
                key = key,
                messages = listOf(AiMessage.system(AiPrompts.ANALYSIS_RULE), AiMessage.user(facts)),
                temperature = config.temperature,
                forceJsonObject = false
            )
        )
        val text = response.content.replace("```json", "").replace("```", "").trim()
        Result.success(text.ifBlank { "AI 没有给出内容，换个时间范围再试" })
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: Exception) {
        Result.failure(AiException("连不上 AI 服务：${e.message?.take(100) ?: "未知错误"}"))
    }

    suspend fun testConnection(config: AiConfig, key: String): Result<String> = try {
        validateText(config, key)
        val result = provider.testConnection(config, key)
        if (result.success) Result.success(result.message)
        else Result.failure(AiException(result.message))
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiError) {
        Result.failure(e.toLegacy())
    } catch (e: Exception) {
        Result.failure(AiException("连不上：${e.message?.take(100) ?: "未知错误"}"))
    }

    private fun validateText(config: AiConfig, key: String) {
        if (config.baseUrl.isBlank()) throw AiException("还没填 Base URL，去设置 → AI 服务配置")
        if (key.isBlank()) throw AiException("还没填 API Key，去设置 → AI 服务配置")
        if (config.textModel.isBlank()) throw AiException("还没填文本模型名，去设置 → AI 服务配置")
    }

    private fun validateVision(config: AiConfig, key: String) {
        if (config.baseUrl.isBlank()) throw AiException("还没填 Base URL，去设置 → AI 服务配置")
        if (key.isBlank()) throw AiException("还没填 API Key，去设置 → AI 服务配置")
        if (config.visionModel.isBlank()) {
            throw AiException("还没填视觉模型名（要支持 image_url），去设置 → AI 服务配置")
        }
    }

    private fun AiError.toLegacy(): AiException =
        AiException(message ?: code)
}
