package com.qingji.app.ui.screen.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.MonthPickerSheet
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ============================================================
 * 单日期查询型日记卡（QJ 日记日期查询工单 · 饮食 / 运动）
 * ------------------------------------------------------------
 * 与 [DiarySectionCard]（多日平铺 + 折叠）是两种不同能力，互不影响：
 * - 顶部日期行：‹ 日期 › + 当天合计千卡，点日期弹首页同款 MonthPickerSheet；
 * - 只显示 [selectedDate] 那天的记录，不回退、不平铺其它日期；
 * - 空数据给简洁空状态；记录多时卡片内部滚动（max 300dp），不撑高页面；
 * - 行内容由调用方注入，保留各日记原有的文字/字段样式，不强行统一。
 *
 * 日期状态由外部 ViewModel 持有（独立于首页），本组件只负责展示与回调。
 * ============================================================
 */
@Composable
fun <T> DayQueryDiaryCard(
    title: String,
    selectedDate: LocalDate,
    items: List<T>,
    /** 当天合计（千卡），显示在日期行右侧；null = 不显示 */
    dayTotalKcal: Int? = null,
    emptyText: String,
    emptyActionText: String? = null,
    onEmptyAction: () -> Unit = {},
    onSelectDate: (LocalDate) -> Unit,
    onShiftDate: (Long) -> Unit,
    onBackToToday: () -> Unit,
    maxListHeight: androidx.compose.ui.unit.Dp = 300.dp,
    rowContent: @Composable RowScope.(T) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val today = AppClock.today()
    val isToday = selectedDate == today
    val listScroll = rememberScrollState()

    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        // ---------- 标题 ----------
        Text(title, fontSize = TextSizes.CardTitle,
            fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))

        // ---------- 日期行：‹ 日期 › + 当天合计（同一行） ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹", fontSize = 22.sp, color = TextSecondary,
                modifier = Modifier
                    .clickable { onShiftDate(-1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                text = if (isToday) s("今天") else selectedDate.format(DATE_CN_FULL),
                fontSize = TextSizes.Body, fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Text(
                text = "›", fontSize = 22.sp,
                color = if (isToday) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                modifier = Modifier
                    .clickable(enabled = !isToday) { onShiftDate(1) }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            if (dayTotalKcal != null) {
                Text(
                    text = "$dayTotalKcal ${s("千卡")}",
                    fontSize = TextSizes.Body, fontWeight = FontWeight.SemiBold,
                    color = QingJiGreen
                )
            }
        }
        if (!isToday) {
            Text(
                text = s("回到今天"),
                fontSize = TextSizes.Tiny, color = QingJiGreen,
                modifier = Modifier.clickable { onBackToToday() }.padding(vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))

        // ---------- 当天记录（仅选中日期，不回退） ----------
        if (items.isEmpty()) {
            Text(emptyText, fontSize = TextSizes.Caption, color = TextSecondary)
            if (emptyActionText != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .background(QingJiGreenLight)
                        .clickable { onEmptyAction() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(emptyActionText, fontSize = TextSizes.Caption,
                        color = QingJiGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight)
                    .verticalScroll(listScroll)
                    .animateContentSize()
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { rowContent(item) }
                }
            }
        }

        if (showDatePicker) {
            MonthPickerSheet(
                selected = selectedDate,
                onSelect = { onSelectDate(it) },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

private val DATE_CN_FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
