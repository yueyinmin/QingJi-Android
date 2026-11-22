package com.qingji.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider

/**
 * V2.1 阶段B：深浅色主题。
 *
 * 不依赖 App Activity 主题状态（Widget 在 App 未启动时也要正确显示），
 * 直接从系统 Configuration.uiMode 判断。Glance 在 provideContent 内可用 LocalContext。
 */
data class WidgetPalette(
    val card: Color,
    val text: Color,
    val sub: Color,
    val green: Color,
    val greenLight: Color,
    val overLimit: Color,
    val warnOrange: Color,
    val divider: Color,
    val macroProtein: Color,
    val macroCarb: Color,
    val macroFat: Color
) {
    fun provider(c: Color) = ColorProvider(c)
}

object WidgetTheme {
    val LIGHT = WidgetPalette(
        card = Color(0xFFFFFFFF),
        text = Color(0xFF1A1A1A),
        sub = Color(0xFF5C6672),
        green = Color(0xFF0E7A5F),
        greenLight = Color(0xFFE8F5EF),
        overLimit = Color(0xFFE5544B),
        warnOrange = Color(0xFFE08A2E),
        divider = Color(0xFFECEFF2),
        macroProtein = Color(0xFF5B8DEF),
        macroCarb = Color(0xFF1F8A70),
        macroFat = Color(0xFFF5A623)
    )

    /** 深色：背景深灰、文字高对比浅色、绿色提亮保证进度与数字可辨。 */
    val DARK = WidgetPalette(
        card = Color(0xFF20262B),
        text = Color(0xFFF2F5F6),
        sub = Color(0xFFB7C0C6),
        green = Color(0xFF4FD1A5),
        greenLight = Color(0xFF2C3B36),
        overLimit = Color(0xFFFF7A72),
        warnOrange = Color(0xFFFFB45C),
        divider = Color(0xFF3A4248),
        macroProtein = Color(0xFF8FB6FF),
        macroCarb = Color(0xFF4FD1A5),
        macroFat = Color(0xFFFFC46B)
    )

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun of(context: Context): WidgetPalette = if (isDark(context)) DARK else LIGHT

    /** 供非 Composable 的 Repository/测试使用。 */
    @Composable
    fun current(): WidgetPalette = of(LocalContext.current)
}
