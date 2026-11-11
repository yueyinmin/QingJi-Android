package com.qingji.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.domain.AppClock
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
    val historyLogs by vm.historyLogs.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    // 打开常驻通知时顺手申请通知权限（Android 13+ 必须）。
    // 不给权限也照常保存开关，只是通知栏看不到 —— 断食状态本身仍由时间推算。
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.setOngoingNotification(true) }

    // v1.0.3：今天是否在生效周期内（1..7，周一=1）
    val fastingDays = settings.fastingDays
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()
    val isFastingDay = AppClock.today().dayOfWeek.value in fastingDays

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
        enabled = settings.fastingEnabled && isFastingDay
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

            // 断食常驻通知：只读现有方案状态，不改断食规则、不碰数据库
            FastingNotifyCard(
                enabled = settings.fastingOngoingNotification,
                onToggle = { enabled ->
                    val needsPermission = enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setOngoingNotification(enabled)
                    }
                }
            )
            Spacer(Modifier.height(Dimens.Gap))

            if (!settings.fastingEnabled) {
                HintBox(text = "还没开启轻断食。点右上角「设置」选个方案（16:8 / 14:10 / 12:12 / 18:6 / 20:4）就能开始。")
                Spacer(Modifier.height(Dimens.Gap))
                NotesCard()
            } else {
                if (!isFastingDay) {
                    HintBox(text = "今天不在你的断食周期里（可在设置里改生效星期），想吃就吃，计划明天继续。")
                    Spacer(Modifier.height(Dimens.Gap))
                }
                TimeBanner(status)
                Spacer(Modifier.height(Dimens.Gap))
                FastingRing(status)
                Spacer(Modifier.height(Dimens.Gap))
                PhaseCard(status)
                Spacer(Modifier.height(Dimens.Gap))
                WeekBars(weekLogs, settings.fastingPlanHours)
                Spacer(Modifier.height(Dimens.Gap))
                HistoryCard(historyLogs)
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
            onSave = { enabled, hours, start, end, days, phaseAlert ->
                showSettings = false
                vm.saveSettings(enabled, hours, start, end, days, phaseAlert)
            }
        )
    }
}

/**
 * 断食常驻通知开关（2026-11-11 专项，第一轮只做这一项）。
 *
 * 通知内容完全由现有 [FastingRules.status] 推出来：断食中就显示计时和剩余时间，
 * 进食窗口内就显示窗口时间。所以它不改断食规则、不新增数据库字段，
 * 关掉开关或杀掉服务都不会影响计算结果。
 */
@Composable
private fun FastingNotifyCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "常驻通知",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "通知栏显示断食计时和剩余时间，无声、不可划掉",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
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
/** v1.0.3：历史记录卡——近 30 天的断食计划倒序列表。 */
@Composable
private fun HistoryCard(logs: List<com.qingji.app.data.local.entity.FastingLog>) {
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
                        Text(
                            text = (if (isToday) "今天 · " else "${log.date.monthValue}月${log.date.dayOfMonth}日 · ") +
                                FastingRules.planLabel(log.planHours),
                            fontSize = 14.sp,
                            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "进食窗口 " +
                                FastingRules.minuteLabel(log.eatStartMinute) + " - " +
                                FastingRules.minuteLabel(log.eatEndMinute),
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (isToday) "进行中" else "按计划",
                        fontSize = 12.sp,
                        color = if (isToday) QingJiGreenDark else TextSecondary
                    )
                }
            }
        }
    }
}

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
    onSave: (enabled: Boolean, planHours: Int, startMinute: Int, endMinute: Int, days: String, phaseAlert: Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(current.fastingEnabled) }
    var planHours by remember { mutableStateOf(current.fastingPlanHours) }
    var startMinute by remember { mutableStateOf(current.fastingEatStartMinute) }
    var endMinute by remember { mutableStateOf(current.fastingEatEndMinute) }

    var fastingDays by remember { mutableStateOf(current.fastingDays) }
    var phaseAlert by remember { mutableStateOf(current.fastingPhaseAlert) }
    val daySet = fastingDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    com.qingji.app.ui.components.AppBottomSheet(
        title = "轻断食设置",
        confirmText = "保存",
        onConfirm = {
            onSave(enabled, planHours, startMinute, endMinute, fastingDays, phaseAlert)
        },
        onDismiss = onDismiss
    ) {
            Column(
                modifier = Modifier
                    .height(480.dp)
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
                FastingRules.PLANS.chunked(3).forEach { rowPlans ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPlans.forEach { hours ->
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
                    if (rowPlans.size < 3) {
                        repeat(3 - rowPlans.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                }

                Spacer(Modifier.height(14.dp))

                // v1.0.3：生效周期（周几多选，默认全选）
                Text(text = "生效周期", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7)
                        .forEach { (label, day) ->
                            val on = day in daySet
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (on) QingJiGreenDark else TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (on) QingJiGreenLight else PageBackground)
                                    .clickable {
                                        val next = if (on) daySet - day else daySet + day
                                        fastingDays = if (next.isEmpty()) fastingDays else next.sorted().joinToString(",")
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                }

                Spacer(Modifier.height(14.dp))

                // v1.0.3：时段切换提醒（按进食窗口自动在开始/结束时提醒）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { phaseAlert = !phaseAlert }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔔", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "时段切换提醒", fontSize = 15.sp, color = TextPrimary)
                        Text(
                            text = "到进食开始、断食开始的时间点提醒你一声",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (phaseAlert) "已开启" else "未开启",
                        fontSize = 12.sp,
                        color = if (phaseAlert) QingJiGreen else TextSecondary
                    )
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
                    text = "窗口外的时段就是断食期。结束时间可以早于开始时间（比如 22:00-06:00，跨零点也能算）。",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
    }
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
