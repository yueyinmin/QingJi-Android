package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * 快速记录喝水（v3.2）：点首页那根高水杯弹出来。
 * 四档常用水量 + 自定义输入 + 记录时间 + 查看今日详情，
 * 容器色/遮罩/把手和 AppBottomSheet 完全一致，不写死任何蓝色。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun QuickWaterSheet(
    waterMl: Int,
    goalMl: Int,
    onAdd: (Int) -> Unit,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val timeText = "${AppClock.today().monthValue}月${AppClock.today().dayOfMonth}日 " +
        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💧", fontSize = 18.sp)
                Spacer(Modifier.size(6.dp))
                Text(
                    text = s("快速记录喝水"),
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

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150, 250, 350, 500).forEach { ml ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .pressable { onAdd(ml); error = "" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${ml}ml",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = QingJiGreenDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = customMl,
                    onValueChange = { customMl = it; error = "" },
                    singleLine = true,
                    placeholder = {
                        Text(text = s("输入自定义水量"), fontSize = 12.sp, color = TextSecondary)
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
                        .background(QingJiGreen)
                        .pressable {
                            val ml = customMl.toIntOrNull()
                            if (ml != null && ml in 1..3000) {
                                onAdd(ml)
                                customMl = ""
                                error = ""
                            } else {
                                error = "1 到 3000 之间都行"
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s("记录"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
            if (error.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text = error, fontSize = 11.sp, color = DangerRed)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PageBackground)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🕐", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(text = timeText, fontSize = 13.sp, color = QingJiGreenDark)
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MultiColor.WaterText, RoundedCornerShape(10.dp))
                    .pressable { onOpenDetail() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s("查看今日饮水详情"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MultiColor.WaterText
                )
            }
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
