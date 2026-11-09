package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * ============================================================
 * 日期下拉面板（v2.5）
 * ------------------------------------------------------------
 * 首页原来只有「‹ ›」两个箭头，一次只能翻一周，
 * 想看三个月前吃了什么得按几十下。
 *
 * 现在点开是一个**月历**：
 * - 顶部「2026年 11月」——**年份可以点**，点开是年份网格，选完回到月历；
 *   左右箭头按月翻；
 * - 中间是标准月历网格（周一起始，前后月补位灰显）；
 * - 未来的日期灰显不可选（和首页日期条一个规矩）；
 * - 选中日期后弹层自动关闭，首页跳到那天。
 *
 * 交互形式是底部弹层——和「+ 记一笔」记录面板、水杯菜单同一套语言。
 * ============================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPickerSheet(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = AppClock.today()

    // 日历当前显示的年月（和「选中的日期」是两回事：可以先翻到别的月份看）
    var viewYear by remember { mutableIntStateOf(selected.year) }
    var viewMonth by remember { mutableIntStateOf(selected.monthValue) }
    var pickingYear by remember { mutableStateOf(false) }

    // v1.2：容器换成全 App 统一的 AppBottomSheet（CardWhite + 把手 + 0.28 遮罩），
    // 之前是裸 ModalBottomSheet，Material 默认配色和整体弹层风格不一致
    AppBottomSheet(
        title = "选择日期",
        subtitle = "年份可点，跨年自由选；未来的日期不能记",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ---------- 头部：翻月箭头 + 年月标题（年份可点） ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 翻月箭头
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PageBackground)
                        .clickable {
                            viewMonth--
                            if (viewMonth < 1) {
                                viewMonth = 12
                                viewYear--
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "‹", fontSize = 18.sp, color = TextPrimary)
                }

                if (pickingYear) {
                    // 年份选择模式：只显示年份提示
                    Text(
                        text = "选择年份",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = QingJiGreenDark
                    )
                } else {
                    // 年月标题：年份可点进入年份选择
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { pickingYear = true }
                    ) {
                        Text(
                            text = "$viewYear 年",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pickingYear) TextSecondary else TextPrimary
                        )
                        Text(
                            text = " ${viewMonth} 月",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "▾",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PageBackground)
                        .clickable {
                            viewMonth++
                            if (viewMonth > 12) {
                                viewMonth = 1
                                viewYear++
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "›", fontSize = 18.sp, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(14.dp))

            if (pickingYear) {
                // ---------- 年份网格：前后各几年 ----------
                val years = (viewYear - 5)..(viewYear + 6)
                years.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { year ->
                            val isSelectedYear = year == viewYear
                            Text(
                                text = "${year} 年",
                                fontSize = 14.sp,
                                color = if (isSelectedYear) Color.White else TextPrimary,
                                fontWeight = if (isSelectedYear) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelectedYear) QingJiGreen else CardWhite)
                                    .clickable {
                                        viewYear = year
                                        pickingYear = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            } else {
                // ---------- 星期表头 ----------
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // ---------- 日期网格：6 行 × 7 列，前后月补位 ----------
                val firstDay = LocalDate.of(viewYear, viewMonth, 1)
                val startOffset = firstDay.dayOfWeek.value - 1   // 周一 = 0
                val startDate = firstDay.minusDays(startOffset.toLong())

                repeat(6) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val date = startDate.plusDays((row * 7 + col).toLong())
                            val inMonth = date.monthValue == viewMonth
                            val isSelected = date == selected
                            val isToday = date == today
                            val isFuture = date.isAfter(today)
                            val enabled = !isFuture

                            val bgColor = when {
                                isSelected -> QingJiGreen
                                isToday -> QingJiGreenLight
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                isSelected -> Color.White
                                isFuture -> TextSecondary.copy(alpha = 0.4f)
                                !inMonth -> TextSecondary.copy(alpha = 0.5f)
                                else -> TextPrimary
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                                    .height(38.dp)
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable(enabled = enabled) {
                                        onSelect(date)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${date.dayOfMonth}",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "未来还没到，先记眼前的吧",
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
