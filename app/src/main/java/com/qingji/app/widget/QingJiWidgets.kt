package com.qingji.app.widget

import android.content.Context
import android.content.Intent
import com.qingji.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.qingji.app.MainActivity
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.domain.WeightUnit
import kotlinx.coroutines.flow.first

/**
 * ============================================================
 * 三种桌面小组件（M8）
 * ------------------------------------------------------------
 * 用 Jetpack Glance（用写界面的思路写小组件），三种：
 * 1. QuickRecordWidget 2×2：早餐 / 体重 / 喝水 / 拍照，四个快捷键
 * 2. OverviewWidget    4×2：今日剩余热量 + 三条营养素迷你条 + 饮水
 * 3. WeightWidget      4×1：当前体重 + 「记录」按钮
 *
 * 三条硬规矩（踩过坑才知道）：
 * - 小组件是**另一个进程**，读不到 App 内存，数据必须现查数据库；
 * - 颜色只能写死（小组件里拿不到 App 的主题状态），这里统一用浅色卡片；
 * - 点「喝水」不打开 App，直接写库 +200ml，然后自己刷新自己。
 *
 * v3.4.0：外观（背景不透明度、展示哪些内容）也走同一条路 —— 直接读 DataStore，
 * 不依赖 App 进程的内存状态。设置页改完会调 WidgetRefresher.refresh() 主动重绘。
 * ============================================================
 */

// ---------- 小组件统一配色（写死，保证任何主题下都看得清） ----------
// V2.2阶段A：显式主题参数传递（不依赖 ThreadLocal / LocalContext）。
// 每个 provideContent 入口从系统 uiMode 计算 palette，逐层显式传给内容构建函数。
private fun textColor(color: Color) = ColorProvider(color)
private fun cardBg(alphaPercent: Int, p: WidgetPalette) =
    ColorProvider(p.card.copy(alpha = (alphaPercent.coerceIn(
        WidgetSpec.ALPHA_MIN, WidgetSpec.ALPHA_MAX
    ) / WidgetSpec.ALPHA_MAX.toFloat())))

/** 点小组件里任意按钮 → 打开 App 并直达某个页面。 */
private fun openApp(context: Context, target: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(WidgetRefresher.EXTRA_TARGET, target)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/**
 * 小组件展示数据统一由 [WidgetSnapshotRepo] 读取（阶段 1）：
 * 口径与首页一致、快照带业务日期/时区、失败有明确降级。
 * 本文件不再自持数据类与进程内 lastGoodData。
 */

/** 小组件外观，和 Room 数据分开读，互不影响。 */
private data class WidgetAppearance(
    val alphaPercent: Int = 100,
    val showWater: Boolean = true,
    val showMacro: Boolean = true,
    val showWeight: Boolean = true
)

/**
 * 直接读 DataStore（跨进程可用），绝不能读 App 里的单例状态。
 * 读失败就回到完全不透明 + 全部显示，桌面不会因此变空白。
 */
private suspend fun loadWidgetAppearance(context: Context): WidgetAppearance = try {
    // 工单 B2：走进程级缓存，避免 provideGlance 主线程冷读 DataStore。
    // 设置写入后 SettingsViewModel 会 invalidate，保证最终一致。
    val s = WidgetSettingsCache.get(context)
    WidgetAppearance(
        alphaPercent = s.widgetAlpha,
        showWater = s.widgetShowWater,
        showMacro = s.widgetShowMacro,
        showWeight = s.widgetShowWeight
    )
} catch (e: Exception) {
    WidgetAppearance()
}

/** 统一数据入口：现查 + 当天有效快照降级 + 明确空态（见 WidgetSnapshotRepo）。 */
private suspend fun loadWidgetData(context: Context): WidgetData = WidgetSnapshotRepo.load(context)

/**
 * W-03：stale 文案按新鲜度区分，并带上快照更新时间。
 * - 无快照：明确空态，不提"最近成功"（因为根本没有）；
 * - 当天快照：显示"最近成功 HH:mm"，超过阈值则标注"数据较旧"。
 */
private fun staleLabel(data: WidgetData): String {
    if (!data.hasSnapshot) return "数据暂不可用 · 打开 App 后再看"
    val time = java.time.Instant.ofEpochMilli(data.snapshotAt)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return when (WidgetSnapshotRepo.freshnessOf(data)) {
        WidgetSnapshotRepo.Freshness.STALE -> "数据较旧 · 最近成功 $time"
        else -> "数据暂不可用 · 最近成功 $time"
    }
}

/**
 * P0：分项数值展示。FAILED → "--"（读失败，绝不显示伪 0）；
 * SUCCESS/EMPTY → 真值（EMPTY 的 0 是「确实没有记录」的真实 0）。
 */
internal fun itemText(state: ItemState, value: String): String =
    if (state == ItemState.FAILED) "--" else value

/** P0：剩余热量。stale 无快照 / 计算输入不全 / 分项失败 → "--"，不显示默认 2000。 */
internal fun remainingText(data: WidgetData): String =
    if (data.stale && !data.hasSnapshot) "--"
    else if (!data.canShowRemaining) "--"
    else "${data.remainingKcal}"

/**
 * P0：营养条进度。
 * - 饮食读取失败 → null（UI 显示 -- 而非空轨道伪进度）；
 * - 用户资料读取失败 → null：三营养素目标来自 profile，失败时目标是伪装出来的 0；
 * - 收尾 P0：goal<=0（profile EMPTY / SUCCESS 但目标无效）→ null，营养摄入有值也不画空轨道。
 */
internal fun macroProgress(
    dietState: ItemState,
    profileState: ItemState,
    current: Int,
    goal: Int
): Float? =
    if (dietState == ItemState.FAILED || profileState == ItemState.FAILED || goal <= 0) null
    else WidgetProgress.fraction(current, goal)

// ============================================================
// 1. 快捷记录 2×2
// ============================================================
class QuickRecordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val trace = WidgetPerf.begin("provideGlance", "QUICK")
        val appearance = loadWidgetAppearance(context)
        WidgetPerf.end(trace, cache = "appearance")
        provideContent {
            val palette = WidgetTheme.of(context)
            val size = LocalSize.current
            // 横向被拉宽（≥200dp）时切换为一排四栏；默认 2×2 用两行两列，
            // 保证 2×2 下每个入口仍有足够触控面积（工单 V2.0 §2.3）。
            val wide = size.width >= 200.dp
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent, palette))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = WidgetSpec.LABEL_QUICK_TITLE,
                        style = TextStyle(
                            color = textColor(palette.sub),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    // 统一弱底色容器：四个入口不再各自铺高饱和色块
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(palette.greenLight.copy(alpha = 0.45f)))
                            .cornerRadius(12.dp)
                            .padding(4.dp)
                    ) {
                        // C2：四个入口统一同一个 QuickTile（同一图标尺寸/圆角/底色/标签基线），
                        // 只注入不同的 Action —— 跳转入口与喝水直写入口不再各写一套布局。
                        if (wide) {
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                QuickTile(R.drawable.ic_widget_diet, WidgetSpec.LABEL_DIET,
                                    jumpAction(context, WidgetRefresher.TARGET_TEXT_AI), GlanceModifier.defaultWeight(), palette, palette.green)
                                Spacer(GlanceModifier.width(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                                QuickTile(R.drawable.ic_widget_weight, WidgetSpec.LABEL_WEIGHT,
                                    jumpAction(context, WidgetRefresher.TARGET_WEIGHT), GlanceModifier.defaultWeight(), palette, palette.warnOrange)
                                Spacer(GlanceModifier.width(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                                QuickTile(R.drawable.ic_widget_water, WidgetSpec.LABEL_WATER,
                                    jumpAction(context, WidgetRefresher.TARGET_WATER), GlanceModifier.defaultWeight(), palette, palette.macroProtein)
                                Spacer(GlanceModifier.width(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                                QuickTile(R.drawable.ic_widget_camera, WidgetSpec.LABEL_PHOTO,
                                    jumpAction(context, WidgetRefresher.TARGET_IMAGE), GlanceModifier.defaultWeight(), palette, palette.macroFat)
                            }
                        } else {
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                QuickTile(R.drawable.ic_widget_diet, WidgetSpec.LABEL_DIET,
                                    jumpAction(context, WidgetRefresher.TARGET_TEXT_AI), GlanceModifier.defaultWeight(), palette, palette.green)
                                Spacer(GlanceModifier.width(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                                QuickTile(R.drawable.ic_widget_weight, WidgetSpec.LABEL_WEIGHT,
                                    jumpAction(context, WidgetRefresher.TARGET_WEIGHT), GlanceModifier.defaultWeight(), palette, palette.warnOrange)
                            }
                            Spacer(GlanceModifier.height(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                QuickTile(R.drawable.ic_widget_water, WidgetSpec.LABEL_WATER,
                                    jumpAction(context, WidgetRefresher.TARGET_WATER), GlanceModifier.defaultWeight(), palette, palette.macroProtein)
                                Spacer(GlanceModifier.width(WidgetSpec.QUICK_TILE_GAP_DP.dp))
                                QuickTile(R.drawable.ic_widget_camera, WidgetSpec.LABEL_PHOTO,
                                    jumpAction(context, WidgetRefresher.TARGET_IMAGE), GlanceModifier.defaultWeight(), palette, palette.macroFat)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 跳转型入口的 Action（复用现有 TARGET_* 路由，不另造 Intent 体系）。 */
private fun jumpAction(context: Context, target: String): Action =
    actionStartActivity(openApp(context, target), actionParametersOf(TARGET_KEY to target))

/**
 * 快捷入口统一实现（工单 C2）：早餐/体重/拍照走 [jumpAction]，喝水走
 * `actionRunCallback<AddWaterAction>()`；四者共享同一图标尺寸、底色、圆角、
 * 间距与标签基线，避免两套重复布局漂移。喝水写入语义保持不变。
 */
@Composable
private fun QuickTile(
    iconRes: Int,
    label: String,
    action: Action,
    modifier: GlanceModifier,
    palette: WidgetPalette,
    /** V3.0：圆形彩底颜色（对齐「记一笔」面板彩色圆形图标观感）。 */
    tint: Color = Color.Unspecified
) {
    Column(
        modifier = modifier
            .background(ColorProvider(palette.card.copy(alpha = WidgetSpec.TILE_BG_ALPHA)))
            .cornerRadius(WidgetSpec.TILE_CORNER_DP.dp)
            .clickable(action),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        // 图标外加圆角方形彩底（对齐悬浮按钮 / 今日记录的圆角方形图标；原 V3.0 为圆形 999dp）
        Box(
            modifier = GlanceModifier.size(30.dp)
                .background(ColorProvider(if (tint != Color.Unspecified) tint.copy(alpha = 0.20f) else palette.greenLight))
                .cornerRadius(WidgetSpec.TILE_ICON_CORNER_DP.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(WidgetSpec.QUICK_ICON_DP.dp)
            )
        }
        Spacer(GlanceModifier.height(WidgetSpec.QUICK_ICON_LABEL_GAP_DP.dp))
        Text(
            text = label,
            style = TextStyle(
                color = textColor(palette.text),
                fontSize = WidgetSpec.QUICK_LABEL_SP.sp
            )
        )
    }
}

// ============================================================
// 2. 今日概览 4×2
// ============================================================
class OverviewWidget : GlanceAppWidget() {
    /** V2.5：固定 4×2 不可调整（用户要求），SizeMode.Single + Provider XML resizeMode=none。 */
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val trace = WidgetPerf.begin("provideGlance", "OVERVIEW")
        val data = WidgetSnapshotRepo.loadToday(context)
        val appearance = loadWidgetAppearance(context)
        WidgetPerf.end(trace, cache = if (data.stale) "stale" else "fresh",
            extra = "hasSnapshot=${data.hasSnapshot}")
        provideContent {
            val palette = WidgetTheme.of(context)
            val size = LocalSize.current
            // V2.4：按高度渐进。4×2=small(核心),4×3/4×4=medium+(加水杯),2×2=tiny
            // V2.5：固定 4×2，按用户截图那套 4×3 的完整元素渲染（实机下方空白证明空间够）。
            // 不再按 LocalSize 高度裁剪营养素/进度条/水杯。
            val tiny = false
            val withWater = true
            val showEaten = data.hasSnapshot && data.canShowRemaining
            val remainingText = when {
                !data.hasSnapshot || !data.goalConfigured || !data.canShowRemaining -> "--"
                else -> "${data.remainingKcal}"
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent, palette))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(WidgetSpec.CARD_PADDING_DP.dp)
                    .clickable(
                        actionStartActivity(
                            openApp(context, WidgetRefresher.TARGET_HOME),
                            actionParametersOf(TARGET_KEY to WidgetRefresher.TARGET_HOME)
                        )
                    )
            ) {
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        // 标题行（4×2 起就带目标胶囊）
                        Row(modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(WidgetSpec.LABEL_REMAINING,
                                style = TextStyle(color = textColor(palette.text),
                                    fontSize = if (tiny) 11.sp else 12.sp, fontWeight = FontWeight.Medium))
                            Spacer(GlanceModifier.defaultWeight())
                            if (!tiny) {
                                Box(modifier = GlanceModifier.background(ColorProvider(palette.greenLight))
                                    .cornerRadius(999.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(when (data.goalMode) {
                                        "gain" -> "增肌"; "keep" -> "保持"; else -> "减脂"
                                    }, style = TextStyle(color = textColor(palette.green), fontSize = 8.sp))
                                }
                            }
                        }
                        if (data.stale && !tiny) {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(todayStaleLabel(data), style = TextStyle(color = textColor(palette.sub), fontSize = 8.sp))
                        }
                        Spacer(GlanceModifier.height(if (tiny) 4.dp else 5.dp))

                        // 大数字（4×2 起 28sp，保证与营养素共存）
                        Row(verticalAlignment = Alignment.Vertical.Bottom) {
                            Text(remainingText, style = TextStyle(
                                color = textColor(when {
                                    !data.canShowRemaining -> palette.sub
                                    data.remainingKcal < 0 -> palette.overLimit
                                    else -> palette.green
                                }),
                                fontSize = if (withWater) 32.sp else if (tiny) 26.sp else 28.sp,
                                fontWeight = FontWeight.Bold))
                            Spacer(GlanceModifier.width(3.dp))
                            Text(WidgetSpec.LABEL_KCAL_UNIT,
                                style = TextStyle(color = textColor(palette.sub), fontSize = 11.sp),
                                modifier = GlanceModifier.padding(bottom = 3.dp))
                            if (showEaten && !withWater) {
                                Spacer(GlanceModifier.width(6.dp))
                                Text("${data.eatenKcal}/${data.goalKcal}" +
                                    if (data.bonusKcal > 0) " +${data.bonusKcal}" else "",
                                    style = TextStyle(color = textColor(palette.sub), fontSize = 9.sp),
                                    modifier = GlanceModifier.padding(bottom = 3.dp), maxLines = 1)
                            }
                        }

                        // 4×2 起：热量进度条（已吃/目标并入大数字同行，省一行高度）
                        if (showEaten) {
                            Spacer(GlanceModifier.height(2.dp))
                            val kcalP = WidgetProgress.fraction(data.eatenKcal, data.goalKcal)
                            Box(modifier = GlanceModifier.fillMaxWidth().height(3.dp)
                                .background(ColorProvider(palette.greenLight)).cornerRadius(1.5.dp)) {
                                if (kcalP > 0f) Box(modifier = GlanceModifier.width(
                                    WidgetLayout.fullBarWidth(size.width) * kcalP
                                ).height(3.dp).background(ColorProvider(palette.green)).cornerRadius(1.5.dp)) {}
                            }
                        }

                        // 4×2 起：三大营养素（渐进：4×2 极简一行，4×3+ 完整含标题）
                        if (!tiny && data.hasSnapshot) {
                            if (withWater) {
                                Spacer(GlanceModifier.height(7.dp))
                                Text("营养素 (g)", style = TextStyle(color = textColor(palette.text),
                                    fontSize = 10.sp, fontWeight = FontWeight.Medium))
                            }
                            Spacer(GlanceModifier.height(if (withWater) 4.dp else 5.dp))
                            val track = WidgetLayout.macroTrackWidth(size.width)
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                TodayMacro(if (withWater) "蛋白质" else "蛋白",
                                    data.proteinCurrent, data.proteinGoal, data.dietState, data.goalConfigured,
                                    palette.macroProtein, track, GlanceModifier.defaultWeight(), palette,
                                    compact = !withWater)
                                Spacer(GlanceModifier.width(WidgetSpec.MACRO_GAP_DP.dp))
                                TodayMacro("碳水", data.carbCurrent, data.carbGoal, data.dietState, data.goalConfigured,
                                    palette.macroCarb, track, GlanceModifier.defaultWeight(), palette,
                                    compact = !withWater)
                                Spacer(GlanceModifier.width(WidgetSpec.MACRO_GAP_DP.dp))
                                TodayMacro("脂肪", data.fatCurrent, data.fatGoal, data.dietState, data.goalConfigured,
                                    palette.macroFat, track, GlanceModifier.defaultWeight(), palette,
                                    compact = !withWater)
                            }
                        }
                    }
                    // 4×3+：右侧水杯（营养素略缩后腾出的空间）
                    if (withWater && data.hasSnapshot && data.waterState != ItemState.FAILED) {
                        Spacer(GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.width(58.dp),
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                            Image(
                                provider = ImageProvider(waterCupBitmap(data.waterPercent / 100f)),
                                contentDescription = "饮水 ${data.waterMl}/${data.waterGoalMl} 毫升",
                                modifier = GlanceModifier.size(width = 54.dp, height = 130.dp)
                                    // V3.0：改为进入首页并打开记录饮水弹窗（原静默+200ml 用户以为没反应）
                                    .clickable(jumpAction(context, WidgetRefresher.TARGET_WATER))
                            )
                            Spacer(GlanceModifier.height(3.dp))
                            Text("${data.waterMl}/${data.waterGoalMl}ml",
                                style = TextStyle(color = textColor(palette.sub), fontSize = 8.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** 今日剩余投影的 stale 文案：安全降级不伪造实时。 */
private fun todayStaleLabel(d: TodayRemainingWidgetData): String {
    if (!d.hasSnapshot) return WidgetSpec.LABEL_STALE_TODAY
    val time = java.time.Instant.ofEpochMilli(d.snapshotAt)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return "数据较旧 · 最近成功 $time"
}

/**
 * 今日剩余营养素列的【真实渲染门控】（收尾补充核查 P0）。
 * 这是 TodayMacro 唯一使用的进度来源：FAILED、目标未配置(profile EMPTY/无效)、goal<=0
 * 一律返回 null → UI 显示 --，绝不画空轨道。与剩余热量的 goalConfigured 门控保持同一语义。
 */
internal fun todayMacroProgress(
    state: ItemState, goalConfigured: Boolean, current: Int, goal: Int
): Float? =
    if (state == ItemState.FAILED || !goalConfigured || goal <= 0) null
    else WidgetProgress.fraction(current, goal)

/** 今日剩余的营养素列：失败/目标无效显示 --；只有可信目标才渲染进度轨道。 */
@Composable
private fun TodayMacro(
    label: String, current: Int, goal: Int, state: ItemState, goalConfigured: Boolean, color: Color,
    trackWidth: androidx.compose.ui.unit.Dp, modifier: GlanceModifier = GlanceModifier,
    palette: WidgetPalette,
    /** V2.4：4×2 极简档——标签/数字/条都压缩，不显示 current/goal 第二行。 */
    compact: Boolean = false
) {
    val progress = todayMacroProgress(state, goalConfigured, current, goal)
    val barH = if (compact) 3.dp else WidgetSpec.MACRO_BAR_HEIGHT_DP.dp
    Column(modifier = modifier) {
        Text(label, style = TextStyle(color = textColor(palette.sub),
            fontSize = if (compact) 8.sp else 9.sp))
        Text(if (progress == null) "--" else "$current",
            style = TextStyle(color = textColor(palette.text),
                fontSize = if (compact) 14.sp else 17.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(1.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().height(barH)
            .background(ColorProvider(color.copy(alpha = WidgetSpec.MACRO_TRACK_ALPHA)))
            .cornerRadius(barH / 2)) {
            if (progress != null && progress > 0f && trackWidth > 0.dp) {
                Box(modifier = GlanceModifier.width(trackWidth * progress)
                    .height(barH).background(ColorProvider(color)).cornerRadius(barH / 2)) {}
            }
        }
        if (!compact) {
            Spacer(GlanceModifier.height(2.dp))
            Text(if (progress == null) "--" else "$current/$goal",
                style = TextStyle(color = textColor(palette.sub), fontSize = 8.sp))
        }
    }
}

// ============================================================
// 3. 体重闪记 4×1
// ============================================================
class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val trace = WidgetPerf.begin("provideGlance", "WEIGHT")
        val data = loadWidgetData(context)
        val appearance = loadWidgetAppearance(context)
        WidgetPerf.end(trace, cache = if (data.stale) "stale" else "fresh")
        provideContent {
            val palette = WidgetTheme.of(context)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent, palette))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(horizontal = WidgetSpec.CARD_PADDING_H_DP.dp, vertical = WidgetSpec.WEIGHT_PADDING_V_DP.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = WidgetSpec.LABEL_CURRENT_WEIGHT,
                            style = TextStyle(color = textColor(palette.sub), fontSize = 11.sp)
                        )
                        if (data.stale) {
                            Text(
                                text = staleLabel(data),
                                style = TextStyle(color = textColor(palette.sub), fontSize = 9.sp)
                            )
                        }
                        Text(
                            text = if (!appearance.showWeight) {
                                WidgetSpec.LABEL_WEIGHT_HIDDEN
                            } else if (data.weightState == ItemState.FAILED) {
                                // P0：读体重失败 → --；无记录(EMPTY) → 原来的「还没有记录」，不显示 0kg。
                                "--"
                            } else {
                                itemText(data.weightState,
                                    data.latestWeight?.let {
                                        WeightUnit.formatWithSymbol(it, data.unit)
                                    } ?: WidgetSpec.LABEL_WEIGHT_EMPTY)
                            },
                            style = TextStyle(
                                color = textColor(
                                    if (!appearance.showWeight || data.weightState == ItemState.FAILED) palette.text else palette.green
                                ), // 视觉精修：有效体重用主题绿，对齐竞品
                                fontSize = WidgetSpec.WEIGHT_NUMBER_SP.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(GlanceModifier.width(WidgetSpec.WEIGHT_BUTTON_GAP_DP.dp))
                    // V2 阶段3：按钮缩窄、弱化底色，体重数字成为视觉主体；
                    // 保持 ≥40dp 高可点击面积（4×1 卡片高度受限，不再压缩到更小）。
                    Box(
                        modifier = GlanceModifier
                            .height(WidgetSpec.WEIGHT_BUTTON_H_DP.dp)
                            .width(WidgetSpec.WEIGHT_BUTTON_W_DP.dp)
                            .background(ColorProvider(palette.greenLight))
                            .cornerRadius(WidgetSpec.TILE_CORNER_DP.dp)
                            .clickable(
                                actionStartActivity(
                                    openApp(context, WidgetRefresher.TARGET_WEIGHT),
                                    actionParametersOf(TARGET_KEY to WidgetRefresher.TARGET_WEIGHT)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = WidgetSpec.LABEL_RECORD,
                            style = TextStyle(
                                color = textColor(palette.green),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 身体数据小组件（对齐进展页「身体数据」卡）：
 * 体重主体 + 目标 / 体脂 / BMI / 减重进度条。
 * 数据来自 WidgetSnapshotRepo（与进展页同一套 domain 纯函数口径），不造假、失败显示 --。
 */
class BodyWidget : GlanceAppWidget() {
    /**
     * 阶段D：复用现有 Provider 身份，切换到身体数据独立投影（body_data_v1）。
     * 4×4 主档完整指标；4×3 保留 BMI/体脂；旧 4×2 实例兼容。
     */
    /** V2.7：固定 4×2；恢复 V2.3 左侧布局原样，标题行右侧嵌缩小版 BMI 轮盘。 */
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val trace = WidgetPerf.begin("provideGlance", "BODY")
        val data = WidgetSnapshotRepo.loadBody(context)
        val appearance = loadWidgetAppearance(context)
        WidgetPerf.end(trace, cache = if (data.stale) "stale" else "fresh")
        provideContent {
            val palette = WidgetTheme.of(context)
            val size = LocalSize.current
            // V2.1阶段C：按实际 LocalSize 分档，不假设 XML targetCell 等于 Launcher 分配。
            val wide = size.width >= 300.dp && size.height >= 200.dp  // 5×4 扩展
            val full = !wide && size.height >= 200.dp                 // 4×4 主档
            val compact = !wide && !full && size.height < 200.dp      // 4×3/4×2 紧凑档
            Box(
                modifier = GlanceModifier.fillMaxSize()
                    .background(cardBg(appearance.alphaPercent, palette))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(WidgetSpec.CARD_PADDING_DP.dp)
                    .clickable(actionStartActivity(openApp(context, WidgetRefresher.TARGET_PROGRESS)))
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    // 标题独占一行：轮盘不再放这里（原 V2.7 把标题行撑到 36dp，
                    // 整块内容超高，底部 BMI/体脂的第三行小字被卡片裁切）。
                    // 轮盘下移到体重行，与体重数字持平；体脂率仍在下方双栏右侧，正好落在轮盘下。
                    Text("身体数据", style = TextStyle(color = textColor(palette.text),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    if (data.stale) {
                        Spacer(GlanceModifier.height(3.dp))
                        Text(bodyStaleLabel(data), style = TextStyle(color = textColor(palette.sub), fontSize = 8.sp))
                    }
                    Spacer(GlanceModifier.height(if (compact) 3.dp else 6.dp))

                    if (!data.canShowBody) {
                        Text(if (data.stale && !data.hasSnapshot) WidgetSpec.LABEL_NO_DATA else WidgetSpec.LABEL_WEIGHT_EMPTY,
                            style = TextStyle(color = textColor(palette.sub), fontSize = 12.sp))
                    } else {
                        // 体重数字 + BMI 轮盘同一行：轮盘与体重数字垂直居中持平。
                        Row(modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Vertical.CenterVertically) {
                            // 左列：体重新数字 + 目标
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Row(verticalAlignment = Alignment.Vertical.Bottom) {
                                    Text(if (data.latestWeight > 0) WeightUnit.format(data.latestWeight, data.unit) else "--",
                                        style = TextStyle(color = textColor(palette.text), fontSize = if (full || wide) 32.sp else 24.sp,
                                            fontWeight = FontWeight.Bold))
                                    Spacer(GlanceModifier.width(3.dp))
                                    Text(WeightUnit.symbol(data.unit),
                                        style = TextStyle(color = textColor(palette.sub), fontSize = 11.sp),
                                        modifier = GlanceModifier.padding(bottom = 4.dp))
                                }
                                Spacer(GlanceModifier.height(3.dp))
                                Text("目标 " + if (data.targetWeight > 0) WeightUnit.format(data.targetWeight, data.unit) else "--",
                                    style = TextStyle(color = textColor(palette.sub), fontSize = 10.sp))
                            }
                            Spacer(GlanceModifier.width(6.dp))
                            // 右列：缩小版 BMI 轮盘，与体重数字持平；体脂率在下方双栏右侧，落在其正下方。
                            Image(
                                provider = ImageProvider(bmiGaugeBitmap(data.bmi, palette)),
                                contentDescription = "BMI ${data.bmiCategory}",
                                modifier = GlanceModifier.width(70.dp).height(36.dp)
                            )
                        }

                        if (!compact && data.goalComputable && data.targetWeight > 0) {
                            Spacer(GlanceModifier.height(6.dp))
                            val p = data.lossProgress.coerceIn(0f, 1f)
                            Box(modifier = GlanceModifier.fillMaxWidth().height(5.dp)
                                .background(ColorProvider(palette.greenLight)).cornerRadius(2.5.dp)) {
                                if (p > 0f) Box(modifier = GlanceModifier.width(
                                    WidgetLayout.fullBarWidth(size.width) * p
                                ).height(5.dp).background(ColorProvider(palette.green)).cornerRadius(2.5.dp)) {}
                            }
                            Spacer(GlanceModifier.height(3.dp))
                            val dir = when (data.goalDirection) {
                                com.qingji.app.domain.BodyMetricsCalculator.GoalDirection.GAIN -> "增重"
                                com.qingji.app.domain.BodyMetricsCalculator.GoalDirection.MAINTAIN -> "维持"
                                else -> "减重"
                            }
                            Text(if (data.goalReached) "${dir}目标已达成" else
                                "${dir}目标 ${(p * 100).toInt()}% · 还差 ${WeightUnit.format(data.remainToTarget, data.unit)} ${WeightUnit.symbol(data.unit)}",
                                style = TextStyle(color = textColor(palette.sub), fontSize = 9.sp))
                        }

                        // 体脂率 / BMI 双栏（位置互换：体脂率在左、BMI 在右）。
                        // 用户反馈下方还有空间，字号放大：数值统一 22sp，标签/来源小字 10sp。
                        Spacer(GlanceModifier.height(if (compact) 4.dp else 8.dp))
                        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp)
                            .background(ColorProvider(palette.divider))) {}
                        Spacer(GlanceModifier.height(if (compact) 3.dp else 6.dp))
                        val dualValueSp = 22.sp
                        val dualLabelSp = 10.sp
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text("体脂率", style = TextStyle(color = textColor(palette.sub), fontSize = dualLabelSp))
                                Text(if (data.bodyFat != null) "${String.format("%.1f", data.bodyFat)}%" else "--",
                                    style = TextStyle(color = textColor(palette.text),
                                        fontSize = dualValueSp, fontWeight = FontWeight.Bold))
                                Text(bodyFatSourceLabel(data.bodyFatSource),
                                    style = TextStyle(color = textColor(if (data.bodyFatSource == "estimated") palette.warnOrange else palette.sub),
                                        fontSize = dualLabelSp))
                            }
                            Spacer(GlanceModifier.width(8.dp))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text("BMI", style = TextStyle(color = textColor(palette.sub), fontSize = dualLabelSp))
                                Text(if (data.bmi > 0) String.format("%.1f", data.bmi) else "--",
                                    style = TextStyle(color = textColor(bmiColor(data.bmiCategory, palette)),
                                        fontSize = dualValueSp, fontWeight = FontWeight.Bold))
                                Text(data.bmiCategory.ifBlank { "--" },
                                    style = TextStyle(color = textColor(bmiColor(data.bmiCategory, palette)), fontSize = dualLabelSp))
                            }
                        }
                        if (full || wide) {
                            Spacer(GlanceModifier.height(6.dp))
                            Text("最近记录 " + (data.latestRecordDate?.toString() ?: "--") +
                                " · 共 ${data.recordCount} 次",
                                style = TextStyle(color = textColor(palette.sub), fontSize = 8.sp))
                        }
                    }
                }
            }
        }
    }
}

/** 身体数据 stale 文案。 */
private fun bodyStaleLabel(d: BodyDataWidgetData): String {
    if (!d.hasSnapshot) return WidgetSpec.LABEL_STALE_WEIGHT
    val time = java.time.Instant.ofEpochMilli(d.snapshotAt)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    return "数据较旧 · 最近成功 $time"
}

/** 体脂来源必须明确区分实测/估算，不能让用户误认。 */
private fun bodyFatSourceLabel(source: String) = when (source) {
    "measured" -> "实测"
    "estimated" -> "估算 · 仅趋势参考"
    "unsupported" -> "暂不支持估算"
    else -> "完善资料后估算"
}

/** V2.3 高保真：生成水杯静态帧（与首页 WaterCup 同形状/配色，固定语义色不随主题）。 */
private fun waterCupBitmap(level: Float): android.graphics.Bitmap =
    WidgetArtwork.waterCup(
        widthPx = 156, heightPx = 380, level = level,
        outline = android.graphics.Color.parseColor("#9FC9DA"),
        fill = android.graphics.Color.parseColor("#7EC8E3"),
        surface = android.graphics.Color.parseColor("#AEDCEC"),
        bubbleTint = android.graphics.Color.parseColor("#E6F4FA")
    )

/** Compose Color → Android ARGB Int（WidgetArtwork 用 Android Canvas 绘制需要）。 */
private fun argb(c: Color): Int =
    android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(),
        (c.green * 255).toInt(), (c.blue * 255).toInt())

/** 生成 BMI 仪表盘 Bitmap（与 App MiniBmiGauge 同算法）。 */
private fun bmiGaugeBitmap(bmi: Double, palette: WidgetPalette): android.graphics.Bitmap =
    WidgetArtwork.bmiGauge(
        bmi = bmi, widthPx = 240, heightPx = 124,
        green = argb(palette.green),
        warn = argb(palette.warnOrange),
        danger = argb(palette.overLimit),
        needle = argb(palette.text),
        track = argb(palette.divider)
    )

private fun bmiColor(category: String, palette: WidgetPalette) = when (category) {
    "正常" -> palette.green
    "超重" -> palette.warnOrange
    "肥胖" -> palette.overLimit
    else -> palette.sub
}
