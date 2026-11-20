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
    const val LABEL_BREAKFAST = "早餐"
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
    val SUB = Color(0xFF8A8F98)
    val GREEN = Color(0xFF1F8A70)
    val GREEN_LIGHT = Color(0xFFE8F5EF)
    val BLUE_LIGHT = Color(0xFFE8F4FB)
    val WATER_TEXT = Color(0xFF2A7FA8)
    val OVER_LIMIT = Color(0xFFE5544B)
    val MACRO_PROTEIN = Color(0xFF5B8DEF)
    val MACRO_CARB = Color(0xFF1F8A70)
    val MACRO_FAT = Color(0xFFF5A623)

    // ---- 尺寸规格（预览卡片按真实 targetCell 比例展示） ----
    const val CORNER_CARD_DP = 18
    const val MACRO_BAR_HEIGHT_DP = 5
}
