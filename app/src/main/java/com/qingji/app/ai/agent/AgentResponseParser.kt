package com.qingji.app.ai.agent

import org.json.JSONArray
import org.json.JSONObject

/** 最终结果解析：只允许五类可信度标签，禁止把推断包装成事实。 */
class AgentResponseParser {

    fun parse(content: String, hasToolEvidence: Boolean): ParsedAgentReply {
        val warnings = mutableListOf<String>()
        val root = try {
            JSONObject(extractJsonBlock(content))
        } catch (e: Exception) {
            warnings.add("模型未按结构化 JSON 输出，已按普通文本展示")
            return ParsedAgentReply(
                answer = content.trim().ifBlank { "AI 没有给出内容" },
                conclusions = emptyList(),
                warnings = warnings
            )
        }

        val answer = root.optString("answer").trim()
        if (answer.isBlank()) warnings.add("模型未给出 answer")

        val conclusions = mutableListOf<AgentConclusion>()
        val array: JSONArray = root.optJSONArray("conclusions") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val text = obj.optString("text").trim()
            if (text.isBlank()) continue

            val rawTrust = obj.optString("trust")
            var trust = TrustLabel.from(rawTrust)
            if (trust == null) {
                warnings.add("未知可信度标签：$rawTrust，已按 UNKNOWN 处理")
                trust = TrustLabel.UNKNOWN
            }

            if ((trust == TrustLabel.FACT || trust == TrustLabel.CALCULATED) && !hasToolEvidence) {
                warnings.add("没有工具证据，不能标为 $trust，已改为 UNKNOWN：$text")
                trust = TrustLabel.UNKNOWN
            }

            val dataRange = obj.optString("dataRange").trim().takeIf { it.isNotBlank() }
            if (dataRange == null) warnings.add("结论缺少数据时间范围：$text")

            conclusions.add(AgentConclusion(text, trust, dataRange))
        }

        return ParsedAgentReply(
            answer = answer.ifBlank { content.trim().ifBlank { "AI 没有给出内容" } },
            conclusions = conclusions,
            warnings = warnings
        )
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("no json object")
        return text.substring(start, end + 1)
    }
}

data class ParsedAgentReply(
    val answer: String,
    val conclusions: List<AgentConclusion>,
    val warnings: List<String>
)
