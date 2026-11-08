package com.qingji.app.domain

import java.util.Locale

/**
 * ============================================================
 * 体重单位换算（M7「体重单位」设置）
 * ------------------------------------------------------------
 * 数据库里永远只存「公斤」，这一点不能变 —— 变了两套数据就乱了。
 * 用户选了「斤」，只在**显示**和**输入**这两头换：
 *   存 → 显示：×2
 *   输入 → 存：÷2
 *
 * 这个文件全是纯函数，不依赖安卓，所以能在电脑上直接跑单元测试。
 * ============================================================
 */
object WeightUnit {

    const val KG = "kg"
    const val JIN = "jin"

    /** 单位符号：kg 显示 kg，jin 显示 斤。 */
    fun symbol(unit: String): String = if (unit == JIN) "斤" else "kg"

    /** 数据库里的公斤 → 界面上要显示的数字。 */
    fun toDisplay(kg: Double, unit: String): Double =
        if (unit == JIN) kg * 2 else kg

    /** 界面上输入的数字 → 数据库要存的公斤。 */
    fun toKg(value: Double, unit: String): Double =
        if (unit == JIN) value / 2 else value

    /** 格式化显示，默认一位小数（比如 68.5）。 */
    fun format(kg: Double, unit: String, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", toDisplay(kg, unit))

    /** 带单位的完整文本，比如「137.0 斤」。 */
    fun formatWithSymbol(kg: Double, unit: String, decimals: Int = 1): String =
        "${format(kg, unit, decimals)} ${symbol(unit)}"

    /**
     * 把用户输入的文字解析成「公斤」。
     * 返回 null 表示输入不合法，界面要提示，不能悄悄存 0。
     */
    fun parseToKg(text: String, unit: String): Double? {
        val value = text.trim().replace("，", "").replace(",", "").toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        val kg = toKg(value, unit)
        // 20kg 到 500kg 之外基本是误输（比如把「斤」当「公斤」填了两次）
        if (kg < 20.0 || kg > 500.0) return null
        return kg
    }

    /** 变化量的文案，比如「-0.5 kg」或「+0.3 斤」。 */
    fun changeText(deltaKg: Double, unit: String): String {
        val value = toDisplay(deltaKg, unit)
        val sign = if (value > 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.1f", value)} ${symbol(unit)}"
    }
}
