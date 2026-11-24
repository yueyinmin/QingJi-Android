package com.qingji.app.ui.components

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * QJ-WATERCUP-REFACTOR-V2：WaterCup 纯计算/测量逻辑。
 *
 * 从 Canvas 高频绘制路径里抽出来的无副作用函数，便于：
 *  1. 在 Composable 层一次性算好几何与文字布局（真正缓存）；
 *  2. JVM 单测直接验证水位边界、气泡周期连续性、文字自适应。
 * 这里不依赖 Compose UI 树，只依赖 [Density] / [TextMeasurer]。
 */

/** 水杯几何：所有像素值在尺寸变化时算一次，绘制帧直接复用。 */
internal data class CupGeometry(
    val width: Float,
    val height: Float,
    val stroke: Float,
    val mouth: Float,
    val top: Float,
    val bottom: Float,
    val centerX: Float,
    val topHalf: Float,
    val bottomHalf: Float,
    val waveLen: Float,
    /** 文字基线 Y（原逻辑固定在杯高 62% 处）。 */
    val textY: Float,
    /** 文字可用宽度（杯内壁在该高度的宽度 × 0.86）。 */
    val textAvailable: Float,
    /** 杯底玻璃高光可用的最大描边宽度（小尺寸自动收窄）。 */
    val footHighlightWidth: Float
)

internal fun cupGeometry(width: Float, height: Float, density: Density): CupGeometry = with(density) {
    val stroke = 1.5.dp.toPx()
    val mouth = (width * 0.2f).coerceAtMost(10.dp.toPx())
    val top = mouth / 2 + stroke
    val bottom = height - stroke * 2
    val centerX = width / 2f
    val topHalf = width / 2f - stroke
    val bottomHalf = topHalf * 0.72f
    val tText = 0.62f
    val textY = top + (bottom - top) * tText
    val textAvailable = (topHalf + (bottomHalf - topHalf) * tText) * 2 * 0.86f
    CupGeometry(
        width = width, height = height, stroke = stroke, mouth = mouth, top = top, bottom = bottom,
        centerX = centerX, topHalf = topHalf, bottomHalf = bottomHalf, waveLen = width * 0.7f,
        textY = textY, textAvailable = textAvailable,
        footHighlightWidth = minOf(stroke * 1.4f, 3.dp.toPx())
    )
}

/** 文字测量+自适应字号的最终结果，仅在内容/尺寸/样式变化时重算。 */
internal data class CupTextLayout(
    val layout: androidx.compose.ui.text.TextLayoutResult,
    val fontSizeSp: Float,
    val available: Float
)

/**
 * 容量文字测量：从 Canvas 绘制路径里移出。
 * 保留原字号自适应：10sp 起、每档 -1sp、最低 >7f、最多 6 次。
 */
internal fun measureCupText(
    measurer: TextMeasurer,
    capacityText: String?,
    g: CupGeometry,
    density: Density
): CupTextLayout? {
    if (capacityText.isNullOrBlank()) return null
    // sp 起点必须是 10.sp，不能用 dp.toSp()（后者会随字体缩放策略失真）。
    var fontSize: TextUnit = 10f.sp
    var layout = measurer.measure(
        capacityText,
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium)
    )
    var guard = 0
    // guard 从原实现的 6 扩到 12：极长容量文字（如带单位的多段文本）在 7sp 前
    // 有更多档位可收敛；绘制端另有 clipPath 兜底，保证任何情况下都不越杯壁。
    while (layout.size.width > g.textAvailable && fontSize.value > 7f && guard < 12) {
        fontSize = (fontSize.value - 1f).sp
        layout = measurer.measure(
            capacityText,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Medium)
        )
        guard++
    }
    return CupTextLayout(layout, fontSize.value, g.textAvailable)
}


/** 安全水位：绘制与气泡计算一律用这个值，绝不直接使用可能超调的动画原始值。 */
internal fun safeLevel(raw: Float): Float = raw.coerceIn(0f, 1f)

/** 水体振幅上限：不超过杯体高度的 8%，防止弹簧超调画出杯壁。 */
internal fun safeAmplitude(raw: Float, geometry: CupGeometry): Float =
    raw.coerceIn(0f, (geometry.bottom - geometry.top) * 0.08f)

/** 水面 Y：从杯底往上，随水位上升。 */
internal fun waterTopY(geometry: CupGeometry, level: Float): Float =
    geometry.bottom - (geometry.bottom - geometry.top) * safeLevel(level)

/** 正弦波上某点的 Y（与旧实现同一公式，供单测与绘制共用）。 */
internal fun waveY(yTop: Float, amplitude: Float, x: Float, shift: Float, len: Float): Float =
    yTop + amplitude * sin(((x + shift) / len) * 2.0 * PI).toFloat()

/** 气泡规格：种子固定、生命周期参数确定，可预生成一次复用。 */
internal data class BubbleSpec(
    val xRatio: Float,       // 0..1，映射到杯内壁宽度
    val radiusDp: Float,
    val speed: Int,          // 上浮速度倍率（整数 1~3，保证 phase 0→1 周期接缝连续）
    val swayPhase: Float,    // 水平摆动相位
    val swayAmp: Float,      // 摆动幅度（归一化）
    val birth: Float         // 出生相位偏移，避免所有气泡同排出现
)

/** 固定种子生成气泡规格；只在 bubbleLevel 变化时调用。 */
internal fun buildBubbleSpecs(level: Int, seed: Long = 20261108L): List<BubbleSpec> {
    val count = when (level) {
        0 -> 0
        1 -> 4
        2 -> 9
        3 -> 16
        else -> 9
    }
    if (count == 0) return emptyList()
    val random = kotlin.random.Random(seed = seed)
    return List(count) {
        BubbleSpec(
            xRatio = 0.32f + 0.4f * random.nextFloat(),
            radiusDp = 1.4f + random.nextFloat() * 1.6f,
            // 整数倍率：phase 从 0 推进到 1 时 cycle 恰好走完整数个周期，无接缝跳变。
            speed = 1 + random.nextInt(3),
            swayPhase = random.nextFloat(),
            swayAmp = 0.10f + random.nextFloat() * 0.18f,
            birth = random.nextFloat()
        )
    }
}

/** 单个气泡在相位 phase∈[0,1) 下的完整状态。 */
internal data class BubbleFrame(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float
)

/**
 * 气泡一帧：从杯底上浮到水面，接近水面淡出；水平摆动限制在杯壁内。
 * 周期用 (phase * speed + birth) % 1，phase 回到 0 时同一 bubble 的相位连续，
 * 不会产生接缝跳变（速度只改变相位推进倍率，不改变周期起止点）。
 */
internal fun bubbleFrameAt(
    spec: BubbleSpec,
    phase: Float,
    geometry: CupGeometry,
    waterTop: Float,
    density: Density
): BubbleFrame? = with(density) {
    val radius = spec.radiusDp.dp.toPx()
    val cycle = ((phase * spec.speed.toFloat() + spec.birth) % 1f + 1f) % 1f
    // 水面之上不留气泡：把行程上限压到水面下方一个半径处
    val travel = (geometry.bottom - waterTop - radius).coerceAtLeast(0f)
    if (travel <= 0f) return null
    val y = geometry.bottom - cycle * travel
    // 接近水面（cycle→1）淡出，避免在水面接缝突然消失
    val alpha = if (cycle > 0.86f) ((1f - cycle) / 0.14f).coerceIn(0f, 1f) else 1f
    // 杯壁内半宽随高度线性收窄；摆幅再留出一个半径 + 描边余量
    val t = ((geometry.bottom - y) / (geometry.bottom - geometry.top)).coerceIn(0f, 1f)
    val innerHalf = geometry.bottomHalf + (geometry.topHalf - geometry.bottomHalf) * t
    val maxOffset = (innerHalf - radius - geometry.stroke * 2f).coerceAtLeast(0f)
    val swayPhase = 2.0 * PI * (cycle + spec.swayPhase)
    val sway = sin(swayPhase).toFloat() * spec.swayAmp * maxOffset
    val baseX = geometry.centerX + (spec.xRatio - 0.5f) * 2f * maxOffset
    val x = (baseX + sway).coerceIn(
        geometry.centerX - maxOffset,
        geometry.centerX + maxOffset
    )
    BubbleFrame(x = x, y = y, radius = radius, alpha = alpha)
}

/** 杯底玻璃高光的两个端点与透明度；小尺寸自动收窄，不画白色粗线。 */
internal data class FootHighlight(
    val startX: Float, val startY: Float,
    val endX: Float, val endY: Float,
    val width: Float, val alpha: Float
)

internal fun footHighlight(
    g: CupGeometry,
    tintAlpha: Float = 0.32f
): FootHighlight {
    val y = g.bottom - g.stroke * 3f
    val half = (g.bottomHalf * 0.62f).coerceAtLeast(0f)
    return FootHighlight(
        startX = g.centerX - half, startY = y,
        endX = g.centerX + half, endY = y,
        width = g.footHighlightWidth,
        alpha = tintAlpha
    )
}
