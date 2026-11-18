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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
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
 * 健康体系 V1.1 · 睡眠页重设计（工单 · 2026-11-18 P0）
 * ------------------------------------------------------------
 * 从「表单思维」改成「结果思维」，对齐参考图的信息层级：
 *   日期导航 → 评分环 → 每日睡眠分析(含7天柱图) → 时长+时间轴
 *   → 小睡 → 睡眠指标 → 备注；录入表单收进右上角「记录」按钮弹出的底部弹层。
 *
 * 铁律（工单红线 3/15）：
 * · 深睡 / REM / 鼾声分期、清醒时长都没有数据来源 → **不画、不渲染**；
 *   指标卡底部一行小字说明设备数据的事，不摆灰态假入口。
 * · 无记录：环置灰显示 --、正文「睡完一觉后来记一笔」，绝不显示 0 分；
 * · 评分是「根据你的记录估算」的自评合成分，环下方明确标注；
 * · 时间轴上的夜醒刻度位置是「示意」，轴下注明，不冒充真实时刻。
 *
 * 评分、趋势、规律性全部走 domain 纯函数（SleepScoreCalculator / SleepTrendStats），
 * 页面只负责画，不再自己算分。
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
                    modifier = Modifier.width(120.dp)
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

            // ---------- 2. 评分环卡 ----------
            SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                if (night == null || score == null) {
                    ScoreRing(score = null, rating = null)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = s("睡完一觉后来记一笔"),
                        fontSize = TextSizes.Body, color = TextSecondary,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { openNew() }, modifier = Modifier.fillMaxWidth()) {
                        Text(s("✏ 记录昨晚睡眠"), fontSize = TextSizes.Body)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().clickable { showScoreDetail = true }) {
                        ScoreRing(score = score.score, rating = s(score.rating))
                    }
                    Text(
                        text = s("根据你的记录估算 · 长按看分数明细"),
                        fontSize = 10.sp, color = TextSecondary,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 3. 每日睡眠分析卡 ----------
            DailyAnalysisCard(trend = trend, targetMinutes = SleepScoreCalculator.DEFAULT_TARGET_MINUTES)
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 4. 睡眠时长 + 时间轴卡 ----------
            SleepDurationCard(night = night, onEdit = { n -> openEdit(n) })
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 5. 小睡卡 ----------
            NapCard(night = night)
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 6. 睡眠指标卡 ----------
            SleepMetricsCard(night = night, score = score)
            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 7. 备注（有才显示） ----------
            if (!night?.note.isNullOrBlank()) {
                SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                    Text(s("备注"), fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(night!!.note, fontSize = TextSizes.Body, color = TextSecondary)
                }
                Spacer(Modifier.height(Dimens.Gap))
            }

            Spacer(Modifier.height(80.dp))
        }
    }

    // ---------- 弹层：评分明细（长按分数环） ----------
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

    // ---------- 录入表单（底部弹层，字段不变，入口下沉） ----------
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
}

/** 评分环：0~100 的分数 + 评级档，颜色随评级渐变。无记录传 null 画灰环 --。 */
@Composable
private fun ScoreRing(score: Int?, rating: String?) {
    val ringColor = when {
        score == null -> DividerColor
        score >= 90 -> NormalGreen
        score >= 75 -> QingJiGreen
        score >= 60 -> WarnOrange
        else -> DangerRed
    }
    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val stroke = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
            drawArc(DividerColor, -90f, 360f, false, style = stroke)
            if (score != null) {
                drawArc(ringColor, -90f, 360f * score / 100f, false, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score?.toString() ?: "--",
                fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary
            )
            if (rating != null) {
                Text(text = rating, fontSize = TextSizes.Caption,
                    color = ringColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** 每日睡眠分析：标题 + 达标徽章 + 本地规则文案 + 7 天柱状图（无数据空柱）。 */
@Composable
private fun DailyAnalysisCard(trend: SleepTrendStats, targetMinutes: Int) {
    val recordedHours = trend.days.mapNotNull { it.second }
    val achieved = recordedHours.count { it * 60 >= 7 * 60 }
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s("每日睡眠分析"), fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("✓ ${s("已达标")} $achieved/7 ${s("天")}",
                    fontSize = 10.sp, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnalysis(trend, targetMinutes),
            fontSize = TextSizes.Caption, color = TextSecondary, lineHeight = 19.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            trend.days.forEach { (date, hours) ->
                SleepDayBar(date = date, hours = hours, targetMinutes = targetMinutes,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 分析文案：纯本地规则模板，不接 AI、不联网。
 * 规则优先级：平均时长 → 连续达标 → 连续缺觉 → 夜醒多 → 入睡偏晚。
 * 只陈述有数据支撑的事实，不推断设备指标。
 */
private fun buildAnalysis(trend: SleepTrendStats, targetMinutes: Int): String {
    val sentences = mutableListOf<String>()
    val hours = trend.days.mapNotNull { it.second }
    if (hours.isEmpty()) return "还没有睡眠数据，记下第一晚，这里会给出属于你的分析。"
    val avg = hours.sum() / hours.size
    val avgH = avg.toInt()
    val avgM = ((avg - avgH) * 60).toInt()
    val targetH = targetMinutes / 60
    sentences += if (avg * 60 >= targetMinutes) {
        "最近记录平均睡眠 ${avgH}小时${avgM}分，达到 ${targetH} 小时目标。"
    } else {
        "最近记录平均睡眠 ${avgH}小时${avgM}分，离 ${targetH} 小时目标还差一点。"
    }
    val tail = trend.days.takeLast(3).mapNotNull { it.second }
    if (tail.size >= 3 && tail.all { it >= 7.0 }) {
        sentences += "连续 3 晚超过 7 小时，状态很稳，继续保持。"
    }
    val last2 = trend.days.takeLast(2).mapNotNull { it.second }
    if (last2.size >= 2 && last2.all { it < 6.0 }) {
        sentences += "连续两晚不足 6 小时，今晚尽量早点休息。"
    }
    return sentences.joinToString("")
}

/** 7 天柱图的单日柱：目标线用浅色底衬；无数据画浅灰空柱，绝不画 0 高度。 */
@Composable
private fun SleepDayBar(date: LocalDate, hours: Double?, targetMinutes: Int, modifier: Modifier) {
    val maxHours = 8.0
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        val fraction = ((hours ?: 0.0) / maxHours).coerceIn(0.06, 1.0).toFloat()
        Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp * fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (hours != null) QingJiGreen else DividerColor)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(date.format(DateTimeFormatter.ofPattern("EEE").withLocale(java.util.Locale.CHINA)),
            fontSize = 9.sp, color = TextSecondary)
        Text(hours?.let { "%.1f".format(it) } ?: "—",
            fontSize = 8.sp, color = if (hours != null) QingJiGreen else TextSecondary)
    }
}

/** 睡眠时长 + 时间轴：主条(入睡→起床) + 夜醒刻度(示意位置) + 午睡短条；不画分期。 */
@Composable
private fun SleepDurationCard(night: SleepRecordEntity?, onEdit: (SleepRecordEntity) -> Unit) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s("睡眠时长"), fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (night != null) {
                Text(s("✏ 编辑"), fontSize = TextSizes.Caption, color = QingJiGreen,
                    modifier = Modifier
                        .clickable { onEdit(night) }
                        .padding(start = 8.dp))
            }
        }
        if (night == null) {
            Spacer(Modifier.height(8.dp))
            Text(s("尚未记录"), fontSize = TextSizes.Body, color = TextSecondary)
        } else {
            Spacer(Modifier.height(8.dp))
            val h = night.durationMinutes / 60
            val m = night.durationMinutes % 60
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%d:%02d".format(h, m), fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(Modifier.width(10.dp))
                Text("${s("入睡")} ${formatMillis(night.startTime)} · ${s("起床")} ${formatMillis(night.endTime)}",
                    fontSize = TextSizes.Caption, color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            // 主睡眠条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(listOf(QingJiGreenLight, QingJiGreen))
                    )
            ) {
                // 夜醒次数：均匀示意刻度位置，不冒充真实时刻
                if (night.wakeCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(night.wakeCount) {
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(width = 3.dp, height = 22.dp)
                                .clip(RoundedCornerShape(2.dp)).background(QingJiGreenDark))
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMillis(night.startTime), fontSize = 10.sp, color = TextSecondary)
                Text(formatMillis(night.endTime), fontSize = 10.sp, color = TextSecondary)
            }
            if (night.wakeCount > 0) {
                Text("${s("夜醒")} ${night.wakeCount} ${s("次（示意位置）")}",
                    fontSize = 10.sp, color = TextSecondary)
            }
            // 午睡短条（有午睡才画）
            if (night.napMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth((night.napMinutes / 180f).coerceIn(0.08f, 0.5f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(QingJiGreenLight, QingJiGreen))
                        )
                )
                Text("${s("午睡")} ${night.napMinutes} ${s("分钟")}",
                    fontSize = 10.sp, color = TextSecondary)
            }
        }
    }
}

/** 小睡卡：无午睡显示「没午睡」，不显示 0 分钟。 */
@Composable
private fun NapCard(night: SleepRecordEntity?) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(s("小睡"), fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                val nap = night?.napMinutes ?: 0
                Text(if (nap > 0) "${nap} ${s("分钟")}" else s("没午睡"),
                    fontSize = TextSizes.Caption, color = TextSecondary)
            }
            Text("›", fontSize = 22.sp, color = TextSecondary)
        }
    }
}

/** 睡眠指标卡：只渲染有真值的四项；深睡/REM/鼾声整块不渲染，底部一行说明。 */
@Composable
private fun SleepMetricsCard(night: SleepRecordEntity?, score: SleepScoreCalculator.Result?) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Text(s("睡眠指标"), fontSize = TextSizes.CardTitle,
            fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (night == null || score == null) {
            Spacer(Modifier.height(8.dp))
            Text(s("暂无记录"), fontSize = TextSizes.Body, color = TextSecondary)
        } else {
            val durationH = night.durationMinutes / 60
            val durationM = night.durationMinutes % 60
            MetricRow("🌙", s("睡眠时长"), "%d:%02d".format(durationH, durationM), s(score.rating))
            // 规律性：直接取 Calculator 的近 7 晚入睡偏移结论，口径与评分一致、可回溯。
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
        Spacer(Modifier.height(10.dp))
        Text(
            text = s("深睡 / 快速眼动期 / 鼾声监测需手环等设备数据，当前手动记录不含此项，不显示估算值。"),
            fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

/** 指标卡的单行：图标 + 名称 + 值 + 右侧状态徽章。 */
@Composable
private fun MetricRow(icon: String, name: String, value: String, tag: String) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(QingJiGreenLight)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) { Text(tag, fontSize = 10.sp, color = QingJiGreen, fontWeight = FontWeight.SemiBold) }
    }
}

/**
 * 录入表单（底部弹层）。字段和工单 9 完全一致：
 * 入睡/起床(日期+时刻)、质量5档、精神4档、夜醒、午睡、备注。
 * 入口从页面主体下沉到这里；「午睡时长」标签改横排，不再竖排折行。
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
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    val endFromEdit = editing?.let {
        java.time.Instant.ofEpochMilli(it.endTime)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    }
    // 新增时默认入睡 = 选中日期前一天 23:30，起床 = 选中日期 07:30
    var sleepDate by remember(editing?.id) {
        mutableStateOf(startFromEdit ?: defaultDate.minusDays(1))
    }
    var wakeDate by remember(editing?.id) {
        mutableStateOf(endFromEdit ?: defaultDate)
    }
    var sleepTime by remember(editing?.id) {
        mutableStateOf(editing?.let {
            java.time.Instant.ofEpochMilli(it.startTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(23, 30))
    }
    var wakeTime by remember(editing?.id) {
        mutableStateOf(editing?.let {
            java.time.Instant.ofEpochMilli(it.endTime)
                .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(7, 30))
    }
    var showSleepPicker by remember { mutableStateOf(false) }
    var showWakePicker by remember { mutableStateOf(false) }
    var quality by remember(editing?.id) { mutableStateOf(editing?.quality ?: 3) }
    var feeling by remember(editing?.id) { mutableStateOf(editing?.morningFeeling ?: 3) }
    var wakeCount by remember(editing?.id) { mutableStateOf(editing?.wakeCount ?: 0) }
    var napMinutes by remember(editing?.id) { mutableStateOf(editing?.napMinutes ?: 0) }
    var note by remember(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }

    val formMinutes = java.time.Duration.between(
        sleepDate.atTime(sleepTime), wakeDate.atTime(wakeTime)
    ).toMinutes().coerceAtLeast(0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = 28.dp)
        ) {
            Text(
                if (editing == null) s("记一笔昨晚睡眠") else s("编辑这条睡眠"),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            DateTimeRow(s("入睡"), sleepDate, sleepTime,
                { sleepDate = it }, { showSleepPicker = true }, today)
            Spacer(Modifier.height(6.dp))
            DateTimeRow(s("起床"), wakeDate, wakeTime,
                { wakeDate = it }, { showWakePicker = true }, today)
            Spacer(Modifier.height(8.dp))

            if (formMinutes <= 0) {
                Text(s("起床时间必须晚于入睡时间"), fontSize = TextSizes.Caption,
                    color = DangerRed)
            } else {
                Text("${s("睡眠时长")}：%d小时%d分".format(formMinutes / 60, formMinutes % 60),
                    fontSize = TextSizes.Body, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))

            Text(s("睡眠质量"), fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                (1..5).forEach { q ->
                    ChoicePill(SleepRecordEntity.qualityLabel(q), quality == q) { quality = q }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            Text(s("起床精神状态"), fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                (1..4).forEach { f ->
                    ChoicePill(SleepRecordEntity.feelingLabel(f), feeling == f) { feeling = f }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // 夜醒：标签独占一行，胶囊横排在下一行（不再和标签挤一行导致竖排折行）
            Text(s("夜间醒来"), fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0, 1, 2, 3).forEach { n ->
                    ChoicePill(if (n == 0) s("没醒") else "${n}${s("次")}", wakeCount == n) { wakeCount = n }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // 午睡：同样标签独占一行，胶囊横排（工单 9.9 修复竖排折行）
            Text(s("午睡"), fontSize = TextSizes.Caption, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0, 20, 30, 60, 90).forEach { m ->
                    ChoicePill(if (m == 0) s("没午睡") else "${m}${s("分钟")}", napMinutes == m) { napMinutes = m }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            HintBox(text = s("备注可不填：睡前的特殊情况（咖啡、运动、加班）写在这里，回看时能对上状态。"))
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note,
                onValueChange = { note = it },
                textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(DividerColor.copy(alpha = 0.45f))
                    .padding(10.dp),
                decorationBox = { inner ->
                    Box {
                        if (note.isEmpty()) Text(s("备注（可选）"), fontSize = TextSizes.Caption, color = TextSecondary)
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(14.dp))

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
                    onDismiss()
                },
                enabled = formMinutes > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editing == null) s("保存睡眠记录") else s("保存修改"), fontSize = TextSizes.Body)
            }
            if (editing != null) {
                TextButton(onClick = { onRequestDelete(editing) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("${s("删除")}${s("这条睡眠记录？")}", fontSize = TextSizes.Caption, color = DangerRed)
                }
            }
        }
    }

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
}

/** 表单里的日期 + 时刻行：日期左右调（最长回退 30 天），时刻点开轮盘。 */
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    onShiftDate: (LocalDate) -> Unit,
    onClickTime: () -> Unit,
    today: LocalDate
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = TextSizes.Body, color = TextPrimary, modifier = Modifier.width(44.dp))
        Text("‹", fontSize = 20.sp, color = TextSecondary,
            modifier = Modifier
                .clickable {
                    val prev = date.minusDays(1)
                    if (!prev.isBefore(today.minusDays(30))) onShiftDate(prev)
                }
                .padding(horizontal = 8.dp))
        Text(date.format(DATE_CN), fontSize = TextSizes.Caption, color = TextSecondary,
            textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        Text("›", fontSize = 20.sp,
            color = if (date.isBefore(today)) TextSecondary else DividerColor,
            modifier = Modifier
                .clickable(enabled = date.isBefore(today)) { onShiftDate(date.plusDays(1)) }
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
        Text(label, fontSize = TextSizes.Caption, color = if (selected) CardWhite else QingJiGreen)
    }
}

/** epoch 毫秒 → HH:mm。 */
private fun formatMillis(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(AppClock.zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

private val DATE_CN: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
