package com.qingji.app.ui.screen.selfdiscipline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * ============================================================
 * 自律管理模块的共享小零件
 * ------------------------------------------------------------
 * 这里只放「两个以上页面会用到」的东西：状态颜色、里程碑条、
 * 多选胶囊、三态选择、等级选择、日历图例、日期选择弹层。
 * 单个页面专用的布局留在各自 Screen 文件里，避免这个文件变成杂物间。
 * ============================================================
 */

/** 打卡状态对应的色块（日历着色 + 图例复用）。 */
fun statusColorOf(status: String): Color = when (status) {
    SelfDisciplineContracts.STATUS_ON_TARGET -> QingJiGreen
    SelfDisciplineContracts.STATUS_OFF_TARGET -> DangerRed
    SelfDisciplineContracts.STATUS_SKIP -> DividerColor
    else -> Color.Transparent
}

/** 状态中文名；空值返回「未记录」。 */
fun statusLabelOf(status: String?): String = when (status) {
    SelfDisciplineContracts.STATUS_ON_TARGET -> "符合目标"
    SelfDisciplineContracts.STATUS_OFF_TARGET -> "目标偏离"
    SelfDisciplineContracts.STATUS_SKIP -> "暂不记录"
    else -> "未记录"
}

/** 日期显示成「11月13日」，需要时带年份。 */
fun LocalDate.displayCn(includeYear: Boolean = false): String =
    if (includeYear) "${year}年${monthValue}月${dayOfMonth}日" else "${monthValue}月${dayOfMonth}日"

/** 当前进度所处的下一个里程碑。 */
val MILESTONES = listOf(1, 3, 7, 14, 30, 60, 90)
fun nextMilestone(currentDays: Int): Int =
    MILESTONES.firstOrNull { it > currentDays } ?: MILESTONES.last()

/** 里程碑进度条 + 节点（仪表盘 / 历史页复用）。 */
@Composable
fun MilestoneTimeline(currentDays: Int, modifier: Modifier = Modifier) {
    val next = nextMilestone(currentDays)
    val prev = MILESTONES.lastOrNull { it <= currentDays } ?: 0
    val progress = when {
        currentDays >= MILESTONES.last() -> 1f
        next == prev -> 1f
        else -> (currentDays - prev).toFloat() / (next - prev).coerceAtLeast(1)
    }
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MILESTONES.forEach { d ->
                Text(
                    "$d",
                    fontSize = TextSizes.Tiny,
                    fontWeight = if (d == next) FontWeight.Bold else FontWeight.Normal,
                    color = if (d <= currentDays) QingJiGreen else TextSecondary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ThinProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            if (currentDays >= MILESTONES.last()) "已达成全部里程碑 🎉"
            else "下一个里程碑：${next} 天（还差 ${next - currentDays} 天）",
            fontSize = TextSizes.Tiny, color = TextSecondary
        )
    }
}

/**
 * 多选胶囊（诱因 / 应对方式复用）。
 * 用 FlowRow 而不是 LazyVerticalGrid：它会自己换行、不滚动，
 * 放进可滚动的页面里不会被「无限高度」撑崩。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val on = opt in selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (on) QingJiGreen else PageBackground)
                    .clickable { onToggle(opt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    opt,
                    fontSize = TextSizes.Body,
                    color = if (on) CardWhite else TextPrimary
                )
            }
        }
    }
}

/** 三态打卡状态选择（符合目标 / 目标偏离 / 暂不记录）。 */
@Composable
fun StatusSegmented(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        SelfDisciplineContracts.STATUS_ON_TARGET to "符合目标",
        SelfDisciplineContracts.STATUS_OFF_TARGET to "目标偏离",
        SelfDisciplineContracts.STATUS_SKIP to "暂不记录"
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (token, label) ->
            val on = token == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (on) statusColorOf(token) else PageBackground)
                    .clickable { onSelect(token) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = TextSizes.Body,
                    color = if (on) CardWhite else TextPrimary
                )
            }
        }
    }
}

/** 1~5 级强度选择（冲动程度 / 应对后强度复用）。 */
@Composable
fun LevelPicker(
    level: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (SelfDisciplineContracts.URGE_MIN..SelfDisciplineContracts.URGE_MAX).forEach { n ->
            val on = n == level
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (on) QingJiGreen else PageBackground)
                    .clickable { onSelect(if (allowClear && on) 0 else n) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$n",
                    fontSize = TextSizes.Body,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) CardWhite else TextPrimary
                )
            }
        }
    }
}

/** 日历图例（符合 / 偏离 / 未记录）。 */
@Composable
fun SelfDisciplineLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center) {
        LegendDot("符合", QingJiGreen)
        LegendDot("偏离", DangerRed)
        LegendDot("未记录", DividerColor)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(color))
        Spacer(Modifier.size(4.dp))
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}

/**
 * 日期选择弹层。
 * App 里原来只有「选月份」（MonthPickerSheet），没有选某一天的通用控件；
 * 打卡日期、偏离日期、目标开始日都要选天，所以在这里做一次，三处共用。
 */
@Composable
fun DatePickerSheet(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate = LocalDate.now()
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    var selected by remember { mutableStateOf(initial) }

    AppBottomSheet(
        title = "选择日期",
        subtitle = if (minDate != null) "可选 ${minDate.displayCn()} 至今天" else "只能选择今天及以前的日期",
        confirmText = "确定",
        // 月历有 6 行格子，半屏放不下，直接整屏展开
        skipPartiallyExpanded = true,
        onConfirm = { onPick(selected) },
        onDismiss = onDismiss
    ) {
        // 月份切换：和训练月历保持同一套「上个月 / 下个月」文字，视觉统一
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                "上个月", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier
                    .clickable { month = month.minusMonths(1) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Text(
                "下个月", fontSize = TextSizes.Caption, color = QingJiGreen,
                modifier = Modifier
                    .clickable {
                        val next = month.plusMonths(1)
                        // 不让用户翻到「未来月份」，减少误选
                        if (!next.isAfter(YearMonth.from(maxDate))) month = next
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                Text(
                    text = d,
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - 1 + 7) % 7   // 周一 = 0
        val days = month.lengthOfMonth()
        val totalCells = ((leading + days + 6) / 7) * 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0 until totalCells / 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        val dayNumber = index - leading + 1
                        Box(
                            modifier = Modifier.weight(1f).height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNumber in 1..days) {
                                val date = month.atDay(dayNumber)
                                val enabled = !date.isAfter(maxDate) &&
                                    (minDate == null || !date.isBefore(minDate))
                                val isSelected = date == selected
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) QingJiGreen else Color.Transparent)
                                        .clickable(enabled = enabled) { selected = date },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$dayNumber",
                                        fontSize = TextSizes.Body,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> CardWhite
                                            !enabled -> TextSecondary.copy(alpha = 0.35f)
                                            else -> TextPrimary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "已选择：${selected.displayCn(includeYear = true)}",
            fontSize = TextSizes.Tiny,
            color = TextSecondary
        )
    }
}


/**
 * 可扩展的多选/单选胶囊（2026-11-13 新增）。
 * 除了预置项，还能像引导页那样「新增一项」：输入后存进设置表，
 * 打卡、冲动、偏离三个页面共用同一份自定义选项，不用各写一套。
 *
 * @param single true = 单选（情绪 / 单个诱因），false = 多选（诱因集合）
 * @param customKey 自定义项存在哪（见 SelfDisciplineContracts.SETTING_CUSTOM_*）
 */
@Composable
fun EditableChips(
    options: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    customKey: String,
    vm: SelfDisciplineViewModel,
    modifier: Modifier = Modifier,
    single: Boolean = false,
    addLabel: String = "新增一项"
) {
    var extras by remember { mutableStateOf<List<String>>(emptyList()) }
    var showInput by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(customKey) {
        extras = SelfDisciplineContracts.splitCsv(vm.getSetting(customKey).orEmpty())
    }

    fun toggle(value: String) {
        onSelectedChange(
            if (single) {
                if (value in selected) emptySet() else setOf(value)
            } else {
                if (value in selected) selected - value else selected + value
            }
        )
    }

    fun add() {
        val v = input.trim()
        if (v.isEmpty()) return
        if (v !in options && v !in extras) {
            extras = extras + v
            onSelectedChange(if (single) setOf(v) else selected + v)
            scope.launch {
                vm.setSetting(customKey, SelfDisciplineContracts.joinCsv(extras))
            }
        } else {
            onSelectedChange(if (single) setOf(v) else selected + v)
        }
        input = ""
        showInput = false
    }

    Column(modifier) {
        MultiSelectChips(
            options = options + extras,
            selected = selected,
            onToggle = { toggle(it) }
        )
        Spacer(Modifier.height(8.dp))
        if (!showInput) {
            Text(
                text = "＋ $addLabel",
                fontSize = TextSizes.Caption,
                color = QingJiGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PageBackground)
                    .clickable { showInput = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("写下你自己的选项", fontSize = TextSizes.Caption, color = TextSecondary) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = TextSizes.Body),
                    shape = RoundedCornerShape(Dimens.ItemCorner),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite,
                        focusedIndicatorColor = QingJiGreen,
                        unfocusedIndicatorColor = DividerColor,
                        cursorColor = QingJiGreen
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "添加",
                    fontSize = TextSizes.Body,
                    color = if (input.isBlank()) TextSecondary else QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = input.isNotBlank()) { add() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
