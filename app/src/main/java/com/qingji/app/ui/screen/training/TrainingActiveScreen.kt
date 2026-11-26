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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.prefs.DraftExercise
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.domain.ExerciseRecordType
import com.qingji.app.domain.SessionCalorieCalculator
import com.qingji.app.domain.TrainingMath
import com.qingji.app.domain.WorkoutItem
import com.qingji.app.domain.input.NumericEmptyPolicy
import com.qingji.app.domain.input.NumericInputState
import com.qingji.app.domain.input.TrainingInputRules
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.SelfSelectingNumberField
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
import kotlinx.coroutines.launch

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
    // QJ 训练联动：初始化状态（进度 / 失败重试）
    val initState by vm.initState.collectAsState()
    // v1.4 健康体系：结算体重 + MET 参数，用来实时算「每组/整场预计消耗」
    val settleWeight by vm.settleWeight.collectAsState()
    val metDefaults by vm.metDefaults.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    // 2026-11-18：改时长 / 改补记日期的滚轮弹层
    var showDurationWheel by remember { mutableStateOf(false) }
    var showDateWheel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

            // QJ 训练联动：初始化中 / 失败重试提示（成功状态不打扰用户）
            if (initState == com.qingji.app.ui.viewmodel.TrainingInitState.Loading) {
                Text("正在识别今天的训练计划…", fontSize = TextSizes.Caption, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
            }
            if (initState == com.qingji.app.ui.viewmodel.TrainingInitState.Failed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .padding(10.dp)
                ) {
                    Column {
                        Text("计划读取失败，已按空白训练继续。", fontSize = TextSizes.Caption, color = TextPrimary)
                        Text(
                            "重试（重新识别当天计划）",
                            fontSize = TextSizes.Caption, color = QingJiGreen,
                            modifier = Modifier.clickable { scope.launch { vm.retryInit() } }.padding(vertical = 4.dp)
                        )
                    }
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
                    val type = com.qingji.app.domain.TrainingItemMapper.resolveType(e.recordType, entity)
                    if (type == ExerciseRecordType.STRENGTH) {
                        val met = vm.metForExercise(e.exerciseUuid, entity?.isStretch == true)
                        SetEditorCard(
                            name = e.name,
                            exerciseUuid = e.exerciseUuid,
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
                    } else {
                        NonStrengthEditorCard(
                            draft = e,
                            type = type,
                            vm = vm,
                            onRemoveExercise = { vm.removeExercise(e.key) }
                        )
                    }
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
            val saving by vm.isSaving.collectAsState()
            Button(
                onClick = { vm.save { ok -> if (ok) onBack() } },
                modifier = Modifier.weight(2f),
                enabled = !saving
            ) {
                Text(if (saving) "保存中…" else "保存训练", fontSize = TextSizes.Body)
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

/**
 * B1.1：非力量项目录入卡。
 * 目标配置只作为提示展示；实际值必须用户输入后才写进实际字段，绝不把目标伪装成成绩。
 * 状态按 draft.key 绑定，删除/排序其他动作不会串数据。
 */
@Composable
private fun NonStrengthEditorCard(
    draft: DraftExercise,
    type: ExerciseRecordType,
    vm: TrainingViewModel,
    onRemoveExercise: () -> Unit
) {
    val parsed = remember(draft.key) { WorkoutItem.fromJson(draft.actualJson) }
    val target = remember(draft.targetConfigJson) {
        com.qingji.app.domain.TargetConfigJson.decode(draft.targetConfigJson)
    }
    // QJ-RECORD-INPUT-UNIFIED-V3 / B2：回填用的原始值只读一次。
    // 用户没动某一项时，保存必须原样写回 —— 不能让「界面显示精度」反推数据
    // （1800 秒 → 30 分钟 刚好相等，但历史里的 1850 秒会被四舍五入改掉）。
    val originalDurationSec: Long = when (parsed) {
        is WorkoutItem.Cardio -> parsed.durationSec
        is WorkoutItem.Hiit -> parsed.durationSec
        is WorkoutItem.Flexibility -> parsed.durationSec
        is WorkoutItem.Other -> parsed.durationSec
        else -> 0L
    }
    val cardio = parsed as? WorkoutItem.Cardio

    // 时长：数据层是秒，界面填分钟（旧代码把秒直接塞进分钟框，1800 秒显示成 1800 分钟）
    var minutesState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(
                value = TrainingInputRules.secondsToMinutes(originalDurationSec),
                min = 0.0,
                max = TrainingInputRules.MAX_MINUTES.toDouble(),
                decimals = 0,
                emptyPolicy = NumericEmptyPolicy.NORMALIZE,
                emptyFallback = 0.0
            )
        )
    }
    // 「用户真改过时长」才重算秒数；只改强度/轮数时保留原始秒数
    var minutesDirty by remember(draft.key) { mutableStateOf(false) }

    // 距离：数据层是米，界面填公里（旧代码把米当公里，5000 米显示 5000 公里）
    var distanceState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(
                value = TrainingInputRules.metersToKm(cardio?.distanceMeters),
                min = 0.0,
                max = TrainingInputRules.MAX_KM,
                decimals = 3,
                emptyPolicy = NumericEmptyPolicy.NORMALIZE,
                emptyFallback = 0.0
            )
        )
    }
    var distanceDirty by remember(draft.key) { mutableStateOf(false) }

    var intensity by remember(draft.key) {
        mutableStateOf(cardio?.intensity
            ?: (parsed as? WorkoutItem.Hiit)?.intensity ?: "medium")
    }
    var workSecState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(
                ((parsed as? WorkoutItem.Hiit)?.workSec ?: 0).toDouble(),
                0.0, TrainingInputRules.MAX_ROUND_SEC
            )
        )
    }
    var restSecState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(
                ((parsed as? WorkoutItem.Hiit)?.restSec ?: 0).toDouble(),
                0.0, TrainingInputRules.MAX_ROUND_SEC
            )
        )
    }
    var roundsState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(
                ((parsed as? WorkoutItem.Hiit)?.rounds ?: 0).toDouble(),
                0.0, TrainingInputRules.MAX_ROUNDS
            )
        )
    }
    var content by remember(draft.key) {
        mutableStateOf((parsed as? WorkoutItem.Flexibility)?.content.orEmpty())
    }
    var repsState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of(((parsed as? WorkoutItem.Other)?.reps ?: 0).toDouble(), 0.0, 9999.0)
        )
    }

    // 2026-11-24：步行类有氧可选时长/步数/公里；固定器械类只时长。
    // 回填时按「当初真填的原始值」判断，绝不从时长反推一个假步数。
    val canChooseMode = type == ExerciseRecordType.CARDIO &&
        com.qingji.app.domain.CardioInputMode.supportsDistanceModes(draft.exerciseUuid)
    var cardioMode by remember(draft.key) {
        mutableStateOf(
            when {
                (cardio?.steps ?: 0) > 0 -> com.qingji.app.domain.CardioInputMode.MODE_STEPS
                (cardio?.distanceMeters ?: 0.0) > 0.0 -> com.qingji.app.domain.CardioInputMode.MODE_KM
                else -> com.qingji.app.domain.CardioInputMode.MODE_MINUTES
            }
        )
    }
    // 当前模式下用户填的原始数字；切换模式时各自保留，不互相换算冒充原始值
    var stepsState by remember(draft.key) {
        mutableStateOf(
            NumericInputState.of((cardio?.steps ?: 0).toDouble(), 0.0, TrainingInputRules.MAX_STEPS)
        )
    }
    var stepsDirty by remember(draft.key) { mutableStateOf(false) }

    /** 当前模式对应的输入状态（三个状态互相独立，切模式不串值）。 */
    fun amountState(): NumericInputState = when (cardioMode) {
        com.qingji.app.domain.CardioInputMode.MODE_STEPS -> stepsState
        com.qingji.app.domain.CardioInputMode.MODE_KM -> distanceState
        else -> minutesState
    }

    fun cardioAmount(): Double = amountState().value ?: 0.0

    fun durationSec(mode: String, amount: Double): Long =
        com.qingji.app.domain.CardioInputMode.toMinutes(mode, amount)
            .let { it.coerceIn(0.0, TrainingInputRules.MAX_MINUTES.toDouble()) }
            .toLong() * 60L

    /** 当前模式下「用户是否真的动过数字」，没动过就保留原始秒数。 */
    fun durationTouched(): Boolean = when {
        canChooseMode && cardioMode == com.qingji.app.domain.CardioInputMode.MODE_STEPS -> stepsDirty
        canChooseMode && cardioMode == com.qingji.app.domain.CardioInputMode.MODE_KM -> distanceDirty
        else -> minutesDirty
    }

    /** HIIT / 柔韧 / 其他仍按原来的分钟输入；没改过就原样保留原始秒数。 */
    fun durationSec(): Long =
        if (minutesDirty) TrainingInputRules.minutesToSeconds(minutesState.value ?: 0.0)
        else originalDurationSec

    fun emit() {
        val common = Triple(draft.key, draft.exerciseUuid, draft.name)
        val item = when (type) {
            ExerciseRecordType.CARDIO -> {
                val amount = cardioAmount()
                val mode = if (canChooseMode) cardioMode else com.qingji.app.domain.CardioInputMode.MODE_MINUTES
                WorkoutItem.Cardio(
                    itemId = common.first, exerciseUuid = common.second, name = common.third, order = 0,
                    // 消耗计算仍需分钟：步数/公里按既有口径折算；没动过就原样保留原始秒数
                    durationSec = if (durationTouched()) durationSec(mode, amount) else originalDurationSec,
                    // 原始录入值各自保留；公里模式另存米数（上限 500 公里防误填）。
                    // 没改过距离时写回原始米数，避免「米→公里→米」的浮点误差改动历史数据。
                    distanceMeters = if (mode == com.qingji.app.domain.CardioInputMode.MODE_KM) {
                        if (distanceDirty) TrainingInputRules.kmToMeters(amount) else cardio?.distanceMeters
                    } else null,
                    steps = if (mode == com.qingji.app.domain.CardioInputMode.MODE_STEPS) {
                        if (stepsDirty) amount.toInt().coerceAtLeast(0) else (cardio?.steps ?: 0)
                    } else 0,
                    intensity = intensity
                )
            }
            ExerciseRecordType.HIIT -> WorkoutItem.Hiit(
                itemId = common.first, exerciseUuid = common.second, name = common.third, order = 0,
                durationSec = durationSec(),
                workSec = (workSecState.value ?: 0.0).toInt()
                    .coerceIn(0, TrainingInputRules.MAX_ROUND_SEC.toInt()),
                restSec = (restSecState.value ?: 0.0).toInt()
                    .coerceIn(0, TrainingInputRules.MAX_ROUND_SEC.toInt()),
                rounds = (roundsState.value ?: 0.0).toInt()
                    .coerceIn(0, TrainingInputRules.MAX_ROUNDS.toInt()),
                intensity = intensity
            )
            ExerciseRecordType.FLEXIBILITY -> WorkoutItem.Flexibility(
                itemId = common.first, exerciseUuid = common.second, name = common.third, order = 0,
                durationSec = durationSec(), content = content
            )
            ExerciseRecordType.OTHER -> WorkoutItem.Other(
                itemId = common.first, exerciseUuid = common.second, name = common.third, order = 0,
                durationSec = durationSec(),
                reps = repsState.value?.toInt()?.takeIf { it >= 0 }
            )
            else -> return
        }
        vm.updateActual(draft.key, item)
    }

    val typeLabel = when (type) {
        ExerciseRecordType.CARDIO -> "有氧"
        ExerciseRecordType.HIIT -> "HIIT 间歇"
        ExerciseRecordType.FLEXIBILITY -> "柔韧"
        else -> "其他"
    }
    val hint = buildString {
        target?.durationSec?.let { append("目标时长 ${it / 60} 分钟；") }
        target?.distanceMeters?.let { append("目标距离 ${"%.2f".format(it / 1000.0)} 公里；") }
        target?.rounds?.let { append("目标 $it 轮；") }
        if (isEmpty()) append("没有计划目标，填实际完成值即可")
    }

    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$typeLabel · ${draft.name}", fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary,
                modifier = Modifier.weight(1f))
            TextButton(onClick = onRemoveExercise) { Text("移除", color = TextSecondary) }
        }
        Text(hint, fontSize = TextSizes.Tiny, color = TextSecondary)
        Spacer(Modifier.height(8.dp))

        // 步行类有氧：模式切换（仿「记录运动」页的时长/步数/公里）；固定器械类只有时长
        if (canChooseMode) {
            Text("记录方式", fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.qingji.app.domain.CardioInputMode.modes().forEach { (key, label) ->
                    val active = cardioMode == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .background(if (active) QingJiGreen else QingJiGreenLight)
                            .clickable { cardioMode = key; emit() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = TextSizes.Caption,
                            color = if (active) CardWhite else QingJiGreen)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // QJ-RECORD-INPUT-UNIFIED-V3 / B2：三种模式各有一份独立状态，
            // 切换模式不串值；输入框切到「首次聚焦全选」，点击数字可直接覆盖。
            val currentMode = cardioMode
            val currentState = when (currentMode) {
                com.qingji.app.domain.CardioInputMode.MODE_STEPS -> stepsState
                com.qingji.app.domain.CardioInputMode.MODE_KM -> distanceState
                else -> minutesState
            }
            SelfSelectingNumberField(
                state = currentState,
                onStateChange = { next ->
                    val changed = next.text != currentState.text
                    when (currentMode) {
                        com.qingji.app.domain.CardioInputMode.MODE_STEPS -> {
                            stepsState = next
                            if (changed) stepsDirty = true
                        }
                        com.qingji.app.domain.CardioInputMode.MODE_KM -> {
                            distanceState = next
                            if (changed) distanceDirty = true
                        }
                        else -> {
                            minutesState = next
                            if (changed) minutesDirty = true
                        }
                    }
                    emit()
                },
                label = "实际${com.qingji.app.domain.CardioInputMode.unit(cardioMode)}（${
                    when (cardioMode) {
                        com.qingji.app.domain.CardioInputMode.MODE_STEPS -> "按 100 步/分钟折算消耗"
                        com.qingji.app.domain.CardioInputMode.MODE_KM -> "按 10 分钟/公里折算消耗"
                        else -> "分钟"
                    }
                }）"
            )
            Spacer(Modifier.height(6.dp))
        } else if (type == ExerciseRecordType.CARDIO) {
            SelfSelectingNumberField(
                state = minutesState,
                onStateChange = { next ->
                    if (next.text != minutesState.text) minutesDirty = true
                    minutesState = next
                    emit()
                },
                label = "实际时长（分钟）"
            )
            Spacer(Modifier.height(6.dp))
        } else {
            SelfSelectingNumberField(
                state = minutesState,
                onStateChange = { next ->
                    if (next.text != minutesState.text) minutesDirty = true
                    minutesState = next
                    emit()
                },
                label = "实际时长（分钟）"
            )
        }
        if (type == ExerciseRecordType.CARDIO) {
            // 固定器械类（划船机/椭圆机/动感单车等）只能记时长；强度仍可选。
            Spacer(Modifier.height(6.dp))
            Text("强度", fontSize = TextSizes.Caption, color = TextSecondary)
            Row {
                listOf(
                    com.qingji.app.domain.ExerciseCalculator.INTENSITY_LIGHT to "轻",
                    com.qingji.app.domain.ExerciseCalculator.INTENSITY_MEDIUM to "中",
                    com.qingji.app.domain.ExerciseCalculator.INTENSITY_HIGH to "强"
                ).forEach { (key, label) ->
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .background(if (intensity == key) QingJiGreen else QingJiGreenLight)
                            .clickable { intensity = key; emit() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = TextSizes.Caption,
                            color = if (intensity == key) CardWhite else QingJiGreen)
                    }
                }
            }
        }
        if (type == ExerciseRecordType.HIIT) {
            Spacer(Modifier.height(6.dp))
            SelfSelectingNumberField(
                state = workSecState,
                onStateChange = { workSecState = it; emit() },
                label = "工作时长（秒/轮）"
            )
            Spacer(Modifier.height(6.dp))
            SelfSelectingNumberField(
                state = restSecState,
                onStateChange = { restSecState = it; emit() },
                label = "休息时长（秒/轮）"
            )
            Spacer(Modifier.height(6.dp))
            SelfSelectingNumberField(
                state = roundsState,
                onStateChange = { roundsState = it; emit() },
                label = "完成轮数"
            )
        }
        if (type == ExerciseRecordType.FLEXIBILITY) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; emit() },
                label = { Text("动作/部位说明（可不填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (type == ExerciseRecordType.OTHER) {
            Spacer(Modifier.height(6.dp))
            SelfSelectingNumberField(
                state = repsState,
                onStateChange = { repsState = it; emit() },
                label = "次数（可不填）"
            )
        }
        if (type == ExerciseRecordType.HIIT || type == ExerciseRecordType.FLEXIBILITY || type == ExerciseRecordType.OTHER) {
            Spacer(Modifier.height(6.dp))
            Text("当前版本保留类型、目标配置和原始输入，不生成估算热量。",
                fontSize = TextSizes.Tiny, color = TextSecondary)
        }
    }
}
