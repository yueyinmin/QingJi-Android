package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.domain.FastingRules
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary


/** 断食设置：启用、方案、进食窗口。 */
@Composable
internal fun FastingSettingsDialog(
    current: QingJiSettings,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, planHours: Int, startMinute: Int, endMinute: Int, days: String, phaseAlert: Boolean) -> Unit
) {
    var enabled by remember { mutableStateOf(current.fastingEnabled) }
    var planHours by remember { mutableStateOf(current.fastingPlanHours) }
    var endMinute by remember { mutableStateOf(current.fastingEatEndMinute) }
    // v1.0.7：可进食时间不再单独选 —— 由「断食开始时间 + 方案小时数」自动对标（跨零点取模），不可修改
    val startMinute = (endMinute + planHours * 60) % 1440

    var fastingDays by remember { mutableStateOf(current.fastingDays) }
    var phaseAlert by remember { mutableStateOf(current.fastingPhaseAlert) }
    val daySet = fastingDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    com.qingji.app.ui.components.AppBottomSheet(
        title = "轻断食设置",
        confirmText = "保存",
        onConfirm = {
            onSave(enabled, planHours, startMinute, endMinute, fastingDays, phaseAlert)
        },
        onDismiss = onDismiss
    ) {
            Column(
                modifier = Modifier
                    .height(480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 启用开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { enabled = !enabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⏱", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "启用轻断食",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (enabled) "已开启" else "未开启",
                        fontSize = 12.sp,
                        color = if (enabled) QingJiGreen else TextSecondary
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(text = "方案", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                FastingRules.PLANS.chunked(3).forEach { rowPlans ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowPlans.forEach { hours ->
                        val selected = hours == planHours
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) QingJiGreenLight else PageBackground)
                                .clickable { planHours = hours }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = FastingRules.planLabel(hours),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) QingJiGreenDark else TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = FastingRules.planSubtitle(hours),
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    if (rowPlans.size < 3) {
                        repeat(3 - rowPlans.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
                }

                Spacer(Modifier.height(14.dp))

                // v1.0.3：生效周期（周几多选，默认全选）
                Text(text = "生效周期", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7)
                        .forEach { (label, day) ->
                            val on = day in daySet
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (on) QingJiGreenDark else TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (on) QingJiGreenLight else PageBackground)
                                    .clickable {
                                        val next = if (on) daySet - day else daySet + day
                                        fastingDays = if (next.isEmpty()) fastingDays else next.sorted().joinToString(",")
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                }

                Spacer(Modifier.height(14.dp))

                // v1.0.3：时段切换提醒（按进食窗口自动在开始/结束时提醒）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .clickable { phaseAlert = !phaseAlert }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔔", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "时段切换提醒", fontSize = 15.sp, color = TextPrimary)
                        Text(
                            text = "到进食开始、断食开始的时间点提醒你一声",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (phaseAlert) "已开启" else "未开启",
                        fontSize = 12.sp,
                        color = if (phaseAlert) QingJiGreen else TextSecondary
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(text = "断食时间", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                TimeAdjustRow(
                    label = "断食开始时间",
                    minute = endMinute,
                    onMinus = { endMinute = (endMinute - 30 + 1440) % 1440 },
                    onPlus = { endMinute = (endMinute + 30) % 1440 }
                )
                Spacer(Modifier.height(8.dp))
                // 只读行：可进食时间自动对标方案，不给加减按钮。
                // 左右各留一个和 ± 按钮同宽（28dp）的占位 —— 时间列就能和上方
                // 「断食开始时间」的那一列严格对齐，不会贴到最右边。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(PageBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "可进食时间",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(28.dp))
                    Text(
                        text = FastingRules.minuteLabel(startMinute),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QingJiGreenDark,
                        modifier = Modifier.width(64.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "结束时间按方案自动对标（${FastingRules.planLabel(planHours)} 即 ${planHours} 小时后），不可修改。" +
                        "窗口外的时段就是断食期，跨零点也能算。",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
    }
}


@Composable
internal fun TimeAdjustRow(
    label: String,
    minute: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.TinyCorner))
            .background(PageBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        TimeStepButton("−", onMinus)
        Text(
            text = FastingRules.minuteLabel(minute),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TimeStepButton("+", onPlus)
    }
}


@Composable
internal fun TimeStepButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 15.sp, color = QingJiGreenDark)
    }
}
