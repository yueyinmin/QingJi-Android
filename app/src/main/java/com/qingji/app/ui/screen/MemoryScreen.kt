package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.MemoryViewModel
import java.time.Instant
import java.time.ZoneId

/**
 * AI 长期记忆管理页（阶段 I）。
 *
 * 设计边界都摆在页面上，不藏在对话框里：
 * - 只在你明确说「记住…」时才保存，聊天正文不会被自动收进来；
 * - 删除会话不会删除记忆，删记忆必须在这里单独点；
 * - AI 推断标「推断」，要你点确认才算事实。
 */
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    vm: MemoryViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    var editTarget by remember { mutableStateOf<MemoryRecord?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<MemoryRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "AI 长期记忆", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HintBox(
                    text = "只有你明确说「记住…」才会保存；删除会话不会删除记忆；" +
                        "AI 推断必须你确认才算事实。恢复出厂才会整表清空。"
                )
            }

            item {
                FilterRow(
                    selected = state.filter,
                    onSelect = vm::setFilter
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardWhite)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "不再记住这类内容",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "关掉只影响以后的新记忆，已经记下的仍在这里，可以单独删。",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                    )
                    listOf(
                        MemoryCategory.PREFERENCE,
                        MemoryCategory.NUTRITION,
                        MemoryCategory.TRAINING,
                        MemoryCategory.GOAL,
                        MemoryCategory.EVENT
                    ).forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${category.label}类",
                                fontSize = 13.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = category !in state.disabled,
                                onCheckedChange = { enabled -> vm.setCategoryEnabled(category, enabled) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "共 ${state.memories.size} 条 · 生效中 ${state.activeCount} 条",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (state.visible.isEmpty()) {
                item {
                    Text(
                        text = "还没有这类记忆。在对话里说「记住我不吃香菜」这样的一句话就会存下来。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            }

            items(state.visible, key = { it.id }) { record ->
                MemoryCard(
                    record = record,
                    onConfirm = { vm.confirm(record) },
                    onCorrect = {
                        editTarget = record
                        editText = record.content
                    },
                    onInvalidate = { vm.invalidate(record) },
                    onDelete = { deleteTarget = record }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            confirmButton = {
                TextButton(onClick = vm::dismissMessage) { Text("知道了") }
            },
            title = { Text("记忆") },
            text = { Text(message) }
        )
    }

    editTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.correct(record, editText)
                    editTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("取消") }
            },
            title = { Text("纠正这条记忆") },
            text = {
                Column {
                    Text(
                        text = "改完会变成「用户事实」，并且重新归类。",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        maxLines = 3
                    )
                }
            }
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(record)
                    deleteTarget = null
                }) { Text("删除", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
            title = { Text("删除这条记忆？") },
            text = {
                Text(
                    "只会删掉这条记忆：${record.content}\n\n" +
                        "你的饮食、体重、训练记录都不会受影响。"
                )
            }
        )
    }
}

@Composable
private fun FilterRow(selected: MemoryCategory?, onSelect: (MemoryCategory?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(null to "全部").plus(MemoryCategory.entries.map { it to it.label }).forEach { (category, label) ->
            val isSelected = category == selected
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else QingJiGreenDark,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) QingJiGreenDark else QingJiGreenLight)
                    .clickable { onSelect(category) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun MemoryCard(
    record: MemoryRecord,
    onConfirm: () -> Unit,
    onCorrect: () -> Unit,
    onInvalidate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(text = record.category.label, color = QingJiGreenDark, background = QingJiGreenLight)
            Spacer(modifier = Modifier.width(6.dp))
            Badge(
                text = record.truthType.label,
                color = when (record.truthType) {
                    MemoryTruth.USER_FACT -> QingJiGreenDark
                    MemoryTruth.CONFIRMED -> QingJiGreenDark
                    MemoryTruth.INFERRED -> Color(0xFFB26A00)
                },
                background = when (record.truthType) {
                    MemoryTruth.INFERRED -> Color(0xFFFFF2DD)
                    else -> QingJiGreenLight
                }
            )
            if (record.status != MemoryStatus.ACTIVE) {
                Spacer(modifier = Modifier.width(6.dp))
                Badge(
                    text = record.status.label,
                    color = TextSecondary,
                    background = Color(0xFFEDEDED)
                )
            }
        }

        Text(
            text = record.content,
            fontSize = 14.sp,
            color = if (record.status == MemoryStatus.ACTIVE) TextPrimary else TextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "更新于 ${formatDate(record.updatedAt)}" +
                if (record.sourceRef.isBlank()) "" else " · 来源：对话 ${record.sourceRef.take(8)}",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (record.truthType == MemoryTruth.INFERRED && record.status == MemoryStatus.ACTIVE) {
                ActionText(text = "确认是事实", color = QingJiGreen, onClick = onConfirm)
            }
            if (record.status == MemoryStatus.ACTIVE) {
                ActionText(text = "纠正", color = QingJiGreenDark, onClick = onCorrect)
                ActionText(text = "失效", color = TextSecondary, onClick = onInvalidate)
            }
            ActionText(text = "删除", color = DangerRed, onClick = onDelete)
        }
    }
}

@Composable
private fun Badge(text: String, color: Color, background: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

private fun formatDate(epochMillis: Long): String = runCatching {
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.getOrDefault("-")
