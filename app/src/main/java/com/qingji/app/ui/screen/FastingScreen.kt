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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.domain.FastingRules
import com.qingji.app.domain.FastingStatus
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.FastingViewModel
import kotlinx.coroutines.delay

/**
 * ============================================================
 * 轻断食（v2.0）
 * ------------------------------------------------------------
 * 一屏说完：现在是不是断食中、断了多久、还剩多久、身体在哪个阶段。
 *
 * 三个设计取舍：
 * 1. 不做后台服务 —— 状态全靠「当前时间 + 进食窗口」现算，每 30 秒刷一次；
 * 2. 不弹通知 —— 通知需要常驻前台服务，一期先不做，避免耗电和权限麻烦；
 * 3. 文案只写常见代谢科普，明确标注不构成医疗建议（合规红线）。
 * ============================================================
 */
@Composable
fun FastingScreen(
    onBack: () -> Unit,
    vm: FastingViewModel = viewModel()
) {
    val settings by vm.settings.collectAsState()
    val weekLogs by vm.weekLogs.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    // 每 30 秒推进一次当前时间，倒计时才会走
    LaunchedEffect(Unit) {
        while (true) {
            vm.tick()
            delay(30_000L)
        }
    }

    val status = FastingRules.status(
        now = vm.now,
        planHours = settings.fastingPlanHours,
        eatStartMinute = settings.fastingEatStartMinute,
        eatEndMinute = settings.fastingEatEndMinute,
        enabled = settings.fastingEnabled
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(
            title = "轻断食",
            onBack = onBack,
            actionText = "设置",
            onAction = { showSettings = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            if (!settings.fastingEnabled) {
                HintBox(text = "还没开启轻断食。点右上角「设置」选个方案（16:8 / 14:10 / 12:12）就能开始。")
                Spacer(Modifier.height(Dimens.Gap))
                NotesCard()
            } else {
                TimeBanner(status)
                Spacer(Modifier.height(Dimens.Gap))
                FastingRing(status)
                Spacer(Modifier.height(Dimens.Gap))
                PhaseCard(status)
                Spacer(Modifier.height(Dimens.Gap))
                WeekBars(weekLogs, settings.fastingPlanHours)
                Spacer(Modifier.height(Dimens.Gap))
                NotesCard()
            }

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showSettings) {
        FastingSettingsDialog(
            current = settings,
            onDismiss = { showSettings = false },
            onSave = { enabled, hours, start, end ->
                showSettings = false
                vm.saveSettings(enabled, hours, start, end)
            }
        )
    }
}

/** 顶部时间条：断食开始 → 可进食。 */
@Composable
private fun TimeBanner(status: FastingStatus) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "断食开始", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = FastingRules.minuteLabel(status.eatEndMinute),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(text = "→", fontSize = 18.sp, color = QingJiGreen)
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "可以进食", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = FastingRules.minuteLabel(status.eatStartMinute),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = QingJiGreenDark
                )
            }
        }
    }
}

/** 圆环倒计时。 */
@Composable
private fun FastingRing(status: FastingStatus) {
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
                color = Color(0xFFE6EAEE),
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
private fun PhaseCard(status: FastingStatus) {
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
@Composable
private fun WeekBars(
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

/** 断食期间的注意事项。 */
@Composable
private fun NotesCard() {
    val notes = listOf(
        "充分补水" to "优先喝白水；无糖茶、黑咖啡也可以，别喝含糖饮料。",
        "关注电解质" to "头晕乏力可少量补点海盐或电解质；仍不适就结束断食。",
        "规律作息" to "睡够、作息固定，能明显减轻饥饿感和白天疲劳。",
        "轻度活动" to "散步、拉伸、瑜伽这类低强度活动更合适，别硬上高强度训练。",
        "守住断食边界" to "断食期间不要摄入含糖饮品、零食，否则整段断食白费。",
        "识别危险信号" to "持续头晕、心悸、出冷汗、剧烈饥饿伴抖动时请立即结束并就医。"
    )

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🛡", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "断食期间注意事项",
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(QingJiGreenLight)
                .padding(10.dp)
        ) {
            Text(
                text = "断食期间若感到明显不适，请立即结束断食；症状持续或加重时请及时就医，别勉强坚持。",
                fontSize = 11.sp,
                color = QingJiGreenDark
            )
        }
        Spacer(Modifier.height(10.dp))
        notes.forEachIndexed { index, (title, detail) ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row {
                Text(text = "·", fontSize = 13.sp, color = QingJiGreen)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(text = detail, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

/** 断食设置：启用、方案、进食窗口。 */
@Composable
private fun FastingSettingsDialog(
    current: QingJiSettings,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Int, Int) -> Unit
) {
    var enabled by remember { mutableStateOf(current.fastingEnabled) }
    var planHours by remember { mutableStateOf(current.fastingPlanHours) }
    var startMinute by remember { mutableStateOf(current.fastingEatStartMinute) }
    var endMinute by remember { mutableStateOf(current.fastingEatEndMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("轻断食设置", fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 启用开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { enabled = !enabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⏱", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "启用轻断食",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (enabled) "已开启" else "未开启",
                        fontSize = 12.sp,
                        color = if (enabled) QingJiGreen else TextSecondary
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(text = "方案", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingRules.PLANS.forEach { hours ->
                        val selected = hours == planHours
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) QingJiGreenLight else PageBackground)
                                .clickable { planHours = hours }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = FastingRules.planLabel(hours),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) QingJiGreenDark else TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = FastingRules.planSubtitle(hours),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(text = "进食窗口", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                TimeAdjustRow(
                    label = "开始进食",
                    minute = startMinute,
                    onMinus = { startMinute = (startMinute - 30 + 1440) % 1440 },
                    onPlus = { startMinute = (startMinute + 30) % 1440 }
                )
                Spacer(Modifier.height(8.dp))
                TimeAdjustRow(
                    label = "结束进食",
                    minute = endMinute,
                    onMinus = { endMinute = (endMinute - 30 + 1440) % 1440 },
                    onPlus = { endMinute = (endMinute + 30) % 1440 }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "窗口外的时段就是断食期。上面选的方案小时数会用来算阶段说明。",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enabled, planHours, startMinute, endMinute) }) {
                Text("保存", color = QingJiGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
private fun TimeAdjustRow(
    label: String,
    minute: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .background(PageBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        StepButton("−", onMinus)
        Text(
            text = FastingRules.minuteLabel(minute),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 15.sp, color = QingJiGreenDark)
    }
}
