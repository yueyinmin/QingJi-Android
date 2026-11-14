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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.screen.DietHeatmapCard
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.TrainingMath
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.SectionCard
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
    /** 饮食 30 天热力图的数据（从进展页搬过来的模块，口径保持一致） */
    foodLogs: List<com.qingji.app.data.local.entity.FoodLog> = emptyList(),
    dailyKcalGoal: Double = 2000.0,
    modifier: Modifier = Modifier
) {
    val sessions by vm.sessions.collectAsState()
    val allExercises by vm.allWorkoutExercises.collectAsState()
    val unit by vm.unit.collectAsState()
    val message by vm.message.collectAsState()

    var month by remember { mutableStateOf(YearMonth.now()) }
    var detailUuid by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

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
                .verticalScroll(rememberScrollState())
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

            Button(onClick = onStartTraining, modifier = Modifier.fillMaxWidth()) {
                Text("开始训练", fontSize = TextSizes.Body)
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 本月总结：数字全部由明细累加，不手写 ----------
            SectionCard {
                Text("本月训练", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryItem("次数", "${monthSessions.size} 次", Modifier.weight(1f))
                    SummaryItem("总时长", "$monthMinutes 分钟", Modifier.weight(1f))
                    SummaryItem("总组数", "$monthSets 组", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            TrainingCalendarCard(
                month = month,
                trainingDates = trainingDates,
                onPrevMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) }
            )
            Spacer(Modifier.height(12.dp))

            Text("训练记录", fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            if (sessions.isEmpty()) {
                SectionCard {
                    Text("还没有训练记录。点上面的「开始训练」，记下第一组就行。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                }
            } else {
                sessions.forEach { s ->
                    SessionRow(
                        session = s,
                        exercises = bySession[s.uuid].orEmpty(),
                        unit = unit,
                        onDetail = { detailUuid = s.uuid },
                        onEdit = {
                            vm.editSession(s.uuid) { onEditSession() }
                        },
                        onDelete = { pendingDelete = s.uuid }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // 从进展页搬过来的「饮食 30 天」，放在训练记录下面
            DietHeatmapCard(logs = foodLogs, dailyGoal = dailyKcalGoal)

            // v9：悬浮底栏会盖住页尾内容，滚动尾部让出 Dock 高度
            val settings = com.qingji.app.data.prefs.SettingsStore.observe(
                androidx.compose.ui.platform.LocalContext.current
            ).collectAsState(initial = com.qingji.app.data.prefs.QingJiSettings()).value
            Spacer(Modifier.height(if (settings.floatingBottomBar) Dimens.FabSpace + 82.dp else Dimens.FabSpace))
        }
    }

    // ---------- 明细弹层 ----------
    detailUuid?.let { uuid ->
        val session = sessions.firstOrNull { it.uuid == uuid }
        val items = bySession[uuid].orEmpty()
        AppBottomSheet(
            title = "训练明细",
            subtitle = session?.date?.toString().orEmpty(),
            skipPartiallyExpanded = true,
            onDismiss = { detailUuid = null }
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Text(item.exerciseNameSnapshot, fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                    "总时长 ${session?.durationMinutes ?: 0} 分钟 · 总组数 ${allSets.size} 组 · 训练量 %.1f kg".format(volume),
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
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
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
        // 右侧 44dp 让给悬浮快捷按钮：以前「明细 / 修改 / 删除」正好压在按钮下面，
        // 手指点下去命中的是按钮而不是文字（实测踩到）。
        Column(modifier = Modifier.padding(end = 44.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.date.toString(), fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${session.durationMinutes} 分钟 · ${exercises.size} 个动作 · ${allSets.size} 组 · 训练量 %.1f kg".format(volume),
                    fontSize = TextSizes.Tiny, color = TextSecondary
                )
            }
            Text("明细", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier.clickable { onDetail() }.padding(6.dp))
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
