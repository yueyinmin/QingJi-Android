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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 冲动记录（SD-006）。
 * 一天可以记多次；冲动 ≠ 目标偏离，记了不影响计时器，只是给统计和复盘留证据。
 */
@Composable
fun SelfDisciplineUrgeScreen(
    goalId: Long,
    vm: SelfDisciplineViewModel,
    onBack: () -> Unit
) {
    val urges by remember(goalId) { vm.observeUrges(goalId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SelfDisciplineUrgeEntity?>(null) }

    val sorted = urges.sortedByDescending { it.occurredAt }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = "冲动记录",
            onBack = onBack,
            actionText = "记一次",
            onAction = { showSheet = true }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            if (sorted.isEmpty()) {
                SectionCard {
                    Text(
                        "还没有冲动记录",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "冲动来的时候记一笔，写下诱因和用了什么办法，时间长了就能看出什么方式对你最有效。",
                        fontSize = TextSizes.Body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(QingJiGreen)
                            .clickable { showSheet = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("记下第一次冲动", fontSize = TextSizes.Body, color = CardWhite)
                    }
                }
            } else {
                sorted.forEach { urge ->
                    UrgeCard(urge = urge, onDelete = { pendingDelete = urge })
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "共 ${sorted.size} 次。冲动记录不会重置计时，也不会被算作目标偏离。",
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showSheet) {
        AddUrgeSheet(
            vm = vm,
            onDismiss = { showSheet = false },
            onSave = { intensity, trigger, coping, after, resolved, note ->
                scope.launch {
                    vm.addUrge(
                        goalId = goalId,
                        intensity = intensity,
                        trigger = trigger,
                        copingMethod = coping,
                        intensityAfter = after,
                        resolved = resolved,
                        note = note
                    )
                    showSheet = false
                }
            }
        )
    }

    pendingDelete?.let { urge ->
        AppDialog(
            title = "删除这条记录？",
            onDismiss = { pendingDelete = null },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch { vm.deleteUrge(urge.id) }
                pendingDelete = null
            }
        ) {
            Text(
                "只删这一条误记，其他记录和计时都不受影响。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun UrgeCard(urge: SelfDisciplineUrgeEntity, onDelete: () -> Unit) {
    val time = remember(urge.occurredAt) {
        Instant.ofEpochMilli(urge.occurredAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                fontSize = TextSizes.Body,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            // 强度用 5 个小点表示，比数字直观
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (1..SelfDisciplineContracts.URGE_MAX).forEach { n ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (n <= urge.intensity) QingJiGreen else TextSecondary.copy(alpha = 0.25f))
                    )
                }
            }
        }
        val detail = buildString {
            if (urge.trigger.isNotBlank()) append("诱因：${urge.trigger}")
            if (urge.copingMethod.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append("应对：${urge.copingMethod}")
            }
            if (urge.intensityAfter > 0) {
                if (isNotEmpty()) append(" · ")
                append("之后：${urge.intensityAfter} 级")
            }
            if (urge.resolved) {
                if (isNotEmpty()) append(" · ")
                append("已缓解")
            }
        }
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(detail, fontSize = TextSizes.Caption, color = TextSecondary)
        }
        if (urge.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(urge.note, fontSize = TextSizes.Caption, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "删除",
            fontSize = TextSizes.Tiny,
            color = DangerRed,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onDelete() }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AddUrgeSheet(
    vm: SelfDisciplineViewModel,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, Int, Boolean, String) -> Unit
) {
    var intensity by remember { mutableStateOf(3) }
    var trigger by remember { mutableStateOf("") }
    var coping by remember { mutableStateOf("") }
    var after by remember { mutableStateOf(0) }
    var resolved by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    AppBottomSheet(
        title = "记一次冲动",
        subtitle = "写下当下的情况，方便以后复盘",
        confirmText = "保存",
        // 内容比半屏高，直接展开到内容高度，避免「保存」被裁到屏幕外
        skipPartiallyExpanded = true,
        onConfirm = { onSave(intensity, trigger, coping, after, resolved, note) },
        onDismiss = onDismiss
    ) {
        // 内容比一屏高时，按钮会被挤出屏幕；这里限高 + 可滚动，
        // 保证「保存」永远在弹层底部看得见。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
        ) {
        Text("冲动程度", fontSize = TextSizes.Body, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        LevelPicker(level = intensity, onSelect = { intensity = it })
        Spacer(Modifier.height(14.dp))

        Text("主要诱因", fontSize = TextSizes.Body, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        EditableChips(
            options = SelfDisciplineContracts.TRIGGERS,
            selected = if (trigger.isBlank()) emptySet() else setOf(trigger),
            onSelectedChange = { trigger = it.firstOrNull().orEmpty() },
            customKey = SelfDisciplineContracts.SETTING_CUSTOM_TRIGGERS,
            vm = vm,
            single = true,
            addLabel = "新增诱因"
        )
        Spacer(Modifier.height(14.dp))

        Text("采取的应对方式", fontSize = TextSizes.Body, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        EditableChips(
            options = SelfDisciplineContracts.COPING_METHODS,
            selected = if (coping.isBlank()) emptySet() else setOf(coping),
            onSelectedChange = { coping = it.firstOrNull().orEmpty() },
            customKey = SelfDisciplineContracts.SETTING_CUSTOM_COPING,
            vm = vm,
            single = true,
            addLabel = "新增应对方式"
        )
        Spacer(Modifier.height(14.dp))

        Text("应对后的冲动程度（可选）", fontSize = TextSizes.Body, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        LevelPicker(level = after, onSelect = { after = it }, allowClear = true)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("是否缓解", fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = resolved,
                onCheckedChange = { resolved = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CardWhite,
                    checkedTrackColor = QingJiGreen
                )
            )
        }
        Spacer(Modifier.height(14.dp))

        LabeledTextField(
            label = "备注（可选）",
            value = note,
            onValueChange = { note = it },
            hint = "当时在做什么、怎么走出来的",
            singleLine = false,
            minLines = 2
        )
        }
    }
}
