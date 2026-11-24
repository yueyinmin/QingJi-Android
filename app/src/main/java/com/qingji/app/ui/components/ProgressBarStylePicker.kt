package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * QJ-PROGRESS-V2：七种进度条特效选择器（设置页 CAT_MOTION 内）。
 * 预览与真实组件共用 [StyledProgressBar] 绘制实现，不会两套效果逐渐偏离。
 * 只有当前选中项播放动画；其余静态展示，页面离开即随 Compose 生命周期停止。
 */
@Composable
fun ProgressBarStylePicker(
    current: ProgressBarStyle,
    onSelect: (ProgressBarStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "进度条特效",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "仅影响首页热量进度条；与「进度条动效」补间开关互不覆盖",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        ProgressBarStyle.entries.forEach { style ->
            val selected = style == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) QingJiGreenLight else Color.Transparent)
                    .clickable { onSelect(style) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = style.label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) QingJiGreenDark else TextPrimary
                    )
                    Text(text = style.description, fontSize = 10.sp, color = TextSecondary)
                }
                Spacer(Modifier.weight(0.4f))
                StyledProgressBar(
                    progress = 0.72f,
                    modifier = Modifier
                        .height(10.dp)
                        .fillMaxWidth(0.42f),
                    color = QingJiGreen,
                    barHeight = 10.dp,
                    style = style,
                    // 只让当前可见且选中的预览播放，其余静态
                    animationEnabled = selected
                )
            }
        }
    }
}
