package com.qingji.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.FastingRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
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
