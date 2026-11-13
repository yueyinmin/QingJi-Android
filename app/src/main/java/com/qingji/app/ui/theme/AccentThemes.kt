package com.qingji.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ============================================================
 * 主题色（v2.0）
 * ------------------------------------------------------------
 * 为什么这么设计：全 App 有几百处引用 QingJiGreen / QingJiGreenDark /
 * QingJiGreenLight。如果改成 CompositionLocal，所有页面都要动一遍，
 * 风险太高。所以还是保留这三个入口，只在内部按「当前主题」取值。
 *
 * 每套主题给三档：
 * - main  主色（按钮、进度条、选中态）
 * - dark  深一档（浮层按钮、强调文字）
 * - soft  浅色底（标签背景、选中底色）
 * 深色模式另给一套更亮的，避免深底上发灰。
 *
 * 三大营养素（蛋白/碳水/脂肪）也跟主题走：
 * 碳水用主题主色，蛋白往冷色偏，脂肪保持暖色 —— 整体协调，
 * 但三条之间仍能一眼分开。
 * ============================================================
 */
object AccentThemes {

    data class Spec(
        val key: String,
        val label: String,
        /** 浅色模式：主色 / 深色 / 浅底 */
        val lightMain: Long,
        val lightDark: Long,
        val lightSoft: Long,
        /** 深色模式：主色 / 深色 / 浅底 */
        val darkMain: Long,
        val darkDark: Long,
        val darkSoft: Long,
        /** 三大营养素：蛋白 / 碳水 / 脂肪 */
        val macroProtein: Long,
        val macroCarb: Long,
        val macroFat: Long
    )

    val ALL = listOf(
        Spec(
            key = "green", label = "松绿",
            lightMain = 0xFF299D76, lightDark = 0xFF1F7E62, lightSoft = 0xFFEAF7F1,
            darkMain = 0xFF4DBB9B, darkDark = 0xFF8FE0C4, darkSoft = 0xFF1D3A33,
            macroProtein = 0xFF5B8DEF, macroCarb = 0xFF299D76, macroFat = 0xFFF5A623
        ),
        Spec(
            key = "blue", label = "海蓝",
            lightMain = 0xFF2F6FED, lightDark = 0xFF2559C4, lightSoft = 0xFFE9EFFD,
            darkMain = 0xFF6FA0FF, darkDark = 0xFFA9C4FF, darkSoft = 0xFF1B2A44,
            macroProtein = 0xFF7B61FF, macroCarb = 0xFF2F6FED, macroFat = 0xFFE0A03A
        ),
        Spec(
            key = "purple", label = "黛紫",
            lightMain = 0xFF7B5CD6, lightDark = 0xFF6247B8, lightSoft = 0xFFF0EBFC,
            darkMain = 0xFFA98BEF, darkDark = 0xFFC9B8F7, darkSoft = 0xFF2A2340,
            macroProtein = 0xFF4FA3D1, macroCarb = 0xFF7B5CD6, macroFat = 0xFFE0935B
        ),
        Spec(
            key = "orange", label = "暖橙",
            lightMain = 0xFFE0803A, lightDark = 0xFFBF6526, lightSoft = 0xFFFDF0E5,
            darkMain = 0xFFF0A566, darkDark = 0xFFF7C79A, darkSoft = 0xFF3A2A1B,
            macroProtein = 0xFF5B8DEF, macroCarb = 0xFFE0803A, macroFat = 0xFFC9A227
        ),
        Spec(
            key = "pink", label = "樱粉",
            lightMain = 0xFFD9548A, lightDark = 0xFFB33F70, lightSoft = 0xFFFCEAF1,
            darkMain = 0xFFEC7FA6, darkDark = 0xFFF5B3C9, darkSoft = 0xFF3A1F29,
            macroProtein = 0xFF6C8AE4, macroCarb = 0xFFD9548A, macroFat = 0xFFE0A03A
        )
    )

    /** 按 key 找主题，找不到回落到第一套（松绿）。 */
    fun byKey(key: String?): Spec = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** 取主色。 */
    fun main(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkMain else spec.lightMain)
    }

    fun darkOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkDark else spec.lightDark)
    }

    fun softOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkSoft else spec.lightSoft)
    }
}
