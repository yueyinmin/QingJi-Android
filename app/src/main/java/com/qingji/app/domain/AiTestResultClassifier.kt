package com.qingji.app.domain

/**
 * AI 测试连接结果分类（工单 §7）。
 * 纯函数：把 AiClient 抛出的中文错误 message 归一到 8 类之一，供 UI 显示稳定标签。
 * 不重复实现请求逻辑——只做错误文本到类别的映射。
 */
object AiTestResultClassifier {

    enum class Category(val label: String) {
        OK("连接正常"),
        NETWORK("网络连接失败"),
        TIMEOUT("请求超时"),
        INVALID_KEY("API Key 无效"),
        MODEL_MISSING("模型不存在"),
        QUOTA("额度不足或限流"),
        SERVER("服务端异常"),
        BAD_FORMAT("返回数据格式异常"),
        UNKNOWN("其他失败")
    }

    /** 成功结果固定归类。 */
    fun classify(success: Boolean, message: String): Category {
        if (success) return Category.OK
        val m = message.lowercase()
        return when {
            "超时" in message || "timeout" in m -> Category.TIMEOUT
            "key" in m && ("无效" in message || "过期" in message || "invalid" in m) -> Category.INVALID_KEY
            "模型不存在" in message || "404" in message -> Category.MODEL_MISSING
            "额度" in message || "余额" in message || "限流" in message ||
                "429" in message || "402" in message || "quota" in m -> Category.QUOTA
            "连不上" in message || "网络" in message || "socket" in m ||
                "unknownhost" in m || "failed to connect" in m -> Category.NETWORK
            "服务商暂时不可用" in message ||
                listOf("500", "501", "502", "503", "504", "505").any { it in message } -> Category.SERVER
            "格式" in message || "json" in m || "解析" in message -> Category.BAD_FORMAT
            else -> Category.UNKNOWN
        }
    }
}
