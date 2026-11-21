package com.qingji.app.ui.screen.training

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.screen.DietHeatmapCard
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.MealRules
import com.qingji.app.domain.TrainingMath
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.MonthPickerSheet
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.delay
import java.time.YearMonth

/**
 * 训练日记视图：日历 + 本月总结 + 历史列表 + 明细弹层。
 * 日记页「训练日记」胶囊和「我的 → 训练记录」全屏页共用这一块，
 * 全屏页把 showModeSwitch 关掉，避免顶上出现两个切换条。
 */
@Composable
fun TrainingDiaryContent(
    vm: TrainingViewModel,
    onStartTraining: () -> Unit,
    onEditSession: () -> Unit,
    /** 2026-11-18：补记一次力量训练（先进补记模式再跳进行中页）。 */
    onBackfill: () -> Unit = {},
    /** 饮食 30 天热力图的数据（从进展页搬过来的模块，口径保持一致） */
    foodLogs: List<com.qingji.app.data.local.entity.FoodLog> = emptyList(),
    dailyKcalGoal: Double = 2000.0,
    modifier: Modifier = Modifier,
    /**
     * V2 工单：月份和滚动状态提到调用方（DiaryScreen）。
     * AnimatedContent 在切换结束后会 dispose 旧内容，状态留在这里会被重建、丢掉；
     * 由外层持有才能保证「训练 ↔ 心情」来回切换后月份和滚动位置都不变。
     * 全屏训练记录页不传，走默认值，行为与以前完全一致。
     */
    month: YearMonth = YearMonth.now(),
    onMonthChange: (YearMonth) -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    /**
     * 日期切换修复工单：selectedDate 也提到外层（与 month 同理，AnimatedContent 切
     * Tab 会 dispose 重建，留在这里会在切心情日记再回来时重置为今天）。
     * 默认 null = 本组件内部持有（全屏训练记录页走此路径，行为不变）。
     */
    selectedDate: java.time.LocalDate? = null,
    onSelectedDateChange: (java.time.LocalDate) -> Unit = {}
) {
    val sessions by vm.sessions.collectAsState()
    val allExercises by vm.allWorkoutExercises.collectAsState()
    val unit by vm.unit.collectAsState()
    val message by vm.message.collectAsState()

    var detailUuid by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    // QJ 日记日期查询：点「训练记录」旁的日期弹首页同款月历（不动外层月份/Tab 状态）
    var showDatePicker by remember { mutableStateOf(false) }

    // ---------- 健康体系 V1.1（工单 10.4）：月历选日期 → 每日健康汇总 ----------
    val context = androidx.compose.ui.platform.LocalContext.current
    val healthRepo = remember(context) {
        com.qingji.app.data.repository.DailyHealthRepository(context)
    }
    // 受控/非受控双模：外层传了就用外层状态（切 Tab 保留），否则内部自持（全屏页）
    var internalSelectedDate by remember { mutableStateOf(com.qingji.app.domain.AppClock.today()) }
    val activeDate = selectedDate ?: internalSelectedDate
    fun pickDate(date: java.time.LocalDate) {
        if (selectedDate != null) onSelectedDateChange(date) else internalSelectedDate = date
    }
    var daySummary by remember { mutableStateOf<com.qingji.app.data.local.entity.DailyHealthSummaryEntity?>(null) }
    val latestActiveDate = rememberUpdatedState(activeDate)
    LaunchedEffect(activeDate) {
        val target = activeDate
        // 先清旧日期汇总（渲染端还有 date 校验），避免慢查询期间显示旧日期数据
        daySummary = null
        val result = healthRepo.summaryFor(target)
        // 竞态防护：查询期间用户又切了日期，丢弃这次结果，绝不让旧查询覆盖新日期。
        // 用 rememberUpdatedState 读「当前」日期，而不是闭包里被 key 固定的旧值。
        if (target == latestActiveDate.value) daySummary = result
    }

    // 当天饮食明细：按「训练日记自己的选中日期」真实查询，不取进展页的 foodMonth（近 30 天）。
    // produceState 会在 selectedDate 变化时取消上一次收集并重新订阅，不会串日期、不重复建监听。
    val foodRepo = remember(context) { com.qingji.app.data.repository.QingJiRepository(context) }
    val dayFoods by produceState(initialValue = emptyList<FoodLog>(), activeDate, foodRepo) {
        foodRepo.observeFoodLogs(activeDate).collect { value = it }
    }

    val bySession = remember(allExercises) { allExercises.groupBy { it.sessionUuid } }
    val trainingDates = remember(sessions) { sessions.map { it.date }.toSet() }
    val monthSessions = sessions.filter { YearMonth.from(it.date) == month }
    val monthMinutes = monthSessions.sumOf { it.durationMinutes }
    val monthSets = monthSessions.sumOf { s ->
        bySession[s.uuid].orEmpty().sumOf { TrainingMath.decodeSets(it.setsJson).size }
    }

    // 轻提示：2 秒后自己消失，不打断操作
    if (message != null) {
        LaunchedEffect(message) {
            delay(2000)
            vm.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(PageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
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

            // 与心情日记共用同一套主按钮 / 三栏统计组件，位置尺寸字号完全一致
            DiaryPrimaryButton("开始训练", onStartTraining)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    vm.startBackfill()
                    onBackfill()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("补记一次（昨天忘了记？点这里）", fontSize = TextSizes.Body)
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 本月总结：数字全部由明细累加，不手写 ----------
            SectionCard {
                Text("本月训练", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DiarySummaryItem("${monthSessions.size} 次", "次数", Modifier.weight(1f))
                    DiarySummaryItem("$monthMinutes 分钟", "总时长", Modifier.weight(1f))
                    DiarySummaryItem("$monthSets 组", "总组数", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            TrainingCalendarCard(
                month = month,
                trainingDates = trainingDates,
                selectedDate = activeDate,
                onPrevMonth = { onMonthChange(month.minusMonths(1)) },
                onNextMonth = { onMonthChange(month.plusMonths(1)) },
                onSelectDate = { pickDate(it) }
            )
            Spacer(Modifier.height(12.dp))

            // ---------- 每日健康汇总（健康体系 V1.1，工单 10.4：选日期看当天全部模块） ----------
            // 切日期时内容从 4 行塌成 1 行空态再填回，原来高度硬切导致下方内容跳动。
            // animateContentSize 让高度平滑过渡（不改滚动位置、不重播日历动画）。
            Box(modifier = Modifier.animateContentSize(animationSpec = tween(
                durationMillis = 200, easing = FastOutSlowInEasing
            ))) {
                DailyHealthSummaryCard(
                    summary = daySummary?.takeIf { it.date == activeDate }
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 当天饮食明细：按选中日期真实查询，四餐次 + 小计 + 合计 ----------
            Box(modifier = Modifier.animateContentSize(animationSpec = tween(
                durationMillis = 200, easing = FastOutSlowInEasing
            ))) {
                DayFoodDetailCard(foods = dayFoods)
            }
            Spacer(Modifier.height(12.dp))

            // QJ 日记日期查询：标题行带日期入口（复用首页 MonthPickerSheet）+ 前后一天切换；
            // 只显示选中日期的训练记录，没有就简洁空态，不回退、不平铺其它日期。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("训练记录", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                Text("‹", fontSize = 20.sp, color = TextSecondary,
                    modifier = Modifier.clickable {
                        val next = activeDate.minusDays(1)
                        pickDate(next)
                        onMonthChange(YearMonth.from(next))
                    }.padding(horizontal = 8.dp))
                Text(
                    text = if (activeDate == com.qingji.app.domain.AppClock.today())
                        "今天" else activeDate.toString(),
                    fontSize = TextSizes.Caption, color = com.qingji.app.ui.theme.QingJiGreen,
                    modifier = Modifier.clickable { showDatePicker = true }.padding(4.dp)
                )
                Text("›", fontSize = 20.sp,
                    color = if (activeDate >= com.qingji.app.domain.AppClock.today())
                        TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                    modifier = Modifier.clickable(enabled = activeDate < com.qingji.app.domain.AppClock.today()) {
                        val next = activeDate.plusDays(1)
                        pickDate(next)
                        onMonthChange(YearMonth.from(next))
                    }.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(8.dp))

            // 日期切换动画修复：原来用 Crossfade，新旧两套内容会同时参与测量、
            // 高度取 max 再收缩，配合下方汇总卡清空，视觉上就是「下方内容跳一下像 bug」。
            // 改为直接渲染当天内容 + animateContentSize：只有高度平滑过渡，
            // 日历/统计卡与外层 verticalScroll 位置完全不动。
            Box(modifier = Modifier.animateContentSize(animationSpec = tween(
                durationMillis = 200, easing = FastOutSlowInEasing
            ))) {
                val daySessions = sessions.filter { it.date == activeDate }
                if (daySessions.isEmpty()) {
                    SectionCard {
                        Text("这一天还没有训练记录", fontSize = TextSizes.Body, color = TextSecondary)
                    }
                } else {
                    daySessions.forEach { s ->
                        SessionRow(
                            session = s,
                            exercises = bySession[s.uuid].orEmpty(),
                            unit = unit,
                            onDetail = { detailUuid = s.uuid },
                            onEdit = { vm.editSession(s.uuid) { onEditSession() } },
                            onDelete = { pendingDelete = s.uuid }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 从进展页搬过来的「饮食 30 天」，放在训练记录下面
            DietHeatmapCard(logs = foodLogs, dailyGoal = dailyKcalGoal)

            // v9：悬浮底栏会盖住页尾内容，滚动尾部让出 Dock 高度
            val settings = com.qingji.app.data.prefs.SettingsStore.observe(
                androidx.compose.ui.platform.LocalContext.current
            ).collectAsState(initial = com.qingji.app.data.prefs.QingJiSettings()).value
            Spacer(Modifier.height(if (settings.floatingBottomBar) 116.dp else Dimens.FabSpace))
        }
    }

    // ---------- 明细弹层 ----------
    detailUuid?.let { uuid ->
        val session = sessions.firstOrNull { it.uuid == uuid }
        val items = bySession[uuid].orEmpty()
        AppBottomSheet(
            title = session?.note?.takeIf { it.isNotBlank() } ?: "力量训练",
            subtitle = session?.date?.toString().orEmpty(),
            skipPartiallyExpanded = true,
            onDismiss = { detailUuid = null }
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // QJ 来源信息：从计划启动的训练显示来源快照，手动训练不显示
                session?.source?.takeIf { it.startsWith("plan:") }?.let { src ->
                    Text(
                        "来自计划：${src.removePrefix("plan:")}",
                        fontSize = TextSizes.Caption, color = QingJiGreen
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // 标题 / 速记：历史会话也能补/改，走 note 单列更新，不重算消耗。
                var noteDraft by remember(uuid) { mutableStateOf(session?.note.orEmpty()) }
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("训练标题 / 速记") },
                    placeholder = { Text("给这次训练留个备注，例如：胸部强化、卧推突破60kg") },
                    singleLine = true,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { vm.updateSessionNote(uuid, noteDraft) },
                    enabled = noteDraft != session?.note.orEmpty()
                ) {
                    Text("保存备注", fontSize = TextSizes.Caption, color = QingJiGreen)
                }
                Spacer(Modifier.height(12.dp))

                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // QJ UI 统一：与计划/开始训练同款动作缩略图
                        com.qingji.app.ui.components.TrainingExerciseThumbnail(
                            exerciseUuid = item.exerciseUuid, fallbackName = item.exerciseNameSnapshot
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(item.exerciseNameSnapshot, fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    TrainingMath.decodeSets(item.setsJson).forEachIndexed { i, set ->
                        val weightText = if (set.weightKg > 0.0) {
                            val shown = if (unit == "lb") TrainingMath.kgToLb(set.weightKg) else set.weightKg
                            "%.1f %s × ".format(shown, unit)
                        } else ""
                        Text(
                            "  第 ${i + 1} 组：$weightText${set.reps} 次" +
                                if (set.rpe > 0) " · RPE ${set.rpe}" else "",
                            fontSize = TextSizes.Body, color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val allSets = items.flatMap { TrainingMath.decodeSets(it.setsJson) }
                val volume = TrainingMath.sessionVolume(allSets)
                Text(
                    "总时长 ${session?.durationMinutes ?: 0} 分钟 · 总组数 ${allSets.size} 组 · 训练量 ${TrainingMath.formatVolume(volume)}",
                    fontSize = TextSizes.Caption, color = TextSecondary
                )
                Text("训练量是「重量 × 次数」的加总，估算值，仅供参考。",
                    fontSize = TextSizes.Tiny, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // ---------- 删除确认 ----------
    pendingDelete?.let { uuid ->
        AppDialog(
            title = "删掉这次训练？",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                vm.deleteSession(uuid)
                pendingDelete = null
                detailUuid = null
            },
            onDismiss = { pendingDelete = null }
        ) {
            Text("这次训练的所有组记录都会一起删掉，删了找不回来。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }

    // QJ 日记日期查询：复用首页 MonthPickerSheet（只改本页选日期，不动外层月份/Tab）
    if (showDatePicker) {
        MonthPickerSheet(
            selected = activeDate,
            onSelect = {
                pickDate(it)
                onMonthChange(YearMonth.from(it))
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun SessionRow(
    session: WorkoutSession,
    exercises: List<WorkoutExercise>,
    unit: String,
    onDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val allSets = exercises.flatMap { TrainingMath.decodeSets(it.setsJson) }
    val volume = TrainingMath.sessionVolume(allSets)
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        // v10：右侧 44dp 让位取消——快捷按钮默认收起，展开时浮到内容上层，
        // 主人明确「不要怕遮挡」；训练记录卡恢复满宽。
        Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.note.takeIf { it.isNotBlank() } ?: "力量训练",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${session.date} · ${session.durationMinutes} 分钟 · ${exercises.size} 个动作 · ${allSets.size} 组 · 训练量 ${TrainingMath.formatVolume(volume)}",
                    fontSize = TextSizes.Tiny, color = TextSecondary
                )
            }
            Text("明细", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier.clickable { onDetail() }.padding(6.dp))
        }
        // 动作内容直接展示：按 orderInSession 原始顺序（DAO 已排序），快照名，逐组准确渲染。
        exercises.forEach { item ->
            val sets = TrainingMath.decodeSets(item.setsJson)
            Spacer(Modifier.height(6.dp))
            Text(item.exerciseNameSnapshot.ifBlank { "未命名动作" },
                fontSize = TextSizes.Body, color = TextPrimary)
            if (sets.isEmpty()) {
                Text("  无组数据", fontSize = TextSizes.Tiny, color = TextSecondary)
            } else {
                // 各组重量/次数全一致时才合并成「重量×次数×组数」，否则逐组列出。
                val uniform = sets.all { it.weightKg == sets.first().weightKg && it.reps == sets.first().reps }
                if (uniform) {
                    val w = sets.first().weightKg
                    val weightText = if (w > 0.0) {
                        val shown = if (unit == "lb") TrainingMath.kgToLb(w) else w
                        "%.1f %s × ".format(shown, unit)
                    } else ""
                    Text("  $weightText${sets.first().reps} 次 × ${sets.size} 组",
                        fontSize = TextSizes.Tiny, color = TextSecondary)
                } else {
                    sets.forEachIndexed { i, set ->
                        val weightText = if (set.weightKg > 0.0) {
                            val shown = if (unit == "lb") TrainingMath.kgToLb(set.weightKg) else set.weightKg
                            "%.1f %s × ".format(shown, unit)
                        } else ""
                        Text("  第 ${i + 1} 组：$weightText${set.reps} 次",
                            fontSize = TextSizes.Tiny, color = TextSecondary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            if (session.date == AppClock.today()) {
                TextButton(onClick = onEdit) { Text("修改", fontSize = TextSizes.Caption, color = QingJiGreen) }
            }
            TextButton(onClick = onDelete) { Text("删除", fontSize = TextSizes.Caption, color = TextSecondary) }
        }
        }
    }
}

/**
 * 当天饮食明细（训练日记日期联动用）：只显示名称/克数/热量 + 每餐小计 + 合计。
 * 刻意不展示蛋白/碳水/脂肪等指标——保留饮食日记「快速看吃了什么」的简洁原则。
 */
@Composable
private fun DayFoodDetailCard(foods: List<FoodLog>) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        val total = foods.sumOf { it.kcal }
        Text("当天饮食 · 合计 ${total.toInt()} 千卡",
            fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        if (foods.isEmpty()) {
            Text("这天还没有饮食记录。", fontSize = TextSizes.Body, color = TextSecondary)
            return@SectionCard
        }
        MealRules.ALL.forEach { meal ->
            val items = foods.filter { it.mealType == meal }
            if (items.isEmpty()) return@forEach
            val sub = items.sumOf { it.kcal }
            Text("${MealRules.label(meal)} · 小计 ${sub.toInt()} 千卡",
                fontSize = TextSizes.Caption, color = QingJiGreen)
            items.forEach { food ->
                Text("  ${food.foodName} · ${food.grams.toInt()}g · ${food.kcal.toInt()} 千卡",
                    fontSize = TextSizes.Tiny, color = TextPrimary)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
