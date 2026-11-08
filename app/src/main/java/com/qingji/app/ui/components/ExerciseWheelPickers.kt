package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import java.time.LocalDateTime

/**
 * ============================================================
 * 运动模块的两个滚轮弹层（v3.3.0）
 * ------------------------------------------------------------
 * 复用了 WheelTimePicker 里「中间高亮 + 上下渐隐 + 松手吸附」的那一套，
 * 但那边只有时/分两列、分钟还按 5 分钟跳；这里要：
 *   · 选运动时长（1~180 分钟，步进 1）—— 参考图 6
 *   · 选记录发生的日期时间（日期 / 时 / 分三列）—— 参考图 5
 * 所以抽出一个能传任意文本列表的公共单列滚轮 NumberWheelColumn。
 * 弹层容器、遮罩、把手都跟全 App 的 AppBottomSheet 保持同一套配色。
 * ============================================================
 */

/**
 * 单列滚轮：labels 是要显示的文本，onSelected 回传当前停在中间那一项的下标。
 *
 * 前后各补 2 个空格子，是为了让第 0 项和最后一项也能滚到正中间。
 * 早期那版（WheelTimePicker 的私有 WheelColumn，v3.3.1 已删除）用
 * 「firstVisibleItemIndex + 2」当中项，首尾会偏 2 格：选 30 分钟会跳成 32，
 * 日期默认会落到「前天」。这里改成补位写法，中心项就是 firstVisibleItemIndex。
 * v3.3.1 起设置里的提醒时间（WheelTimePicker）也改用本组件，
 * 全 App 只剩这一套滚轮逻辑，不再有两份实现各自维护。
 */
@Composable
fun NumberWheelColumn(
    labels: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    val itemHeight = 40.dp
    val pad = 2
    val lastIndex = (labels.size - 1).coerceAtLeast(0)
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, lastIndex)
    )

    val centerIndex by remember {
        derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, lastIndex) }
    }

    // 松手后吸附到最近的一档
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val target = state.firstVisibleItemIndex.coerceIn(0, lastIndex)
            state.animateScrollToItem(target)
            onSelected(target)
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * (pad * 2 + 1))
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(PageBackground)
        )
        LazyColumn(
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(count = pad) {
                Spacer(modifier = Modifier.fillMaxWidth().height(itemHeight))
            }
            items(count = labels.size) { index ->
                val isCenter = index == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[index],
                        fontSize = if (isCenter) 17.sp else 14.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) QingJiGreenDark else TextSecondary.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            items(count = pad) {
                Spacer(modifier = Modifier.fillMaxWidth().height(itemHeight))
            }
        }
    }
}

/** 统一的弹层把手，跟 AppDialogs 里的样式一致。 */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(DividerColor)
    )
}

/**
 * 图 6：点「自定义」后弹出的数字滚轮，顶部「取消 / 确定」。
 * 四种记录方式共用：时长传 1~180、千卡 10~2000、步数 100~30000（步进 100）、公里 1~100。
 * values 由调用方给，组件只负责显示和回传，不掺和业务口径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseNumberWheelSheet(
    values: List<Int>,
    initialValue: Int,
    unitLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val safeInitial = values.indexOf(initialValue).coerceAtLeast(0)
    var pickedIndex by remember { mutableStateOf(safeInitial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .pressable { onDismiss() }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "确定",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .pressable {
                            onConfirm(values[pickedIndex.coerceIn(0, values.size - 1)])
                            onDismiss()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            NumberWheelColumn(
                labels = values.map { "$it $unitLabel" },
                initialIndex = safeInitial,
                modifier = Modifier.fillMaxWidth()
            ) { idx -> pickedIndex = idx.coerceIn(0, values.size - 1) }
        }
    }
}

/**
 * 图 5：底部日期时间入口，三列滚轮（日期 / 时 / 分），底部大「确定」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDateTimeWheelSheet(
    initial: LocalDateTime,
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val today = AppClock.today()
    val days = (0..6).map { today.minusDays(it.toLong()) }
    val weekdayCn = listOf("一", "二", "三", "四", "五", "六", "日")
    val dayLabels = days.mapIndexed { i, d ->
        when (i) {
            0 -> "今天"
            1 -> "昨天"
            else -> "${d.monthValue}月${d.dayOfMonth}日周${weekdayCn[d.dayOfWeek.value - 1]}"
        }
    }
    val initialDay = days.indexOf(initial.toLocalDate()).coerceAtLeast(0)
    var dayIndex by remember { mutableStateOf(initialDay) }
    var hour by remember { mutableStateOf(initial.hour.coerceIn(0, 23)) }
    var minute by remember { mutableStateOf(initial.minute.coerceIn(0, 59)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择运动时间",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "取消",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .pressable { onDismiss() }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberWheelColumn(
                    labels = dayLabels,
                    initialIndex = initialDay,
                    modifier = Modifier.weight(1.6f)
                ) { dayIndex = it }
                NumberWheelColumn(
                    labels = (0..23).map { "%02d".format(it) },
                    initialIndex = hour,
                    modifier = Modifier.weight(0.8f)
                ) { hour = it }
                NumberWheelColumn(
                    labels = (0..59).map { "%02d".format(it) },
                    initialIndex = minute,
                    modifier = Modifier.weight(0.8f)
                ) { minute = it }
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(QingJiGreen)
                    .pressable {
                        val pickedDay = days[dayIndex.coerceIn(0, days.size - 1)]
                        onConfirm(pickedDay.atTime(hour, minute))
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确定",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
