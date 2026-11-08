package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark

/**
 * ============================================================
 * 右侧三个快捷圆按钮：喝水 / 记体重 / 拍照
 * ------------------------------------------------------------
 * 原来只写在首页里，删了首页就没了。抽出来之后
 * 首页、进展、日记三个页面共用同一套，长得一样、行为一样。
 *
 * 用文字（水 / 秤 / 拍）而不是图标：
 * 中文字一眼就懂，也不用为三个小图标再引一套资源。
 * ============================================================
 */
@Composable
fun QuickActionColumn(
    onWater: () -> Unit,
    onWeight: () -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 三个按钮统一取主题色的三档深浅，不再各自写死蓝/绿色
        QuickActionButton(text = "水", color = QingJiGreen, onClick = onWater)
        QuickActionButton(text = "秤", color = QingJiGreenDark, onClick = onWeight)
        QuickActionButton(text = "拍", color = QingJiGreen.copy(alpha = 0.72f), onClick = onPhoto)
    }
}

@Composable
private fun QuickActionButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
