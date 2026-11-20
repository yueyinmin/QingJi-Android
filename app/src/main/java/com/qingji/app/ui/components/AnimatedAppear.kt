package com.qingji.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * ============================================================
 * 入场动画（v2.3）
 * ------------------------------------------------------------
 * 列表和卡片不要「啪」地一下全出来。让它们错开 45ms 依次浮上来，
 * 眼睛就有节奏可循，比整体一闪要柔和得多。
 *
 * tabfix：改成「内容始终占位、只淡入透明度」。
 * 旧实现用 AnimatedVisibility，visible=false 时卡片根本不参与测量，
 * 随着 index 的 delay 一张张插进 Column，整页高度一帧一变；
 * 再叠上每张卡片 1/8 自身高度的 slideInVertically 和 NavHost 的页面位移，
 * 就是切 Tab 时看到的「卡片往上跳、页面跟着抖」。
 * 现在淡入只改 alpha，布局尺寸从头到尾恒定，滚动条也不会跳。
 *
 * 用法：
 * ```
 * items.forEachIndexed { index, item ->
 *     AppearItem(index = index) { MyCard(item) }
 * }
 * ```
 * 错开只算到第 5 项（见 Motion.MaxStaggerIndex），总计不超过 80ms，
 * 加上 150ms 淡入，和页面 150ms 转场基本同期结束，不拖尾。
 * ============================================================
 */
@Composable
fun AppearItem(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val wait = index.coerceAtMost(Motion.MaxStaggerIndex) * Motion.StaggerStep
        if (wait > 0) delay(wait)
        visible = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.Fast, easing = Motion.decelerate),
        label = "appearAlpha"
    )

    // graphicsLayer 只影响绘制，不影响测量：content 永远在布局里。
    Box(modifier = modifier.graphicsLayer { alpha = contentAlpha }) {
        content()
    }
}

/**
 * 数字变化时「滚一下」。
 *
 * 直接换数字（2313 → 1890）会显得生硬，
 * 让旧数字往上滑走、新数字从下滑进来，能明确传达「这个值变了」。
 */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = Motion.Normal,
                            easing = Motion.decelerate
                        )
                    ) { height -> height } + fadeIn(
                        animationSpec = tween(durationMillis = Motion.Fast)
                    )
                    ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = Motion.Normal,
                            easing = Motion.accelerate
                        )
                    ) { height -> -height } + fadeOut(
                        animationSpec = tween(durationMillis = Motion.Fast)
                    )
                    )
            },
            label = "number"
        ) { target ->
            Text(text = "$target", style = style, color = color)
        }
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                fontSize = 13.sp,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp),
                fontWeight = FontWeight.Normal
            )
        }
    }
}
