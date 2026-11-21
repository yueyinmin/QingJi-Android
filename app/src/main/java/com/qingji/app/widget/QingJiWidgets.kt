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
private val W_CARD get() = WidgetSpec.CARD
private val W_TEXT get() = WidgetSpec.TEXT
private val W_SUB get() = WidgetSpec.SUB
private val W_GREEN get() = WidgetSpec.GREEN
private val W_GREEN_LIGHT get() = WidgetSpec.GREEN_LIGHT
private val W_BLUE_LIGHT get() = WidgetSpec.BLUE_LIGHT

/**
 * 三大营养素固定色（v3.5.1 与设置页预览对齐）。
 * 小组件是另一个进程，读不到 App 内存里的主题状态（QingJiThemeState），
 * 所以不能用 ui.theme 的 MacroProtein 等 getter，取默认主题（green）的三个值写死：
 * 蛋白蓝 / 碳水绿 / 脂肪橙黄——和「桌面小组件」页预览里那三条一个颜色。
 */
private val W_MACRO_PROTEIN get() = WidgetSpec.MACRO_PROTEIN
private val W_MACRO_CARB get() = WidgetSpec.MACRO_CARB
private val W_MACRO_FAT get() = WidgetSpec.MACRO_FAT

/** 背景透明度：设置里存的是 30..100 的百分比，这里换成 Color 的 0..1 alpha。 */
private fun cardBg(alphaPercent: Int = 100) =
    ColorProvider(W_CARD.copy(alpha = (alphaPercent.coerceIn(30, 100) / 100f)))

private fun textColor(color: Color = W_TEXT) = ColorProvider(color)

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
    val s = SettingsStore.observe(context).first()
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

// ============================================================
// 1. 快捷记录 2×2
// ============================================================
class QuickRecordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appearance = loadWidgetAppearance(context)
        provideContent {
            val size = LocalSize.current
            // 横向被拉宽（≥200dp）时切换为一排四栏；默认 2×2 用两行两列，
            // 保证 2×2 下每个入口仍有足够触控面积（工单 V2.0 §2.3）。
            val wide = size.width >= 200.dp
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = WidgetSpec.LABEL_QUICK_TITLE,
                        style = TextStyle(
                            color = textColor(W_SUB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    // 统一弱底色容器：四个入口不再各自铺高饱和色块
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(ColorProvider(W_GREEN_LIGHT.copy(alpha = 0.45f)))
                            .cornerRadius(12.dp)
                            .padding(4.dp)
                    ) {
                        if (wide) {
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                QuickTile(context, R.drawable.ic_widget_breakfast, WidgetSpec.LABEL_BREAKFAST,
                                    WidgetRefresher.TARGET_TEXT_AI, GlanceModifier.defaultWeight())
                                Spacer(GlanceModifier.width(2.dp))
                                QuickTile(context, R.drawable.ic_widget_weight, WidgetSpec.LABEL_WEIGHT,
                                    WidgetRefresher.TARGET_WEIGHT, GlanceModifier.defaultWeight())
                                Spacer(GlanceModifier.width(2.dp))
                                WaterTile(GlanceModifier.defaultWeight())
                                Spacer(GlanceModifier.width(2.dp))
                                QuickTile(context, R.drawable.ic_widget_camera, WidgetSpec.LABEL_PHOTO,
                                    WidgetRefresher.TARGET_IMAGE, GlanceModifier.defaultWeight())
                            }
                        } else {
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                QuickTile(context, R.drawable.ic_widget_breakfast, WidgetSpec.LABEL_BREAKFAST,
                                    WidgetRefresher.TARGET_TEXT_AI, GlanceModifier.defaultWeight())
                                Spacer(GlanceModifier.width(2.dp))
                                QuickTile(context, R.drawable.ic_widget_weight, WidgetSpec.LABEL_WEIGHT,
                                    WidgetRefresher.TARGET_WEIGHT, GlanceModifier.defaultWeight())
                            }
                            Spacer(GlanceModifier.height(2.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                                WaterTile(GlanceModifier.defaultWeight())
                                Spacer(GlanceModifier.width(2.dp))
                                QuickTile(context, R.drawable.ic_widget_camera, WidgetSpec.LABEL_PHOTO,
                                    WidgetRefresher.TARGET_IMAGE, GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 图标 + 短标签的轻量入口（跳转型）。 */
@Composable
private fun QuickTile(
    context: Context,
    iconRes: Int,
    label: String,
    target: String,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier
            .background(ColorProvider(W_CARD.copy(alpha = 0.55f)))
            .cornerRadius(10.dp)
            .clickable(
                actionStartActivity(
                    openApp(context, target),
                    actionParametersOf(TARGET_KEY to target)
                )
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = label,
            modifier = GlanceModifier.size(18.dp)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(color = textColor(W_TEXT), fontSize = 10.sp)
        )
    }
}

/** 喝水入口：保持 AddWaterAction 直接写 200ml，不改成跳转。 */
@Composable
private fun WaterTile(modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .background(ColorProvider(W_CARD.copy(alpha = 0.55f)))
            .cornerRadius(10.dp)
            .clickable(actionRunCallback<AddWaterAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_water),
            contentDescription = WidgetSpec.LABEL_WATER,
            modifier = GlanceModifier.size(18.dp)
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = WidgetSpec.LABEL_WATER,
            style = TextStyle(color = textColor(W_TEXT), fontSize = 10.sp)
        )
    }
}

// ============================================================
// 2. 今日概览 4×2
// ============================================================
class OverviewWidget : GlanceAppWidget() {
    /**
     * 阶段 3：响应启动器实际分配的空间，按 2×2 / 4×2 / 4×4 三档切换内容密度，
     * 不把同一布局整体缩放（工单 §5）。Provider 身份与 targetCell 均不变。
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),   // 2×2
            DpSize(250.dp, 110.dp),   // 4×2
            DpSize(250.dp, 250.dp)    // 4×4
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val appearance = loadWidgetAppearance(context)
        provideContent {
            val size = LocalSize.current
            val density = when {
                size.height >= 200.dp -> 3   // 4×4：完整概览
                size.width >= 200.dp -> 2    // 4×2：营养+饮水
                else -> 1                     // 2×2：只要关键数字
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(14.dp)
                    .clickable(
                        actionStartActivity(
                            openApp(context, WidgetRefresher.TARGET_HOME),
                            actionParametersOf(TARGET_KEY to WidgetRefresher.TARGET_HOME)
                        )
                    )
            ) {
                // v3.5.1：布局与「桌面小组件」页预览对齐——
                // 大数字 + 三个并排彩色营养短条 + 左下角饮水，去掉旧的右侧数字与全宽白条
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = WidgetSpec.LABEL_REMAINING,
                        style = TextStyle(color = textColor(W_SUB), fontSize = 11.sp)
                    )
                    if (data.stale) {
                        Text(
                            // W-03：旧快照必须标注更新时间，不能伪装成刚刷新
                            text = staleLabel(data),
                            style = TextStyle(color = textColor(W_SUB), fontSize = 9.sp)
                        )
                    }
                    Row(verticalAlignment = Alignment.Vertical.Bottom) {
                        Text(
                            // 阶段 1：与首页 remainingWithBonus 同口径（含运动补偿）。
                            // 首次失败无快照时 remainingKcal=默认值，但上面已明确标注不可用，
                            // 不把它伪装成真实剩余额度。
                            text = if (data.stale && !data.hasSnapshot) "--" else "${data.remainingKcal}",
                            style = TextStyle(
                                color = textColor(
                                    if (!data.stale && data.remainingKcal < 0) {
                                        WidgetSpec.OVER_LIMIT
                                    } else {
                                        W_TEXT
                                    }
                                ),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = WidgetSpec.LABEL_KCAL_UNIT,
                            style = TextStyle(color = textColor(W_SUB), fontSize = 13.sp),
                            modifier = GlanceModifier.padding(bottom = 5.dp)
                        )
                    }

                    // 2×2 只保留关键数字；4×2 起才显示营养与饮水
                    if (density >= 2 && appearance.showMacro) {
                        Spacer(GlanceModifier.height(10.dp))
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            MacroBar(
                                label = WidgetSpec.LABEL_PROTEIN,
                                current = data.protein,
                                goal = data.proteinGoal,
                                color = W_MACRO_PROTEIN,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            MacroBar(
                                label = WidgetSpec.LABEL_CARB,
                                current = data.carb,
                                goal = data.carbGoal,
                                color = W_MACRO_CARB,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            MacroBar(
                                label = WidgetSpec.LABEL_FAT,
                                current = data.fat,
                                goal = data.fatGoal,
                                color = W_MACRO_FAT,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    // V2 阶段2：去掉 defaultWeight 弹性留白，三层信息紧凑排列，
                    // 饮水紧贴营养素区，不再被推到卡片底部造成中间大块空白。
                    if (density >= 2 && appearance.showWater) {
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            text = "${WidgetSpec.LABEL_WATER_SHORT} ${data.water} / ${data.waterGoal} ml",
                            style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
                        )
                    }

                    // 4×4：完整健康概览。W-06 体重遵守 showWeight；W-07 训练/睡眠失败显示 --
                    if (density >= 3) {
                        Spacer(GlanceModifier.height(4.dp))
                        val workoutText = if (data.workoutAvailable) "${data.workoutMinutes} 分" else "--"
                        val sleepText = if (data.sleepAvailable) "${data.sleepMinutes} 分" else "--"
                        val weightText = if (!appearance.showWeight) "" else
                            (data.latestWeight?.let { " · 体重 ${WeightUnit.formatWithSymbol(it, data.unit)}" } ?: "")
                        Text(
                            text = "训练 $workoutText · 睡眠 $sleepText$weightText",
                            style = TextStyle(color = textColor(W_SUB), fontSize = 9.sp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一条彩色营养短条（对齐预览样式）：标签小字在上，下面一条细彩条，
 * 淡色做轨道、同色做进度；和预览一致，不显示数字。
 */
@Composable
private fun MacroBar(
    label: String,
    current: Int,
    goal: Int,
    color: Color,
    modifier: GlanceModifier = GlanceModifier
) {
    // 目标缺失(<=0)时安全显示空轨道，不伪造进度；比例钳制在 0..1 防止溢出。
    val progress = if (goal <= 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
        )
        Spacer(GlanceModifier.height(3.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(WidgetSpec.MACRO_BAR_HEIGHT_DP.dp)
                .background(ColorProvider(color.copy(alpha = 0.25f)))
                .cornerRadius(2.5.dp)
        ) {
            if (progress > 0f) {
                // V2 阶段2：进度按轨道实际可用宽度的比例填充，不再写死 64dp，
                // 横向拉宽后不会被固定长度截断，也不会超出容器。
                // Glance 1.1.1 的 fillMaxWidth() 不支持 fraction。
                // 三条并排且各有间距，单条轨道约为 Widget 宽度的 1/3.5；
                // 用该估算宽度 × 比例，既随容器缩放也不会溢出轨道。
                val trackWidth = LocalSize.current.width / 3.5f
                Box(
                    modifier = GlanceModifier
                        .width(trackWidth * progress)
                        .height(WidgetSpec.MACRO_BAR_HEIGHT_DP.dp)
                        .background(ColorProvider(color))
                        .cornerRadius(2.5.dp)
                ) {}
            }
        }
    }
}

// ============================================================
// 3. 体重闪记 4×1
// ============================================================
class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val appearance = loadWidgetAppearance(context)
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent))
                    .cornerRadius(WidgetSpec.CORNER_CARD_DP.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = WidgetSpec.LABEL_CURRENT_WEIGHT,
                            style = TextStyle(color = textColor(W_SUB), fontSize = 11.sp)
                        )
                        if (data.stale) {
                            Text(
                                text = staleLabel(data),
                                style = TextStyle(color = textColor(W_SUB), fontSize = 9.sp)
                            )
                        }
                        Text(
                            text = if (!appearance.showWeight) {
                                WidgetSpec.LABEL_WEIGHT_HIDDEN
                            } else if (data.stale && !data.hasSnapshot) {
                                "--"
                            } else {
                                data.latestWeight?.let {
                                    WeightUnit.formatWithSymbol(it, data.unit)
                                } ?: WidgetSpec.LABEL_WEIGHT_EMPTY
                            },
                            style = TextStyle(
                                color = textColor(W_TEXT),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Box(
                        modifier = GlanceModifier
                            .height(36.dp)
                            .width(88.dp)
                            .background(ColorProvider(W_GREEN))
                            .cornerRadius(12.dp)
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
                                color = textColor(Color.White),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
