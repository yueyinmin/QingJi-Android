package com.qingji.app.ui.screen.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FoodDayGrouping
import com.qingji.app.domain.FoodDayNutrition
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.RecordDateDialog
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.screen.MealCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.FoodDayDetailViewModel
import java.time.LocalDate

/**
 * ============================================================
 * 饮食日详情（QJ-RECORD-INPUT-UNIFIED-V3 / B5）
 * ------------------------------------------------------------
 * 页面组织参考「饮水详情」：顶部标题 + 日期 + 汇总 + 分区内容；
 * 餐食内容直接复用首页的 [MealCard]，
 * 所以同一条 FoodLog 在首页和这里必然显示成一样的名字/克数/热量/三大营养素
 * —— 不是「再写一套看起来差不多的卡片」，而是同一个组件。
 *
 * 与单餐详情 [com.qingji.app.ui.screen.MealDetailScreen] 的分工：
 * 那个是「某天某一餐，重点在重新识别 / 改餐次」；
 * 这个是「某天四餐全景，重点在跨餐对比与补看历史」。
 * 点某一餐的标题仍可进单餐详情，两个页面不互相替代。
 * ============================================================
 */
@Composable
fun FoodDayDetailScreen(
    dateIso: String,
    onBack: () -> Unit,
    onOpenMealDetail: (LocalDate, String) -> Unit,
    vm: FoodDayDetailViewModel = viewModel()
) {
    // 导航参数 → ViewModel（非法日期回退今天，绝不因此空白或崩溃）
    LaunchedEffect(dateIso) {
        val day = runCatching { LocalDate.parse(dateIso) }.getOrDefault(AppClock.today())
        vm.bind(day)
    }

    val date by vm.date.collectAsState()
    val logs by vm.logs.collectAsState()
    // 分组与汇总都是纯函数，logs 不变就不重算
    val grouped = remember(logs) { FoodDayGrouping.group(logs) }
    val totals = remember(logs) { FoodDayGrouping.totals(logs) }
    // 折叠态按「日期 + 餐次」存，切换日期不串状态（与首页一致）
    var mealExpanded by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showDateWheel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(
            title = "饮食详情",
            onBack = onBack,
            actionText = "换日期",
            onAction = { showDateWheel = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 日期 + 前后一天 ----------
            DateSwitchRow(
                date = date,
                onPrev = { vm.setDate(date.minusDays(1)) },
                onNext = { vm.setDate(date.plusDays(1)) }
            )

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 全天汇总 ----------
            DaySummaryCard(totals)

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 四餐（复用首页 MealCard） ----------
            MealRules.ALL.forEach { type ->
                val items = grouped[type].orEmpty()
                val key = "${date}|$type"
                val expanded = mealExpanded[key] ?: items.isNotEmpty()
                androidx.compose.runtime.key(key) {
                    MealCard(
                        type = type,
                        items = items,
                        onDelete = { id -> vm.deleteLog(id) },
                        onOpenDetail = { onOpenMealDetail(date, type) },
                        expanded = expanded,
                        onToggleExpand = { mealExpanded = mealExpanded + (key to !expanded) }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (logs.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "这一天还没有饮食记录。回首页记一笔，或用食物库加一餐。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showDateWheel) {
        RecordDateDialog(
            initialDate = date,
            onDismiss = { showDateWheel = false },
            onConfirm = { picked ->
                vm.setDate(picked)
                showDateWheel = false
            }
        )
    }
}

/** 日期 + 前一天/后一天：翻历史最快的方式，不用每次开滚轮。 */
@Composable
private fun DateSwitchRow(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val today = AppClock.today()
    val canGoNext = date.isBefore(today)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardWhite)
                .pressable { onPrev() },
            contentAlignment = Alignment.Center
        ) {
            Text("‹", fontSize = 18.sp, color = QingJiGreenDark)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = AppClock.formatDateFull(date),
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = when (date) {
                    today -> "今天"
                    today.minusDays(1) -> "昨天"
                    else -> "历史记录"
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (canGoNext) CardWhite else QingJiGreenLight)
                .clickable(enabled = canGoNext) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "›",
                fontSize = 18.sp,
                color = if (canGoNext) QingJiGreenDark else TextSecondary
            )
        }
    }
}

/** 全天汇总：总热量 + 三大营养素，口径与首页/单餐详情一致（直接对字段求和）。 */
@Composable
private fun DaySummaryCard(totals: FoodDayNutrition) {
    SectionCard {
        Text(text = "全天总热量", fontSize = TextSizes.Caption, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${totals.kcal.toInt()}",
                fontSize = TextSizes.HugeNumber,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "千卡",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${totals.itemCount} 项记录",
                fontSize = TextSizes.Caption,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        MacroRow("蛋白", totals.protein, totals.protein * 4 / totals.macroKcal, MacroProtein)
        Spacer(Modifier.height(8.dp))
        MacroRow("碳水", totals.carb, totals.carb * 4 / totals.macroKcal, MacroCarb)
        Spacer(Modifier.height(8.dp))
        MacroRow("脂肪", totals.fat, totals.fat * 9 / totals.macroKcal, MacroFat)
    }
}

@Composable
private fun MacroRow(name: String, grams: Double, ratio: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.width(32.dp)
        )
        ThinProgressBar(
            progress = ratio.toFloat().coerceIn(0f, 1f),
            modifier = Modifier.weight(1f),
            color = color,
            barHeight = 6.dp
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "${grams.toInt()} g",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.width(42.dp)
        )
    }
}
