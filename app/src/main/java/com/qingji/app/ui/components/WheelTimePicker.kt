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
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * ============================================================
 * 三列日期滚轮（年 / 月 / 日）—— 睡眠表单点日期弹出（2026-11-18）
 * ------------------------------------------------------------
 * · 第一列年（默认近 6 年）、第二列月（1~12）、第三列日（1~31），三列独立滚。
 * · 复用 NumberWheelColumn 那套「中间高亮 + 松手吸附」，和时/分滚轮同一手感。
 * · 日轮固定 1~31：三列是独立滚轮，月日组合可能拼出当月不存在的日期
 *   （2 月 30 日）或超出调用方允许范围（比如只能记最近 30 天），
 *   所以这里只回传年/月/日三个数，合法性由调用方校验 ——
 *   跟 WheelTimePicker「只管滚、不管业务口径」的分工一致。
 * · 每次滚动停下即回传（和 WheelTimePicker 相同），调用方对非法值不落库即可。
 * ============================================================
 */
@Composable
fun WheelDatePicker(
    initialDate: LocalDate,
    modifier: Modifier = Modifier,
    /** 年轮的范围，默认最近 6 年（含今年）：记历史睡眠够用，滚起来也不费劲 */
    yearRange: IntRange = IntRange(
        AppClock.today().year - 5,
        AppClock.today().year
    ),
    onSelected: (year: Int, month: Int, day: Int) -> Unit
) {
    var year by remember { mutableStateOf(initialDate.year.coerceIn(yearRange.first, yearRange.last)) }
    var month by remember { mutableStateOf(initialDate.monthValue) }
    var day by remember { mutableStateOf(initialDate.dayOfMonth) }
    // 跳过首次组合的那次回传（同 WheelTimePicker）
    var ready by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DateWheelColumn(
            title = "年",
            labels = yearRange.map { "${it}年" },
            initialIndex = year - yearRange.first,
            modifier = Modifier.weight(1f)
        ) { year = yearRange.first + it }
        DateWheelColumn(
            title = "月",
            labels = (1..12).map { "${it}月" },
            initialIndex = month - 1,
            modifier = Modifier.weight(1f)
        ) { month = it + 1 }
        DateWheelColumn(
            title = "日",
            labels = (1..31).map { "${it}日" },
            initialIndex = day - 1,
            modifier = Modifier.weight(1f)
        ) { day = it + 1 }
    }

    LaunchedEffect(year, month, day) {
        if (!ready) {
            ready = true
            return@LaunchedEffect
        }
        onSelected(year, month, day)
    }
}

/** 一列滚轮 + 顶部的「年 / 月 / 日」标签，避免三列数字看不出哪个是哪个单位。 */
@Composable
private fun DateWheelColumn(
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
