package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark

/**
 * ============================================================
 * 右侧三个快捷圆按钮：喝水 / 记体重 / 拍照
 * ------------------------------------------------------------
 * 首页、进展、日记三个页面共用同一套，长得一样、行为一样。
 * v3.2.1 起换成矢量图形图标（以前是「水 / 秤 / 拍」三个字），
 * 三颗颜色统一取主题色的深浅档，切主题时跟着变。
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
        QuickActionButton(icon = Icons.Outlined.WaterDrop, color = QingJiGreen, onClick = onWater)
        QuickActionButton(icon = Icons.Outlined.MonitorWeight, color = QingJiGreenDark, onClick = onWeight)
        QuickActionButton(
            icon = Icons.Outlined.PhotoCamera,
            color = QingJiGreen.copy(alpha = 0.72f),
            onClick = onPhoto
        )
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}
