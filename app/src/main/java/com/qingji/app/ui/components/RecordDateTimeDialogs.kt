package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import java.time.LocalDate
import java.time.LocalTime

/**
 * ============================================================
 * 记录日期 / 时间的统一入口（QJ-RECORD-INPUT-UNIFIED-V3 / B3·B4）
 * ------------------------------------------------------------
 * 以前饮水和食物各写一套：饮水有滚轮但食物只有「今天/昨天/前天」，
 * 而且「HH:mm」的格式化散落在两个文件里。这里收敛成:
 * · [RecordDateChip] / [RecordTimeChip] —— 两枚可点标签（外观一致）
 * · [RecordDateDialog] / [RecordTimeDialog] —— 滚轮选择器（规则一致）
 *
 * 日期规则（与饮水保持一致，工单 7.2）：
 * · 不允许未来日期；
 * · 最早到今天往前一年；
 * · 非法日期（2 月 30 日）或超范围时「确定」置灰，不回写草稿。
 * 选择器本身只回传数字，业务校验在调用方做 —— 与 WheelDatePicker 分工一致。
 * ============================================================
 */
@Composable
fun RecordDateChip(
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PageBackground)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "📅", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = AppClock.formatDateLabel(date),
            fontSize = 13.sp,
            color = QingJiGreenDark,
            maxLines = 1
        )
    }
}

@Composable
fun RecordTimeChip(
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PageBackground)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🕐", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = AppClock.formatClock(time),
            fontSize = 13.sp,
            color = QingJiGreenDark,
            maxLines = 1
        )
    }
}

/** 日期 + 时间两枚标签并排（食物与饮水弹层共用同一行结构）。 */
@Composable
fun RecordDateTimeRow(
    date: LocalDate,
    time: LocalTime,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecordDateChip(date = date, onClick = onPickDate, modifier = Modifier.weight(1f))
        RecordTimeChip(time = time, onClick = onPickTime, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RecordDateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val today = AppClock.today()
    val earliest = today.minusDays(365)
    var year by remember { mutableStateOf(initialDate.year) }
    var month by remember { mutableStateOf(initialDate.monthValue) }
    var day by remember { mutableStateOf(initialDate.dayOfMonth) }

    val draftDate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
    val valid = draftDate != null && !draftDate.isAfter(today) && !draftDate.isBefore(earliest)

    AppDialog(
        title = "选择记录日期",
        confirmText = "确定",
        confirmEnabled = valid,
        onConfirm = { if (draftDate != null && valid) onConfirm(draftDate) },
        onDismiss = onDismiss
    ) {
        Text(
            text = if (valid) "可记录最近一年内的日期，未来日期不能记" else "日期无效或超出可记录范围",
            fontSize = 11.sp,
            color = com.qingji.app.ui.theme.TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        WheelDatePicker(
            initialDate = initialDate,
            yearRange = IntRange(today.year - 1, today.year)
        ) { y, m, d ->
            year = y
            month = m
            day = d
        }
    }
}

@Composable
fun RecordTimeDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    var time by remember { mutableStateOf(initialTime) }
    AppDialog(
        title = "选择记录时间",
        confirmText = "确定",
        onConfirm = { onConfirm(time) },
        onDismiss = onDismiss
    ) {
        WheelTimePicker(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute
        ) { h, m -> time = LocalTime.of(h, m) }
    }
}
