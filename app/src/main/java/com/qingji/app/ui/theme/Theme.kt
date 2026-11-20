package com.qingji.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.prefs.QingJiSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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

    // ---------- 自定义字体（v3.1）----------
    // 用户在「主题与显示」里导入的 ttf/otf 会存成私有文件路径，
    // 这里读出来包成 FontFamily，塞进 Typography 的默认字族——全 App 即刻换字。
    val context = LocalContext.current
    val settings by remember { SettingsStore.observe(context) }
        .collectAsState(initial = QingJiSettings())
    val customFamily = remember(settings.customFontPath) {
        runCatching {
            val f = java.io.File(settings.customFontPath)
            if (settings.customFontPath.isNotBlank() && f.exists() && f.length() > 0) {
                androidx.compose.ui.text.font.FontFamily(
                    android.graphics.Typeface.createFromFile(f)
                )
            } else {
                null
            }
        }.getOrNull()
    }

    // Material3 的 Typography 没有「默认字族」参数，要逐个样式覆盖
    val typography = if (customFamily != null) {
        with(QingJiTypography) {
            copy(
                displayLarge = displayLarge.copy(fontFamily = customFamily),
                displayMedium = displayMedium.copy(fontFamily = customFamily),
                displaySmall = displaySmall.copy(fontFamily = customFamily),
                headlineLarge = headlineLarge.copy(fontFamily = customFamily),
                headlineMedium = headlineMedium.copy(fontFamily = customFamily),
                headlineSmall = headlineSmall.copy(fontFamily = customFamily),
                titleLarge = titleLarge.copy(fontFamily = customFamily),
                titleMedium = titleMedium.copy(fontFamily = customFamily),
                titleSmall = titleSmall.copy(fontFamily = customFamily),
                bodyLarge = bodyLarge.copy(fontFamily = customFamily),
                bodyMedium = bodyMedium.copy(fontFamily = customFamily),
                bodySmall = bodySmall.copy(fontFamily = customFamily),
                labelLarge = labelLarge.copy(fontFamily = customFamily),
                labelMedium = labelMedium.copy(fontFamily = customFamily),
                labelSmall = labelSmall.copy(fontFamily = customFamily)
            )
        }
    } else {
        QingJiTypography
    }

    MaterialTheme(
        colorScheme = if (dark) darkScheme() else lightScheme(),
        typography = typography,
        content = content
    )
}
