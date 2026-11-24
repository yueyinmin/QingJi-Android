package com.qingji.app.domain

/**
 * 数字键盘录入的纯逻辑（QJ-FOOD-NUMERIC-PAD-V1）。
 *
 * 全部以 String 为输入/输出，保留 "0." 这类中间状态，
 * 避免输入过程中被提前转成 Double 破坏输入体验。
 * 与 UI 无关，便于单测。
 */
object NumericKeypadLogic {

    /** 追加数字：处理前导零与小数位上限。 */
    fun appendDigit(current: String, digit: Char, decimals: Int = 1): String {
        if (!digit.isDigit()) return current
        // 前导零：当前只有 "0" 时直接替换，不出现 "05"
        if (current == "0") return if (decimals > 0 && digit == '0') "0" else digit.toString()
        // 小数位达上限则忽略后续数字
        if (current.contains('.')) {
            val fraction = current.substringAfter('.')
            if (fraction.length >= decimals) return current
        }
        return current + digit
    }

    /** 追加小数点：只允许一个；空串从 "0." 开始。 */
    fun appendDot(current: String, decimals: Int = 1): String {
        if (decimals <= 0 || current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }

    /** 退格：删最后一个字符，空串保持不变。 */
    fun backspace(current: String): String =
        if (current.isEmpty()) current else current.dropLast(1)

    /** 按步长增减，夹紧到 [min, max]；非法当前值按 min 起算。 */
    fun step(current: String, delta: Double, min: Double, max: Double, decimals: Int = 1): String {
        val base = asDouble(current) ?: min
        val next = (base + delta).coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min))
        return format(next, decimals)
    }

    /** 安全转 Double；空串/小数点结尾/非法值返回 null。 */
    fun asDouble(value: String): Double? = value.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** 最终提交校验：必须能解析且落在 [min, max]。 */
    fun isValid(value: String, min: Double, max: Double): Boolean {
        val n = asDouble(value) ?: return false
        return n >= min && n <= max
    }

    /** 格式化输出：保留指定小数位，去掉无意义的 .0。 */
    fun format(value: Double, decimals: Int = 1): String {
        val scale = Math.pow(10.0, decimals.toDouble())
        val rounded = Math.round(value * scale) / scale
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format("%.${decimals}f", rounded)
    }
}
