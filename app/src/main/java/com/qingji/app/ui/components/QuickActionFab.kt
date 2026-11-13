package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
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
import com.qingji.app.ui.theme.MultiColor

/**
 * ============================================================
 * 右侧四颗快捷圆按钮：喝水 / 记体重 / 食物库 / 拍照
 * ------------------------------------------------------------
 * 首页、进展、日记三个页面共用同一套，长得一样、行为一样。
 * v3.2.1 起换成矢量图形图标（以前是「水 / 秤 / 拍」三个字）。
 * v1.0.2：四颗齐全，顺序是 喝水 / 记体重 / 食物库 / 拍照。
 *
 * ------------------------------------------------------------
 * v1.0.5 图标对齐专项
 * ------------------------------------------------------------
 * 反馈是「图标看起来有点歪 / 大小不一」。查下来的结论：
 *
 * 1. 布局层面本来就是正的 —— 三个 Box 层层 Center，没有任何 rotate、
 *    offset、scale 或 graphicsLayer 变换。这里再收一次，把「绘制区域」
 *    统一成一个固定的 24dp 方框，四个图标都在框里居中，谁也别单独找位置。
 *    顺带把内层背景从写死的 size(52.dp) 改成 fillMaxSize()：以前内外
 *    两个 52dp 是各写一遍的，以后改按钮尺寸只改一处就够，不会漏。
 *
 * 2. 真正的毛病是「记体重」这颗用了水的蓝色（v1.0.2 加它的时候漏了配色，
 *    直接复制了上一颗的 QuickWater）。水滴和秤同色 → 一列看下来像同一个
 *    功能重复了两次，视觉上自然觉得「不对劲」。现在改成专门的 QuickWeight 绿。
 *
 * 3. 剩下的参差来自图标本身的形状（水滴上尖下圆、书本下沿是弧、相机带底座），
 *    这是 Material 图标固有的视觉重量差，不是布局能修的。**刻意没有**
 *    对单个图标做旋转或偏移去「凑」——那种补偿在没有真机截图对照的情况下，
 *    十有八九是把正的改成歪的。要动也应该先看着截图动。
 * ============================================================
 */

/** 按钮直径。三个页面共用，改这里一处就够（2026-11-13 从 44 放大到 50）。 */
private val BUTTON_SIZE = 50.dp

/** 图标绘制框。四个图标都塞进同样大小的框里居中，尺寸天然统一（20 → 24dp）。 */
private val ICON_SIZE = 24.dp

@Composable
fun QuickActionColumn(
    onWater: () -> Unit,
    onWeight: () -> Unit,
    onOpenFoodLibrary: () -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 360dp 小屏上 50dp 会占掉太多右侧空间，自动缩到 44dp
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380
    val buttonSize = if (compact) 44.dp else 50.dp
    val iconSize = if (compact) 21.dp else 24.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 按功能固定配色，不跟主题开关变 —— 靠颜色区分功能
        QuickActionButton(
            icon = Icons.Outlined.WaterDrop,
            color = MultiColor.QuickWater,
            buttonSize = buttonSize,
            iconSize = iconSize,
            onClick = onWater
        )
        QuickActionButton(
            icon = Icons.Outlined.MonitorWeight,
            color = MultiColor.QuickWeight,
            buttonSize = buttonSize,
            iconSize = iconSize,
            onClick = onWeight
        )
        QuickActionButton(
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            color = MultiColor.QuickBook,
            buttonSize = buttonSize,
            iconSize = iconSize,
            onClick = onOpenFoodLibrary
        )
        QuickActionButton(
            icon = Icons.Outlined.PhotoCamera,
            color = MultiColor.QuickCamera,
            buttonSize = buttonSize,
            iconSize = iconSize,
            onClick = onPhoto
        )
    }
}

/**
 * 一颗圆形快捷按钮。
 *
 * 三层结构，每层的尺寸来源都只有一处：
 *   ① 外层 52dp 白圆 —— 点击区域就是整颗，和以前完全一样
 *   ② 中层 fillMaxSize 的浅色底（16% 透明度）
 *   ③ 内层 24dp 绘制框 + Alignment.Center，图标填满这个框
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    color: Color,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(15.dp))
            .background(CardWhite)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 浅色底跟着外层走，不再重复写 52dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(15.dp))
                .background(color.copy(alpha = 0.16f))
        )
        // 固定绘制框：四个图标共用同一个尺寸和同一个对齐方式，
        // 剩下能造成"看起来歪"的只剩图标自己的形状，不会再有布局因素
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
