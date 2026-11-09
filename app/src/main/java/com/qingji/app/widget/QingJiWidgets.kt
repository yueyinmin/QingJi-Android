package com.qingji.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.qingji.app.MainActivity
import com.qingji.app.data.prefs.SettingsStore
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
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
private val W_CARD = Color(0xFFFFFFFF)
private val W_TEXT = Color(0xFF1A1A1A)
private val W_SUB = Color(0xFF8A8F98)
private val W_GREEN = Color(0xFF1F8A70)
private val W_GREEN_LIGHT = Color(0xFFE8F5EF)
private val W_BLUE_LIGHT = Color(0xFFE8F4FB)

/**
 * 三大营养素固定色（v3.5.1 与设置页预览对齐）。
 * 小组件是另一个进程，读不到 App 内存里的主题状态（QingJiThemeState），
 * 所以不能用 ui.theme 的 MacroProtein 等 getter，取默认主题（green）的三个值写死：
 * 蛋白蓝 / 碳水绿 / 脂肪橙黄——和「桌面小组件」页预览里那三条一个颜色。
 */
private val W_MACRO_PROTEIN = Color(0xFF5B8DEF)
private val W_MACRO_CARB = Color(0xFF1F8A70)
private val W_MACRO_FAT = Color(0xFFF5A623)

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

/** 小组件要展示的数据，一次查完。 */
private data class WidgetData(
    val goalKcal: Int = 2000,
    val eatenKcal: Int = 0,
    val protein: Int = 0,
    val proteinGoal: Int = 0,
    val carb: Int = 0,
    val carbGoal: Int = 0,
    val fat: Int = 0,
    val fatGoal: Int = 0,
    val water: Int = 0,
    val waterGoal: Int = 2000,
    val latestWeight: Double? = null,
    val unit: String = "kg"
)

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

/** 从数据库现查，查不到就给默认值 —— 小组件绝不能因为读库失败而变空白。 */
private suspend fun loadWidgetData(context: Context): WidgetData {
    return try {
        val repo = QingJiRepository(context)
        val today = AppClock.today()
        val profile = repo.observeProfile().first()
        val summary = repo.observeDailySummary(today).first()
        val water = repo.observeWaterMl(today).first() ?: 0
        val latest = repo.latestWeight()?.weightKg
        WidgetData(
            goalKcal = profile?.dailyKcalGoal?.takeIf { it > 0 } ?: 2000,
            eatenKcal = (summary?.totalKcal ?: 0.0).toInt(),
            protein = (summary?.totalProtein ?: 0.0).toInt(),
            proteinGoal = profile?.proteinGoalG ?: 0,
            carb = (summary?.totalCarb ?: 0.0).toInt(),
            carbGoal = profile?.carbGoalG ?: 0,
            fat = (summary?.totalFat ?: 0.0).toInt(),
            fatGoal = profile?.fatGoalG ?: 0,
            water = water,
            waterGoal = profile?.waterGoalMl?.takeIf { it > 0 } ?: 2000,
            latestWeight = latest,
            unit = profile?.unit ?: "kg"
        )
    } catch (e: Exception) {
        WidgetData()
    }
}

// ============================================================
// 1. 快捷记录 2×2
// ============================================================
class QuickRecordWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appearance = loadWidgetAppearance(context)
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent))
                    .cornerRadius(18.dp)
                    .padding(10.dp)
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Text(
                        text = "记一笔",
                        style = TextStyle(
                            color = textColor(W_SUB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        QuickTile(
                            label = "早餐",
                            color = W_GREEN_LIGHT,
                            textColorValue = W_GREEN,
                            target = WidgetRefresher.TARGET_TEXT_AI,
                            context = context,
                            modifier = GlanceModifier.defaultWeight().height(40.dp)
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        QuickTile(
                            label = "体重",
                            color = W_GREEN_LIGHT,
                            textColorValue = W_GREEN,
                            target = WidgetRefresher.TARGET_WEIGHT,
                            context = context,
                            modifier = GlanceModifier.defaultWeight().height(40.dp)
                        )
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        // 喝水：直接记账，不打开 App（PRD 验收清单点名要求）
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .height(40.dp)
                                .background(ColorProvider(W_BLUE_LIGHT))
                                .cornerRadius(12.dp)
                                .clickable(actionRunCallback<AddWaterAction>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "喝水",
                                style = TextStyle(
                                    color = textColor(Color(0xFF2A7FA8)),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        QuickTile(
                            label = "拍照",
                            color = W_GREEN_LIGHT,
                            textColorValue = W_GREEN,
                            target = WidgetRefresher.TARGET_IMAGE,
                            context = context,
                            modifier = GlanceModifier.defaultWeight().height(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    color: Color,
    textColorValue: Color,
    target: String,
    context: Context,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .background(ColorProvider(color))
            .cornerRadius(12.dp)
            .clickable(
                actionStartActivity(
                    openApp(context, target),
                    actionParametersOf(TARGET_KEY to target)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = textColor(textColorValue),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ============================================================
// 2. 今日概览 4×2
// ============================================================
class OverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val appearance = loadWidgetAppearance(context)
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg(appearance.alphaPercent))
                    .cornerRadius(18.dp)
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
                        text = "今日剩余",
                        style = TextStyle(color = textColor(W_SUB), fontSize = 11.sp)
                    )
                    Row(verticalAlignment = Alignment.Vertical.Bottom) {
                        Text(
                            text = "${data.goalKcal - data.eatenKcal}",
                            style = TextStyle(
                                color = textColor(
                                    if (data.goalKcal - data.eatenKcal < 0) {
                                        Color(0xFFE5544B)
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
                            text = "千卡",
                            style = TextStyle(color = textColor(W_SUB), fontSize = 13.sp),
                            modifier = GlanceModifier.padding(bottom = 5.dp)
                        )
                    }

                    if (appearance.showMacro) {
                        Spacer(GlanceModifier.height(10.dp))
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            MacroBar(
                                label = "蛋白",
                                current = data.protein,
                                goal = data.proteinGoal,
                                color = W_MACRO_PROTEIN,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            MacroBar(
                                label = "碳水",
                                current = data.carb,
                                goal = data.carbGoal,
                                color = W_MACRO_CARB,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Spacer(GlanceModifier.width(8.dp))
                            MacroBar(
                                label = "脂肪",
                                current = data.fat,
                                goal = data.fatGoal,
                                color = W_MACRO_FAT,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    Spacer(GlanceModifier.defaultWeight())

                    if (appearance.showWater) {
                        Text(
                            text = "饮水 ${data.water} / ${data.waterGoal} ml",
                            style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
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
    val progress = if (goal <= 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)
    val barWidth = 64.dp
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
        )
        Spacer(GlanceModifier.height(3.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(5.dp)
                .background(ColorProvider(color.copy(alpha = 0.25f)))
                .cornerRadius(2.5.dp)
        ) {
            if (progress > 0f) {
                Box(
                    modifier = GlanceModifier
                        .width(barWidth * progress)
                        .height(5.dp)
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
                    .cornerRadius(18.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = "当前体重",
                            style = TextStyle(color = textColor(W_SUB), fontSize = 11.sp)
                        )
                        Text(
                            text = if (!appearance.showWeight) {
                                "已隐藏"
                            } else {
                                data.latestWeight?.let {
                                    WeightUnit.formatWithSymbol(it, data.unit)
                                } ?: "还没有记录"
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
                            text = "记录",
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
