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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * ============================================================
 * 饮水计量杯（v1.2 按 ChatGPT 方案整套重做，主人确认「完全按 ChatGPT 那套连杯形一起换」）
 * ------------------------------------------------------------
 * - 杯形：锥形（上宽下窄）+ 杯口椭圆 + 玻璃高光条，取代旧直筒杯；
 * - 水面：**双层正弦波**（错相位、不同波长，主波 + 背景波），
 *   加水时波浪振幅脉冲放大后衰减；
 * - 杯内：**容量文字**画在杯中下部（水下白字 / 水上灰字），水位 1200ms 平滑过渡；
 * - 气泡保留：固定种子保证位置不乱跳，只随时间上浮；
 * - 旧的杯壁刻度按 ChatGPT 版去掉，`tickTint` 参数保留兼容但不再绘制。
 *
 * 参数签名向后兼容：老调用不传新参数行为正常。
 * 颜色仍在 Composable 体内取好再进绘制 lambda（绘制 lambda 非 @Composable）。
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
    /** v1.2 起杯形重做后不再画刻度，参数仅为兼容保留 */
    tickTint: Color? = null,
    /** v1.2：杯内容量文字（如 "1000/2500 ml"），null 不显示 */
    capacityText: String? = null
) {
    val safe = ratio.coerceIn(0f, 1f)

    val outline = outlineColor ?: QingJiGreen.copy(alpha = 0.42f)
    val waterFill = fillColor ?: QingJiGreenLight
    val surface = surfaceColor ?: QingJiGreen.copy(alpha = 0.5f)
    val bubbleColor = bubbleTint ?: QingJiGreen.copy(alpha = 0.28f)
    val textNormal = outlineColor ?: Color(0xFF8A8F98)

    // 水位上涨动画：1200ms 标准减速曲线。
    // v7：切 Tab 回首页时 Composable 重建，水位会从 0 重新涨一遍（闪一下的
    // 另一个来源）。首帧 snap 落在当前水位，之后加水才走 1200ms 涨水。
    var levelReady by remember { mutableStateOf(false) }
    val level by animateFloatAsState(
        targetValue = safe,
        animationSpec = if (levelReady) {
            tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "waterLevel"
    )
    LaunchedEffect(Unit) { levelReady = true }

    // 加水波动反馈：目标一变（排除首次组合）就把脉冲打到 1，再 1.1 秒衰减到 0
    val pulse = remember { Animatable(0f) }
    var lastTarget by remember { mutableStateOf(safe) }
    LaunchedEffect(safe) {
        if (safe != lastTarget) {
            lastTarget = safe
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
        }
    }

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

    val textMeasurer = rememberTextMeasurer()

    val maxBubbles = when (bubbleLevel) {
        0 -> 0
        1 -> 4
        2 -> 9
        3 -> 16
        else -> 9
    }

    // 点击不要长方形的 ripple：整个 Canvas 是矩形，默认的选中高亮会盖出一个方角块，
    // 和水杯形状完全对不上。indication = null 去掉高亮，只保留点击本身；
    // 真要反馈也该是水杯整体缩放，而不是在矩形边界里画圆角光晕。
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Canvas(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val mouth = (w * 0.2f).coerceAtMost(10.dp.toPx())
        val top = mouth / 2 + stroke
        val bottom = h - stroke * 2
        val centerX = w / 2f

        // 锥形杯：顶部半宽 / 底部半宽（底部收窄到 72%）
        val topHalf = w / 2f - stroke
        val bottomHalf = topHalf * 0.72f

        // 杯形 path：梯形（波浪 / 气泡 / 高光全部 clip 在这里面）
        val cupPath = Path().apply {
            moveTo(centerX - topHalf, top)
            lineTo(centerX + topHalf, top)
            lineTo(centerX + bottomHalf, bottom)
            lineTo(centerX - bottomHalf, bottom)
            close()
        }

        val waterTop = bottom - (bottom - top) * level
        val amp = 3.0.dp.toPx() * (1f + 3.2f * pulse.value)
        val waveLen = w * 0.7f
        val shift = phase * waveLen
        val backShift = phase * waveLen * 1.18f + waveLen * 0.35f

        fun wavePath(yTop: Float, amplitude: Float, sh: Float, len: Float, closeBottom: Boolean): Path {
            val p = Path()
            val n = 24
            p.moveTo(centerX - topHalf, yTop + amplitude * sin(((centerX - topHalf + sh) / len) * 2.0 * PI).toFloat())
            for (i in 1..n) {
                val x = centerX - topHalf + (topHalf * 2) * i / n
                val y = yTop + amplitude * sin(((x + sh) / len) * 2.0 * PI).toFloat()
                p.lineTo(x, y)
            }
            if (closeBottom) {
                p.lineTo(centerX + topHalf, bottom)
                p.lineTo(centerX - topHalf, bottom)
                p.close()
            }
            return p
        }

        // ---- 杯内：全部裁进锥形杯里 ----
        clipPath(cupPath) {
            if (level > 0.02f) {
                // 背景波：稍浅、错相位、略高一点，制造层次
                drawPath(
                    wavePath(waterTop - 2.5.dp.toPx(), amp * 1.15f, backShift, waveLen * 0.85f, true),
                    waterFill.copy(alpha = 0.45f)
                )
                // 主水体
                drawPath(
                    wavePath(waterTop, amp, shift, waveLen, true),
                    waterFill
                )
                // 主波峰描线
                drawPath(
                    wavePath(waterTop, amp, shift, waveLen, false),
                    surface,
                    style = Stroke(width = stroke)
                )
            }

            // v1.0.7：玻璃高光的两条白色竖圆角条去掉了（用户看着像多余的竖条）
            // 杯壁高光改由水面波纹和描边承担，不再单独画竖条。

            // 气泡：固定种子保证位置不乱跳，只随时间上浮
            val random = Random(seed = 20261108)
            val count = (maxBubbles * level).toInt()
            repeat(count) { index ->
                val bx = w * (0.32f + 0.4f * random.nextFloat())
                val baseY = waterTop + (bottom - waterTop) * (0.1f + 0.8f * random.nextFloat())
                val radius = (1.4f + random.nextFloat() * 1.6f).dp.toPx()
                val offsetPhase = (phase + index * 0.17f) % 1f
                val floatY = baseY - offsetPhase * 7.dp.toPx()
                if (floatY > waterTop + radius) {
                    drawCircle(color = bubbleColor, radius = radius, center = Offset(bx, floatY))
                }
            }
        }

        // ---- 杯口椭圆 + 锥形杯壁描边 ----
        drawOval(
            color = outline,
            topLeft = Offset(centerX - topHalf, top - mouth / 2),
            size = Size(topHalf * 2, mouth),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = outline,
            start = Offset(centerX - topHalf, top),
            end = Offset(centerX - bottomHalf, bottom),
            strokeWidth = stroke
        )
        drawLine(
            color = outline,
            start = Offset(centerX + topHalf, top),
            end = Offset(centerX + bottomHalf, bottom),
            strokeWidth = stroke
        )
        drawLine(
            color = outline,
            start = Offset(centerX - bottomHalf, bottom),
            end = Offset(centerX + bottomHalf, bottom),
            strokeWidth = stroke
        )

        // ---- 杯内容量文字：水下白字、水上灰字；太长自动缩字号 ----
        if (!capacityText.isNullOrBlank() && level > 0.1f) {
            val textY = top + (bottom - top) * 0.62f
            val inWater = textY > waterTop
            val t = ((textY - top) / (bottom - top)).coerceIn(0f, 1f)
            val available = (topHalf + (bottomHalf - topHalf) * t) * 2 * 0.86f
            var fontSize = 10.sp
            var layout = textMeasurer.measure(
                capacityText,
                style = TextStyle(fontSize = fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            )
            var guard = 0
            while (layout.size.width > available && fontSize.value > 7f && guard < 6) {
                fontSize = (fontSize.value - 1f).sp
                layout = textMeasurer.measure(
                    capacityText,
                    style = TextStyle(fontSize = fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                )
                guard++
            }
            val color = if (inWater) Color.White.copy(alpha = 0.92f) else textNormal.copy(alpha = 0.85f)
            drawText(
                textLayoutResult = layout,
                color = color,
                topLeft = Offset(centerX - layout.size.width / 2f, textY - layout.size.height / 2f)
            )
        }
    }
}
