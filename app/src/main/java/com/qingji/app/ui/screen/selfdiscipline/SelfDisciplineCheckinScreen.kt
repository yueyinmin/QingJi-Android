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
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
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
 * 每日打卡（SD-005）。
 * 同一目标同一天只保留一条：再进来就是编辑原记录，不会产生重复。
 * 顶部留一个「一键符合目标」的快路径，下面才是完整表单，非必填项都能留空。
 */
@Composable
fun SelfDisciplineCheckinScreen(
    goalId: Long,
    vm: SelfDisciplineViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var date by remember { mutableStateOf(LocalDate.now()) }
    var status by remember { mutableStateOf(SelfDisciplineContracts.STATUS_ON_TARGET) }
    var mood by remember { mutableStateOf("") }
    var urgeLevel by remember { mutableStateOf(0) }
    var triggers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var coping by remember { mutableStateOf<Set<String>>(emptySet()) }
    var note by remember { mutableStateOf("") }
    var hasExisting by remember { mutableStateOf(false) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 换日期就重新读那天的记录（没有就清空成默认值）
    LaunchedEffect(goalId, date) {
        val c = vm.getCheckin(goalId, date)
        hasExisting = c != null
        status = c?.status ?: SelfDisciplineContracts.STATUS_ON_TARGET
        mood = c?.mood.orEmpty()
        urgeLevel = c?.urgeLevel ?: 0
        triggers = SelfDisciplineContracts.splitCsv(c?.triggers.orEmpty()).toSet()
        coping = SelfDisciplineContracts.splitCsv(c?.copingMethods.orEmpty()).toSet()
        note = c?.note.orEmpty()
    }

    fun save(quickStatus: String? = null) {
        if (saving) return
        saving = true
        scope.launch {
            vm.saveCheckin(
                goalId = goalId,
                date = date,
                status = quickStatus ?: status,
                mood = mood,
                urgeLevel = urgeLevel,
                triggers = triggers.toList(),
                copingMethods = coping.toList(),
                note = note
            )
            saving = false
            onSaved()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "每日打卡", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            // 日期
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(PageBackground)
                        .clickable { showDateSheet = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("记录日期", fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(
                        date.displayCn(includeYear = true),
                        fontSize = TextSizes.Body,
                        color = QingJiGreen
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (hasExisting) "这一天已经记过，保存会覆盖原记录" else "支持补记以前的日子",
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))

            // 快路径
            if (!hasExisting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .clickable { save(SelfDisciplineContracts.STATUS_ON_TARGET) }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "一键打卡：今天符合目标",
                        fontSize = TextSizes.Body,
                        fontWeight = FontWeight.Medium,
                        color = QingJiGreen
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 详细打卡
            SectionCard {
                Text(
                    "今日状态",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                StatusSegmented(selected = status, onSelect = { status = it })
                Spacer(Modifier.height(16.dp))

                Text("情绪（可选）", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                MultiSelectChips(
                    options = SelfDisciplineContracts.MOODS,
                    selected = if (mood.isBlank()) emptySet() else setOf(mood),
                    onToggle = { mood = if (mood == it) "" else it }
                )
                Spacer(Modifier.height(16.dp))

                Text("冲动程度（可选）", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                LevelPicker(
                    level = urgeLevel,
                    onSelect = { urgeLevel = it },
                    allowClear = true
                )
                Spacer(Modifier.height(16.dp))

                Text("主要诱因（可选，多选）", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                MultiSelectChips(
                    options = SelfDisciplineContracts.TRIGGERS,
                    selected = triggers,
                    onToggle = { triggers = triggers.toggle(it) }
                )
                Spacer(Modifier.height(16.dp))

                Text("应对方式（可选，多选）", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                MultiSelectChips(
                    options = SelfDisciplineContracts.COPING_METHODS,
                    selected = coping,
                    onToggle = { coping = coping.toggle(it) }
                )
                Spacer(Modifier.height(16.dp))

                LabeledTextField(
                    label = "备注（可选）",
                    value = note,
                    onValueChange = { note = it },
                    hint = "今天想记点什么",
                    singleLine = false,
                    minLines = 3
                )
            }

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = if (saving) "保存中…" else "保存打卡",
                enabled = !saving,
                onClick = { save() }
            )

            if (hasExisting) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .clickable { showDeleteDialog = true }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("删除这一天记录", fontSize = TextSizes.Body, color = TextSecondary)
                }
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

    if (showDeleteDialog) {
        AppDialog(
            title = "删除这天的打卡？",
            onDismiss = { showDeleteDialog = false },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    vm.getCheckin(goalId, date)?.let { vm.deleteCheckin(it.id) }
                    showDeleteDialog = false
                    onSaved()
                }
            }
        ) {
            Text(
                "只删掉这一天，其他日子的记录不受影响。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
        }
    }
}

/** 集合里没有就加、有就减。多选胶囊共用。 */
private fun Set<String>.toggle(value: String): Set<String> =
    if (contains(value)) this - value else this + value
