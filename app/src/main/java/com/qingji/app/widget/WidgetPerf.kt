package com.qingji.app.widget

import android.os.SystemClock
import android.util.Log
import com.qingji.app.BuildConfig

/**
 * 桌面小组件性能埋点（工单 B1）。
 *
 * 设计约束：
 * - 统一 tag `QingJiWidgetPerf`，只在 DEBUG 构建输出，Release 零开销（编译期内联常量判断）；
 * - 只记录阶段名、组件类型、耗时、缓存命中状态与行数/目标集合等【非隐私】信息；
 *   绝不记录饮食内容、体重数值、姓名等用户数据；
 * - 系统 Launcher 的固定动画/等待属于系统阶段，本类只记 App 内部阶段，
 *   两端耗时在报告里分开统计，不把系统阶段算进 App 指标。
 *
 * 用法：
 *   val t = WidgetPerf.begin("glance", "OVERVIEW")
 *   ...
 *   WidgetPerf.end(t, cache = "fresh")
 */
object WidgetPerf {
    const val TAG = "QingJiWidgetPerf"

    /** Release 构建完全静默；避免高频调试日志影响正常运行。 */
    private val enabled: Boolean get() = BuildConfig.DEBUG

    data class Trace(val event: String, val widget: String, val start: Long)

    fun begin(event: String, widget: String = "-"): Trace =
        // V3：Release 零开销——enabled=false 时不读时钟、不分配计时对象
        if (enabled) Trace(event, widget, SystemClock.elapsedRealtime())
        else Trace(event, widget, 0L)

    fun end(trace: Trace, cache: String = "-", extra: String = "-") {
        if (!enabled) return
        val cost = SystemClock.elapsedRealtime() - trace.start
        Log.d(
            TAG,
            "event=${trace.event} widget=${trace.widget} costMs=$cost cache=$cache extra=$extra"
        )
    }

    /** 单点打点（无耗时区间），用于 Pin 请求/回调、刷新入队等瞬态事件。 */
    fun mark(event: String, widget: String = "-", detail: String = "-") {
        if (!enabled) return
        Log.d(TAG, "event=$event widget=$widget detail=$detail")
    }
}
