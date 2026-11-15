package com.qingji.app.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FastingRules
import com.qingji.app.domain.FastingStatus
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes


/** 胶囊按钮：filled = 实底绿（主操作），否则浅绿（次操作）。 */
@Composable
internal fun PillText(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) QingJiGreen else QingJiGreenLight)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else QingJiGreenDark
        )
    }
}


/** 开始时刻 → 「今天 20:00 / 昨天 20:00 / 11月9日 20:00」。 */
internal fun startLabel(millis: Long): String {
    val zoned = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val date = zoned.toLocalDate()
    val today = AppClock.today()
    val day = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
    return "%s %02d:%02d".format(day, zoned.hour, zoned.minute)
}


/** 顶部时间条：断食开始 → 可进食。 */
@Composable
internal fun TimeBanner(status: FastingStatus, now: java.time.LocalTime) {
    // 2026-11-11：时间条以前只有「15:40 → 07:40」，看不出是哪天。
    // 凌晨（还没到进食窗口开始）时断食是昨天起的、今天就能吃；
    // 其余情况今天开始断食、明天才能吃。
    val nowMinute = now.hour * 60 + now.minute
    val startedYesterday = nowMinute < status.eatStartMinute
    val startDay = if (startedYesterday) "昨天" else "今天"
    val eatDay = if (startedYesterday) "今天" else "明天"

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "断食开始时间", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$startDay ${FastingRules.minuteLabel(status.eatEndMinute)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(text = "→", fontSize = 18.sp, color = QingJiGreen)
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "可进食时间", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$eatDay ${FastingRules.minuteLabel(status.eatStartMinute)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = QingJiGreenDark
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 这句是用户最常问的：到底要不要自己点开始/结束
        Text(
            text = "到点自动开始和结束，不需要手动操作；两段切换前会发提醒",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}


/** 圆环倒计时。 */
@Composable
internal fun FastingRing(status: FastingStatus) {
    val target = FastingRules.progress(status)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "fastingRing"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(210.dp)) {
            val stroke = 15.dp.toPx()
            // 底圈
            drawArc(
                color = DividerColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // 进度圈：从 12 点方向开始顺时针
            if (progress > 0f) {
                drawArc(
                    color = QingJiGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (status.active) "断食中" else "可以进食",
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (status.active) {
                    FastingRules.durationLabel(status.elapsedMinutes)
                } else {
                    FastingRules.planLabel(status.planHours)
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (status.active) {
                    "剩余 ${FastingRules.durationLabel(status.remainMinutes)}"
                } else {
                    "断食 ${status.planHours} 小时"
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}


/** 当前身体阶段的说明。 */
@Composable
internal fun PhaseCard(status: FastingStatus) {
    val (title, detail) = FastingRules.phase(status.elapsedHours)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(QingJiGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📈", fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (status.active) "$title · 已断食 ${status.elapsedHours.toInt()}h" else "当前不在断食时段",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (status.active) detail else "在进食窗口内正常吃就好，别忘了记饮食",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "以上为常见代谢科普参考，个体差异大，不构成医疗建议。",
            fontSize = 10.sp,
            color = TextSecondary
        )
    }
}


/** 近 7 日断食柱状图。 */
/**
 * 断食成就（2026-11-11）：口径是「连续断食天数」。
 *
 * 不累计历史总天数 —— 断食最怕三天打鱼两天晒网，连续才值得鼓励。
 * 里程碑只放 6 个档位（3/7/14/30/60/100），够得着才有意义；
 * 达成填实底、未达成浅底，一眼能看出还差多远。
 */
@Composable
internal fun FastingStreakCard(days: Int) {
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "连续断食",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (days <= 0) {
                        "今天或昨天记一次断食，连续天数就开始累计"
                    } else {
                        FastingRules.nextMilestoneText(days)
                    },
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = "$days",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (days > 0) QingJiGreenDark else TextSecondary
            )
            Text(
                text = " 天",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FastingRules.STREAK_MILESTONES.forEach { (target, name) ->
                val done = days >= target
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (done) QingJiGreen else QingJiGreenLight)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = name,
                        fontSize = 9.sp,
                        color = if (done) QingJiGreenDark else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@Composable
internal fun WeekBars(
    logs: List<com.qingji.app.data.local.entity.FastingLog>,
    planHours: Int
) {
    val byDate = logs.associateBy { it.date }
    val days = (0..6).map { offset ->
        com.qingji.app.domain.AppClock.today().minusDays((6 - offset).toLong())
    }
    val peak = maxOf(16, planHours)

    SectionCard {
        Text(
            text = "近 7 日断食",
            fontSize = TextSizes.CardTitle,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { date ->
                val hours = byDate[date]?.planHours ?: 0
                val ratio = (hours.toFloat() / peak).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(70.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PageBackground),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (ratio > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((70 * ratio).dp)
                                    .background(QingJiGreen)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (date.dayOfWeek.value) {
                            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
                            5 -> "五"; 6 -> "六"; else -> "日"
                        },
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
        if (logs.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "还没有断食记录。在设置里保存一次方案，今天这条就会记下来。",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}
