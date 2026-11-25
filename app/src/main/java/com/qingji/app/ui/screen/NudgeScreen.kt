package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.qingji.app.domain.nudge.NudgeKind
import com.qingji.app.domain.nudge.NudgeOutcome
import com.qingji.app.domain.nudge.NudgeRecord
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.NudgeViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 静默时段三档：宽一点的是默认，关掉就是全天都可能提醒。 */
private val SILENT_PRESETS = listOf(
    Triple("22:00–08:00", 22 * 60, 8 * 60),
    Triple("21:00–07:00", 21 * 60, 7 * 60),
    Triple("不静默", 0, 0)
)

/**
 * 「管家提醒」设置与历史页（阶段 J）。
 *
 * 边界写在页面上，不让用户猜：
 * - 全部判断在本地做，不联网，也不发系统通知；
 * - 只有点「让管家看看」才会把提醒里的几个数字发给 AI；
 * - 一天最多一条，同类还有冷却，静默时段一律不说话。
 */
@Composable
fun NudgeScreen(
    onBack: () -> Unit,
    vm: NudgeViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    var deleteTarget by remember { mutableStateOf<NudgeRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "管家提醒", onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HintBox(
                    text = "提醒全部由本地规则判断，不联网、不弹通知；只有你点「让管家看看」，" +
                        "才会把这条提醒里的几个数字发给 AI。一天最多一条，同类提醒有冷却。"
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "主动提醒",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (state.policy.enabled) "开着，进管家页时才判断" else "已关闭",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = state.policy.enabled,
                            onCheckedChange = vm::setEnabled
                        )
                    }

                    Text(
                        text = "每天最多几条",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2).forEach { limit ->
                            Chip(
                                text = "$limit 条",
                                selected = state.policy.dailyLimit == limit,
                                enabled = state.policy.enabled,
                                onClick = { vm.setDailyLimit(limit) }
                            )
                        }
                    }

                    Text(
                        text = "不打扰时段",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SILENT_PRESETS.forEach { (label, start, end) ->
                            Chip(
                                text = label,
                                selected = state.policy.silentStartMinute == start &&
                                    state.policy.silentEndMinute == end,
                                enabled = state.policy.enabled,
                                onClick = { vm.setSilentWindow(start, end) }
                            )
                        }
                    }
                }
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
                        text = "哪几类可以提醒我",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "关掉某一类只影响它自己，其它类不受影响。",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                    NudgeKind.entries.forEach { kind ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = kind.label,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = kindHint(kind),
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = kind !in state.policy.disabledKinds,
                                enabled = state.policy.enabled,
                                onCheckedChange = { checked -> vm.setKindEnabled(kind, checked) }
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近的提醒",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.history.isNotEmpty()) {
                        Text(
                            text = "清空",
                            fontSize = 12.sp,
                            color = QingJiGreenDark,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(QingJiGreenLight)
                                .clickable { vm.clearHistory() }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (state.history.isEmpty()) {
                item {
                    Text(
                        text = "还没有发过提醒。晚上进管家页，如果今天确实缺记录或没练，才会出现。",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }

            items(state.history, key = { it.id }) { record ->
                HistoryRow(
                    record = record,
                    onDelete = { deleteTarget = record }
                )
            }

            item { Column(modifier = Modifier.padding(bottom = 24.dp)) {} }
        }
    }

    deleteTarget?.let { record ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条记录？") },
            text = { Text("只删历史，不影响提醒设置，也不影响你的饮食/训练数据。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        vm.deleteRecord(record)
                        deleteTarget = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

private fun kindHint(kind: NudgeKind): String = when (kind) {
    NudgeKind.WEIGHT_DEVIATION -> "体重往目标反方向走，或两周没动（记录够多才判断）"
    NudgeKind.PLAN_NOT_DONE -> "今天计划里有训练，但还没记训练"
    NudgeKind.PROTEIN_LOW -> "吃了两餐以上，蛋白还差目标一大截"
    NudgeKind.RECORD_INCOMPLETE -> "晚上八点后，今天几乎没记饮食"
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = when {
            !enabled -> TextSecondary
            selected -> Color.White
            else -> QingJiGreenDark
        },
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    !enabled -> QingJiGreenLight.copy(alpha = 0.5f)
                    selected -> QingJiGreenDark
                    else -> QingJiGreenLight
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun HistoryRow(record: NudgeRecord, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.kind.label,
                fontSize = 11.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                text = formatTime(record.shownAt),
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 8.dp)
            )
            Column(modifier = Modifier.weight(1f)) {}
            if (record.outcome != NudgeOutcome.SHOWN) {
                Text(
                    text = if (record.outcome == NudgeOutcome.ACTED) "已去看" else "已忽略",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = "删除",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(
            text = record.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = record.body,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
