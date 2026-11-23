package com.qingji.app.widget

import androidx.compose.ui.graphics.Color

/**
 * 小组件共享规格（阶段 3）：真实 Glance UI 与设置页手工预览共用同一组常量，
 * 消除「预览写死 2313/92.0kg、台北进度条无填充、文案 +/- 记录」等错位。
 *
 * 只放展示规格（文案/颜色/示例数值），不含业务计算与 Room 访问。
 */
object WidgetSpec {
    // ---- 文案（预览与 Glance 共用，改一处两处同步） ----
    const val LABEL_QUICK_TITLE = "记一笔"
    // 2×2 快捷入口：原「早餐」改为「饮食」（入口指向记饮食，不再限定早餐）；图标用与今日记录午餐同款的 Restaurant 刀叉。
    const val LABEL_DIET = "饮食"
    const val LABEL_WEIGHT = "体重"
    const val LABEL_WATER = "喝水"
    const val LABEL_PHOTO = "拍照"
    const val LABEL_REMAINING = "今日剩余"
    const val LABEL_KCAL_UNIT = "千卡"
    const val LABEL_PROTEIN = "蛋白"
    const val LABEL_CARB = "碳水"
    const val LABEL_FAT = "脂肪"
    const val LABEL_WATER_SHORT = "饮水"
    const val LABEL_CURRENT_WEIGHT = "当前体重"
    const val LABEL_RECORD = "记录"
    const val LABEL_WEIGHT_HIDDEN = "已隐藏"
    const val LABEL_WEIGHT_EMPTY = "还没有记录"
    const val LABEL_NO_DATA = "数据暂不可用"
    const val LABEL_STALE_TODAY = "数据暂不可用 · 显示今天最近一次成功数据"
    const val LABEL_STALE_WEIGHT = "数据暂不可用 · 今天的最近值"

    // ---- 示例值（仅预览用，且预览会明确标注「示例数据，非真实记录」） ----
    const val SAMPLE_REMAINING = 2313
    const val SAMPLE_WEIGHT = "92.0"
    const val SAMPLE_WATER = 0
    const val DEFAULT_WATER_GOAL = 3200 // 与 SettingsStore.waterGoalMl 默认一致

    // ---- 配色（Glance 写死值，预览对齐用同一份） ----
    val CARD = Color(0xFFFFFFFF)
    val TEXT = Color(0xFF1A1A1A)
    val SUB = Color(0xFF5C6672)  // 视觉精修：从浅灰加深，壁纸上可读
    val GREEN = Color(0xFF0E7A5F)  // 视觉精修：更深更饱和，主数字一眼可见
    val GREEN_LIGHT = Color(0xFFE8F5EF)
    val BLUE_LIGHT = Color(0xFFE8F4FB)
    val WATER_TEXT = Color(0xFF2A7FA8)
    val OVER_LIMIT = Color(0xFFE5544B)
    val WARN_ORANGE = Color(0xFFE08A2E)  // 首页同款：运动消耗橙字
    val DIVIDER = Color(0xFFECEFF2)  // 首页同款分隔线
    val MACRO_PROTEIN = Color(0xFF5B8DEF)
    val MACRO_CARB = Color(0xFF1F8A70)
    val MACRO_FAT = Color(0xFFF5A623)

    // ---- 尺寸规格（预览卡片按真实 targetCell 比例展示） ----
    const val CORNER_CARD_DP = 18
    const val MACRO_BAR_HEIGHT_DP = 5

    // ---- V3 视觉精修：Glance 与设置页预览共享的尺寸/间距规格 ----
    /** 卡片内边距（今日概览四周、体重左右）。 */
    const val CARD_PADDING_DP = 14
    const val CARD_PADDING_H_DP = 14
    /** 体重组件垂直内边距（压缩留白，保持触控面积）。 */
    const val WEIGHT_PADDING_V_DP = 6
    /** 营养条并排间距。 */
    const val MACRO_GAP_DP = 8
    /** 快捷入口格子底色透明度与圆角。 */
    const val TILE_BG_ALPHA = 1.0f  // 视觉精修：实心浅绿格，不再半透糊
    const val TILE_CORNER_DP = 10
    /** 快捷入口图标的彩色底板圆角：由原来的圆形(999)改为圆角方形(10dp)，对齐悬浮按钮/今日记录的图标观感。 */
    const val TILE_ICON_CORNER_DP = 10
    /** 2×2 快捷入口图标尺寸：去底后放大，图标本身成为主视觉。 */
    const val QUICK_ICON_DP = 30
    const val QUICK_LABEL_SP = 10
    const val QUICK_ICON_LABEL_GAP_DP = 2
    const val QUICK_TILE_GAP_DP = 2
    /** 今日剩余大数字字号（Glance 与预览共用）。 */
    const val REMAINING_NUMBER_SP = 30  // 视觉精修：主数字更大
    /** 体重数字字号 / 记录按钮尺寸与间距。 */
    const val WEIGHT_NUMBER_SP = 24
    const val WEIGHT_BUTTON_W_DP = 60
    const val WEIGHT_BUTTON_H_DP = 40
    const val WEIGHT_BUTTON_GAP_DP = 8

    // ---- V2.1阶段B：深浅色主题选择（Glance 与预览共用） ----
    /** 浅色主题（保持 V2 配色，老用户视觉不突变）。 */
    fun light() = WidgetTheme.LIGHT
    /** 深色主题：深灰底 + 高对比浅字 + 提亮绿。 */
    fun dark() = WidgetTheme.DARK
    /** 根据系统 uiMode 选主题；不依赖 App Activity 状态。 */
    fun theme(dark: Boolean) = if (dark) WidgetTheme.DARK else WidgetTheme.LIGHT

    // ---- V2 阶段5：预览与 Glance 共享的展示规则（避免两处硬编码漂移） ----
    /** 背景不透明度范围（SettingsStore/ViewModel/Glance/预览四处共用）。 */
    // 0~100 自由可调：滑杆拖到 0 时卡片背景完全透明（文字仍在，浅色壁纸下可能看不清，由用户自行取舍）。
    // 老用户已存值原样读取；DataStore 非 Room，无需数据库迁移。
    const val ALPHA_MIN = 0
    const val ALPHA_MAX = 100
    /** 营养条轨道色透明度。 */
    const val MACRO_TRACK_ALPHA = 0.42f  // 视觉精修：轨道加深，进度可辨
    /** 预览示例营养素进度（用示例 current/goal 计算，勿写死比例）。 */
    const val SAMPLE_PROTEIN = 80
    const val SAMPLE_PROTEIN_GOAL = 120
    const val SAMPLE_CARB = 100
    const val SAMPLE_CARB_GOAL = 200
    const val SAMPLE_FAT = 30
    const val SAMPLE_FAT_GOAL = 60
}
