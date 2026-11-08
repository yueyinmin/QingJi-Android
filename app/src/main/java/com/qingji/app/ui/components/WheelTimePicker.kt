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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 轮盘时间选择器（v3.0）
 * ------------------------------------------------------------
 * 之前提醒时间是一排 0~23 的方块（24 个挤在一起还容易点错），
 * 现在改成上下滚动的轮盘：转起来像老式时钟的分针盘，
 * 松手自动吸附到最近的档位。
 *
 * 两列轮盘：小时（0~23）+ 分钟（0~55，步进 5 分钟）。
 * 在「通知与提醒」「记一笔」等需要选时间的地方统一复用。
 * ============================================================
 */
@Composable
fun WheelTimePicker(
    initialHour: Int,
    initialMinute: Int,
    modifier: Modifier = Modifier,
    onSelected: (hour: Int, minute: Int) -> Unit
) {
    var hour by remember { mutableStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember { mutableStateOf((initialMinute / 5).coerceIn(0, 11)) } // 分钟档位

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        WheelColumn(
            items = (0..23).map { "%02d".format(it) },
            initialIndex = hour,
            unit = "时",
            modifier = Modifier.width(110.dp)
        ) { idx -> hour = idx }

        Spacer(Modifier.width(12.dp))

        WheelColumn(
            items = (0..11).map { "%02d".format(it * 5) },
            initialIndex = minute,
            unit = "分",
            modifier = Modifier.width(110.dp)
        ) { idx -> minute = idx * 5 }

        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier.align(Alignment.CenterVertically),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = "时", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Text(text = "分", fontSize = 13.sp, color = TextSecondary)
        }
    }

    // 值变化即回传（父级实时拿最新选择）
    LaunchedEffect(hour, minute) {
        onSelected(hour, minute * 5)
    }
}

/**
 * 单列轮盘：中间高亮、上下渐隐、松手自动吸附。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    unit: String,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    val itemHeight = 40.dp
    val visible = 5
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - visible / 2).coerceAtLeast(0))

    // 中心项 = 第一个可见项 + 可见数的一半
    val centerIndex by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + visible / 2).mod(items.size)
        }
    }

    // 滚动停止时自动吸附到中心
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val target = (state.firstVisibleItemIndex + visible / 2).coerceIn(0, items.size - 1)
            state.animateScrollToItem(target - visible / 2)
            onSelected(target.mod(items.size))
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visible)
            .clip(RoundedCornerShape(12.dp))
            .background(PageBackground)
    ) {
        // 中央选中条
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(QingJiGreen.copy(alpha = 0.14f))
        )
        LazyColumn(state = state, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            items(items.size) { index ->
                val isCenter = index == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isCenter) 17.sp else 14.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) QingJiGreenDark else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
