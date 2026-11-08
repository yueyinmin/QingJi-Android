package com.qingji.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * ============================================================
 * 三个小组件的「接收器」
 * ------------------------------------------------------------
 * 安卓系统要更新小组件时，找的是这个 Receiver（不是 Widget 本身）。
 * 它们都继承 Glance 提供的 GlanceAppWidgetReceiver，我们只需要
 * 告诉它「这个接收器归哪个小组件管」，剩下的系统调用由 Glance 处理。
 *
 * 名字必须和 AndroidManifest.xml 里 android:name 完全一致，
 * 三个 Receiver 分别在 res/xml/ 下有对应的 *_widget_info.xml。
 * ============================================================
 */

/** 2×2 快捷记录。 */
class QuickRecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickRecordWidget()
}

/** 4×2 今日概览。 */
class OverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OverviewWidget()
}

/** 4×1 体重闪记。 */
class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}
