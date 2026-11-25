package com.qingji.app.ai.agent

import com.qingji.app.data.remote.ai.AiMessage
import com.qingji.app.data.remote.ai.AiRequest
import com.qingji.app.data.remote.ai.AiToolChoice
import com.qingji.app.data.remote.ai.AiToolDefinition

/**
 * C02 上下文构造器。
 *
 * 只注入当前任务必需的信息：日期、任务类型、页面入口、少量摘要和工具清单。
 * 不把聊天全文、全部业务数据或推断塞进上下文。
 */
class ContextBuilder(
    private val maxSummaryChars: Int = 1200
) {
    fun build(request: AgentRequest, tools: List<AiToolDefinition>): AiRequest {
        val toolLines = if (tools.isEmpty()) {
            "当前没有可用工具；只能基于已给上下文回答，缺失数据必须标 UNKNOWN。"
        } else {
            tools.joinToString("\n") { "- ${it.name}：${it.description}" }
        }

        val summary = request.recentSummary?.trim().takeUnless { it.isNullOrBlank() }
            ?.let { truncate(it, maxSummaryChars) }
            ?: "[UNKNOWN] 未提供额外上下文摘要；不得猜测缺失数据。"

        val pageLine = request.page?.takeIf { it.isNotBlank() }?.let { "页面入口：$it" } ?: "页面入口：未提供"

        val system = buildString {
            append("你是轻记 App 的本地 AI 管家。只回答与当前任务相关的问题。\n")
            append("当前日期：").append(request.date).append('\n')
            append("任务类型：").append(request.taskType).append('\n')
            append(pageLine).append('\n')
            append("可用只读工具：\n").append(toolLines).append("\n\n")
            append("上下文摘要（只代表给定范围）：\n").append(summary).append("\n\n")
            append("【硬规则】\n")
            append("1. 只能调用上列只读工具；不得写库，不得要求宿主执行写操作。\n")
            append("2. 需要数据时先调用工具；不要把推断当成事实。\n")
            append("3. 事实只能来自正式记录或工具结果，标 FACT。\n")
            append("4. 聚合/百分比只能来自本地计算结果，标 CALCULATED；不要自行编造计算结果。\n")
            append("5. 推断标 INFERRED，建议标 SUGGESTION；数据不足标 UNKNOWN，并说明缺什么与时间范围。\n")
            append("6. 每个重要结论都要给数据时间范围，例如“今天”“近 7 天”。\n")
            append("7. 禁止诊断、治疗、用药、疾病、处方类表述。\n\n")
            append("【输出契约】只输出 JSON：\n")
            append("{\"answer\":\"给用户看的中文回答\"," +
                "\"conclusions\":[{\"text\":\"结论\",\"trust\":\"FACT|CALCULATED|INFERRED|UNKNOWN|SUGGESTION\"," +
                "\"dataRange\":\"今天|近7天|具体范围\"}]}\n")
            append("没有可靠数据时 conclusions 可以给出 UNKNOWN；不要为了凑数编造结论。")
        }.trim()

        return AiRequest(
            config = request.config,
            key = request.key,
            messages = listOf(
                AiMessage.system(system),
                AiMessage.user("用户问题：${request.question.trim()}")
            ),
            temperature = 0.2,
            forceJsonObject = true,
            tools = tools,
            toolChoice = AiToolChoice.Auto
        )
    }

    private fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max) + "…（已截断）"
}
