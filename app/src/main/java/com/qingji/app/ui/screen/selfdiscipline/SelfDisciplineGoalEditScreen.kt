package com.qingji.app.ui.screen.selfdiscipline

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.WheelTimePicker
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 新建 / 编辑目标（SD-003）。
 * 新建时可选开始日期；编辑时开始日期锁死，避免误改把历史周期算歪。
 */
@Composable
fun SelfDisciplineGoalEditScreen(
    goalId: Long,
    vm: SelfDisciplineViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isEdit = goalId > 0

    var loaded by remember { mutableStateOf(!isEdit) }
    var title by remember { mutableStateOf("") }
    var goalType by remember { mutableStateOf(SelfDisciplineContracts.GOAL_PORN) }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var targetDays by remember { mutableStateOf(30) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var reminderMinute by remember { mutableStateOf(21 * 60) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    var showDateSheet by remember { mutableStateOf(false) }
    var showTimeSheet by remember { mutableStateOf(false) }

    // 编辑模式：把原值回填一次
    LaunchedEffect(goalId) {
        if (isEdit) {
            vm.getGoal(goalId)?.let { g ->
                title = g.title
                goalType = g.goalType
                startDate = g.startDate
                targetDays = g.targetDays
                reminderEnabled = g.reminderEnabled
                reminderMinute = if (g.reminderMinute >= 0) g.reminderMinute else 21 * 60
                note = g.note
            }
            loaded = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = if (isEdit) "编辑目标" else "新建目标",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            SectionCard {
                LabeledTextField(
                    label = "目标名称",
                    value = title,
                    onValueChange = {
                        title = it
                        error = ""
                    },
                    hint = "例如：减少色情内容、早睡、控制刷手机"
                )
                Spacer(Modifier.height(14.dp))

                Text("管理范围", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                MultiSelectChips(
                    options = SelfDisciplineContracts.GOAL_TYPE_LABELS.values.toList(),
                    selected = setOf(
                        SelfDisciplineContracts.GOAL_TYPE_LABELS[goalType] ?: "自定义目标"
                    ),
                    onToggle = { label ->
                        val token = SelfDisciplineContracts.GOAL_TYPE_LABELS.entries
                            .firstOrNull { it.value == label }?.key ?: SelfDisciplineContracts.GOAL_CUSTOM
                        goalType = token
                        // 名称还是空的就顺手填上，省得用户再打一遍
                        if (title.isBlank() && token != SelfDisciplineContracts.GOAL_CUSTOM) {
                            title = SelfDisciplineContracts.GOAL_TYPE_LABELS[token].orEmpty()
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))

                Text("开始日期", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(PageBackground)
                        .clickable(enabled = !isEdit) { showDateSheet = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        startDate.displayCn(includeYear = true),
                        fontSize = TextSizes.Body,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isEdit) "不可修改" else "修改",
                        fontSize = TextSizes.Caption,
                        color = TextSecondary
                    )
                }
                if (isEdit) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "开始日期决定周期起点，改动会让历史周期对不上，所以编辑时锁死。",
                        fontSize = TextSizes.Tiny,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(14.dp))

                Text("目标周期", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                MultiSelectChips(
                    options = listOf("7 天", "14 天", "30 天", "60 天", "90 天", "不限"),
                    selected = setOf(
                        if (targetDays <= 0) "不限" else "$targetDays 天"
                    ),
                    onToggle = { label ->
                        targetDays = if (label == "不限") 0 else label.filter { it.isDigit() }.toIntOrNull() ?: 0
                    }
                )
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("打卡提醒", fontSize = TextSizes.Body, color = TextPrimary)
                        Text(
                            "只发中性提示，不写具体行为",
                            fontSize = TextSizes.Tiny,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CardWhite,
                            checkedTrackColor = QingJiGreen
                        )
                    )
                }
                if (reminderEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(PageBackground)
                            .clickable { showTimeSheet = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("提醒时间", fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text(
                            "%02d:%02d".format(reminderMinute / 60, reminderMinute % 60),
                            fontSize = TextSizes.Body,
                            color = QingJiGreen
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                LabeledTextField(
                    label = "备注（可选）",
                    value = note,
                    onValueChange = { note = it },
                    hint = "写点给自己的话",
                    singleLine = false,
                    minLines = 3
                )

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    HintBox(text = error, isError = true)
                }
            }

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = if (saving) "保存中…" else "保存目标",
                enabled = !saving && loaded,
                onClick = {
                    if (title.isBlank()) {
                        error = "请先填写目标名称"
                        return@PrimaryButton
                    }
                    saving = true
                    scope.launch {
                        if (isEdit) {
                            val old = vm.getGoal(goalId)
                            if (old != null) {
                                vm.updateGoal(
                                    old.copy(
                                        title = title.trim(),
                                        goalType = goalType,
                                        targetDays = targetDays,
                                        reminderEnabled = reminderEnabled,
                                        reminderMinute = if (reminderEnabled) reminderMinute else -1,
                                        note = note
                                    )
                                )
                            }
                        } else {
                            vm.createGoal(
                                title = title,
                                goalType = goalType,
                                startDate = startDate,
                                targetDays = targetDays,
                                reminderEnabled = reminderEnabled,
                                reminderMinute = if (reminderEnabled) reminderMinute else -1,
                                note = note
                            )
                        }
                        saving = false
                        onSaved()
                    }
                }
            )
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showDateSheet) {
        DatePickerSheet(
            initial = startDate,
            onPick = {
                startDate = it
                showDateSheet = false
            },
            onDismiss = { showDateSheet = false }
        )
    }

    if (showTimeSheet) {
        var hour by remember { mutableStateOf(reminderMinute / 60) }
        var minute by remember { mutableStateOf(reminderMinute % 60) }
        AppBottomSheet(
            title = "提醒时间",
            subtitle = "到点发一条中性提醒",
            confirmText = "确定",
            onConfirm = { reminderMinute = hour * 60 + minute },
            onDismiss = { showTimeSheet = false }
        ) {
            WheelTimePicker(
                initialHour = hour,
                initialMinute = minute,
                onSelected = { h, m ->
                    hour = h
                    minute = m
                }
            )
        }
    }
}
