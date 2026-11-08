package com.qingji.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * ============================================================
 * 全 App 配色（M7 深色模式改造）
 * ------------------------------------------------------------
 * 以前这里是一堆写死的常量，切深色时所有页面还是白底黑字。
 * 现在的做法：每个颜色都是一个带 get() 的属性，读取时看一眼
 * QingJiThemeState.darkMode（一个 Compose State）。
 *
 * 为什么这样最省事：页面里原来写 `PageBackground` 的地方一个字都不用改，
 * 但因为 getter 里读了 State，界面会自动订阅 → 切换主题时全站跟着重组。
 * 不用把几十个文件里的颜色全部换成 CompositionLocal。
 * ============================================================
 */

/** 全局主题状态。只由 QingJiTheme 写入，其余地方只读。 */
object QingJiThemeState {
    var darkMode: Boolean by mutableStateOf(false)
        private set

    /** 当前主题色 key：green / blue / purple / orange / pink，默认松绿 */
    var accentKey: String by mutableStateOf("green")
        private set

    fun apply(dark: Boolean) {
        darkMode = dark
    }

    fun applyAccent(key: String) {
        accentKey = key
    }
}

/** 浅色/深色两套原始色值，Theme.kt 也要用，所以是 internal 不是 private。 */
object Light {
    val Green = Color(0xFF1F8A70)
    val GreenDark = Color(0xFF16795F)
    val GreenLight = Color(0xFFE8F5EF)
    val Page = Color(0xFFF4F5F6)
    val Card = Color(0xFFFFFFFF)
    val Text = Color(0xFF1A1A1A)
    val TextSub = Color(0xFF8A8F98)
    val Divider = Color(0xFFEEF0F2)
}

object Dark {
    val Green = Color(0xFF4DBB9B)       // 深色背景上要更亮才看得清
    val GreenDark = Color(0xFF8FE0C4)
    val GreenLight = Color(0xFF1D3A33)  // 深绿底，不再是浅绿
    val Page = Color(0xFF121416)
    val Card = Color(0xFF1E2124)
    val Text = Color(0xFFF2F4F5)
    val TextSub = Color(0xFF9AA1AA)
    val Divider = Color(0xFF2C3136)
}

private val isDark: Boolean get() = QingJiThemeState.darkMode

/**
 * 品牌色：按钮、选中态、进度填充。
 * 名字还叫 Green 是为了不动几百处引用，实际取的是「当前主题」的主色。
 */
val QingJiGreen: Color
    get() = AccentThemes.main(QingJiThemeState.accentKey, QingJiThemeState.darkMode)

/** 深一档的品牌色：底部导航中央加号、按下状态 */
val QingJiGreenDark: Color
    get() = AccentThemes.darkOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)

/** 浅色底：标签背景、选中项背景（深色下变成深色底） */
val QingJiGreenLight: Color
    get() = AccentThemes.softOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)

// ---------- 三大营养素：跟主题走，但仍能一眼分开 ----------

/** 蛋白质：偏冷色 */
val MacroProtein: Color
    get() = Color(AccentThemes.byKey(QingJiThemeState.accentKey).macroProtein)

/** 碳水：跟主题主色一致 */
val MacroCarb: Color
    get() = Color(AccentThemes.byKey(QingJiThemeState.accentKey).macroCarb)

/** 脂肪：保持暖色 */
val MacroFat: Color
    get() = Color(AccentThemes.byKey(QingJiThemeState.accentKey).macroFat)

/** 页面底色 */
val PageBackground: Color get() = if (isDark) Dark.Page else Light.Page

/** 卡片底色 */
val CardWhite: Color get() = if (isDark) Dark.Card else Light.Card

/** 主文字 */
val TextPrimary: Color get() = if (isDark) Dark.Text else Light.Text

/** 次要文字 */
val TextSecondary: Color get() = if (isDark) Dark.TextSub else Light.TextSub

/** 分割线 */
val DividerColor: Color get() = if (isDark) Dark.Divider else Light.Divider

/** 警示橙：BMI 超重段（深浅色都够亮） */
val WarnOrange: Color = Color(0xFFF5A623)

/** 危险红：BMI 肥胖段 */
val DangerRed: Color = Color(0xFFE5544B)

/** 正常绿：BMI 正常段 */
val NormalGreen: Color = Color(0xFF3CBF6E)

/**
 * ============================================================
 * 多彩语义色（v3.2.2，用户要求变回参考图那种多彩）
 * ------------------------------------------------------------
 * 这些是**固定语义色**，故意不跟 accentKey 变：
 * 蓝色永远代表喝水、橙色永远代表断食/早餐、紫色永远代表加餐……
 * 一眼就能靠颜色分辨功能，这正是参考图的做法。
 *
 * 主题色开关仍然管：主按钮、选中态、进度条、顶部渐变、周历方块。
 * 两条线互不打架 —— 主题负责「整体基调」，这里负责「功能识别」。
 *
 * 全部集中在这一个对象里，以后要调色只改这里，不要再散落写死。
 * ============================================================
 */
object MultiColor {
    // 喝水：杯壁 / 水体 / 水面 / 数字
    val WaterOutline = Color(0xFF9FC9DA)
    val WaterFill = Color(0xFF7EC8E3)
    val WaterSurface = Color(0xFFAEDCEC)
    val WaterText = Color(0xFF3FA9C9)

    // 断食 / 连续天数：橙
    val FastingText = Color(0xFFE0803A)
    val FastingBackground = Color(0xFFFFF3DF)
    val StreakText = Color(0xFFE0803A)
    val StreakBackground = Color(0xFFFFF3DF)

    // 四餐：橙 / 绿 / 蓝 / 紫
    val Breakfast = Color(0xFFF5A623)
    val Lunch = Color(0xFF1F8A70)
    val Dinner = Color(0xFF5B8DEF)
    val Snack = Color(0xFF9B8AFB)

    // 右侧三颗快捷按钮：水滴蓝 / 书紫 / 相机绿
    val QuickWater = Color(0xFF4FA9C9)
    val QuickBook = Color(0xFF9B8AFB)
    val QuickCamera = Color(0xFF26A69A)

    // 加号面板（按参考图第 5 张实取色）：
    // 横幅是柔和的鼠尾草绿渐变，不是主题绿；运动卡是蓝、体重卡是绿
    val ScanStart = Color(0xFFA9CDA4)
    val ScanEnd = Color(0xFF7FB091)
    val ScanArrow = Color(0xFF4F8B63)

    val Voice = Color(0xFFC2569B)          // 语音输入：粉紫
    val FoodLibrary = Color(0xFF8E62C4)    // 食物库：紫
    val Drink = Color(0xFF4FA9C9)         // 记录喝水：水蓝
    val Exercise = Color(0xFF3F7FC4)      // 记录运动：蓝
    val Weight = Color(0xFF6FA070)        // 记录体重：绿

    // 这两个是参考图里没有的补充入口，用低饱和淡彩，不抢主视觉
    val TextAi = Color(0xFF6E8FA8)
    val Barcode = Color(0xFF8A7FC0)
}
