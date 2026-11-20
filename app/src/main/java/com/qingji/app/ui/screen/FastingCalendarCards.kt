package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FastingRules
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary


/** 断食期间的注意事项。 */
/** v1.0.3：历史记录卡——近 30 天的断食计划倒序列表。 */
/**
 * 断食日历（2026-11-11 专项）。
 *
 * 纯展示：数据只读近一年的 fasting_log，不加字段、不改规则。
 * 周一开头（国内习惯），有记录的日子浅绿底，今天淡绿底，点某天在下方看那天的详情。
 * 月份只能在「11 个月前 ~ 今天所在月」之间翻——再往前数据没查，翻了也是空白。
 */
@Composable
internal fun FastingCalendarCard(
    logs: List<com.qingji.app.data.local.entity.FastingLog>,
    today: java.time.LocalDate
) {
    val byDate = remember(logs) { logs.associateBy { it.date } }
    val minMonth = remember(today) { java.time.YearMonth.from(today).minusMonths(11) }
    val maxMonth = remember(today) { java.time.YearMonth.from(today) }
    var month by remember { mutableStateOf(maxMonth) }
    var selected by remember { mutableStateOf<java.time.LocalDate?>(null) }

    SectionCard {
        // 标题 + 月份切换
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "断食日历",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "‹",
                fontSize = 18.sp,
                color = if (month > minMonth) QingJiGreenDark else TextSecondary.copy(alpha = 0.3f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = month > minMonth) { month = month.minusMonths(1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = "›",
                fontSize = 18.sp,
                color = if (month < maxMonth) QingJiGreenDark else TextSecondary.copy(alpha = 0.3f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = month < maxMonth) { month = month.plusMonths(1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Spacer(Modifier.height(4.dp))

        // 日期网格
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FastingRules.monthCells(month).chunked(7).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    row.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f))
                            return@forEach
                        }
                        val mark = FastingRules.dayMark(date, today, byDate.keys)
                        val isToday = date == today
                        val isSelected = date == selected
                        val bg = when {
                            isSelected -> QingJiGreen
                            mark == FastingRules.DayMark.RECORDED -> QingJiGreenLight
                            isToday -> QingJiGreen.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                        val fg = when {
                            isSelected -> Color.White
                            mark == FastingRules.DayMark.FUTURE -> TextSecondary.copy(alpha = 0.35f)
                            mark == FastingRules.DayMark.RECORDED -> QingJiGreenDark
                            isToday -> QingJiGreenDark
                            else -> TextSecondary
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(bg)
                                // 今天单独加一圈边框：否则「今天有记录」和「别的日子有记录」长一样，
                                // 一眼找不到今天在哪
                                .then(
                                    if (isToday && !isSelected) {
                                        Modifier.border(1.dp, QingJiGreen, RoundedCornerShape(9.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(enabled = mark != FastingRules.DayMark.FUTURE) {
                                    selected = if (isSelected) null else date
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${date.dayOfMonth}",
                                fontSize = 11.sp,
                                fontWeight = if (mark == FastingRules.DayMark.RECORDED || isToday) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = fg
                            )
                        }
                    }
                }
            }
        }

        // 选中那天的详情
        selected?.let { day ->
            Spacer(Modifier.height(10.dp))
            val log = byDate[day]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(QingJiGreenLight.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "${day.monthValue} 月 ${day.dayOfMonth} 日",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        log == null -> "这天没有断食记录"
                        log.freeMode -> "自由断食 · ${FastingRules.freeDurationLabel(log.actualMinutes)} · " +
                            FastingRules.minuteLabel(log.eatStartMinute) + " - " +
                            FastingRules.minuteLabel(log.eatEndMinute)
                        else -> "${FastingRules.planLabel(log.planHours)} · 进食窗口 " +
                            FastingRules.minuteLabel(log.eatStartMinute) + " - " +
                            FastingRules.minuteLabel(log.eatEndMinute)
                    },
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}


@Composable
internal fun HistoryCard(logs: List<com.qingji.app.data.local.entity.FastingLog>) {
    SectionCard {
        Text(text = "历史记录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        if (logs.isEmpty()) {
            Text(text = "还没有记录，保存一次方案就会开始记。", fontSize = 13.sp, color = TextSecondary)
        } else {
            val today = AppClock.today()
            logs.sortedByDescending { it.date }.take(10).forEach { log ->
                val isToday = log.date == today
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val head = if (isToday) "今天 · " else "${log.date.monthValue}月${log.date.dayOfMonth}日 · "
                        Text(
                            text = head + if (log.freeMode) "自由断食" else FastingRules.planLabel(log.planHours),
                            fontSize = 14.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (log.freeMode) {
                                // 自由断食记的是真实起止，不显示「进食窗口」这个方案概念
                                "断食 ${FastingRules.freeDurationLabel(log.actualMinutes)} · " +
                                    FastingRules.minuteLabel(log.eatStartMinute) + " - " +
                                    FastingRules.minuteLabel(log.eatEndMinute)
                            } else {
                                "进食窗口 " +
                                    FastingRules.minuteLabel(log.eatStartMinute) + " - " +
                                    FastingRules.minuteLabel(log.eatEndMinute)
                            },
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (log.freeMode) "已完成" else if (isToday) "进行中" else "按计划",
                        fontSize = 12.sp,
                        color = if (log.freeMode || isToday) QingJiGreenDark else TextSecondary
                    )
                }
            }
        }
    }
}
