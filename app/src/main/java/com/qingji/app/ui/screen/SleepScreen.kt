package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.SleepScoreCalculator
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.MonthPickerSheet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.WheelDatePicker
import com.qingji.app.ui.components.WheelTimePicker
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.NormalGreen
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.viewmodel.SleepTrendStats
import com.qingji.app.ui.viewmodel.SleepViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠页（2026-11-19 UI 全面重构 · 工单 P1）
 * ------------------------------------------------------------
 * 本次是**纯表现层重构**，数据结构、评分算法、趋势统计、跨天口径一行没动：
 * · 主页面从「7 张卡纵向堆叠」改为「4 张卡的紧凑仪表盘」：
 *   ① 今日睡眠核心卡（评分 + 时长合并，小环替代 150dp 大环）
 *   ② 七天趋势卡（压缩柱高、平均值/达标前置、点击某天切日期）
 *   ③ 睡眠时段卡（突出时间轴，不重复放大时长/入睡/起床）
 *   ④ 其他分析卡（指标 + 免责 + 备注合并）
 * · 录入弹层重排：时间区统一成一块、胶囊分组统一层级、备注缩小、
 *   保存按钮底部固定（内容区 weight 滚动 + imePadding + 防重复提交）。
 *
 * 铁律保持不变：深睡/REM/鼾声无数据来源 → 不画；无记录 → 空态不显示 0 分；
 * 评分是「根据你的记录估算」的自评合成分；全部计算走 domain 纯函数，页面只画。
 * ============================================================
 */
@Composable
fun SleepScreen(
    vm: SleepViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDate by vm.selectedDate.collectAsState()
    val night by vm.selectedNight.collectAsState()
    val recent30 by vm.recent30.collectAsState()
    val message by vm.message.collectAsState()
    val editing by vm.editing.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var showScoreDetail by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SleepRecordEntity?>(null) }
    // QJ-HEALTH-DATE-WATER-004：点顶部日期弹首页同款月历，选历史日期直接跳。
    var showDatePicker by remember { mutableStateOf(false) }

    val today = AppClock.today()
    val isToday = selectedDate == today
    // 7 天窗口以选中日期为右端点；历史某晚的评分和趋势都跟着这一晚走。
    val trend = SleepTrendStats.of(recent30, selectedDate, 7)
    // 评分只算选中那晚；规律性按工单取「近 7 晚」入睡时刻，不能把 30 天全塞进去。
    val weekForRegularity = recent30.filter {
        !it.date.isBefore(selectedDate.minusDays(6)) && !it.date.isAfter(selectedDate)
    }
    val score = night?.let { SleepScoreCalculator.score(it, weekForRegularity) }

    fun openNew() {
        vm.cancelEdit()
        showForm = true
    }

    fun openEdit(record: SleepRecordEntity) {
        vm.startEdit(record)
        showForm = true
    }

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = s("睡眠"),
            onBack = onBack,
            actionText = s("✏ 记录"),
            onAction = { openNew() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- 1. 顶部日期导航 ----------
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "‹", fontSize = 26.sp, color = TextSecondary,
                    modifier = Modifier
                        .clickable { vm.shiftSelectedDate(-1) }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
                Text(
                    text = if (isToday) s("今天") else selectedDate.format(DATE_CN),
                    fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold,
                    color = TextPrimary, textAlign = TextAlign.Center,
                    // QJ-HEALTH-DATE-WATER-004：点日期弹月历；加个轻微底色提示可点。
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDatePicker = true }
                        .padding(vertical = 4.dp)
                )
                Text(
                    text = "›", fontSize = 26.sp,
                    color = if (isToday) DividerColor else TextSecondary,
                    modifier = Modifier
                        .clickable(enabled = !isToday) { vm.shiftSelectedDate(1) }
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }
            if (!isToday) {
                TextButton(
                    onClick = { vm.backToToday() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(s("回到今天"), fontSize = TextSizes.Caption, color = QingJiGreen) }
            }
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

            // ---------- 2. 今日睡眠核心卡（评分 + 时长合并） ----------
            CoreSleepDashboard(
                night = night,
                score = score,
                isToday = isToday,
                selectedDate = selectedDate,
                onNew = { openNew() },
                onShowDetail = { showScoreDetail = true }
            )
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 3. 七天趋势卡（压缩 + 点击切日期） ----------
            WeeklyTrendCard(
                trend = trend,
                targetMinutes = SleepScoreCalculator.DEFAULT_TARGET_MINUTES,
                selectedDate = selectedDate,
                onPickDate = { vm.selectDate(it) }
            )
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 4. 睡眠时段卡（时间轴，不重复放大时长） ----------
            SleepDurationCard(night = night, onEdit = { n -> openEdit(n) })
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 5. 其他分析卡（指标 + 免责 + 备注合并） ----------
            OtherAnalysisCard(night = night, score = score)
            Spacer(Modifier.height(80.dp))
        }
    }

    // ---------- 弹层：评分明细（点核心卡分数区） ----------
    if (showScoreDetail && score != null) {
        AppDialog(
            title = s("分数怎么来的"),
            confirmText = s("知道了"),
            onConfirm = null,
            dismissText = s("关闭"),
            onDismiss = { showScoreDetail = false }
        ) {
            score.parts.forEach { part ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(part.label, fontSize = TextSizes.Caption, color = TextSecondary,
                        modifier = Modifier.weight(1f))
                    Text(
                        if (part.delta > 0) "+${part.delta}" else part.delta.toString(),
                        fontSize = TextSizes.Caption,
                        color = if (part.delta < 0) DangerRed else QingJiGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (part.note.isNotBlank()) {
                    Text(part.note, fontSize = 10.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                s("这是根据你填写的内容算出的估算分，不是设备监测结果。"),
                fontSize = 10.sp, color = TextSecondary
            )
        }
    }

    // ---------- 录入表单（底部弹层） ----------
    if (showForm) {
        SleepFormSheet(
            vm = vm,
            editing = editing,
            defaultDate = selectedDate,
            onDismiss = { showForm = false; vm.cancelEdit() },
            onRequestDelete = { pendingDelete = it }
        )
    }

    // ---------- 删除确认 ----------
    pendingDelete?.let { r ->
        AppDialog(
            title = s("删除这条睡眠记录？"),
            confirmText = s("删除"),
            danger = true,
            onConfirm = { vm.delete(r.id); pendingDelete = null; showForm = false },
            onDismiss = { pendingDelete = null }
        ) {
            Text("${r.date} 的记录会被删掉，其他数据不受影响。", fontSize = 14.sp, color = TextPrimary)
        }
    }

    // ---------- QJ-HEALTH-DATE-WATER-004：点顶部日期弹月历（复用首页组件） ----------
    if (showDatePicker) {
        MonthPickerSheet(
            selected = selectedDate,
            // vm.selectDate 内含禁未来 + 近 30 天范围校验，越界不写状态；
            // 数据由 selectedDate 驱动的 selectedNight/trend 自动刷新。
            onSelect = { vm.selectDate(it) },
            onDismiss = { showDatePicker = false }
        )
    }
}

/**
 * 今日睡眠核心卡：评分（小环）+ 实际睡眠时长同卡展示，入睡/起床为次级信息。
 * 无记录时整卡显示空态与记录入口，绝不显示虚假评分。
 */
@Composable
private fun CoreSleepDashboard(
    night: SleepRecordEntity?,
    score: SleepScoreCalculator.Result?,
    isToday: Boolean,
    selectedDate: LocalDate,
    onNew: () -> Unit,
    onShowDetail: () -> Unit
) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isToday) s("今日睡眠") else s("当日睡眠"),
                fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            if (score != null) {
                Text(
                    s("分数怎么算") + " ›", fontSize = TextSizes.Caption, color = QingJiGreen,
                    modifier = Modifier.clickable { onShowDetail() }.padding(vertical = 4.dp)
                )
            }
        }

        if (night == null || score == null) {
            // 空态：不显示 0 分 / 0 小时
            Spacer(Modifier.height(14.dp))
            Text(
                s("睡完一觉后来记一笔"),
                fontSize = TextSizes.Body, color = TextSecondary,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Text(s("✏ 记录昨晚睡眠"), fontSize = TextSizes.Body)
            }
            return@SectionCard
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 小评分环 + 评级
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompactScoreRing(score = score.score)
                Spacer(Modifier.height(4.dp))
                Text(s(score.rating), fontSize = TextSizes.Caption,
                    color = scoreColor(score.score), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(62.dp).background(DividerColor))
            Spacer(Modifier.width(18.dp))
            // 实际睡眠时长（足够视觉权重）
            Column(modifier = Modifier.weight(1f)) {
                val h = night.durationMinutes / 60
                val m = night.durationMinutes % 60
                Text("%d小时%d分".format(h, m), fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(s("实际睡眠时长"), fontSize = TextSizes.Caption, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(12.dp))
        // 入睡 / 起床作为次级信息
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(PageBackground).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${s("入睡")} ${formatMillis(night.startTime)}",
                fontSize = TextSizes.Caption, color = TextPrimary)
            Text("${s("起床")} ${formatMillis(night.endTime)}",
                fontSize = TextSizes.Caption, color = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            s("根据记录估算的睡眠质量 · 非设备监测结果"),
            fontSize = 10.sp, color = TextSecondary
        )
    }
}

/** 评分颜色：与评分档一致。 */
private fun scoreColor(score: Int): Color = when {
    score >= 90 -> NormalGreen
    score >= 75 -> QingJiGreen
    score >= 60 -> WarnOrange
    else -> DangerRed
}

/** 小型评分环（74dp），替代原 150dp 大环；分数数字仍清晰。 */
@Composable
private fun CompactScoreRing(score: Int) {
    val ring = scoreColor(score)
    val size = 74.dp
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(DividerColor, -90f, 360f, false, style = stroke)
            drawArc(ring, -90f, 360f * score / 100f, false, style = stroke)
        }
        Text(score.toString(), fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold, color = TextPrimary)
    }
}

/**
 * 七天趋势卡：标题 + 达标徽章、平均时长前置醒目、压缩柱图（40dp）、
 * 点某一天切换日期；无数据日期空柱 + "—"，绝不填 0。
 */
@Composable
private fun WeeklyTrendCard(
    trend: SleepTrendStats,
    targetMinutes: Int,
    selectedDate: LocalDate,
    onPickDate: (LocalDate) -> Unit
) {
    val recordedHours = trend.days.mapNotNull { it.second }
    val achieved = recordedHours.count { it * 60 >= 7 * 60 }
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s("每日睡眠分析"), fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight).padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("✓ ${s("已达标")} $achieved/7 ${s("天")}",
                    fontSize = 10.sp, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        // 平均睡眠时长前置醒目（原来埋在长文案里）
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s("最近七天平均睡眠"), fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.width(8.dp))
            if (trend.avgHours == null) {
                Text("--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            } else {
                val avgH = trend.avgHours!!.toInt()
                val avgM = ((trend.avgHours!! - avgH) * 60).toInt()
                Text("%d小时%d分".format(avgH, avgM), fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = QingJiGreen)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 压缩后的 7 天柱状图
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            trend.days.forEach { (date, hours) ->
                SleepDayBar(
                    date = date, hours = hours, targetMinutes = targetMinutes,
                    isSelected = date == selectedDate,
                    onClick = { onPickDate(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 7 天柱图单日柱：40dp 高、无数据画浅灰空柱、当天高亮、可点击切日期。 */
@Composable
private fun SleepDayBar(
    date: LocalDate,
    hours: Double?,
    targetMinutes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val maxHours = 10.0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) QingJiGreenLight else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        val fraction = ((hours ?: 0.0) / maxHours).coerceIn(0.06, 1.0).toFloat()
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(40.dp * fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (hours != null) QingJiGreen else DividerColor)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(date.format(DateTimeFormatter.ofPattern("EEE").withLocale(java.util.Locale.CHINA)),
            fontSize = 9.sp, color = if (isSelected) QingJiGreen else TextSecondary)
        Text(hours?.let { "%.1f".format(it) } ?: "—",
            fontSize = 8.sp, color = if (hours != null) QingJiGreen else TextSecondary)
    }
}

/**
 * 睡眠时段卡：核心卡已给时长/入睡/起床，这里只突出时间轴（轻量渐变条 +
 * 夜醒示意点 + 两端时刻），不再用 14dp 高渐变条与 22dp 大刻度重复放大。
 */
@Composable
private fun SleepDurationCard(night: SleepRecordEntity?, onEdit: (SleepRecordEntity) -> Unit) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s("睡眠时段"), fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (night != null) {
                Spacer(Modifier.weight(1f))
                Text(s("✏ 编辑"), fontSize = TextSizes.Caption, color = QingJiGreen,
                    modifier = Modifier.clickable { onEdit(night) })
            }
        }
        if (night == null) {
            Spacer(Modifier.height(8.dp))
            Text(s("尚未记录"), fontSize = TextSizes.Body, color = TextSecondary)
        } else {
            Spacer(Modifier.height(14.dp))
            // 轻量时间轴：8dp 渐变条，夜醒用小圆点示意（位置非真实时刻）
            Box(
                modifier = Modifier.fillMaxWidth().height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(QingJiGreenLight, QingJiGreen)))
            ) {
                if (night.wakeCount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        repeat(night.wakeCount) {
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(QingJiGreenDark))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMillis(night.startTime), fontSize = 11.sp, color = TextSecondary)
                Text(formatMillis(night.endTime), fontSize = 11.sp, color = TextSecondary)
            }
            if (night.wakeCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text("${s("夜醒")} ${night.wakeCount} ${s("次（示意位置）")}",
                    fontSize = 10.sp, color = TextSecondary)
            }
            // 午睡并入时段卡，不再单独占一张卡
            if (night.napMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                Text("${s("午睡")} ${night.napMinutes} ${s("分钟")}",
                    fontSize = TextSizes.Caption, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 其他睡眠分析卡：有真值的指标行 + 免责说明 + 备注，三块合并为一张卡。 */
@Composable
private fun OtherAnalysisCard(night: SleepRecordEntity?, score: SleepScoreCalculator.Result?) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Text(s("睡眠指标"), fontSize = TextSizes.CardTitle,
            fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (night == null || score == null) {
            Spacer(Modifier.height(8.dp))
            Text(s("暂无记录"), fontSize = TextSizes.Body, color = TextSecondary)
        } else {
            // 规律性（口径与评分一致）
            val reg = score.regularity
            if (reg == null) {
                MetricRow("🌗", s("睡眠规律性"), "--", s("数据不足"))
            } else {
                val regTag = if (reg.delta == 0) s("规律") else s("注意")
                MetricRow("🌗", s("睡眠规律性"), "${reg.deviationMinutes} ${s("分钟")}", regTag)
            }
            val wakeTag = when {
                night.wakeCount == 0 -> s("正常")
                night.wakeCount <= 2 -> s("一般")
                else -> s("偏多")
            }
            MetricRow("👁", s("夜间清醒"), "${night.wakeCount} ${s("次")}", wakeTag)
            MetricRow("☀", s("起床精神状态"),
                SleepRecordEntity.feelingLabel(night.morningFeeling),
                SleepRecordEntity.feelingLabel(night.morningFeeling))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = s("深睡 / 快速眼动期 / 鼾声监测需手环等设备数据，当前手动记录不含此项，不显示估算值。"),
            fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp
        )
        // 备注并入本卡（有才显示）
        if (!night?.note.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
            Spacer(Modifier.height(10.dp))
            Text(s("备注"), fontSize = TextSizes.Caption,
                fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(night!!.note, fontSize = TextSizes.Body, color = TextPrimary)
        }
    }
}

/** 指标卡的单行：图标 + 名称 + 值 + 右侧状态徽章。 */
@Composable
private fun MetricRow(icon: String, name: String, value: String, tag: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PageBackground)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(name, fontSize = TextSizes.Caption, color = TextPrimary,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, fontSize = TextSizes.Body, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(QingJiGreenLight)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) { Text(tag, fontSize = 10.sp, color = QingJiGreen, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * 录入表单（底部弹层）。
 * 本次重排（工单阶段五）：
 * · 时间选择区统一成一块灰底盒（入睡/起床同款布局 + 睡眠时长醒目结果）；
 * · 质量/精神/夜醒/午睡四组用统一的 ChoiceGroup（标签 + 横排胶囊），层级一致；
 * · 备注说明缩成一行小字；
 * · 内容区 weight(1f) 滚动，保存栏底部固定，imePadding 适配键盘，防重复提交。
 * 字段、校验、跨天口径、滚轮日期与原来完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepFormSheet(
    vm: SleepViewModel,
    editing: SleepRecordEntity?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onRequestDelete: (SleepRecordEntity) -> Unit
) {
    val today = AppClock.today()
    val startFromEdit = editing?.let {
        java.time.Instant.ofEpochMilli(it.startTime)
            .atZone(AppClock.zone).toLocalDate()
    }
    val endFromEdit = editing?.let {
        java.time.Instant.ofEpochMilli(it.endTime)
            .atZone(AppClock.zone).toLocalDate()
    }
    // 新增时默认入睡 = 选中日期前一天 23:30，起床 = 选中日期 07:30
    val initialSleepDate = startFromEdit ?: defaultDate.minusDays(1)
    var sleepDate by remember(editing?.id) { mutableStateOf(initialSleepDate) }
    var wakeDate by remember(editing?.id) { mutableStateOf(endFromEdit ?: defaultDate) }
    var sleepTime by remember(editing?.id) {
        mutableStateOf(editing?.let {
            java.time.Instant.ofEpochMilli(it.startTime).atZone(AppClock.zone).toLocalTime()
        } ?: LocalTime.of(23, 30))
    }
    var wakeTime by remember(editing?.id) {
        mutableStateOf(editing?.let {
            java.time.Instant.ofEpochMilli(it.endTime).atZone(AppClock.zone).toLocalTime()
        } ?: LocalTime.of(7, 30))
    }
    var showSleepPicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }
    var showSleepDatePicker by remember { mutableStateOf(false) }
    var showWakeDatePicker by remember { mutableStateOf(false) }
    val minSleepDate = minOf(today.minusDays(30), initialSleepDate)
    var quality by remember(editing?.id) { mutableStateOf(editing?.quality ?: 3) }
    var feeling by remember(editing?.id) { mutableStateOf(editing?.morningFeeling ?: 3) }
    var wakeCount by remember(editing?.id) { mutableStateOf(editing?.wakeCount ?: 0) }
    var napMinutes by remember(editing?.id) { mutableStateOf(editing?.napMinutes ?: 0) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    // 防重复提交：一次点击置 true，保存触发即关闭弹层，杜绝连点多次入库
    var saving by remember { mutableStateOf(false) }

    val formMinutes = java.time.Duration.between(
        sleepDate.atTime(sleepTime), wakeDate.atTime(wakeTime)
    ).toMinutes().coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite
    ) {
        // 固定高度比例（0.9 屏）：内容区可滚动 + 保存栏固定，手势下拉关闭仍由 ModalBottomSheet 提供
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding()
        ) {
            // ---------- 顶部标题（固定） ----------
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (editing == null) s("记录睡眠") else s("编辑这条睡眠"),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text("✕", fontSize = 18.sp, color = TextSecondary,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
            }

            // ---------- 内容区（滚动） ----------
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
            ) {
                Spacer(Modifier.height(8.dp))
                // 时间选择区：入睡/起床统一成一块
                DateTimeBlock(
                    sleepDate = sleepDate, sleepTime = sleepTime,
                    wakeDate = wakeDate, wakeTime = wakeTime,
                    formMinutes = formMinutes, today = today,
                    onSleepDate = { sleepDate = it },
                    onSleepDateClick = { showSleepDatePicker = true },
                    onSleepTimeClick = { showSleepPicker = true },
                    onWakeDate = { wakeDate = it },
                    onWakeDateClick = { showWakeDatePicker = true },
                    onWakeTimeClick = { showWakePicker = true }
                )
                Spacer(Modifier.height(14.dp))

                // 四组胶囊统一组件与间距
                ChoiceGroup(s("睡眠质量"),
                    (1..5).map { SleepRecordEntity.qualityLabel(it) to it },
                    quality) { quality = it }
                ChoiceGroup(s("起床精神状态"),
                    (1..4).map { SleepRecordEntity.feelingLabel(it) to it },
                    feeling) { feeling = it }
                ChoiceGroup(s("夜间醒来"),
                    listOf(s("没醒") to 0, "${1}${s("次")}" to 1,
                        "${2}${s("次")}" to 2, "${3}${s("次")}" to 3),
                    wakeCount) { wakeCount = it }
                ChoiceGroup(s("午睡"),
                    listOf(s("没午睡") to 0, "20${s("分钟")}" to 20, "30${s("分钟")}" to 30,
                        "60${s("分钟")}" to 60, "90${s("分钟")}" to 90),
                    napMinutes) { napMinutes = it }

                // 备注：小型辅助说明 + 简洁输入框
                Text(s("备注（选填）"), fontSize = TextSizes.Caption, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(DividerColor.copy(alpha = 0.45f))
                        .padding(10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) Text(s("睡前特殊情况可写这里"),
                                fontSize = TextSizes.Caption, color = TextSecondary)
                            inner()
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
            }

            // ---------- 底部固定保存栏 ----------
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp)
            ) {
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        vm.save(
                            sleepDate = sleepDate,
                            sleepHour = sleepTime.hour, sleepMinute = sleepTime.minute,
                            wakeDate = wakeDate,
                            wakeHour = wakeTime.hour, wakeMinute = wakeTime.minute,
                            quality = quality, morningFeeling = feeling,
                            wakeCount = wakeCount, napMinutes = napMinutes, note = note
                        )
                        onDismiss()
                    },
                    enabled = formMinutes > 0 && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (editing == null) s("保存睡眠记录") else s("保存修改"),
                        fontSize = TextSizes.Body)
                }
                if (editing != null) {
                    TextButton(onClick = { onRequestDelete(editing) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(s("删除这条睡眠记录？"), fontSize = TextSizes.Caption, color = DangerRed)
                    }
                }
            }
        }
    }

    // 时间滚轮
    if (showSleepPicker) {
        AppDialog(title = s("选择入睡时间"), confirmText = s("确定"),
            onConfirm = { showSleepPicker = false }, onDismiss = { showSleepPicker = false }) {
            WheelTimePicker(sleepTime.hour, sleepTime.minute,
                onSelected = { h, m -> sleepTime = LocalTime.of(h, m) })
        }
    }
    if (showWakePicker) {
        AppDialog(title = s("选择起床时间"), confirmText = s("确定"),
            onConfirm = { showWakePicker = false }, onDismiss = { showWakePicker = false }) {
            WheelTimePicker(wakeTime.hour, wakeTime.minute,
                onSelected = { h, m -> wakeTime = LocalTime.of(h, m) })
        }
    }

    // 日期滚轮（年/月/日三列）：非法日期确定置灰，不写回表单
    if (showSleepDatePicker) {
        var picked by remember {
            mutableStateOf(Triple(sleepDate.year, sleepDate.monthValue, sleepDate.dayOfMonth))
        }
        val pickedDate = runCatching { LocalDate.of(picked.first, picked.second, picked.third) }.getOrNull()
        val valid = pickedDate != null &&
            !pickedDate.isBefore(minSleepDate) && !pickedDate.isAfter(today)
        AppDialog(title = s("选择入睡日期"), confirmEnabled = valid,
            onConfirm = {}, onDismiss = { showSleepDatePicker = false }) {
            WheelDatePicker(initialDate = sleepDate) { y, m, d ->
                picked = Triple(y, m, d)
                val parsed = runCatching { LocalDate.of(y, m, d) }.getOrNull()
                if (parsed != null && !parsed.isBefore(minSleepDate) && !parsed.isAfter(today)) {
                    sleepDate = parsed
                }
            }
            if (!valid) {
                Spacer(Modifier.height(8.dp))
                Text(s("超出可记录范围") + "：${minSleepDate.format(DATE_CN)} ~ ${today.format(DATE_CN)}",
                    fontSize = TextSizes.Caption, color = DangerRed)
            }
        }
    }
    if (showWakeDatePicker) {
        var picked by remember {
            mutableStateOf(Triple(wakeDate.year, wakeDate.monthValue, wakeDate.dayOfMonth))
        }
        val pickedDate = runCatching { LocalDate.of(picked.first, picked.second, picked.third) }.getOrNull()
        val valid = pickedDate != null &&
            !pickedDate.isBefore(sleepDate) && !pickedDate.isAfter(today)
        AppDialog(title = s("选择起床日期"), confirmEnabled = valid,
            onConfirm = {}, onDismiss = { showWakeDatePicker = false }) {
            WheelDatePicker(initialDate = wakeDate) { y, m, d ->
                picked = Triple(y, m, d)
                val parsed = runCatching { LocalDate.of(y, m, d) }.getOrNull()
                if (parsed != null && !parsed.isBefore(sleepDate) && !parsed.isAfter(today)) {
                    wakeDate = parsed
                }
            }
            if (!valid) {
                Spacer(Modifier.height(8.dp))
                Text(s("起床日期不能早于入睡日期"),
                    fontSize = TextSizes.Caption, color = DangerRed)
            }
        }
    }
}

/**
 * 时间选择区：入睡/起床两块同款布局，包在一块浅底盒里，减少箭头/日期/时间的视觉割裂；
 * 下方睡眠时长作为醒目结果，非法时间红字提示（跨天/负数在 Repository 也有兜底）。
 */
@Composable
private fun DateTimeBlock(
    sleepDate: LocalDate, sleepTime: LocalTime,
    wakeDate: LocalDate, wakeTime: LocalTime,
    formMinutes: Long, today: LocalDate,
    onSleepDate: (LocalDate) -> Unit, onSleepDateClick: () -> Unit, onSleepTimeClick: () -> Unit,
    onWakeDate: (LocalDate) -> Unit, onWakeDateClick: () -> Unit, onWakeTimeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(PageBackground).padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        DateTimeRow(s("入睡"), sleepDate, sleepTime, onSleepDate, onSleepDateClick, onSleepTimeClick, today)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(8.dp))
        DateTimeRow(s("起床"), wakeDate, wakeTime, onWakeDate, onWakeDateClick, onWakeTimeClick, today)
        Spacer(Modifier.height(10.dp))
        if (formMinutes <= 0) {
            Text(s("起床时间必须晚于入睡时间"), fontSize = TextSizes.Caption, color = DangerRed)
        } else {
            Text("${s("睡眠时长")}：%d小时%d分".format(formMinutes / 60, formMinutes % 60),
                fontSize = TextSizes.Body, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 表单里的时间行：‹ 日期 › + 时刻，两块布局完全一致。 */
@Composable
private fun DateTimeRow(
    label: String, date: LocalDate, time: LocalTime,
    onShiftDate: (LocalDate) -> Unit, onDateClick: () -> Unit,
    onClickTime: () -> Unit, today: LocalDate
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.width(44.dp))
        Text("‹", fontSize = 20.sp, color = TextSecondary,
            modifier = Modifier.clickable {
                val prev = date.minusDays(1)
                if (!prev.isBefore(minOf(today.minusDays(30), date))) onShiftDate(prev)
            }.padding(horizontal = 8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .background(DividerColor.copy(alpha = 0.45f))
                .clickable { onDateClick() }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(date.format(DATE_CN), fontSize = TextSizes.Caption, color = TextPrimary)
        }
        Text("›", fontSize = 20.sp,
            color = if (date.isBefore(today)) TextSecondary else DividerColor,
            modifier = Modifier.clickable(enabled = date.isBefore(today)) { onShiftDate(date.plusDays(1)) }
                .padding(horizontal = 8.dp))
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(Dimens.PillCorner))
                .background(QingJiGreenLight)
                .clickable { onClickTime() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(time.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = TextSizes.Body, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 统一的胶囊分组：标签 + 横排胶囊，四组选项共用，保证层级一致。 */
@Composable
private fun ChoiceGroup(
    label: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(label, fontSize = TextSizes.Caption, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEachIndexed { index, (text, value) ->
            ChoicePill(text, selected == value) { onSelect(value) }
            if (index != options.lastIndex) Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
}

/** 单选胶囊：选中主绿白字，未选浅绿绿字，尺寸恒定（无放大动画）。 */
@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(Dimens.PillCorner))
            .background(if (selected) QingJiGreen else QingJiGreenLight)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = TextSizes.Caption, color = if (selected) CardWhite else QingJiGreen)
    }
}

/** 日期展示格式「11月19日」。 */
private val DATE_CN: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

/** epoch 毫秒 → HH:mm。 */
private fun formatMillis(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(AppClock.zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))
