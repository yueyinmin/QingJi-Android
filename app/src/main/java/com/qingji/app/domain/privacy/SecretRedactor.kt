package com.qingji.app.domain.privacy

/**
 * ============================================================
 * 阶段 K：敏感信息脱敏器
 * ------------------------------------------------------------
 * 为什么要有它：
 * 1. 上游 AI 服务返回的错误正文，有时会把请求里的东西回显出来（401/400 的 body）；
 * 2. 这类正文会被拼进 [com.qingji.app.data.remote.ai.AiError] 的 message，
 *    一路显示到聊天界面、甚至落进聊天记录；
 * 3. 日志和错误提示都不允许出现 Key、Token、密码、URL 里的账号密码。
 *
 * 规矩：
 * - 纯函数、无 IO、不依赖 Android，任何层都能直接调；
 * - 宁可多打码，不可漏打码：看着像密钥的一律打码；
 * - 只用于「给人和日志看」的文本，绝不用于要回传给服务商的请求体。
 * ============================================================
 */
object SecretRedactor {

    const val MASK = "***"

    /** 错误提示默认截断长度；太长的上游正文没必要整段展示。 */
    const val DEFAULT_MAX_LENGTH = 160

    /** Bearer 之后的整段 token。 */
    private val bearerPattern = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._\\-]{4,}")

    /** OpenAI 风格的 sk- / sk-proj- 开头的 Key。 */
    private val skPattern = Regex("\\bsk-[A-Za-z0-9_\\-]{4,}")

    /**
     * key=value / token: value 形式。
     * 覆盖 api_key / apiKey / access_token / token / secret / password 等常见写法，
     * URL 查询串（?key=xxx&…）也在这个模式里。
     */
    private val keyValuePattern = Regex(
        "(?i)\\b(api[_-]?key|access[_-]?token|refresh[_-]?token|auth[_-]?token|" +
            "token|secret|password|passwd|pwd)\\b\\s*[=:]\\s*[\"']?[^\\s\"'&,;)]+"
    )

    /**
     * 单独一个 key 的情况（很多兼容代理用 ?key=xxx）。
     * 要求值至少 8 位，避免把「key: 键盘」这类正常文字也打码。
     * 必须放在 [keyValuePattern] 之后执行：先被强关键字处理过的片段，值已经是 ***，不会再命中。
     */
    private val bareKeyPattern = Regex(
        "(?i)\\b(key)\\b\\s*[=:]\\s*[\"']?[^\\s\"'&,;)]{8,}"
    )

    /** URL 里的 user:password@host。 */
    private val urlUserInfoPattern = Regex(
        "([a-z][a-z0-9+.-]*://)[^/\\s:@]+:[^/\\s@]+@",
        RegexOption.IGNORE_CASE
    )

    /**
     * 把文本里像密钥的片段换成 [MASK]，并按 [maxLength] 截断。
     * 截断放在脱敏之后，避免把一段 token 截成半截而绕过识别。
     */
    fun redact(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        if (text.isEmpty()) return text
        var out = text
        out = bearerPattern.replace(out) { "Bearer $MASK" }
        out = skPattern.replace(out) { "sk-$MASK" }
        out = keyValuePattern.replace(out) { match -> "${match.groupValues[1]}=$MASK" }
        out = bareKeyPattern.replace(out) { match -> "${match.groupValues[1]}=$MASK" }
        out = urlUserInfoPattern.replace(out) { match -> "${match.groupValues[1]}$MASK@" }
        out = maskLongTokens(out)
        if (maxLength > 0 && out.length > maxLength) {
            out = out.take(maxLength) + "…"
        }
        return out
    }

    /** 这段文本里是否含有会被打码的内容；测试与自检用。 */
    fun containsSecret(text: String): Boolean = redact(text, maxLength = 0) != text

    /**
     * 长串打码：长度 ≥ 32 且同时含字母和数字的连续串，视为疑似密钥/哈希。
     * 单独处理而不是正则，是为了避免误伤中文与普通长单词。
     */
    private fun maskLongTokens(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (isTokenChar(text[i])) {
                var j = i
                while (j < text.length && isTokenChar(text[j])) j++
                val token = text.substring(i, j)
                sb.append(if (looksLikeSecret(token)) MASK else token)
                i = j
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun isTokenChar(c: Char): Boolean =
        (c.isLetterOrDigit() && c.code < 128) || c in "+/=_-"

    private fun looksLikeSecret(token: String): Boolean {
        if (token.length < 32) return false
        return token.any { it.isDigit() } && token.any { it.isLetter() }
    }
}
