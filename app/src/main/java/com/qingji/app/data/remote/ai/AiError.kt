package com.qingji.app.data.remote.ai

/**
 * AI 服务统一错误模型。
 *
 * 目标：UI 与 Agent 可以按类型处理，而不是把所有失败都显示成“请求失败”。
 * 部分类型（StreamInterrupted / ToolArgument / PermissionDenied / VersionConflict）
 * 在 B01 不会产生，但提前定义，避免 B02/B03 改调用方签名。
 */
sealed class AiError(
    open val code: String,
    message: String,
    open val retryable: Boolean,
    cause: Throwable? = null
) : Exception(message, cause) {

    class Network(cause: Throwable? = null) : AiError(
        code = "NETWORK",
        message = "连不上 AI 服务，请检查网络",
        retryable = true,
        cause = cause
    )

    class InvalidApiKey : AiError(
        code = "INVALID_API_KEY",
        message = "API Key 无效或没有权限",
        retryable = false
    )

    class RateLimited(val retryAfterSec: Long? = null) : AiError(
        code = "RATE_LIMITED",
        message = "调用太频繁，请稍后再试",
        retryable = true
    )

    class InsufficientBalance : AiError(
        code = "INSUFFICIENT_BALANCE",
        message = "AI 服务余额不足",
        retryable = false
    )

    class ModelUnavailable(val detail: String = "") : AiError(
        code = "MODEL_UNAVAILABLE",
        message = "模型暂不可用",
        retryable = true
    )

    class Timeout : AiError(
        code = "TIMEOUT",
        message = "AI 响应超时，请重试",
        retryable = true
    )

    /** B02 预留：流式连接中断。 */
    class StreamInterrupted : AiError(
        code = "STREAM_INTERRUPTED",
        message = "AI 输出中断",
        retryable = true
    )

    class InvalidResponse(val detail: String = "") : AiError(
        code = "INVALID_RESPONSE",
        message = "AI 返回内容无法解析",
        retryable = false
    )

    /** B03 预留：工具参数错误。 */
    class ToolArgument(val toolName: String, val detail: String) : AiError(
        code = "TOOL_ARGUMENT",
        message = "工具 $toolName 参数不合法",
        retryable = false
    )

    /** 阶段 E 预留：权限拒绝。 */
    class PermissionDenied(val action: String = "") : AiError(
        code = "PERMISSION_DENIED",
        message = "没有执行该操作的权限",
        retryable = false
    )

    /** 阶段 E 预留：数据版本冲突。 */
    class VersionConflict : AiError(
        code = "VERSION_CONFLICT",
        message = "数据已变化，请刷新后再试",
        retryable = false
    )

    class Cancelled : AiError(
        code = "CANCELLED",
        message = "已取消 AI 请求",
        retryable = false
    )

    class Unknown(val detail: String = "") : AiError(
        code = "UNKNOWN",
        message = "AI 服务异常",
        retryable = false
    )
}
