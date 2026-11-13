package com.qingji.app.ui.screen.selfdiscipline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 目标偏离（SD-007）。
 * 文案保持中性：不说失败、不羞辱，只记一次「和计划不一样」。
 * 保存后结束当前周期，历史全部保留；可立刻重新开始，也可撤销这次误操作。
 */
@Composable
fun SelfDisciplineDeviationScreen(
    goalId: Long,
    vm: SelfDisciplineViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now()) }
    var triggers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mood by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDateSheet by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 保存成功后拿到被结束的周期 id，用来撤销
    var endedPeriodId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "目标偏离", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            if (endedPeriodId != null) {
                // 已记录：给「重新开始」和「撤销」两个出口
                SectionCard {
                    Text(
                        "已记录这次偏离",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当前周期在 ${date.displayCn()} 结束，之前的记录都还在。要不要从今天重新开始？",
                        fontSize = TextSizes.Body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "从今天重新开始",
                        onClick = {
                            scope.launch {
                                vm.restartPeriod(goalId)
                                onBack()
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(PageBackground)
                            .clickable {
                                val pid = endedPeriodId ?: return@clickable
                                scope.launch {
                                    vm.undoDeviation(pid, date, goalId)
                                    endedPeriodId = null
                                    onBack()
                                }
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("记错了，撤销这次记录", fontSize = TextSizes.Body, color = TextSecondary)
                    }
                }
            } else {
                SectionCard {
                    Text(
                        "记录一次目标偏离",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这不是失败，只是一次记录。记下来，然后决定要不要重新开始。",
                        fontSize = TextSizes.Body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))

                    Text("发生日期", fontSize = TextSizes.Body, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(PageBackground)
                            .clickable { showDateSheet = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(date.displayCn(includeYear = true), fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("修改", fontSize = TextSizes.Caption, color = QingJiGreen)
                    }
                    Spacer(Modifier.height(14.dp))

                    Text("诱因（可选，多选）", fontSize = TextSizes.Body, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    EditableChips(
                        options = SelfDisciplineContracts.TRIGGERS,
                        selected = triggers,
                        onSelectedChange = { triggers = it },
                        customKey = SelfDisciplineContracts.SETTING_CUSTOM_TRIGGERS,
                        vm = vm,
                        addLabel = "新增诱因"
                    )
                    Spacer(Modifier.height(14.dp))

                    Text("当时的情绪（可选）", fontSize = TextSizes.Body, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    EditableChips(
                        options = SelfDisciplineContracts.MOODS,
                        selected = if (mood.isBlank()) emptySet() else setOf(mood),
                        onSelectedChange = { mood = it.firstOrNull().orEmpty() },
                        customKey = SelfDisciplineContracts.SETTING_CUSTOM_MOODS,
                        vm = vm,
                        single = true,
                        addLabel = "新增情绪"
                    )
                    Spacer(Modifier.height(14.dp))

                    LabeledTextField(
                        label = "备注（可选）",
                        value = note,
                        onValueChange = { note = it },
                        hint = "发生了什么、下次可以怎么做",
                        singleLine = false,
                        minLines = 3
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .padding(12.dp)
                ) {
                    Text(
                        "保存后会结束当前这一轮计时，历史最长记录不会被删。结束之后可以随时重新开始。",
                        fontSize = TextSizes.Tiny,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (saving) "保存中…" else "记录这次偏离",
                    enabled = !saving,
                    onClick = { showConfirm = true }
                )
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showDateSheet) {
        DatePickerSheet(
            initial = date,
            onPick = {
                date = it
                showDateSheet = false
            },
            onDismiss = { showDateSheet = false }
        )
    }

    if (showConfirm) {
        AppDialog(
            title = "确认记录这次偏离？",
            onDismiss = { showConfirm = false },
            confirmText = "确认记录",
            onConfirm = {
                showConfirm = false
                saving = true
                scope.launch {
                    endedPeriodId = vm.recordDeviation(
                        goalId = goalId,
                        date = date,
                        triggers = triggers.toList(),
                        mood = mood,
                        note = note
                    )
                    saving = false
                }
            }
        ) {
            Text(
                "记录后当前周期会结束，但所有历史都会保留。如果点错了，下一步可以直接撤销。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
        }
    }
}
