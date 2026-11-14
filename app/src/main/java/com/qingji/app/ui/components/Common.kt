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
