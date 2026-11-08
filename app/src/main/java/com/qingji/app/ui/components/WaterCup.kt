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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * ============================================================
 * 饮水计量杯
 * ------------------------------------------------------------
 * 为什么不用进度条：进度条只能看出「一半」，看不出「我今天喝了多少」。
 * 一个杯子从空到满，是最符合直觉的表达——这也是用户要的。
 *
 * 设计要点：
 * 1. 杯身是上宽下窄的梯形，比圆角矩形更像杯子；
 * 2. 水位随饮水量动画上涨，喝满就整杯灌满；
 * 3. 水里的气泡数量跟水量成正比，喝得越多气泡越多；
 *    气泡还会缓慢往上浮，看起来像刚倒进去的水。
 * 4. 气泡数量可以在设置里调（0 = 关掉，省电）。
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

    Canvas(
        modifier = modifier.clickable { onClick() }
    ) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // 杯口留一点窄边，杯底收窄，形成梯形
        val mouthInset = 3.dp.toPx()
        val bottomInset = 9.dp.toPx()
        val top = stroke
        val bottom = h - stroke

        val cupPath = Path().apply {
            moveTo(mouthInset, top)
            lineTo(w - mouthInset, top)
            lineTo(w - bottomInset, bottom)
            lineTo(bottomInset, bottom)
            close()
        }

        // 水面高度
        val waterTop = bottom - (bottom - top) * level

        clipPath(cupPath) {
            if (level > 0f) {
                // 水体
                drawRect(
                    color = Color(0xFF7EC8E3),
                    topLeft = Offset(0f, waterTop),
                    size = Size(w, bottom - waterTop)
                )
                // 液面稍微亮一点，看得出「水面」
                drawRect(
                    color = Color(0xFFAEDCEC),
                    topLeft = Offset(0f, waterTop),
                    size = Size(w, 2.dp.toPx())
                )
                // 气泡：位置固定（用固定种子），只让 y 随时间上浮
                val random = Random(seed = 20261108)
                val count = (maxBubbles * level).toInt()
                repeat(count) { index ->
                    val bx = mouthInset + (w - mouthInset - bottomInset) * (0.15f + 0.7f * random.nextFloat())
                    val baseY = waterTop + (bottom - waterTop) * (0.1f + 0.8f * random.nextFloat())
                    val radius = (1.4f + random.nextFloat() * 1.6f).dp.toPx()
                    // 每个气泡的相位错开，避免整齐划一地一起动
                    val offsetPhase = (phase + index * 0.17f) % 1f
                    val floatY = baseY - offsetPhase * 7.dp.toPx()
                    if (floatY > waterTop + radius) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.6f),
                            radius = radius,
                            center = Offset(bx, floatY)
                        )
                    }
                }
            }
        }

        // 杯壁（画在水上面，边框才不会被水盖住）
        drawPath(
            path = cupPath,
            color = Color(0xFF9FC9DA),
            style = Stroke(width = stroke)
        )
    }
}
