package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.MonitorWeight
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
import com.qingji.app.ui.theme.MultiColor

/**
 * ============================================================
 * 右侧三个快捷圆按钮：喝水 / 食物库 / 拍照
 * ------------------------------------------------------------
 * 首页、进展、日记三个页面共用同一套，长得一样、行为一样。
 * v3.2.1 起换成矢量图形图标（以前是「水 / 秤 / 拍」三个字）。
 * v3.2.2 起三颗按参考图恢复固定多彩（蓝 / 紫 / 绿），
 * 由 MultiColor 集中管理，不跟主题色开关变 —— 靠颜色区分功能。
 *
 * v3.2.4：第二颗「记体重」和首页「今日记录」右侧的「食物库」互换位置，
 * 所以这里统一改成书图标 + 打开食物库；记体重挪到首页标题行右侧。
 * 三个页面一起换，避免同一个悬浮栏在不同页面长得不一样。
 * v1.0.2：「记体重」回到悬浮栏第二颗（主人要求），现在四颗：
 * 喝水（蓝）/ 记体重（绿）/ 食物库（紫）/ 拍照（青），首页、进展、日记共用。
 * ============================================================
 */
@Composable
fun QuickActionColumn(
    onWater: () -> Unit,
    onWeight: () -> Unit,
    onOpenFoodLibrary: () -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 参考图里三颗是蓝 / 紫 / 绿，按功能固定，不跟主题变
        QuickActionButton(
            icon = Icons.Outlined.WaterDrop,
            color = MultiColor.QuickWater,
            onClick = onWater
        )
        QuickActionButton(
            icon = Icons.Outlined.MonitorWeight,
            color = MultiColor.QuickWater,
            onClick = onWeight
        )
        QuickActionButton(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            color = MultiColor.QuickBook,
            onClick = onOpenFoodLibrary
        )
        QuickActionButton(
            icon = Icons.Outlined.PhotoCamera,
            color = MultiColor.QuickCamera,
            onClick = onPhoto
        )
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
