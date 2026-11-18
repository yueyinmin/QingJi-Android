package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.WheelTimePicker
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SleepTrendStats
import com.qingji.app.ui.viewmodel.SleepViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠记录页（工单 9）
 * ------------------------------------------------------------
 * 从记一笔「睡眠记录」直达（工单 9.2：不用先进设置页）。
 * 结构：昨晚摘要 → 记录表单（新增/编辑共用）→ 最近 7 天趋势 → 历史。
 * 铁律：
 * · 无记录显示「尚未记录」，不显示 0 小时（工单 9.5）；
 * · 跨午夜天然支持（入睡昨天 23:30 / 起床今天 07:30 是默认值）；
 * · 历史补录：日期用左右箭头调，最长回退 30 天内自由选。
 * ============================================================
 */
@Composable
fun SleepScreen(
    vm: SleepViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastNight by vm.lastNight.collectAsState()
    val recent30 by vm.recent30.collectAsState()
    val editing by vm.editing.collectAsState()
    val message by vm.message.collectAsState()
    var pendingDelete by remember { mutableStateOf<SleepRecordEntity?>(null) }

    // 表单状态：入睡（日期+时:分）、起床（日期+时:分）、质量、精神、夜醒、午睡、备注
    val today = LocalDate.now()
    var sleepDate by remember(editing?.id) {
        mutableStateOf(
            editing?.let { java.time.Instant.ofEpochMilli(it.startTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                ?: today.minusDays(1)
        )
    }
    var wakeDate by remember(editing?.id) {
        mutableStateOf(
            editing?.let { java.time.Instant.ofEpochMilli(it.endTime).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                ?: today
        )
    }
    var sleepTime by remember(editing?.id) {
        mutableStateOf(
            editing?.let {
                java.time.Instant.ofEpochMilli(it.startTime)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            } ?: LocalTime.of(23, 30)
        )
    }
    var wakeTime by remember(editing?.id) {
        mutableStateOf(
            editing?.let {
                java.time.Instant.ofEpochMilli(it.endTime)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            } ?: LocalTime.of(7, 30)
        )
    }
    var showSleepPicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }
    var quality by remember(editing?.id) { mutableStateOf(editing?.quality ?: 3) }
    var feeling by remember(editing?.id) { mutableStateOf(editing?.morningFeeling ?: 3) }
    var wakeCount by remember(editing?.id) { mutableStateOf(editing?.wakeCount ?: 0) }
    var napMinutes by remember(editing?.id) { mutableStateOf(editing?.napMinutes ?: 0) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }

    // 表单里的时长实时预览（跨午夜靠两个 LocalDate + LocalTime 组合，天然正确）
    val sleepDateTime = sleepDate.atTime(sleepTime)
    val wakeDateTime = wakeDate.atTime(wakeTime)
    val formMinutes = java.time.Duration.between(sleepDateTime, wakeDateTime).toMinutes().let {
        if (it < 0) 0 else it
    }

    val trend = SleepTrendStats.of(recent30, today, 7)

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "睡眠记录", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(8.dp))
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

            // ---------- 1. 昨晚摘要（无记录显示「尚未记录」，绝不显示 0 小时） ----------
            SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                Text("昨晚睡眠", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                if (lastNight == null) {
                    Text("尚未记录", fontSize = TextSizes.Body, color = TextSecondary)
                    Text("睡完一觉后，在下面记一下昨晚的情况。",
                        fontSize = TextSizes.Tiny, color = TextSecondary)
                } else {
                    val r = lastNight!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "%d小时%d分".format(r.durationMinutes / 60, r.durationMinutes % 60),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (r.quality > 0) {
                            Text(
                                SleepRecordEntity.qualityLabel(r.quality),
                                fontSize = TextSizes.Caption, color = QingJiGreen
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "入睡 ${formatMillis(r.startTime)} · 起床 ${formatMillis(r.endTime)}" +
                            if (r.morningFeeling > 0) " · 起床${SleepRecordEntity.feelingLabel(r.morningFeeling)}" else "",
                        fontSize = TextSizes.Caption, color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---------- 2. 记录表单（新增 / 编辑共用） ----------
            SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                Text(
                    if (editing == null) "记一笔昨晚睡眠" else "编辑这条睡眠",
                    fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                if (editing != null) {
                    TextButton(onClick = { vm.cancelEdit() }) { Text("取消编辑", fontSize = TextSizes.Caption) }
                }
                Spacer(Modifier.height(8.dp))

                // 入睡 / 起床两行：日期左右调 + 时间点弹轮盘选择
                DateTimeRow(
                    label = "入睡",
                    date = sleepDate,
                    time = sleepTime,
                    onShiftDate = { sleepDate = it },
                    onClickTime = { showSleepPicker = true }
                )
                Spacer(Modifier.height(6.dp))
                DateTimeRow(
                    label = "起床",
                    date = wakeDate,
                    time = wakeTime,
                    onShiftDate = { wakeDate = it },
                    onClickTime = { showWakePicker = true }
                )
                Spacer(Modifier.height(6.dp))
                // 自动算时长（工单 9.3），填反了给红字提示
                if (formMinutes <= 0) {
                    Text("起床时间必须晚于入睡时间", fontSize = TextSizes.Caption,
                        color = androidx.compose.ui.graphics.Color(0xFFD32F2F))
                } else {
                    Text("睡眠时长：%d小时%d分".format(formMinutes / 60, formMinutes % 60),
                        fontSize = TextSizes.Body, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))

                // 睡眠质量：5 档胶囊（工单 9.3）
                Text("睡眠质量", fontSize = TextSizes.Caption, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..5).forEach { q ->
                        ChoicePill(
                            label = SleepRecordEntity.qualityLabel(q),
                            selected = quality == q,
                            onClick = { quality = q }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 起床精神状态：4 档胶囊
                Text("起床精神状态", fontSize = TextSizes.Caption, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..4).forEach { f ->
                        ChoicePill(
                            label = SleepRecordEntity.feelingLabel(f),
                            selected = feeling == f,
                            onClick = { feeling = f }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 夜醒次数 / 午睡时长（可选）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("夜间醒来", fontSize = TextSizes.Caption, color = TextSecondary,
                        modifier = Modifier.weight(1f))
                    listOf(0, 1, 2, 3).forEach { n ->
                        ChoicePill(
                            label = if (n == 0) "没醒" else "${n}次",
                            selected = wakeCount == n,
                            onClick = { wakeCount = n }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("午睡时长", fontSize = TextSizes.Caption, color = TextSecondary,
                        modifier = Modifier.weight(1f))
                    listOf(0, 20, 30, 60, 90).forEach { m ->
                        ChoicePill(
                            label = if (m == 0) "没午睡" else "${m}分",
                            selected = napMinutes == m,
                            onClick = { napMinutes = m }
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                com.qingji.app.ui.components.HintBox(text = "备注可不填：睡前的特殊情况（咖啡、运动、加班）写在这里，回看时能对上状态。")
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = TextSizes.Body, color = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(DividerColor.copy(alpha = 0.45f))
                        .padding(10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) Text("备注（可选）", fontSize = TextSizes.Caption, color = TextSecondary)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.save(
                            sleepDate = sleepDate,
                            sleepHour = sleepTime.hour, sleepMinute = sleepTime.minute,
                            wakeDate = wakeDate,
                            wakeHour = wakeTime.hour, wakeMinute = wakeTime.minute,
                            quality = quality, morningFeeling = feeling,
                            wakeCount = wakeCount, napMinutes = napMinutes, note = note
                        )
                    },
                    enabled = formMinutes > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editing == null) "保存睡眠记录" else "保存修改", fontSize = TextSizes.Body)
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---------- 3. 最近 7 天趋势 ----------
            SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                Text("最近 7 天趋势", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                // 「连续 7 天」明确标注是滚动窗口，不是月历（工单 8.2 的区分要求）
                Text("连续 7 天滚动视图（含今天，按起床日期归属）",
                    fontSize = TextSizes.Tiny, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                if (trend.recordedCount == 0) {
                    Text("还没有睡眠数据，记下第一晚吧。", fontSize = TextSizes.Body, color = TextSecondary)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trend.days.forEach { (date, hours) ->
                            DayBar(date = date, hours = hours, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "平均 ${trend.avgHours?.let { "%.1f".format(it) } ?: "—"} 小时 · 记了 ${trend.recordedCount}/7 天",
                        fontSize = TextSizes.Caption, color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // ---------- 4. 历史记录（点编辑、删） ----------
            if (recent30.isNotEmpty()) {
                Text("历史记录", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("按起床日期归属，最近 30 天内的记录",
                    fontSize = TextSizes.Tiny, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                recent30.take(30).forEach { r ->
                    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.date.toString(), fontSize = TextSizes.Body, color = TextPrimary)
                                Text(
                                    "%d小时%d分 · %s".format(
                                        r.durationMinutes / 60, r.durationMinutes % 60,
                                        if (r.quality > 0) SleepRecordEntity.qualityLabel(r.quality) else "未填质量"
                                    ),
                                    fontSize = TextSizes.Tiny, color = TextSecondary
                                )
                            }
                            TextButton(onClick = { vm.startEdit(r) }) {
                                Text("编辑", fontSize = TextSizes.Caption, color = QingJiGreen)
                            }
                            Icon(
                                Icons.Filled.Delete, contentDescription = "删除",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickable { pendingDelete = r }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // ---------- 弹层：入睡时间选择 ----------
    if (showSleepPicker) {
        AppDialog(title = "选择入睡时间", confirmText = "确定", onConfirm = { showSleepPicker = false },
            onDismiss = { showSleepPicker = false }) {
            WheelTimePicker(
                initialHour = sleepTime.hour,
                initialMinute = sleepTime.minute,
                onSelected = { h, m -> sleepTime = LocalTime.of(h, m) }
            )
        }
    }
    // ---------- 弹层：起床时间选择 ----------
    if (showWakePicker) {
        AppDialog(title = "选择起床时间", confirmText = "确定", onConfirm = { showWakePicker = false },
            onDismiss = { showWakePicker = false }) {
            WheelTimePicker(
                initialHour = wakeTime.hour,
                initialMinute = wakeTime.minute,
                onSelected = { h, m -> wakeTime = LocalTime.of(h, m) }
            )
        }
    }
    // ---------- 弹层：删除确认 ----------
    pendingDelete?.let { r ->
        AppDialog(
            title = "删除这条睡眠记录？",
            confirmText = "删除",
            danger = true,
            onConfirm = { vm.delete(r.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        ) {
            Text("${r.date} 的记录会被删掉，其他数据不受影响。", fontSize = 14.sp, color = TextPrimary)
        }
    }
}

/** 表单里的一行：标签 + 日期左右调 + 时间（点了弹轮盘）。 */
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    onShiftDate: (LocalDate) -> Unit,
    onClickTime: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.width(44.dp))
        // 日期左右箭头：补录往前翻，最长不早于 30 天前（跟统计窗口对齐）
        Text("‹", fontSize = 20.sp, color = TextSecondary,
            modifier = Modifier
                .clickable {
                    val prev = date.minusDays(1)
                    if (!prev.isBefore(LocalDate.now().minusDays(30))) onShiftDate(prev)
                }
                .padding(horizontal = 8.dp))
        Text(date.format(DateTimeFormatter.ofPattern("M月d日")),
            fontSize = TextSizes.Caption, color = TextSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp))
        Text("›", fontSize = 20.sp, color = if (date.isBefore(LocalDate.now())) TextSecondary else DividerColor,
            modifier = Modifier
                .clickable(enabled = date.isBefore(LocalDate.now())) { onShiftDate(date.plusDays(1)) }
                .padding(horizontal = 8.dp))
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.PillCorner))
                .background(QingJiGreenLight)
                .clickable { onClickTime() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = TextSizes.Body, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 单选胶囊。 */
@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.PillCorner))
            .background(if (selected) QingJiGreen else QingJiGreenLight)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = TextSizes.Caption,
            color = if (selected) CardWhite else QingJiGreen)
    }
}

/** 7 天趋势的单日柱：没记录的画虚底（不画 0 高度柱，避免「0 小时」错觉）。 */
@Composable
private fun DayBar(date: LocalDate, hours: Double?, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val maxHours = 12.0
        val fraction = ((hours ?: 0.0) / maxHours).coerceIn(0.05, 1.0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((64.dp * fraction.toFloat()))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (hours != null) QingJiGreen else DividerColor)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = date.format(DateTimeFormatter.ofPattern("M/d")),
            fontSize = 9.sp, color = TextSecondary
        )
        Text(
            text = hours?.let { "%.1f".format(it) } ?: "—",
            fontSize = 8.sp, color = if (hours != null) QingJiGreen else TextSecondary
        )
    }
}

/** epoch 毫秒 → 「HH:mm」显示。 */
private fun formatMillis(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
