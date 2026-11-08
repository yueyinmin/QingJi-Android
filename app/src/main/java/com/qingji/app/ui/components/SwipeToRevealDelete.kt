package com.qingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ============================================================
 * 左滑删除（v2.9）
 * ------------------------------------------------------------
 * 之前每条记录右侧都挂一个「×」小按钮：既占地方又容易误触。
 * 现在改成左滑露出删除按钮：
 *
 * - 拖动时带**阻尼**（手指每移动 1px，内容只跟 0.55px——有"拖着涩"的手感）；
 * - 松手后用弹簧回弹或吸附到全开位（阻尼弹簧，会轻轻弹一下）；
 * - 露出后点红色区域执行删除；
 * - 向右滑回或点击内容区域都会收回。
 *
 * 用法：把原来「Row(内容) + 删除按钮」直接套进来：
 * ```
 * SwipeToRevealDelete(onDelete = { vm.delete(id) }) {
 *     MyRowContent()
 * }
 * ```
 * ============================================================
 */
@Composable
fun SwipeToRevealDelete(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    /** 删除按钮的圆角跟随外层卡片风格 */
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val maxSwipePx = with(density) { 92.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var revealed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        // ---------- 背景：右侧红色删除区（被内容盖住，左滑露出） ----------
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (revealed) Color(0xFFD64545) else Color(0xFFD64545).copy(alpha = 0.85f),
                    RoundedCornerShape(cornerRadius)
                )
                .clickable(enabled = revealed) { onDelete() },
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "删除",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(end = 26.dp)
            )
        }

        // ---------- 前景：内容本体，横向拖动 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                // 阻尼：拖动只跟 0.55 倍，越滑越"涩"
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount * 0.55f)
                                        .coerceIn(-maxSwipePx, 0f)
                                )
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                // 超过 45% → 吸附全开；否则弹回
                                val target =
                                    if (abs(offsetX.value) > maxSwipePx * 0.45f) -maxSwipePx else 0f
                                offsetX.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = 0.75f,          // 阻尼感：到位后轻弹一下
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                revealed = target != 0f
                                if (target == 0f) offsetX.snapTo(0f)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
