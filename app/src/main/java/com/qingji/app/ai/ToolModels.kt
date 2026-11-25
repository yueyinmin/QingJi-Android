package com.qingji.app.ai

import com.qingji.app.data.remote.ai.AiError

/** Agent 发给本地工具的一次调用请求。 */
data class ToolRequest(
    val callId: String,
    val name: String,
    val argumentsJson: String
)

/** 工具执行结果，始终带 callId，避免并行结果串位。 */
data class ToolResult(
    val callId: String,
    val ok: Boolean,
    val resultJson: String,
    val error: AiError? = null,
    val durationMs: Long = 0
)

/** 本地业务工具接口。B01/C01 阶段只允许只读工具注册为可执行工具。 */
interface AiTool {
    val name: String
    val description: String
    val parametersJson: String
    val readOnly: Boolean

    suspend fun execute(argumentsJson: String): String
}
