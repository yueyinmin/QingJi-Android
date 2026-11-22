package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.WheelDatePicker
import com.qingji.app.ui.components.WheelTimePicker
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.HomeUiState
import java.time.LocalDate


/**
 * 记录饮水（V2 工单 · 确认式）：首页水杯、四个一级页面的悬浮水滴、
 * 「＋」面板的喝水入口共用这一个弹层。
 *
 * 关键行为（工单第六部分）：
 * - 打开弹层**不写库**，只是把默认 200ml 摆出来；
 * - 档位 / [-][+] / 自定义输入都只改「待记录」的数值，不落库；
 * - 只有点「确认记录」才回调 onAdd（一次），随后关闭；
 * - 点「取消」、下拉关闭、点遮罩都不写任何数据；
 * - submitted 标志防止极快的连续点击产生两条记录。
 *
 * onOpenDetail 传 null 时隐藏「查看今日饮水详情」——非首页的调用方
 * 没有对应的详情弹层，宁可不显示也不接一个空回调。
 * 容器色/遮罩/把手和 AppBottomSheet 完全一致，不写死任何蓝色。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuickWaterSheet(
    waterMl: Int,
    goalMl: Int,
    onAdd: (Int, LocalDate, java.time.LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onOpenDetail: (() -> Unit)? = null
) {
    // 默认 200ml；只在确认时把这个值交给业务层
    var pendingMl by remember { mutableStateOf(200) }
    var customMl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    // QJ-HEALTH-DATE-WATER-004：日期默认今天、时间默认当前（走 AppClock 时区）；
    // 用户可用滚轮改历史日期与具体时间，取消不写库。
    var pendingDate by remember { mutableStateOf(AppClock.today()) }
    var pendingTime by remember { mutableStateOf(AppClock.now().toLocalTime().withSecond(0).withNano(0)) }
    var showDateWheel by remember { mutableStateOf(false) }
    var showTimeWheel by remember { mutableStateOf(false) }
    // 滚轮临时值：年/月/日三个数字（滚轮本身不校验业务口径，
    // 是否合法、是否未来日期在确定时统一校验，非法不写回 pendingDate）
    var draftY by remember { mutableStateOf(pendingDate.year) }
    var draftM by remember { mutableStateOf(pendingDate.monthValue) }
    var draftD by remember { mutableStateOf(pendingDate.dayOfMonth) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // V3.1：内容区可滚动，取消/确认按钮固定在底部，永远不用上滑才能看到。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 限制内容高度：超出滚动，按钮区永远留在弹窗内可见
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💧", fontSize = 18.sp)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = s("记录饮水"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已喝 $waterMl / 目标 ${goalMl}ml",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------- 当前待记录的水量（大字 + 加减） ----------
            Text(text = s("今日饮水"), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QingJiGreenLight)
                        .pressable {
                            pendingMl = (pendingMl - 50).coerceAtLeast(50)
                            customMl = ""
                            error = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 20.sp, color = QingJiGreenDark)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$pendingMl",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MultiColor.WaterText
                    )
                    Text("ml", fontSize = 12.sp, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QingJiGreenLight)
                        .pressable {
                            pendingMl = (pendingMl + 50).coerceAtMost(3000)
                            customMl = ""
                            error = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, color = QingJiGreenDark)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 常用档位：只选中，不写库 ----------
            Text(text = s("常用饮水量"), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150, 200, 300).forEach { ml ->
                    val selected = pendingMl == ml
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) QingJiGreen else QingJiGreenLight)
                            .pressable { pendingMl = ml; customMl = ""; error = "" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${ml}ml",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) CardWhite else QingJiGreenDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 自定义：输入后按「用这个数」替换待记录值，仍不写库 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = customMl,
                    onValueChange = { customMl = it; error = "" },
                    singleLine = true,
                    placeholder = {
                        Text(text = s("自定义水量"), fontSize = 12.sp, color = TextSecondary)
                    },
                    trailingIcon = {
                        Text(
                            text = "ml",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(QingJiGreenLight)
                        .pressable {
                            val ml = customMl.toIntOrNull()
                            if (ml != null && ml in 1..3000) {
                                pendingMl = ml
                                error = ""
                            } else {
                                error = "1 到 3000 之间都行"
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s("用这个数"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = QingJiGreenDark
                    )
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = error, fontSize = 11.sp, color = DangerRed)
            }

            Spacer(Modifier.height(12.dp))
            // QJ-HEALTH-DATE-WATER-004：日期和时间由只读文本改为可点区域，复用项目滚轮。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PageBackground)
                        .pressable {
                            draftY = pendingDate.year
                            draftM = pendingDate.monthValue
                            draftD = pendingDate.dayOfMonth
                            showDateWheel = true
                        }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📅", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${pendingDate.monthValue}月${pendingDate.dayOfMonth}日",
                        fontSize = 13.sp, color = QingJiGreenDark, maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PageBackground)
                        .pressable { showTimeWheel = true }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🕐", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(pendingTime),
                        fontSize = 13.sp, color = QingJiGreenDark, maxLines = 1
                    )
                }
            }

            } // end 可滚动内容区

            Spacer(Modifier.height(12.dp))
            // ---------- 取消 / 确认：唯一的写库时机（固定底部） ----------
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(QingJiGreenLight)
                        // 取消不写库，直接关
                        .pressable { onDismiss() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s("取消"), fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, color = QingJiGreenDark)
                }
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(QingJiGreen)
                        .pressable {
                            if (submitted) return@pressable
                            submitted = true
                            onAdd(pendingMl, pendingDate, pendingTime)
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s("确认记录"), fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            // ---------- 今日详情：只有首页有对应的详情弹层 ----------
            if (onOpenDetail != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MultiColor.WaterText, RoundedCornerShape(10.dp))
                        .pressable {
                            onDismiss()
                            onOpenDetail.invoke()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        // QJ-HEALTH-DATE-WATER-004：首页可能正在看历史日期，标题不再写「今日」
                        text = s("查看饮水详情"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MultiColor.WaterText
                    )
                }
            }
        }
    }

    // ---------- QJ-HEALTH-DATE-WATER-004：日期滚轮（禁未来 + 非法日期拦截） ----------
    if (showDateWheel) {
        val today = AppClock.today()
        // 拼日期可能非法（2月30日）；非法或未来/超一年都置灰确定，不写回表单
        val draftDate = runCatching { LocalDate.of(draftY, draftM, draftD) }.getOrNull()
        val draftValid = draftDate != null &&
            !draftDate.isAfter(today) && !draftDate.isBefore(today.minusDays(365))
        AppDialog(
            title = s("选择记录日期"),
            confirmText = s("确定"),
            confirmEnabled = draftValid,
            onConfirm = {
                if (draftDate != null && draftValid) {
                    pendingDate = draftDate
                    showDateWheel = false
                }
            },
            onDismiss = { showDateWheel = false }
        ) {
            Text(
                text = if (draftValid) s("可记录最近一年内的日期，未来日期不能记")
                       else s("日期无效或超出可记录范围"),
                fontSize = 11.sp, color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            WheelDatePicker(
                initialDate = pendingDate,
                yearRange = IntRange(today.year - 1, today.year)
            ) { y, m, d -> draftY = y; draftM = m; draftD = d }
        }
    }

    // ---------- QJ-HEALTH-DATE-WATER-004：时间滚轮（只改具体时刻，日期不变） ----------
    if (showTimeWheel) {
        AppDialog(
            title = s("选择记录时间"),
            confirmText = s("确定"),
            onConfirm = { showTimeWheel = false },
            onDismiss = { showTimeWheel = false }
        ) {
            WheelTimePicker(
                initialHour = pendingTime.hour,
                initialMinute = pendingTime.minute
            ) { h, m -> pendingTime = java.time.LocalTime.of(h, m) }
        }
    }
}


/**
 * 一周日期条：参考吃有数的样式 —— 没选中的是虚线圆圈，选中那天
 * 变成主题浅色的圆角方块，下面带一颗主题色小点。
 * 颜色全部取主题色，切换主题时一起变。
 */
@Composable
internal fun DateStrip(
    state: HomeUiState,
    onSelect: (LocalDate) -> Unit
) {
    val weekLabels = listOf(s("周一"), s("周二"), s("周三"), s("周四"), s("周五"), s("周六"), s("周日"))
    val monday = state.date.minusDays((state.date.dayOfWeek.value - 1).toLong())
    val today = AppClock.today()

    // drawBehind 的 lambda 不是 @Composable，颜色要在这里先取出来。
    // 虚线圈不能用 DividerColor：它太淡，压在顶部浅色渐变上等于没画；
    // 选中块也不能用 QingJiGreenLight：和渐变背景同色，看不出方块。
    val dashColor = QingJiGreen.copy(alpha = 0.42f)
    val selectedBg = QingJiGreen.copy(alpha = 0.16f)
    val selectedText = QingJiGreenDark
    val dotColor = QingJiGreen

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekLabels.forEachIndexed { index, label ->
            val date = monday.plusDays(index.toLong())
            val selected = date == state.date
            val isFuture = date.isAfter(today)

            Column(
                modifier = Modifier
                    // 固定宽度：个位数（8）和两位数（18）原本宽度不同，
                    // 翻周时会左右抽动，固定住就不抖了
                    .width(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isFuture) { onSelect(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isFuture -> DividerColor
                        selected -> selectedText
                        else -> TextSecondary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) selectedBg else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.drawBehind {
                                    drawCircle(
                                        color = dashColor,
                                        radius = (size.minDimension - 2.dp.toPx()) / 2,
                                        style = Stroke(
                                            width = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(6f, 5f)
                                            )
                                        )
                                    )
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isFuture -> DividerColor
                            selected -> selectedText
                            else -> TextPrimary
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) dotColor else Color.Transparent)
                )
            }
        }
    }
}
