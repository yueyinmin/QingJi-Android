package com.qingji.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.ChartMath
import com.qingji.app.domain.DiaryRules
import com.qingji.app.ui.components.LegendDot
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.WarnOrange
import java.time.LocalDate


/** 时间范围切换：点了真的会过滤曲线数据。 */
@Composable
internal fun TimeRangeChips(
    selectedDays: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(
        "近一周" to 7,
        "近一个月" to 30,
        "近90天" to 90,
        "全部" to null
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(CardWhite)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { (label, days) ->
            val selected = selectedDays == days
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (selected) QingJiGreenDark else TextSecondary,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) QingJiGreenLight else Color.Transparent)
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}


/**
 * 体重曲线（M4 补齐）：
 * 实线 = 实测体重；深绿斜虚线 = 目标轨迹（起始体重 → 目标体重线性插值）；
 * 浅绿横线 = 目标体重基准；手指点按能看某一天的具体数值。
 *
 * 所有坐标换算走 ChartMath（纯 Kotlin、有单元测试），包括「只有 1 条记录」
 * 这种容易崩的边界，界面这里不自己算数。
 */
@Composable
internal fun WeightChart(
    logs: List<WeightLog>,
    startWeight: Double,
    targetWeight: Double,
    /** 一条记录都没有时（例如刚引导完 / 清除过记录），用档案里的当前体重当起点 */
    fallbackWeight: Double = 0.0,
    unit: String = "kg"
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // 换了时间范围导致条数变化，清掉旧选中，避免指向不存在的下标
    LaunchedEffect(logs.size) { selectedIndex = null }

    // 进场时曲线从左往右「画」出来（0 → 1），跟手写一遍曲线一样
    var chartEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chartEntered = true }
    val reveal by animateFloatAsState(
        targetValue = if (chartEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "chartReveal"
    )

    val values = logs.map { it.weightKg.toFloat() }.ifEmpty {
        if (fallbackWeight > 0.0) listOf(fallbackWeight.toFloat()) else emptyList()
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = s("体重走势"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = "共 ${logs.size} 条记录", fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PageBackground),
            contentAlignment = Alignment.Center
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "这个时间范围还没有体重记录",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(16.dp)
                        .pointerInput(logs.size) {
                            detectTapGestures { offset ->
                                selectedIndex = ChartMath.nearestIndex(
                                    offset.x, logs.size, size.width.toFloat()
                                )
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val bounds = ChartMath.paddedBounds(
                        values,
                        if (targetWeight > 0) targetWeight.toFloat() else null
                    )
                    val points = ChartMath.points(values, bounds, w, h)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                    // ① 目标体重横向基准线
                    if (targetWeight > 0) {
                        val y = ChartMath.yOf(targetWeight.toFloat(), bounds, h)
                        drawLine(
                            color = QingJiGreenLight,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dash
                        )
                    }

                    // ② 目标轨迹：从起始体重到目标体重线性下降
                    if (startWeight > 0 && targetWeight > 0 && points.size >= 2) {
                        val trajectory = ChartMath.targetTrajectory(
                            startWeight.toFloat(), targetWeight.toFloat(), points.size
                        ).mapIndexed { index, value ->
                            Offset(points[index].x, ChartMath.yOf(value, bounds, h))
                        }
                        for (i in 1 until trajectory.size) {
                            drawLine(
                                color = QingJiGreenDark,
                                start = trajectory[i - 1],
                                end = trajectory[i],
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = dash
                            )
                        }
                    }

                    // ③ 实际体重实线 + ④ 记录点
                    // 用 clipRect 限制只画到 w * reveal 的位置，
                    // reveal 从 0 涨到 1 的过程看起来就是「线正在被画出来」。
                    clipRect(right = w * reveal) {
                        if (points.size >= 2) {
                            val path = Path().apply {
                                points.forEachIndexed { index, point ->
                                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                                }
                            }
                            drawPath(path, color = QingJiGreen, style = Stroke(width = 3.dp.toPx()))
                        }

                        // ④ 每个记录点 + 选中点高亮
                        points.forEachIndexed { index, point ->
                            val active = selectedIndex == index
                            drawCircle(
                                color = if (active) Color.White else QingJiGreen,
                                radius = if (active) 7.dp.toPx() else 4.dp.toPx(),
                                center = Offset(point.x, point.y)
                            )
                            if (active) {
                                drawCircle(
                                    color = QingJiGreen,
                                    radius = 4.dp.toPx(),
                                    center = Offset(point.x, point.y)
                                )
                            }
                        }
                    }
                }

                // 点按后显示那一天的具体数值
                selectedIndex?.let { index ->
                    logs.getOrNull(index)?.let { log ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${log.date} · ${
                                    com.qingji.app.domain.WeightUnit.formatWithSymbol(log.weightKg, unit)
                                }",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = QingJiGreenDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = logs.firstOrNull()?.date?.toString().orEmpty(),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Row {
                LegendDot(color = QingJiGreen, label = "实际")
                Spacer(Modifier.padding(6.dp))
                if (targetWeight > 0) LegendDot(color = QingJiGreenDark, label = "目标轨迹")
            }
            Text(
                text = logs.lastOrNull()?.date?.toString().orEmpty(),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(12.dp))

        // 最新体重 + 区间变化量（需求 M4）
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s("最新"), fontSize = 12.sp, color = TextSecondary)
                Text(
                    text = values.lastOrNull()?.let {
                        com.qingji.app.domain.WeightUnit.formatWithSymbol(it.toDouble(), unit)
                    } ?: "--",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(text = s("本区间变化"), fontSize = 12.sp, color = TextSecondary)
                Text(
                    text = run {
                        val first = values.firstOrNull()
                        val last = values.lastOrNull()
                        when {
                            first == null || last == null -> "--"
                            kotlin.math.abs(last - first) < 0.05f -> "持平"
                            else -> com.qingji.app.domain.WeightUnit
                                .changeText((last - first).toDouble(), unit) +
                                if (last < first) " ↓" else " ↑"
                        }
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QingJiGreen
                )
            }
        }
        if (logs.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = s("只有 1 条记录，再记几次就能连成曲线"),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}



// ============================================================
// 以下是本轮新增的五个模块（近一周饮水 / 营养摄入 / 运动消耗 /
// 身体维度 / 进度照片）。都放在进展页，共用 SectionCard 的样式。
// ============================================================

/** 七根柱子：近一周每天的饮水量。 */
/**
 * 饮食 30 天热力图（2026-11-12，对标竞品「吃有数」）。
 *
 * 网格排法复用日记模块的 DiaryRules.cells（近 30 天滚动、周一开头、前后补位），
 * 着色按当天摄入 / 目标热量：
 *   没记录 = 灰；0-80% = 绿（吃得偏少）；80-120% = 黄（贴着预算）；120% 以上 = 红（超了）。
 * 点一天，下方显示那天的摄入和目标。
 */
@Composable
fun DietHeatmapCard(
    logs: List<com.qingji.app.data.local.entity.FoodLog>,
    dailyGoal: Double
) {
    val today = AppClock.today()
    val kcalByDate = remember(logs) {
        logs.groupBy { it.date }.mapValues { entry -> entry.value.sumOf { it.kcal } }
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "饮食 · 近 30 天",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(6.dp))
            // v1.4（工单 8.2）：明确标识这是「连续 30 天滚动窗口」，不是自然月历——
            // 两种视图不得混用同一标题造成误解。
            Text(
                text = "连续 30 天滚动视图",
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DividerColor.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        val cells = DiaryRules.cells(today, 30)
        val leading = cells.first().dayOfWeek.value - 1
        // v1.4：尾部也补齐到 7 的倍数。否则最后一行格子数不满 7 时，
        // weight(1f) 会把这几个格子拉宽，跟上面的行对不齐（工单 8.2 排列真实性问题）。
        val grid: List<LocalDate?> =
            List(leading) { null } + cells + List((7 - (leading + cells.size) % 7) % 7) { null }

        // 搬到训练日记后，右侧有 4 颗悬浮按钮（50dp + 右留 16dp）。
        // 给网格整体留 44dp 安全边距，最后一列才不会被按钮挡住点不到。
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            grid.chunked(7).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f))
                            return@forEach
                        }
                        val eaten = kcalByDate[date] ?: 0.0
                        val hasRecord = eaten > 0.0
                        val isSelected = date == selected
                        val bg = when {
                            isSelected -> QingJiGreen
                            !hasRecord -> DividerColor.copy(alpha = 0.5f)
                            eaten < dailyGoal * 0.8 -> QingJiGreen
                            eaten <= dailyGoal * 1.2 -> WarnOrange
                            else -> DangerRed
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bg)
                                .clickable { selected = if (isSelected) null else date },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 图例：四种颜色（含灰=未记录），wrapContent 居中，不随宽度拉伸
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = QingJiGreen, label = "0-80%")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = WarnOrange, label = "80-120%")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = DangerRed, label = "120%+")
            Spacer(Modifier.width(10.dp))
            LegendDot(color = DividerColor.copy(alpha = 0.5f), label = "未记录")
        }

        selected?.let { day ->
            Spacer(Modifier.height(8.dp))
            val eaten = kcalByDate[day] ?: 0.0
            Text(
                text = if (eaten > 0.0) {
                    "${day.monthValue}月${day.dayOfMonth}日 · 摄入 ${eaten.toInt()} 千卡 / 目标 ${dailyGoal.toInt()} 千卡"
                } else {
                    "${day.monthValue}月${day.dayOfMonth}日 · 当天没有饮食记录"
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
