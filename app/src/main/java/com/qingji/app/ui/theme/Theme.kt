package com.qingji.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color

/**
 * 全 App 主题（M7）。
 * themeMode 由设置页存进 DataStore，MainActivity 收集后传进来：
 *   "light" 浅色 / "dark" 深色 / "system" 跟随系统。
 */
private fun lightScheme() = lightColorScheme(
    primary = QingJiGreen,
    onPrimary = Color.White,
    primaryContainer = QingJiGreenLight,
    onPrimaryContainer = QingJiGreenDark,
    secondary = QingJiGreenDark,
    onSecondary = Color.White,
    background = Light.Page,
    onBackground = Light.Text,
    surface = Light.Card,
    onSurface = Light.Text,
    surfaceVariant = Light.Page,
    onSurfaceVariant = Light.TextSub,
    outline = Light.Divider,
    outlineVariant = Light.Divider,
    error = DangerRed,
    onError = Color.White
)

private fun darkScheme() = darkColorScheme(
    primary = QingJiGreen,
    onPrimary = Color(0xFF10251F),
    primaryContainer = QingJiGreenLight,
    onPrimaryContainer = QingJiGreenDark,
    secondary = QingJiGreenDark,
    onSecondary = Color(0xFF10251F),
    background = Dark.Page,
    onBackground = Dark.Text,
    surface = Dark.Card,
    onSurface = Dark.Text,
    surfaceVariant = Dark.Page,
    onSurfaceVariant = Dark.TextSub,
    outline = Dark.Divider,
    outlineVariant = Dark.Divider,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun QingJiTheme(
    themeMode: String = "system",
    /** 主题色 key：green / blue / purple / orange / pink */
    accentKey: String = "green",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    // SideEffect 把「是否深色」同步给 Color.kt 里的全局 State，
    // 所有页面读的颜色属性会自动重组，不用逐个页面传参。
    SideEffect {
        QingJiThemeState.apply(dark)
        QingJiThemeState.applyAccent(accentKey)
    }

    MaterialTheme(
        colorScheme = if (dark) darkScheme() else lightScheme(),
        typography = QingJiTypography,
        content = content
    )
}
