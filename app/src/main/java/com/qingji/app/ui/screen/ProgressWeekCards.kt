package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.MacroCarb
import com.qingji.app.ui.theme.MacroFat
import com.qingji.app.ui.theme.MacroProtein
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.pressable
import java.time.LocalDate


@Composable
internal fun WaterWeekCard(week: List<Pair<LocalDate, Int>>, goalMl: Int) {
    val total = week.sumOf { it.second }
    val peak = maxOf(goalMl, week.maxOfOrNull { it.second } ?: 0, 1)
    val barColor = QingJiGreen.copy(alpha = 0.65f)

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s("近一周饮水"),
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(text = "共 $total ml", fontSize = TextSizes.Caption, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))

        if (week.isEmpty()) {
            Text(text = s("还没有饮水记录"), fontSize = 13.sp, color = TextSecondary)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                week.forEach { (date, ml) ->
                    val ratio = (ml.toFloat() / peak).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(PageBackground),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (ratio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(ratio)
                                        .background(barColor)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${date.dayOfMonth}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "目标 $goalMl ml/天 · 柱子满格代表达标",
                fontSize = TextSizes.Tiny,
                color = TextSecondary
            )
        }
    }
}


/** 近一周营养：每天热量 + 平均三大营养素。 */
@Composable
internal fun NutritionWeekCard(foodWeek: List<com.qingji.app.data.local.entity.FoodLog>) {
    val byDate = foodWeek.groupBy { it.date }
    val days = (0..6).map { offset -> AppClock.today().minusDays((6 - offset).toLong()) }
    val kcalByDay = days.map { d -> d to (byDate[d]?.sumOf { it.kcal } ?: 0.0) }
    val peak = maxOf(kcalByDay.maxOfOrNull { it.second } ?: 0.0, 1.0)
    val avgProtein = foodWeek.sumOf { it.proteinG } / 7.0
    val avgCarb = foodWeek.sumOf { it.carbG } / 7.0
    val avgFat = foodWeek.sumOf { it.fatG } / 7.0
    val totalKcal = foodWeek.sumOf { it.kcal }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s("近一周营养摄入"),
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "共 ${totalKcal.toInt()} 千卡",
                fontSize = TextSizes.Caption,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            kcalByDay.forEach { (date, kcal) ->
                val ratio = (kcal / peak).coerceIn(0.0, 1.0).toFloat()
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PageBackground),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(ratio)
                                    .background(QingJiGreen)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${date.dayOfMonth}", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MacroFigure("日均蛋白", avgProtein, MacroProtein)
            MacroFigure("日均碳水", avgCarb, MacroCarb)
            MacroFigure("日均脂肪", avgFat, MacroFat)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = s("按最近 7 天平均算；没记的日子也计入分母"),
            fontSize = TextSizes.Tiny,
            color = TextSecondary
        )
    }
}


@Composable
internal fun MacroFigure(label: String, grams: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${grams.toInt()} g",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}


/** 本周运动消耗。 */
@Composable
fun ExerciseWeekCard(
    list: List<com.qingji.app.data.local.entity.ExerciseLog>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val total = list.sumOf { it.kcal }

    // 360dp 窄屏走查：右侧 QuickActionColumn 是 4 颗 52dp 圆按钮、右留 16dp，
    // 浮在内容之上，占住屏幕右侧 (W-68dp) ~ (W-16dp) 这一条。
    // 而卡片内容区右边界是 (W-16dp 外层 - 16dp SectionCard) = W-32dp，
    // v10：44dp 让位取消——快捷按钮默认收起，展开时浮到内容上层，
    // 主人明确「不要怕遮挡」；fabSafeEnd 保留为 0，以后要恢复让位改回这里即可。
    val fabSafeEnd = 0.dp

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = fabSafeEnd),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("本周运动消耗"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "共 ${total.toInt()} 千卡 · ${list.size} 次",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Text(
                text = s("+ 记一次"),
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(QingJiGreenLight)
                    .pressable { onAdd() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        if (list.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            list.takeLast(3).reversed().forEach { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .padding(end = fabSafeEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🏃", fontSize = 14.sp)
                    Spacer(Modifier.size(8.dp))
                    // v1.0.6：原来一行塞「日期 类型 N 分钟」，再想显示步数就放不下了。
                    // 改成两行：第一行日期 + 类型，第二行明细（分钟一定有，步数/公里
                    // 只在用户确实按那个方式记过时才出现，都记为 0 时不显示假数据）。
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${log.date.monthValue}/${log.date.dayOfMonth} ${log.type}",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = exerciseDetail(log),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(text = "${log.kcal.toInt()} 千卡", fontSize = 12.sp, color = QingJiGreenDark)
                    Spacer(Modifier.size(6.dp))
                    // 原来只是 Text 加 4dp 内边距，点击热区不到 24dp，
                    // 就算没被悬浮栏挡住也很难点中；改成 36dp 透明方形热区，
                    // 视觉上还是同一个「×」，只是更容易点。
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onDelete(log.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "×", fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}


/**
 * 运动记录的第二行明细（v1.0.6）。
 *
 * 分钟一定有（任何方式记录都会换算出分钟）；步数和公里只在用户
 * 确实按那个方式填过时才显示 —— 存的是 0 表示「当时没按这个方式记」，
 * 不是「走了 0 步」，所以不能显示成「0 步」。
 */
internal fun exerciseDetail(log: com.qingji.app.data.local.entity.ExerciseLog): String {
    val parts = mutableListOf("${log.minutes} 分钟")
    if (log.steps > 0) parts.add("${log.steps} 步")
    if (log.distanceKm > 0) parts.add(kmText(log.distanceKm))
    return parts.joinToString(" · ")
}


/** 公里数去掉多余的 .0：1.0 → 「1 公里」，5.5 → 「5.5 公里」。 */
internal fun kmText(km: Double): String {
    val raw = String.format(java.util.Locale.US, "%.1f", km)
    val trimmed = if (raw.endsWith(".0")) raw.dropLast(2) else raw
    return "$trimmed 公里"
}
