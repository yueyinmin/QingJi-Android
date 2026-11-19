package com.qingji.app.ui.screen.training

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.domain.SessionCalorieCalculator
import com.qingji.app.domain.TrainingMath
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.delay

/**
 * 进行中训练页。
 * 计时只依赖 startedAt 时间戳 + 草稿里的暂停累计，所以杀进程回来能对上；
 * 每改一组都同步落草稿，不会「练了半小时、接个电话全没了」。
 */
@Composable
fun TrainingActiveScreen(
    vm: TrainingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by vm.draft.collectAsState()
    val now by vm.now.collectAsState()
    val unit by vm.unit.collectAsState()
    val library by vm.exercises.collectAsState()
    val message by vm.message.collectAsState()
    // v1.4 健康体系：结算体重 + MET 参数，用来实时算「每组/整场预计消耗」
    val settleWeight by vm.settleWeight.collectAsState()
    val metDefaults by vm.metDefaults.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    // 2026-11-18：改时长 / 改补记日期的滚轮弹层
    var showDurationWheel by remember { mutableStateOf(false) }
    var showDateWheel by remember { mutableStateOf(false) }

    // 进页面：没有草稿就开一次新训练；每秒刷一次计时显示
    LaunchedEffect(Unit) {
        vm.startNew()
        // P1：动作详情「用这个动作开始训练」带过来的动作，第一帧就加进草稿
        vm.consumePendingExercise()?.let { vm.addExercise(it) }
        while (true) {
            vm.tick()
            delay(1000)
        }
    }
    // 草稿里的备注同步到输入框（编辑旧会话时要用）
    LaunchedEffect(draft?.sessionUuid) {
        noteText = draft?.note.orEmpty()
    }

    val d = draft
    val allSets = d?.exercises.orEmpty().flatMap { it.sets }
    val volume = TrainingMath.sessionVolume(allSets)
    val paused = (d?.pausedAt ?: 0L) > 0L
    // v1.4 健康体系：组间休息计时状态
    val restTimer by vm.restTimer.collectAsState()
    val restRemaining = restTimer.remainingSeconds(now)
    val restFinished = restTimer.totalMs > 0L && restRemaining <= 0L && !restTimer.paused

    // 休息倒计时走完轻量提醒：短震一下（没震动器的设备静默跳过），不弹窗不抢焦点
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(restFinished) {
        if (restFinished) {
            runCatching {
                val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                        as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vib?.vibrate(android.os.VibrationEffect.createOneShot(300L,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(300L)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = when {
                d != null && d.backfillDate.isNotBlank() -> "补记训练"
                d?.editingUuid?.isNotEmpty() == true -> "修改训练"
                else -> "开始训练"
            },
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .padding(10.dp)
                ) {
                    Text(message.orEmpty(), fontSize = TextSizes.Caption, color = QingJiGreen)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (d != null) {
                WorkoutTimerBar(
                    elapsedSeconds = d.elapsedSeconds(now),
                    paused = paused,
                    editing = d.editingUuid.isNotEmpty(),
                    onTogglePause = { vm.togglePause() },
                    durationOverrideMin = d.durationOverrideMin,
                    onDurationClick = { showDurationWheel = true },
                    onResetDuration = { vm.setDurationOverride(0) },
                    backfillDate = d.backfillDate,
                    onDateClick = { showDateWheel = true },
                    editingDurationMin = d.fixedDurationMinutes
                )
                // v1.4 健康体系：组间休息倒计时条（勾选「完成此组」自动开始）
                if (restTimer.totalMs > 0L) {
                    Spacer(Modifier.height(8.dp))
                    RestTimerBar(
                        totalSeconds = restTimer.totalMs / 1000L,
                        remainingSeconds = restRemaining,
                        paused = restTimer.paused,
                        finished = restFinished,
                        onPauseResume = { if (restTimer.paused) vm.resumeRest() else vm.pauseRest() },
                        onSkip = { vm.skipRest() }
                    )
                }
                Spacer(Modifier.height(10.dp))

                // kg / lb 只影响显示与输入，库里始终存 kg
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("重量单位", fontSize = TextSizes.Caption, color = TextSecondary,
                        modifier = Modifier.weight(1f))
                    listOf(WorkoutDraftStore.UNIT_KG, WorkoutDraftStore.UNIT_LB).forEach { u ->
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(Dimens.PillCorner))
                                .background(if (unit == u) QingJiGreen else QingJiGreenLight)
                                .clickable { vm.setUnit(u) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(u.uppercase(), fontSize = TextSizes.Caption,
                                color = if (unit == u) CardWhite else QingJiGreen)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                d.exercises.forEach { e ->
                    val entity = library.firstOrNull { it.uuid == e.exerciseUuid }
                    val met = vm.metForExercise(e.exerciseUuid, entity?.isStretch == true)
                    SetEditorCard(
                        name = e.name,
                        sets = e.sets,
                        unit = unit,
                        fromLast = e.fromLast,
                        setKcal = SessionCalorieCalculator.previewSetKcalList(
                            metLow = met.first,
                            metModerate = met.second,
                            metHigh = met.third,
                            weightKg = settleWeight,
                            sets = e.sets
                        ),
                        onUpdate = { index, set -> vm.updateSet(e.key, index, set) },
                        onAdd = { vm.addSet(e.key) },
                        onRemoveSet = { index -> vm.removeSet(e.key, index) },
                        onRemoveExercise = { vm.removeExercise(e.key) },
                        onToggleComplete = { index -> vm.toggleSetComplete(e.key, index) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                PrimaryButton(text = "添加动作", icon = Icons.Filled.Add, onClick = { showPicker = true })
                Spacer(Modifier.height(12.dp))

                SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                    Text("训练备注（可不填）", fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            vm.setNote(it)
                        },
                        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(DividerColor.copy(alpha = 0.45f))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            if (noteText.isEmpty()) Text("今天状态怎么样？", fontSize = TextSizes.Caption, color = TextSecondary)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))

                SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                    Text("本次小结", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    // v1.4 健康体系：整场预计消耗 = 各组做功+组间休息（静息 MET）+ 动作切换准备时间
                    val previewEntries = d.exercises.map { e ->
                        val entity = library.firstOrNull { it.uuid == e.exerciseUuid }
                        val met = vm.metForExercise(e.exerciseUuid, entity?.isStretch == true)
                        SessionCalorieCalculator.PreviewEntry(met.first, met.second, met.third, e.sets)
                    }
                    val previewKcal = SessionCalorieCalculator.previewSessionTotalKcal(previewEntries, settleWeight)
                    Text("${d.exercises.size} 个动作 · ${allSets.size} 组 · 训练量 ${TrainingMath.formatVolume(volume)}",
                        fontSize = TextSizes.Body, color = TextPrimary)
                    if (allSets.any { it.reps > 0 || it.weightKg > 0.0 }) {
                        Spacer(Modifier.height(2.dp))
                        Text("预计消耗约 ${SessionCalorieCalculator.displayKcal(previewKcal)} 千卡（估算，保存后计入运动消耗）",
                            fontSize = TextSizes.Body, color = QingJiGreen)
                    }
                    Text("训练量与消耗都是估算值，仅供参考，不是医学结论。",
                        fontSize = TextSizes.Tiny, color = TextSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // 底部固定操作条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                // 实测：手势导航下，按钮贴近屏幕底边时会有一截点不到。
                // 主动给底部导航条让位，把整排按钮抬到手势区之上。
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { showDiscard = true }, modifier = Modifier.weight(1f)) {
                Text("放弃", fontSize = TextSizes.Body)
            }
            Button(
                onClick = { vm.save { ok -> if (ok) onBack() } },
                modifier = Modifier.weight(2f)
            ) {
                Text("保存训练", fontSize = TextSizes.Body)
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = library,
            onPick = { vm.addExercise(it) },
            onAddCustom = { name, category -> vm.addCustomExercise(name, category) },
            onDismiss = { showPicker = false }
        )
    }

    // 2026-11-18：改训练时长（5~300 分钟，步进 5）
    if (showDurationWheel) {
        com.qingji.app.ui.components.ExerciseNumberWheelSheet(
            values = (5..300 step 5).toList(),
            initialValue = (d?.durationOverrideMin ?: 60).coerceIn(5, 300),
            unitLabel = "分钟",
            onConfirm = { vm.setDurationOverride(it) },
            onDismiss = { showDurationWheel = false }
        )
    }

    // 2026-11-18：补记模式下改训练日期（最多往回翻 90 天）
    if (showDateWheel && d != null && d.backfillDate.isNotBlank()) {
        val initialDate = runCatching { java.time.LocalDate.parse(d.backfillDate) }
            .getOrDefault(java.time.LocalDate.now())
        com.qingji.app.ui.components.ExerciseDateTimeWheelSheet(
            initial = initialDate.atTime(12, 0),
            pastDays = 90,
            title = "补记到哪一天？",
            onConfirm = { dt -> vm.setBackfillDate(dt.toLocalDate()) },
            onDismiss = { showDateWheel = false }
        )
    }

    if (showDiscard) {
        AppDialog(
            title = "放弃这次训练？",
            confirmText = "放弃",
            danger = true,
            onConfirm = {
                vm.discard()
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false }
        ) {
            Text("还没保存的动作和组数据都会清掉，已经保存过的训练不受影响。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }
}
