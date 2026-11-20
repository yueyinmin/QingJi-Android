package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.TextSecondary
import androidx.compose.foundation.layout.size

/**
 * 全 App 复用的两个小零件：细进度条 + 白色卡片。
 * 单独抽出来是为了以后要改样式，只改这一个文件。
 */

/**
 * 细进度条。
 * 故意没用系统自带的 LinearProgressIndicator，因为它的高度不好控制，
 * 而且不同版本 API 写法会变，自己画最稳。
 *
 * @param progress 0 到 1 之间的比例，超出范围会自动夹紧
 */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = QingJiGreen,
    trackColor: Color = DividerColor,
    barHeight: Dp = 6.dp
) {
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(trackColor)
    ) {
        // 内容为 0 时不画填充部分，避免出现一个小圆点
        if (safe > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safe)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(color)
            )
        }
    }
}

/** 统一的白卡片：16dp 圆角、无阴影、16dp 内边距。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        // 全 App 统一卡片圆角（图标规范 18~22dp，取 20）
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * 一行等分的单选块（性别、目标模式这类）。
 * 每项是「显示文字 to 取值」，选中项绿底白字。
 *
 * 2026-11-15：原先「我的」页的 OptionRow 与「资料」页的 ChoiceRow 是同一份实现
 * 复制成两份，只有间距（6/8）、圆角（8/10）、内边距（9/10）差 1~2dp，
 * 而且 pair 的语义还写反了。这里合并成一个带参数的实现，语义统一成 label to value。
 */
@Composable
fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    corner: Dp = 10.dp,
    verticalPadding: Dp = 10.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(corner))
                    .background(if (isSelected) QingJiGreen else PageBackground)
                    .clickable { onSelect(value) }
                    .padding(vertical = verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

/**
 * 图表/日历的小图例：一个圆点 + 一行说明。
 *
 * 2026-11-15：进度页和自律日历原先各有一份，圆点 8dp vs 9dp、
 * 圆角一半 vs 999dp、外层留白 0 vs 6dp，本质是同一个东西。
 * 合并后统一用「圆点直径的一半」做圆角，外观与原来一致。
 */
@Composable
fun LegendDot(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    horizontalPadding: Dp = 0.dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = horizontalPadding)
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(RoundedCornerShape(dotSize / 2))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
