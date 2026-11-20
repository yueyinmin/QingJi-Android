package com.qingji.app.ui.screen.training

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.domain.DiaryRules
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.MonthPickerSheet
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import java.time.LocalDate
import java.time.YearMonth

/**
 * ============================================================
 * 心情日记（P1 改造）
 * ------------------------------------------------------------
 * 主人要求「训练日记放默认页，心情日记复用训练日记的结构和规则，
 * 但是记录心情好坏」。所以这里和 TrainingDiaryContent 是同一套骨架：
 *   顶部胶囊切换 → 主操作按钮 → 本月总结 → 月历 → 历史列表 → 明细弹层
 * 月历直接复用 TrainingCalendarCard，只是把「练过打绿点」换成
 * 「按 DiaryRules.level 的四档心情给格子着色」，规则仍在 domain 层，不重写。
 * ============================================================
 */
@Composable
fun MoodDiaryContent(
    diaries: List<Diary>,
    onOpenEdit: (Long) -> Unit,
    onRequestDelete: (Diary) -> Unit,
    /**
     * 与训练日记共用同一个月份状态（由 DiaryScreen 持有）。
     * 旧版两个 Tab 各 remember 一份，翻过月份再切回来日历会跳月，
     * 这是「切换像切屏」的直观来源之一。唯一调用方就是 DiaryScreen，
     * 所以直接设成必传，不留空回调。
     */
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    var detailDate by remember { mutableStateOf<LocalDate?>(null) }
    // QJ 日记日期查询：点「日记记录」旁的日期弹首页同款月历，只显示该日记录
    var queryDate by remember { mutableStateOf(com.qingji.app.domain.AppClock.today()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 一天可能写多篇，取当天最高的那次评分（和原来近 30 天格子同一套规则）
    val ratingByDate = remember(diaries) {
        diaries.groupBy { it.date }.mapValues { (_, list) -> list.maxOf { it.rating } }
    }
    val monthDiaries = diaries.filter { YearMonth.from(it.date) == month }
    val avgRating = if (monthDiaries.isEmpty()) 0.0
    else monthDiaries.map { it.rating }.average()
    val streak = remember(diaries) { currentStreak(ratingByDate.keys) }

    // 主人 2026-11-13 要求：日历里的灰色块去掉。没写日记的日子不再铺灰色底，
    // 只给当天真的有记录的日子着色；没记录的日子就是透明底 + 普通数字。
    val dateColors = remember(month, ratingByDate) {
        ratingByDate
            .filterKeys { YearMonth.from(it) == month }
            .mapValues { (date, rating) -> moodColor(DiaryRules.level(rating)) }
    }

    Box(modifier = modifier.fillMaxSize().background(PageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            // 与训练日记共用同一套主按钮 / 三栏统计组件，位置尺寸字号完全一致
            DiaryPrimaryButton("写一篇日记") { onOpenEdit(0L) }
            Spacer(Modifier.height(12.dp))

            // ---------- 本月总结：和训练日记同样三栏 ----------
            SectionCard {
                Text("本月心情", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    DiarySummaryItem("${monthDiaries.size} 篇", "篇数", Modifier.weight(1f))
                    DiarySummaryItem(
                        // 当月平均：只对「这个月真的写了的日记」求平均，不是按 30 天摊
                        //（例如当月写了 10 篇，就是这 10 篇评分的平均；没写日记的天数不参与计算）
                        if (monthDiaries.isEmpty()) "--" else "%.1f / 5".format(avgRating),
                        "平均评分", Modifier.weight(1f)
                    )
                    DiarySummaryItem("$streak 天", "连续记录", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 月历（复用训练日历） ----------
            TrainingCalendarCard(
                month = month,
                trainingDates = emptySet(),
                dateColors = dateColors,
                onPrevMonth = { onMonthChange(month.minusMonths(1)) },
                onNextMonth = { onMonthChange(month.plusMonths(1)) },
                onSelectDate = { detailDate = it }
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MoodLegend("低", QingJiGreenLight)
                MoodLegend("中", QingJiGreen.copy(alpha = 0.55f))
                MoodLegend("高", QingJiGreen)
            }
            Spacer(Modifier.height(12.dp))

            // QJ 日记日期查询：标题行带日期入口 + 前后一天；列表只显示选中日期
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("日记记录", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    modifier = Modifier.weight(1f))
                Text("‹", fontSize = 20.sp, color = TextSecondary,
                    modifier = Modifier.clickable {
                        queryDate = queryDate.minusDays(1)
                        onMonthChange(YearMonth.from(queryDate))
                    }.padding(horizontal = 8.dp))
                Text(
                    text = if (queryDate == com.qingji.app.domain.AppClock.today())
                        "今天" else queryDate.toString(),
                    fontSize = TextSizes.Caption, color = QingJiGreen,
                    modifier = Modifier.clickable { showDatePicker = true }.padding(4.dp)
                )
                Text("›", fontSize = 20.sp,
                    color = if (queryDate >= com.qingji.app.domain.AppClock.today())
                        TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                    modifier = Modifier.clickable(enabled = queryDate < com.qingji.app.domain.AppClock.today()) {
                        queryDate = queryDate.plusDays(1)
                        onMonthChange(YearMonth.from(queryDate))
                    }.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(8.dp))

            val dayDiaries = diaries.filter { it.date == queryDate }
            if (dayDiaries.isEmpty()) {
                SectionCard {
                    Text("这天没有日记。点上面的「写一篇日记」，记下心情怎么样。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                }
            } else {
                dayDiaries.forEach { diary ->
                    SectionCard(
                        modifier = Modifier.clickable { onOpenEdit(diary.id) },
                        contentPadding = Dimens.CardPaddingCompact
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(diary.date.toString(), fontSize = TextSizes.CardTitle,
                                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Spacer(Modifier.height(3.dp))
                                Text(DiaryRules.previewText(diary.content, diary.date),
                                    fontSize = TextSizes.Tiny, color = TextSecondary, maxLines = 2)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(moodLabel(diary.rating), fontSize = TextSizes.Caption, color = QingJiGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { detailDate = diary.date }) {
                                Text("明细", fontSize = TextSizes.Caption, color = QingJiGreen)
                            }
                            TextButton(onClick = { onRequestDelete(diary) }) {
                                Text("删除", fontSize = TextSizes.Caption, color = TextSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // v9：悬浮底栏会盖住页尾内容，滚动尾部让出 Dock 高度
            // TASK-05：统一从 LocalAppSettings 取，不再单独订阅 SettingsStore。
            val settings = com.qingji.app.ui.LocalAppSettings.current
            Spacer(Modifier.height(if (settings.floatingBottomBar) 116.dp else Dimens.FabSpace))
        }
    }

    // ---------- 某一天的明细弹层（和训练明细同款） ----------
    detailDate?.let { date ->
        val sameDay = diaries.filter { it.date == date }
        AppBottomSheet(
            title = "心情明细",
            subtitle = date.toString(),
            skipPartiallyExpanded = true,
            onDismiss = { detailDate = null }
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (sameDay.isEmpty()) {
                    Text("这天还没写日记。", fontSize = TextSizes.Body, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { detailDate = null; onOpenEdit(0L) }) {
                        Text("写一篇", fontSize = TextSizes.Body)
                    }
                } else {
                    sameDay.forEach { d ->
                        Text(moodLabel(d.rating) + "  " + "★".repeat(d.rating.coerceIn(0, 5)),
                            fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                        Spacer(Modifier.height(6.dp))
                        Text(d.content.ifBlank { "（没写内容）" },
                            fontSize = TextSizes.Body, color = TextPrimary, lineHeight = 21.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { detailDate = null; onOpenEdit(d.id) }) {
                                Text("编辑", fontSize = TextSizes.Caption, color = QingJiGreen)
                            }
                            TextButton(onClick = { onRequestDelete(d); detailDate = null }) {
                                Text("删除", fontSize = TextSizes.Caption, color = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    // QJ 日记日期查询：复用首页 MonthPickerSheet（只改本页查询日期，不动外层月份）
    if (showDatePicker) {
        MonthPickerSheet(
            selected = queryDate,
            onSelect = {
                queryDate = it
                onMonthChange(YearMonth.from(it))
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun MoodLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(4.dp))
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}

private fun moodColor(level: Int): Color = when (level) {
    DiaryRules.LEVEL_LOW -> QingJiGreenLight
    DiaryRules.LEVEL_MID -> QingJiGreen.copy(alpha = 0.55f)
    DiaryRules.LEVEL_HIGH -> QingJiGreen
    else -> DividerColor
}

private fun moodLabel(rating: Int): String = when {
    rating <= 0 -> "未评分"
    rating <= 2 -> "有点糟"
    rating == 3 -> "一般"
    rating == 4 -> "不错"
    else -> "很好"
}

/** 从今天往回数，连续有记录的天数；今天没写就从昨天算起。 */
private fun currentStreak(dates: Set<LocalDate>): Int {
    var day = LocalDate.now()
    if (day !in dates) day = day.minusDays(1)
    var count = 0
    while (day in dates) {
        count++
        day = day.minusDays(1)
    }
    return count
}
