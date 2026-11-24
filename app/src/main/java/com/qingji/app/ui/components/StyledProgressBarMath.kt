package com.qingji.app.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * QJ-PROGRESS-V2：进度条绘制纯计算。
 * 从 Canvas 高频路径抽离，便于单测与复用，避免绘制帧创建临时复杂对象。
 */

/** 动画值/进度值一律夹紧，弹簧过冲不会画出轨道。 */
internal fun safeProgress(raw: Float): Float = raw.coerceIn(0f, 1f)

/**
 * 低进度填充宽度：真实比例，不强制扩大到 barHeight。
 * 极低但 >0 时返回最小 1px 保证可见，绝不放大成整根高度。
 */
internal fun fillWidth(progress: Float, width: Float, minVisiblePx: Float = 1f): Float {
    val p = safeProgress(progress)
    if (p <= 0f) return 0f
    if (p >= 1f) return width
    val raw = width * p
    return if (raw < minVisiblePx) minVisiblePx.coerceAtMost(width) else raw
}

/** 填充矩形（带圆角端点由调用方 clip）；返回 null 表示不绘制。 */
internal fun fillRect(progress: Float, width: Float, height: Float): Rect? {
    val w = fillWidth(progress, width)
    if (w <= 0f) return null
    return Rect(0f, 0f, w, height)
}

/** 彗星：短进度下限制光点数量，避免拥挤。 */
internal fun cometCount(progress: Float, fullWidth: Float, barHeight: Float): Int {
    if (progress <= 0f || fullWidth <= 0f) return 0
    val tailLength = floatArrayOf(barHeight * 1.2f, barHeight * 2.2f, barHeight * 3.2f)
    val available = fillWidth(progress, fullWidth)
    return tailLength.count { it <= available }.coerceIn(0, 3)
}

/** 循环相位 0..1，保证 phase=0 与 phase=1 等价（接缝连续）。 */
internal fun loopPhase(raw: Float): Float = ((raw % 1f) + 1f) % 1f

/** 扫描高光位置：-width..2width 之间循环，接缝在轨道外，不会跳色。 */
internal fun shimmerX(phase: Float, width: Float): Float = -width + loopPhase(phase) * 3f * width

/** 斜纹相位：步长固定，循环接缝连续。 */
internal fun stripeOffset(phase: Float, spacing: Float): Float =
    (loopPhase(phase) * spacing) - spacing

/** 极光色带：基于主题色与青蓝色按相位混合，首尾同色无跳变。 */
internal fun auroraColor(phase: Float, theme: Color): Color {
    val p = loopPhase(phase)
    // 3 段连续混合：theme → 青蓝 → 绿 → theme，首尾回到 theme
    val cyan = Color(0xFF4FC3F7)
    val green = Color(0xFF66BB6A)
    return when {
        p < 1f / 3f -> lerpColor(theme, cyan, p * 3f)
        p < 2f / 3f -> lerpColor(cyan, green, (p - 1f / 3f) * 3f)
        else -> lerpColor(green, theme, (p - 2f / 3f) * 3f)
    }
}

internal fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        alpha = a.alpha + (b.alpha - a.alpha) * k,
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k
    )
}

/** 气泡规格：固定种子生成，只生成一次。 */
internal data class ProgressBubble(
    val xRatio: Float,
    val radiusRatio: Float,
    val speed: Int,
    val birth: Float,
    val sway: Float
)

internal fun buildProgressBubbles(count: Int, seed: Long = 20261124L): List<ProgressBubble> {
    if (count <= 0) return emptyList()
    val random = kotlin.random.Random(seed)
    return List(count) {
        ProgressBubble(
            xRatio = 0.15f + random.nextFloat() * 0.7f,
            radiusRatio = 0.12f + random.nextFloat() * 0.16f,
            speed = 1 + random.nextInt(3), // 整数倍率保证 phase 0→1 接缝连续
            birth = random.nextFloat(),
            sway = 0.06f + random.nextFloat() * 0.10f
        )
    }
}

/** 液体气泡单帧：杯内底→液面，接近液面淡出，左右摆动限制在填充宽度内。 */
internal data class ProgressBubbleFrame(
    val x: Float, val y: Float, val radius: Float, val alpha: Float
)

internal fun progressBubbleFrame(
    spec: ProgressBubble,
    phase: Float,
    width: Float,
    height: Float,
    fillLeft: Float
): ProgressBubbleFrame? {
    if (width <= 0f || height <= 0f) return null
    val radius = height * spec.radiusRatio
    val travel = (height - radius).coerceAtLeast(0f)
    if (travel <= 0f) return null
    val cycle = loopPhase(phase * spec.speed.toFloat() + spec.birth)
    val y = height - cycle * travel
    val alpha = if (cycle > 0.85f) ((1f - cycle) / 0.15f).coerceIn(0f, 1f) else 1f
    val centerX = fillLeft + width * spec.xRatio
    val swayPx = sin(2.0 * PI * (cycle + spec.birth)).toFloat() * spec.sway * width
    val x = (centerX + swayPx).coerceIn(fillLeft + radius, fillLeft + width - radius)
    return ProgressBubbleFrame(x, y, radius, alpha)
}

/** 双光环呼吸：相位错开半周期，透明度低，避免长时间吸睛。 */
internal data class PulseRing(val radius: Float, val alpha: Float)

internal fun pulseRings(phase: Float, maxRadius: Float): List<PulseRing> =
    listOf(0f, 0.5f).map { offset ->
        val p = loopPhase(phase + offset)
        PulseRing(
            radius = maxRadius * (0.3f + 0.7f * p),
            alpha = 0.28f * (1f - p)
        )
    }
