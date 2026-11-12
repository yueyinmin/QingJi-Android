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
import androidx.compose.foundation.border
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
import com.qingji.app.ui.theme.WarnOrange
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
    val yearLogs by vm.yearLogs.collectAsState()

    // 连续断食天数（成就口径）：近一年记录里，从今天往前一天接一天数
    val streakDays = remember(yearLogs) {
        FastingRules.consecutiveDays(
            fastingDates = yearLogs.map { it.date }.toSet(),
            today = AppClock.today()
        )
    }
    var showSettings by remember { mutableStateOf(false) }

    // 自由断食：自定义开始时间（复用运动模块的日期+时分三列滚轮）
    var showFreeStartPicker by remember { mutableStateOf(false) }

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

            // 连续断食放最上面：打开页第一眼就是「坚持了几天」
            FastingStreakCard(days = streakDays)
            Spacer(Modifier.height(Dimens.Gap))

            if (!settings.fastingEnabled) {
                // 未开启：方案预览卡（含断食开始/可进食的具体时间 + 一键开启）
                FastingPreviewCard(
                    planHours = settings.fastingPlanHours,
                    eatStartMinute = settings.fastingEatStartMinute,
                    eatEndMinute = settings.fastingEatEndMinute,
                    onEnable = { vm.enableFasting() }
                )
            } else {
                if (!isFastingDay) {
                    HintBox(text = "今天不在你的断食周期里（可在设置里改生效星期），想吃就吃，计划明天继续。")
                    Spacer(Modifier.height(Dimens.Gap))
                }
                // 断食时间（中上位置，打开一眼看到）
                TimeBanner(status, vm.now)
                Spacer(Modifier.height(Dimens.Gap))
                // 断食方案（点一颗直接切换，进食窗口仍在右上角设置里调）
                FastingPlanCard(
                    planHours = settings.fastingPlanHours,
                    onSelect = { vm.updatePlanHours(it) }
                )
                Spacer(Modifier.height(Dimens.Gap))
                FastingRing(status)
                Spacer(Modifier.height(Dimens.Gap))
                PhaseCard(status)
                Spacer(Modifier.height(Dimens.Gap))
                WeekBars(weekLogs, settings.fastingPlanHours)
            }

            // 常驻通知：放在方案下面
            Spacer(Modifier.height(Dimens.Gap))
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

            // 自由断食：不预设方案、可自定义开始时间；与常驻通知联动
            FastingFreeCard(
                freeStart = settings.fastingFreeStart,
                message = vm.freeMessage,
                onStartNow = { vm.startFreeFasting() },
                onPickStart = { showFreeStartPicker = true },
                onEnd = { vm.endFreeFasting() },
                onCancel = { vm.cancelFreeFasting() }
            )
            Spacer(Modifier.height(Dimens.Gap))

            FastingCalendarCard(logs = yearLogs, today = AppClock.today())
            Spacer(Modifier.height(Dimens.Gap))

            HistoryCard(historyLogs)
            Spacer(Modifier.height(Dimens.Gap))
            NotesCard()

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showFreeStartPicker) {
        com.qingji.app.ui.components.ExerciseDateTimeWheelSheet(
            initial = java.time.LocalDateTime.now(),
            title = "选择断食开始时间",
            futureDays = 7,
            pastDays = 0,
            onConfirm = { picked ->
                showFreeStartPicker = false
                vm.startFreeFasting(
                    picked.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                )
            },
            onDismiss = { showFreeStartPicker = false }
        )
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

/**
 * 自由断食卡片（2026-11-11 专项）。
 *
 * 与上面的「方案断食」互不影响：这里只认一个开始时间戳（存在 DataStore，
 * 进程被杀再打开也能接着算），断了多久 = 现在 - 开始，所以跨日、跨多日都对。
 * 开始 / 结束都会 sync 常驻通知，两边状态永远一致。
 */
@Composable
private fun FastingFreeCard(
    freeStart: Long,
    message: String,
    onStartNow: () -> Unit,
    onPickStart: () -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    val running = freeStart > 0L
    // 预约状态：开始时间在未来。这时不显示计时，显示「还有多久开始」
    val scheduled = running && FastingRules.freeIsScheduled(freeStart, System.currentTimeMillis())
    var elapsedSeconds by remember(freeStart) { mutableStateOf(0) }
    LaunchedEffect(freeStart) {
        while (freeStart > 0L) {
            elapsedSeconds = ((System.currentTimeMillis() - freeStart) / 1000L).toInt().coerceAtLeast(0)
            delay(1000L)
        }
        elapsedSeconds = 0
    }

    SectionCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "自由断食",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            when {
                scheduled -> {
                    Text(
                        text = "预约中 · ${FastingRules.freeScheduleLabel(freeStart, System.currentTimeMillis())}",
                        fontSize = 12.sp,
                        color = WarnOrange
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "到点自动开始计时并更新常驻通知，不需要守着",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                running -> {
                    val minutes = elapsedSeconds / 60
                    Text(
                        text = "已断食 ${clockText(elapsedSeconds)} · " +
                            FastingRules.phase(minutes / 60.0).first,
                        fontSize = 12.sp,
                        color = QingJiGreenDark
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "开始于 ${startLabel(freeStart)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                else -> {
                    Text(
                        text = "不预设方案；可约未来 7 天内的开始时间，结束后记进历史",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            scheduled -> PillText(
                text = "取消预约",
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel
            )
            running -> PillText(
                text = "结束并保存",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnd
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillText(
                    text = "现在开始",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStartNow
                )
                PillText(
                    text = "自定义时间",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onPickStart
                )
            }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = message, fontSize = 11.sp, color = QingJiGreenDark)
        }
    }
}

/** 胶囊按钮：filled = 实底绿（主操作），否则浅绿（次操作）。 */
@Composable
private fun PillText(
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

/** 秒数 → 00:12:34（跨日会出现 26:10:05，不截断）。 */
private fun clockText(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

/** 开始时刻 → 「今天 20:00 / 昨天 20:00 / 11月9日 20:00」。 */
private fun startLabel(millis: Long): String {
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

/**
 * 未开启时的方案预览（2026-11-11 改版）。
 *
 * 以前这里只有一句「点右上角设置选个方案」：看不到具体时间，也不知道开完会怎样。
 * 现在直接把方案和时间摆出来，一个按钮开启，并写明「开启后全自动、不用手动」。
 */
@Composable
private fun FastingPreviewCard(
    planHours: Int,
    eatStartMinute: Int,
    eatEndMinute: Int,
    onEnable: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${FastingRules.planLabel(planHours)} 方案",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "未开启", fontSize = 11.sp, color = WarnOrange)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "断食开始时间", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "今天 ${FastingRules.minuteLabel(eatEndMinute)}",
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
                    text = "明天 ${FastingRules.minuteLabel(eatStartMinute)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = QingJiGreenDark
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "开启后按这个时间自动开始和结束，到点前会提醒，不需要你手动操作；" +
                "吃饭时间想改，右上角「设置」里能调。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreen)
                .clickable { onEnable() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "开启轻断食",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 断食方案卡（2026-11-12 从设置弹窗里提出来）：点一颗直接切换并保存。
 * 进食窗口 / 生效星期是精确数字，仍保留在右上角的设置弹窗里。
 */
@Composable
private fun FastingPlanCard(
    planHours: Int,
    onSelect: (Int) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "断食方案",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "进食窗口在右上角「设置」里调", fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FastingRules.PLANS.forEach { hours ->
                val selected = hours == planHours
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) QingJiGreenLight else PageBackground)
                        .clickable { onSelect(hours) }
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
    }
}

/** 顶部时间条：断食开始 → 可进食。 */
@Composable
private fun TimeBanner(status: FastingStatus, now: java.time.LocalTime) {
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
/**
 * 断食成就（2026-11-11）：口径是「连续断食天数」。
 *
 * 不累计历史总天数 —— 断食最怕三天打鱼两天晒网，连续才值得鼓励。
 * 里程碑只放 6 个档位（3/7/14/30/60/100），够得着才有意义；
 * 达成填实底、未达成浅底，一眼能看出还差多远。
 */
@Composable
private fun FastingStreakCard(days: Int) {
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
/**
 * 断食日历（2026-11-11 专项）。
 *
 * 纯展示：数据只读近一年的 fasting_log，不加字段、不改规则。
 * 周一开头（国内习惯），有记录的日子浅绿底，今天淡绿底，点某天在下方看那天的详情。
 * 月份只能在「11 个月前 ~ 今天所在月」之间翻——再往前数据没查，翻了也是空白。
 */
@Composable
private fun FastingCalendarCard(
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
    var endMinute by remember { mutableStateOf(current.fastingEatEndMinute) }
    // v1.0.7：可进食时间不再单独选 —— 由「断食开始时间 + 方案小时数」自动对标（跨零点取模），不可修改
    val startMinute = (endMinute + planHours * 60) % 1440

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
                Text(text = "断食时间", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                TimeAdjustRow(
                    label = "断食开始时间",
                    minute = endMinute,
                    onMinus = { endMinute = (endMinute - 30 + 1440) % 1440 },
                    onPlus = { endMinute = (endMinute + 30) % 1440 }
                )
                Spacer(Modifier.height(8.dp))
                // 只读行：可进食时间自动对标方案，不给加减按钮。
                // 左右各留一个和 ± 按钮同宽（28dp）的占位 —— 时间列就能和上方
                // 「断食开始时间」的那一列严格对齐，不会贴到最右边。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(PageBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "可进食时间",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = FastingRules.minuteLabel(startMinute),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QingJiGreenDark,
                        modifier = Modifier.width(64.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "结束时间按方案自动对标（${FastingRules.planLabel(planHours)} 即 ${planHours} 小时后），不可修改。" +
                        "窗口外的时段就是断食期，跨零点也能算。",
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
