package com.qingji.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.qingji.app.data.prefs.QingJiSettings

/**
 * ============================================================
 * 全 App 唯一的「设置」读取出口（TASK-05）
 * ------------------------------------------------------------
 * 以前每个页面各自写一遍：
 *
 *     val settings by remember { SettingsStore.observe(context) }
 *         .collectAsState(initial = QingJiSettings())
 *
 * 一个页面一条订阅，首页、进展页、设置页、根页面各读各的 —— 虽然底层都是同一个
 * DataStore，但订阅点分散，状态节奏不统一，也让「设置改了哪些页面会变」变得难以推断。
 *
 * 现在改成：MainActivity 里只订阅一次（它本来就要读主题和语言），
 * 结果通过这个 CompositionLocal 往下发，所有页面读同一份值。
 * 设置一改 → DataStore 发新值 → MainActivity 重组 → 整棵树同步拿到新设置。
 *
 * 为什么用 staticCompositionLocalOf：
 * 设置是全局低频数据（改一次就几百毫秒一次重组），不需要细粒度追踪读取位置；
 * static 版本在值变化时让内容整体重组，语义最简单，也不会漏更新。
 *
 * 注意：只给「页面读取」用。后台服务、仓库层、系统闹钟这些拿不到 Composable
 * 上下文的地方（Repository / FastingService / Reminders），仍然直接读
 * SettingsStore.observe(context) —— 那是一次性取值或后台常驻消费，不属于页面订阅。
 */
val LocalAppSettings = staticCompositionLocalOf { QingJiSettings() }
