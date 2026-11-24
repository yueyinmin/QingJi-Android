package com.qingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * ============================================================
 * 七种进度条特效（QJ-PROGRESS-V2 从零新增）
 * ------------------------------------------------------------
 * 统一动画管理 + 七种独立绘制策略，单文件实现，避免过度抽象。
 *
 * 生命周期规则：
 *  - SIMPLE 静态，不启动无限动画；
 *  - 无可见填充（progress<=0）不启动无限动画；
 *  - animationEnabled=false 停止正在运行的循环；重新开启恢复；
 *  - style 切换时复位相位，不会叠加多个循环协程。
 * ============================================================
 */
@Composable
fun StyledProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
    trackColor: Color = androidx.compose.ui.graphics.Color(0xFFE8ECF0),
    barHeight: Dp = 6.dp,
    style: ProgressBarStyle = ProgressBarStyle.SIMPLE,
    animationEnabled: Boolean = true,
    /** 进度值补间：默认关，由调用方（如首页 kcal 进度条）自行控制动画。 */
    animateProgress: Boolean = false
) {
    val target = safeProgress(progress)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animateProgress && animationEnabled) {
            tween(400, easing = FastOutSlowInEasing)
        } else snap(),
        label = "styledProgressValue"
    )
    val safe = safeProgress(shown)

    // ---------- 统一无限相位：单一 Animatable owner ----------
    val animatedStyles = style != ProgressBarStyle.SIMPLE
    val needLoop = animationEnabled && animatedStyles && safe > 0f
    val phaseAnim = remember { Animatable(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(style, needLoop) {
        phaseAnim.stop()
        phaseAnim.snapTo(0f)
        phase = 0f
        if (needLoop) {
            while (isActive) {
                phaseAnim.animateTo(1f, tween(2800, easing = LinearEasing)) { phase = value }
                phaseAnim.snapTo(0f)
                phase = 0f
            }
        }
    }

    // ---------- 绘制资源：尺寸无关的颜色/对象池只建一次 ----------
    val bubbles = remember(style) {
        if (style == ProgressBarStyle.LIQUID) buildProgressBubbles(12) else emptyList()
    }
    val reusablePath = remember { Path() }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val radius = h / 2f
        val trackPath = reusablePath.apply {
            rewind()
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, w, h,
                    androidx.compose.ui.geometry.CornerRadius(radius, radius)
                )
            )
        }
        drawPath(trackPath, trackColor)

        val fw = fillWidth(safe, w)
        if (fw <= 0f) return@Canvas
        val fillPath = reusablePath.apply {
            rewind()
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, fw, h,
                    androidx.compose.ui.geometry.CornerRadius(
                        radius.coerceAtMost(fw / 2f),
                        radius.coerceAtMost(fw / 2f)
                    )
                )
            )
        }

        when (style) {
            ProgressBarStyle.SIMPLE -> drawPath(fillPath, color)
            ProgressBarStyle.COMET -> drawComet(fw, h, phase, reusablePath, color)
            ProgressBarStyle.SHIMMER -> drawShimmer(fillPath, fw, h, phase, color)
            ProgressBarStyle.LIQUID -> drawLiquid(fw, h, phase, bubbles, color, reusablePath)
            ProgressBarStyle.STRIPE -> drawStripe(fillPath, fw, h, phase, color, reusablePath)
            ProgressBarStyle.AURORA -> drawAurora(fillPath, fw, h, phase, color)
            ProgressBarStyle.PULSE -> drawPulse(fw, h, phase, color)
        }
    }
}

// ---------------- 七种绘制策略 ----------------

private fun DrawScope.drawComet(w: Float, h: Float, phase: Float, path: Path, color: Color) {
    val tailLen = (h * 2.2f).coerceAtMost(w)
    val headX = w - h / 2f
    val brush = Brush.linearGradient(
        colors = listOf(color.copy(alpha = 0.15f), color),
        start = Offset(headX - tailLen, h / 2f),
        end = Offset(headX, h / 2f)
    )
    path.rewind()
    path.addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            headX - tailLen, 0f, w, h,
            androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        )
    )
    drawPath(path, brush)
    // 光核：始终在末端，短进度下也只有一个，不重复绘制
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = h * 0.28f, center = Offset(headX, h / 2f))
    // 相位只做极轻微呼吸，避免高频位移
    val pulse = 0.85f + 0.15f * sin(phase * 2.0 * PI).toFloat()
    drawCircle(color = color.copy(alpha = 0.35f * pulse), radius = h * 0.42f, center = Offset(headX, h / 2f))
}

private fun DrawScope.drawShimmer(path: Path, w: Float, h: Float, phase: Float, color: Color) {
    clipPath(path) {
        drawRect(color)
        val x = shimmerX(phase, w)
        // 高光只覆盖 1/3 宽度，透明度压低，避免刺眼
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                start = Offset(x, 0f), end = Offset(x + w / 3f, 0f)
            ),
            topLeft = Offset(x, 0f), size = Size(w / 3f, h)
        )
    }
}

private fun DrawScope.drawLiquid(
    w: Float, h: Float, phase: Float,
    bubbles: List<ProgressBubble>, color: Color, path: Path
) {
    clipPath(path) {
        drawRect(color.copy(alpha = 0.85f))
        // 双层波浪：错相位、波长不同，接缝连续
        val amplitude = h * 0.30f
        for (layer in 0..1) {
            val wavePath = Path().apply {
                val shift = loopPhase(phase + layer * 0.37f) * w
                moveTo(0f, h / 2f + amplitude * sin(shift / w * 2.0 * PI).toFloat())
                var x = 0f
                while (x <= w) {
                    val y = h / 2f + amplitude * sin((x + shift) / w * 2.0 * PI).toFloat()
                    lineTo(x, y)
                    x += w / 24f
                }
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(wavePath, color.copy(alpha = if (layer == 0) 0.45f else 0.25f))
        }
        // 气泡：固定池，逐帧计算，不创建 Random
        bubbles.take((bubbles.size * (w / size.width).coerceIn(0f, 1f)).toInt()).forEach { spec ->
            val frame = progressBubbleFrame(spec, phase, w, h, 0f) ?: return@forEach
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * frame.alpha),
                radius = frame.radius, center = Offset(frame.x, frame.y)
            )
        }
    }
}

private fun DrawScope.drawStripe(path: Path, w: Float, h: Float, phase: Float, color: Color, reusable: Path) {
    clipPath(path) {
        drawRect(color)
        // 条纹间距随高度缩放；小尺寸下加大间距，避免密集噪声
        val spacing = (h * 2.2f).coerceAtLeast(8.dp.toPx())
        val off = stripeOffset(phase, spacing)
        val strokeW = (h * 0.28f).coerceAtMost(spacing * 0.35f)
        reusable.rewind()
        var x = off - spacing
        while (x < w + spacing) {
            reusable.moveTo(x, h)
            reusable.lineTo(x + h, 0f)
            x += spacing
        }
        drawPath(reusable, Color.White.copy(alpha = 0.28f), style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawAurora(path: Path, w: Float, h: Float, phase: Float, color: Color) {
    clipPath(path) {
        // 5 段渐变覆盖整条，色首尾同色，phase 循环不跳色
        val brush = Brush.horizontalGradient(
            colors = (0..4).map { auroraColor(phase + it / 4f, color) }
        )
        drawRect(brush)
    }
}

private fun DrawScope.drawPulse(w: Float, h: Float, phase: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset.Zero, size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
    )
    clipPath(Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)))
    }) {
        pulseRings(phase, h * 2f).forEach { ring ->
            drawCircle(
                color = Color.White.copy(alpha = ring.alpha),
                radius = ring.radius,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = h * 0.16f)
            )
        }
    }
}
