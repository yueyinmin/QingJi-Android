package com.qingji.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 营养条轨道宽度计算（验收补充工单 P1 / C1）。
 *
 * 依据（Glance 1.1.1 源码 `SizeMode.Responsive` 注释）：
 * - Android 12+：composable 对声明的每个 size 调用一次，系统按当前尺寸选对应 view；
 * - Android 12 以下：按可用空间选「能放下的最大声明 size」。
 * 因此 `LocalSize.current.width` 是**声明尺寸之一**（本项目 110dp / 250dp），
 * 不是启动器任意值——这是可靠的布局约束，不是经验系数。
 *
 * 布局推导（OverviewWidget 4×2 / 4×4）：
 * 卡片 Box `.fillMaxSize().padding(CARD_PADDING_DP)` → 内容宽 = W - 2×padding；
 * 三条 MacroBar 在 Row 里各 `defaultWeight()`，中间两条 `MACRO_GAP_DP` Spacer
 * → 单条轨道宽 = (内容宽 - 2×gap) / 3；
 * 进度填充 = trackWidth × progress，progress 已钳制 0..1 → 永不溢出。
 */
internal object WidgetLayout {
    /**
     * @param widgetWidth LocalSize.current.width（Responsive 声明尺寸之一）
     * @return 单条营养轨道宽度；尺寸过小返回 0dp（UI 跳过进度绘制，不画负宽）。
     */
    fun macroTrackWidth(
        widgetWidth: Dp,
        cardPadding: Dp = WidgetSpec.CARD_PADDING_DP.dp,
        gap: Dp = WidgetSpec.MACRO_GAP_DP.dp,
        bars: Int = 3
    ): Dp {
        if (bars <= 0) return 0.dp
        val usable = widgetWidth - cardPadding * 2 - gap * (bars - 1)
        return (usable / bars).coerceAtLeast(0.dp)
    }

    /**
     * V2.2：全宽进度条（热量进度 / 减重进度）的填充宽度。
     * 轨道是 fillMaxWidth()，内容宽 = W - 2×padding；不能用 macroTrackWidth（那是单条营养轨道宽 ≈W/3）。
     * @return 全宽轨道可用宽度；尺寸异常时返回 0dp，UI 跳过绘制。
     */
    fun fullBarWidth(
        widgetWidth: Dp,
        cardPadding: Dp = WidgetSpec.CARD_PADDING_DP.dp
    ): Dp = (widgetWidth - cardPadding * 2).coerceAtLeast(0.dp)
}
