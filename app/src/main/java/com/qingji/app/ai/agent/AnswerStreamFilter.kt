package com.qingji.app.ai.agent

/**
 * 流式 answered 文本过滤器。
 *
 * Agent 要求模型输出 JSON（answer + conclusions），直接把 SSE 片段显示给用户
 * 会露出半截 JSON。这里只把 JSON 里 "answer" 字段的内容逐段放出去，
 * 遇到转义字符按原样保留给上层解析，不提前解码。
 */
internal class AnswerStreamFilter {
    private val buffer = StringBuilder()
    private var emitted = 0

    /** 喂入一段原始 SSE 文本，返回本次新增的 answer 可见文本。 */
    fun accept(delta: String): String {
        buffer.append(delta)
        val visible = extractAnswer(buffer.toString())
        val newPart = if (visible.length > emitted) visible.substring(emitted) else ""
        emitted = visible.length
        return newPart
    }

    /** 整段结束时调用；若模型没按 JSON 输出，则退回原文避免空白回答。 */
    fun finish(): String {
        val visible = extractAnswer(buffer.toString())
        if (visible.isNotBlank()) return visible
        val raw = buffer.toString().trim()
        return raw.takeIf { it.isNotBlank() && !it.startsWith("{") }.orEmpty()
    }

    companion object {
        /** 允许 JSON 未闭合：找到 answer 起始引号后一直取到结尾或结束引号。 */
        fun extractAnswer(raw: String): String {
            val keyIndex = raw.indexOf("\"answer\"")
            if (keyIndex < 0) return ""
            val colon = raw.indexOf(':', keyIndex + 8)
            if (colon < 0) return ""
            var cursor = colon + 1
            while (cursor < raw.length && raw[cursor].isWhitespace()) cursor++
            if (cursor >= raw.length || raw[cursor] != '"') return ""

            val out = StringBuilder()
            cursor++
            var escaped = false
            while (cursor < raw.length) {
                val ch = raw[cursor]
                if (escaped) {
                    out.append(ch)
                    escaped = false
                    cursor++
                    continue
                }
                if (ch == '\\') {
                    out.append(ch)
                    escaped = true
                    cursor++
                    continue
                }
                if (ch == '"') break
                out.append(ch)
                cursor++
            }
            return out.toString()
        }
    }
}
