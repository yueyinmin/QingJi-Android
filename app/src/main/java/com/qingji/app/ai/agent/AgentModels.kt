package com.qingji.app.ai.agent

import com.qingji.app.data.local.entity.AiConfig
import java.time.LocalDate

/** 结论可信度标识，禁止模型把推断包装成事实。 */
enum class TrustLabel {
    FACT,
    CALCULATED,
    INFERRED,
    UNKNOWN,
    SUGGESTION;

    companion object {
        fun from(value: String?): TrustLabel? = entries.firstOrNull { it.name == value?.trim()?.uppercase() }
    }
}

/** C02 Agent 输入。config/key 由调用方从 Repository 读取后传入。 */
data class AgentRequest(
    val config: AiConfig,
    val key: String,
    val question: String,
    val taskType: String = "general",
    val date: LocalDate,
    val page: String? = null,
    val recentSummary: String? = null,
    /** D 阶段图片输入：base64 不写库、不进日志，只在本次请求内传给模型。 */
    val imageBase64: String? = null,
    val imageMime: String = "image/jpeg",
    val maxRounds: Int = 4,
    val maxToolsPerRound: Int = 5,
    val maxTotalTools: Int = 10
)

/** 模型最终结论。dataRange 为空表示模型未给出时间范围，应视为弱证据。 */
data class AgentConclusion(
    val text: String,
    val trust: TrustLabel,
    val dataRange: String? = null
)

/** 工具执行记录，供 UI/调试追踪。 */
data class AgentToolExecution(
    val callId: String,
    val name: String,
    val argumentsJson: String,
    val ok: Boolean,
    val errorCode: String? = null,
    val durationMs: Long = 0,
    val warning: String? = null
)

/** Agent 最终输出。 */
data class AgentResult(
    val answer: String,
    val conclusions: List<AgentConclusion>,
    val rounds: Int,
    val toolExecutions: List<AgentToolExecution>,
    val warnings: List<String> = emptyList()
)
