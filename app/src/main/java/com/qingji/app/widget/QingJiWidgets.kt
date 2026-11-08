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
 * ============================================================
 */

// ---------- 小组件统一配色（写死，保证任何主题下都看得清） ----------
private val W_CARD = Color(0xFFFFFFFF)
private val W_TEXT = Color(0xFF1A1A1A)
private val W_SUB = Color(0xFF8A8F98)
private val W_GREEN = Color(0xFF1F8A70)
private val W_GREEN_LIGHT = Color(0xFFE8F5EF)
private val W_BLUE_LIGHT = Color(0xFFE8F4FB)
private val W_TRACK = Color(0xFFEEF0F2)

private fun cardBg() = ColorProvider(W_CARD)
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
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg())
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
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg())
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .clickable(
                        actionStartActivity(
                            openApp(context, WidgetRefresher.TARGET_HOME),
                            actionParametersOf(TARGET_KEY to WidgetRefresher.TARGET_HOME)
                        )
                    )
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "今日剩余",
                                style = TextStyle(color = textColor(W_SUB), fontSize = 11.sp)
                            )
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
                            Text(
                                text = "千卡 · 已吃 ${data.eatenKcal} / ${data.goalKcal}",
                                style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.Horizontal.End) {
                            Text(
                                text = "饮水 ${data.water}/${data.waterGoal}ml",
                                style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp)
                            )
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = "点开看详情 ›",
                                style = TextStyle(color = textColor(W_GREEN), fontSize = 10.sp)
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    MiniBar("蛋白", data.protein, data.proteinGoal)
                    Spacer(GlanceModifier.height(5.dp))
                    MiniBar("碳水", data.carb, data.carbGoal)
                    Spacer(GlanceModifier.height(5.dp))
                    MiniBar("脂肪", data.fat, data.fatGoal)
                }
            }
        }
    }
}

/** 一条迷你营养条：左边名称 + 数字，下面一条进度。 */
@Composable
private fun MiniBar(label: String, current: Int, goal: Int) {
    val progress = if (goal <= 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = label,
                style = TextStyle(color = textColor(W_SUB), fontSize = 10.sp),
                modifier = GlanceModifier.width(28.dp)
            )
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .background(ColorProvider(W_TRACK))
                    .cornerRadius(2.dp)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = GlanceModifier
                            .width((96 * progress).dp)
                            .height(4.dp)
                            .background(ColorProvider(W_GREEN))
                            .cornerRadius(2.dp)
                    ) {}
                }
            }
            Text(
                text = " $current/${goal}g",
                style = TextStyle(color = textColor(W_TEXT), fontSize = 10.sp)
            )
        }
    }
}

// ============================================================
// 3. 体重闪记 4×1
// ============================================================
class WeightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg())
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
                            text = data.latestWeight?.let {
                                WeightUnit.formatWithSymbol(it, data.unit)
                            } ?: "还没有记录",
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
