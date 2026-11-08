package com.qingji.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import kotlin.random.Random

/**
 * ============================================================
 * 饮水计量杯（v3.2 改成直筒玻璃杯）
 * ------------------------------------------------------------
 * 首页右侧那根高杯子：椭圆杯口 + 直筒杯壁 + 椭圆杯底，
 * 水位随饮水量动画上涨，杯壁有几道刻度线。
 *
 * 颜色全部取当前主题色：以前写死的浅蓝在切到暖橙/樱粉主题时
 * 会显得像另一个 App 的控件，现在跟着主题一起变。
 * 注意颜色一定要在 Composable 体内读出来再传进 Canvas 的绘制 lambda，
 * 绘制 lambda 不是 @Composable，在里面读 State 不会触发重组。
 * ============================================================
 */
@Composable
fun WaterCup(
    /** 当前完成度 0～1 */
    ratio: Float,
    /** 气泡量：0 关 / 1 少 / 2 标准 / 3 多 */
    bubbleLevel: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val safe = ratio.coerceIn(0f, 1f)

    // 在 Composable 作用域里取色，主题色一变这里会重组、杯子跟着换色
    val outline = QingJiGreen.copy(alpha = 0.42f)
    val waterFill = QingJiGreenLight
    val surface = QingJiGreen.copy(alpha = 0.5f)
    val bubbleColor = QingJiGreen.copy(alpha = 0.28f)
    val tickColor = QingJiGreen.copy(alpha = 0.22f)

    // 水位上涨动画：刚记完一杯时能看见水面往上走
    val level by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 650),
        label = "waterLevel"
    )

    // 气泡上浮用的循环相位
    val transition = rememberInfiniteTransition(label = "bubbles")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubblePhase"
    )

    val maxBubbles = when (bubbleLevel) {
        0 -> 0
        1 -> 4
        2 -> 9
        3 -> 16
        else -> 9
    }

    Canvas(modifier = modifier.clickable { onClick() }) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val mouth = (w * 0.22f).coerceAtMost(11.dp.toPx())   // 杯口椭圆的高度
        val top = mouth / 2 + stroke
        val bottom = h - mouth / 2 - stroke
        val waterTop = bottom - (bottom - top) * level

        // ---- 杯里的水：先画水，再画杯壁，边框才不会被盖住 ----
        if (level > 0f) {
            drawRect(
                color = waterFill,
                topLeft = Offset(stroke, waterTop),
                size = Size(w - stroke * 2, bottom - waterTop)
            )
            // 水面椭圆，像真的有一层水面
            drawOval(
                color = surface,
                topLeft = Offset(stroke, waterTop - mouth / 2),
                size = Size(w - stroke * 2, mouth),
                style = Stroke(width = stroke)
            )
            // 气泡：固定种子保证位置不乱跳，只让 y 随时间上浮
            val random = Random(seed = 20261108)
            val count = (maxBubbles * level).toInt()
            repeat(count) { index ->
                val bx = w * (0.18f + 0.64f * random.nextFloat())
                val baseY = waterTop + (bottom - waterTop) * (0.1f + 0.8f * random.nextFloat())
                val radius = (1.4f + random.nextFloat() * 1.6f).dp.toPx()
                val offsetPhase = (phase + index * 0.17f) % 1f
                val floatY = baseY - offsetPhase * 7.dp.toPx()
                if (floatY > waterTop + radius) {
                    drawCircle(
                        color = bubbleColor,
                        radius = radius,
                        center = Offset(bx, floatY)
                    )
                }
            }
        }

        // ---- 杯壁：杯口整椭圆 + 两侧竖线 + 杯底下半椭圆 ----
        drawOval(
            color = outline,
            topLeft = Offset(stroke, stroke),
            size = Size(w - stroke * 2, mouth),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = outline,
            start = Offset(stroke, top),
            end = Offset(stroke, bottom),
            strokeWidth = stroke
        )
        drawLine(
            color = outline,
            start = Offset(w - stroke, top),
            end = Offset(w - stroke, bottom),
            strokeWidth = stroke
        )
        drawArc(
            color = outline,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(stroke, bottom - mouth / 2),
            size = Size(w - stroke * 2, mouth),
            style = Stroke(width = stroke)
        )

        // ---- 杯壁刻度：四道短横，一眼能看出喝到第几格 ----
        (1..4).forEach { i ->
            val y = top + (bottom - top) * i / 5f
            drawLine(
                color = tickColor,
                start = Offset(w - stroke - 12.dp.toPx(), y),
                end = Offset(w - stroke - 3.dp.toPx(), y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
            )
        }
    }
}
