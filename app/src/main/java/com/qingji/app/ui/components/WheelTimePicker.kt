package com.qingji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 两列时间滚轮（v3.3.1 起复用运动模块那套滚轮）
 * ------------------------------------------------------------
 * · 两列：小时 00~23、分钟 00~59（步进 1，跟运动模块「选择运动时间」一致）。
 * · 直接复用 ExerciseWheelPickers.kt 的 NumberWheelColumn：
 *   前后各补 2 个空格子、中心项 = firstVisibleItemIndex、松手吸附。
 *   旧版私有 WheelColumn 用「firstVisibleItemIndex + visible/2」当中项且首尾不补位，
 *   第 0 项会偏 2 格（选 0 点变 2 点），这里不再有。
 * · 设置页「通知与提醒」的称重 / 断食 / 热量三处共用。
 * · onSelected 回传的是真实小时与真实分钟（旧版内部按 5 分钟档位换算过）。
 * · 首次进页面不回传：避免刚打开设置就写一遍偏好、重排一次闹钟；
 *   只有用户真正滚动松手后才回调。
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
    var minute by remember { mutableStateOf(initialMinute.coerceIn(0, 59)) }
    // 跳过首次组合的那次回传
    var ready by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimeWheelColumn(
            title = "时",
            labels = (0..23).map { "%02d".format(it) },
            initialIndex = hour,
            modifier = Modifier.weight(1f)
        ) { hour = it }
        TimeWheelColumn(
            title = "分",
            labels = (0..59).map { "%02d".format(it) },
            initialIndex = minute,
            modifier = Modifier.weight(1f)
        ) { minute = it }
    }

    LaunchedEffect(hour, minute) {
        if (!ready) {
            ready = true
            return@LaunchedEffect
        }
        onSelected(hour, minute)
    }
}

/** 一列滚轮 + 顶部的「时 / 分」标签，避免两列数字看不出哪列是哪个单位。 */
@Composable
private fun TimeWheelColumn(
    title: String,
    labels: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        NumberWheelColumn(
            labels = labels,
            initialIndex = initialIndex,
            modifier = Modifier.fillMaxWidth(),
            onSelected = onSelected
        )
    }
}
