package com.qingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * ============================================================
 * 饮水计量杯（v3.5.2 重构水杯动画，参考 ChatGPT 版交互演示的显示效果）
 * ------------------------------------------------------------
 * 首页右侧那根高杯子：椭圆杯口 + 直筒杯壁 + 椭圆杯底 + 四道刻度。
 *
 * v3.5.2 只改「显示与动画」，参数签名不变，调用方零改动：
 * 1. 真正的波浪水面：水面顶部边缘是正弦波，随相位持续横向流动（原来只是一条静态椭圆线）；
 * 2. 水位过渡对齐参考实现的 1200ms（原 650ms 直线）；
 * 3. 每次饮水量变化（加水 / 撤销）时水面产生一次短暂波动后衰减——
 *    用一个 1→0 的脉冲 Animatable 放大波浪振幅实现，不需要调用方传任何「刚加了水」的信号；
 * 4. 气泡保留：固定种子保证位置不乱跳，只随时间上浮；
 * 5. 杯口椭圆保留（那是杯子的透视轮廓，不是水面）。
 *
 * v3.2.2 起颜色可外部传入：首页传 MultiColor 那套固定水蓝，
 * 让「喝水的东西永远是蓝的」；不传时仍跟随主题色（其它页面用）。
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
    onClick: () -> Unit = {},
    /** 传 null = 跟主题色；传固定色 = 用多彩语义色（首页传一套水蓝） */
    fillColor: Color? = null,
    surfaceColor: Color? = null,
    outlineColor: Color? = null,
    bubbleTint: Color? = null,
    tickTint: Color? = null
) {
    val safe = ratio.coerceIn(0f, 1f)

    // 在 Composable 作用域里取色（含默认值），主题色一变这里会重组
    val outline = outlineColor ?: QingJiGreen.copy(alpha = 0.42f)
    val waterFill = fillColor ?: QingJiGreenLight
    val surface = surfaceColor ?: QingJiGreen.copy(alpha = 0.5f)
    val bubbleColor = bubbleTint ?: QingJiGreen.copy(alpha = 0.28f)
    val tickColor = tickTint ?: QingJiGreen.copy(alpha = 0.22f)

    // 水位上涨动画：对齐参考实现的 1200ms，用标准减速曲线，比直线过渡自然
    val level by animateFloatAsState(
        targetValue = safe,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "waterLevel"
    )

    // 加水波动反馈：目标一变（排除首次组合）就把脉冲打到 1，再 1.1 秒衰减到 0。
    // 波浪振幅 = 基础振幅 × (1 + 2.2 × 脉冲)，所以刚加水时浪头明显，随后慢慢平息。
    val pulse = remember { Animatable(0f) }
    var lastTarget by remember { mutableStateOf(safe) }
    LaunchedEffect(safe) {
        if (safe != lastTarget) {
            lastTarget = safe
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
        }
    }

    // 波浪 / 气泡共用的循环相位（0→1 循环流动）
    val transition = rememberInfiniteTransition(label = "water")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waterPhase"
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
        if (level > 0.02f) {
            // 波浪参数：基础振幅很小（安静的水面），加水脉冲时放大两倍多
            val baseAmp = 2.5.dp.toPx()
            val amp = baseAmp * (1f + 2.2f * pulse.value)
            val waveLen = w * 0.8f
            val shift = phase * waveLen          // 相位驱动整条波向一侧流动
            val step = w / 24f                    // 采样步长，24 段足够平滑

            fun waveY(x: Float): Float =
                waterTop + amp * sin(((x + shift) / waveLen) * 2.0 * PI).toFloat()

            // 水体：顶部边缘是波浪线，下面闭合到杯底，整体填水色
            val body = Path().apply {
                moveTo(stroke, waveY(stroke))
                var x = stroke
                while (x < w - stroke) {
                    x = (x + step).coerceAtMost(w - stroke)
                    lineTo(x, waveY(x))
                }
                lineTo(w - stroke, bottom)
                lineTo(stroke, bottom)
                close()
            }
            drawPath(body, waterFill)

            // 水面波线：沿波浪描一条同色描边，让浪头看得见
            val crest = Path().apply {
                moveTo(stroke, waveY(stroke))
                var x = stroke
                while (x < w - stroke) {
                    x = (x + step).coerceAtMost(w - stroke)
                    lineTo(x, waveY(x))
                }
            }
            drawPath(crest, surface, style = Stroke(width = stroke))

            // 气泡：固定种子保证位置不乱跳，只让 y 随时间上浮（从波浪下方升起）
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
