package com.qingji.app.ui.screen.selfdiscipline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.training.TrainingCalendarCard
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 历史日历（SD-008）。
 * 复用训练月历的格子（dateColors 着色），点某一天看当天完整记录。
 * 未记录的日子保持空白，绝不自动填成「符合目标」。
 */
@Composable
fun SelfDisciplineHistoryScreen(
    goalId: Long,
    vm: SelfDisciplineViewModel,
    onBack: () -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var goalStart by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(goalId) {
        goalStart = vm.getGoal(goalId)?.startDate
    }

    val checkinsFlow = remember(goalId, month) {
        vm.observeCheckinRange(goalId, month.atDay(1), month.atEndOfMonth())
    }
    val monthCheckins by checkinsFlow.collectAsState(initial = emptyList())
    val urges by remember(goalId) { vm.observeUrges(goalId) }.collectAsState(initial = emptyList())
    val periods by remember(goalId) { vm.observePeriods(goalId) }.collectAsState(initial = emptyList())

    // 偏离日期从「周期结束原因」取：当天即使有自己的打卡，也要显示成偏离色
    val deviationPeriods = periods.filter {
        it.endReason == SelfDisciplineContracts.REASON_DEVIATION && it.endAt != null
    }

    val dateColors: Map<LocalDate, Color> = buildMap {
        monthCheckins
            .filter { it.status != SelfDisciplineContracts.STATUS_SKIP }
            .forEach { put(it.localDate, statusColorOf(it.status)) }
        deviationPeriods
            .mapNotNull { it.endAt }
            .filter { YearMonth.from(it) == month }
            .forEach { put(it, DangerRed) }
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "历史记录", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            TrainingCalendarCard(
                month = month,
                trainingDates = emptySet(),
                onPrevMonth = { month = month.minusMonths(1) },
                onNextMonth = { month = month.plusMonths(1) },
                onSelectDate = { selectedDate = it },
                dateColors = dateColors,
                // 全屏页没有悬浮按钮，不需要让位
                fabSafeEnd = 0.dp
            )

            Spacer(Modifier.height(10.dp))
            SelfDisciplineLegend(modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text(
                    "本月概览",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                val onTarget = monthCheckins.count { it.status == SelfDisciplineContracts.STATUS_ON_TARGET }
                val offTarget = monthCheckins.count { it.status == SelfDisciplineContracts.STATUS_OFF_TARGET }
                val skipped = monthCheckins.count { it.status == SelfDisciplineContracts.STATUS_SKIP }
                Row(modifier = Modifier.fillMaxWidth()) {
                    MonthStat("$onTarget", "符合", Modifier.weight(1f))
                    MonthStat("$offTarget", "偏离", Modifier.weight(1f))
                    MonthStat("$skipped", "暂不记录", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "点日历上的任意一天，可以看当天的打卡和冲动记录。",
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    val day = selectedDate
    if (day != null) {
        DayDetailSheet(
            date = day,
            checkin = monthCheckins.firstOrNull { it.localDate == day },
            urgesOfDay = urges.filter { it.localDate() == day },
            deviationPeriods = deviationPeriods.filter { it.endAt == day },
            isBeforeGoal = goalStart != null && day.isBefore(goalStart),
            onDismiss = { selectedDate = null }
        )
    }
}

/** 冲动记录的时间戳转成当地日期，用来判断「这一天有没有冲动」。 */
private fun SelfDisciplineUrgeEntity.localDate(): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun MonthStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = TextSizes.CardTitle, fontWeight = FontWeight.Bold, color = QingJiGreen)
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}

@Composable
private fun DayDetailSheet(
    date: LocalDate,
    checkin: SelfDisciplineCheckinEntity?,
    urgesOfDay: List<SelfDisciplineUrgeEntity>,
    deviationPeriods: List<com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity>,
    isBeforeGoal: Boolean,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        title = date.displayCn(includeYear = true),
        subtitle = when {
            isBeforeGoal -> "这天在目标开始之前，不算有效日期"
            deviationPeriods.isNotEmpty() -> "这天记录了目标偏离"
            checkin == null -> "这天没有记录"
            else -> statusLabelOf(checkin.status)
        },
        confirmText = "知道了",
        onConfirm = {},
        onDismiss = onDismiss
    ) {
        if (checkin == null && urgesOfDay.isEmpty() && deviationPeriods.isEmpty()) {
            Text(
                if (isBeforeGoal) "目标还没开始，这天不参与统计。" else "这天没有打卡，也没有冲动记录。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
            return@AppBottomSheet
        }

        if (deviationPeriods.isNotEmpty()) {
            Text(
                "目标偏离记录：这天的偏离结束了当轮周期",
                fontSize = TextSizes.Body,
                color = DangerRed
            )
            Spacer(Modifier.height(6.dp))
        }

        if (checkin != null) {
            Text(
                "状态：${statusLabelOf(checkin.status)}",
                fontSize = TextSizes.Body,
                color = TextPrimary
            )
            if (checkin.mood.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("情绪：${checkin.mood}", fontSize = TextSizes.Body, color = TextSecondary)
            }
            if (checkin.urgeLevel > 0) {
                Spacer(Modifier.height(6.dp))
                Text("冲动程度：${checkin.urgeLevel} 级", fontSize = TextSizes.Body, color = TextSecondary)
            }
            val triggers = SelfDisciplineContracts.splitCsv(checkin.triggers)
            if (triggers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("诱因：${triggers.joinToString("、")}", fontSize = TextSizes.Body, color = TextSecondary)
            }
            val coping = SelfDisciplineContracts.splitCsv(checkin.copingMethods)
            if (coping.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("应对：${coping.joinToString("、")}", fontSize = TextSizes.Body, color = TextSecondary)
            }
            if (checkin.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("备注：${checkin.note}", fontSize = TextSizes.Body, color = TextSecondary)
            }
        }

        if (urgesOfDay.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "这天的冲动记录（${urgesOfDay.size} 次）",
                fontSize = TextSizes.Body,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            urgesOfDay.forEach { u ->
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("强度 ${u.intensity} 级")
                        if (u.trigger.isNotBlank()) append(" · 诱因 ${u.trigger}")
                        if (u.copingMethod.isNotBlank()) append(" · 应对 ${u.copingMethod}")
                        if (u.resolved) append(" · 已缓解")
                    },
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
        }
    }
}
