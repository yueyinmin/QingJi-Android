package com.qingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * ============================================================
 * 饮水计量杯（QJ-WATERCUP-REFACTOR-V2 安全重构）
 * ------------------------------------------------------------
 * 保留 v1.2 的锥形杯形、双层正弦波、容量文字与全部参数签名/颜色语义；
 * 本次按工单修正 5 类问题：
 *  1. P0 容量文字测量移出 Canvas 绘制帧，仅在文字/尺寸变化时重测；
 *  2. P0 animationEnabled 控制无限波浪/脉冲/按压的启停与恢复，默认 true 兼容老调用；
 *     注意：不复用 progressAnimation（设置里是「进度条动效」，语义仅覆盖进度条）。
 *  3. P1 气泡：固定种子对象池 + 完整底→面生命周期 + 速度/摆动周期连续 + 杯壁约束 + 水面淡出；
 *  4. P1 水位弹簧：动画原始值与绘制安全值分离，超调/越界都走 clamp；
 *  5. P2 杯底玻璃高光：用轮廓色半透明绘制，深浅主题自适应、小尺寸自动收窄。
 *
 * 点击反馈：整体水面振幅脉冲（非局部涟漪）+ 按压缩放 + 触觉反馈。
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
    /** 杯内容量文字（如 "1000/2500 ml"），null 不显示 */
    capacityText: String? = null,
    /**
     * QJ-WATERCUP-REFACTOR-V2：动效总开关，默认 true（旧调用行为不变）。
     * 关闭时：停止无限波浪、取消脉冲、停止按压缩放；水位直接 snap 到目标。
     * 与设置项 progressAnimation（进度条动效）语义不同，二者互不覆盖。
     */
    animationEnabled: Boolean = true
) {
    val safe = safeLevel(ratio)

    val outline = outlineColor ?: QingJiGreen.copy(alpha = 0.42f)
    val waterFill = fillColor ?: QingJiGreenLight
    val surface = surfaceColor ?: QingJiGreen.copy(alpha = 0.5f)
    val bubbleColor = bubbleTint ?: QingJiGreen.copy(alpha = 0.28f)
    val textNormal = outlineColor ?: Color(0xFF8A8F98)

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    // ---------- 几何：尺寸变化才算一次，绘制帧只读 ----------
    var cupWidth by remember { mutableStateOf(0f) }
    var cupHeight by remember { mutableStateOf(0f) }
    val geometry = remember(cupWidth, cupHeight, density) {
        cupGeometry(cupWidth, cupHeight, density)
    }

    // ---------- 容量文字：真正缓存（P0） ----------
    // key 含文字内容、几何（含可用宽度）、density；动画帧变化不会触发重新测量。
    val textLayout = remember(capacityText, geometry, density, textMeasurer) {
        measureCupText(textMeasurer, capacityText, geometry, density)
    }

    // ---------- 气泡对象池：bubbleLevel 变化才重建 ----------
    val bubbleSpecs = remember(bubbleLevel) { buildBubbleSpecs(bubbleLevel) }

    // ---------- 水位弹簧（P1） ----------
    // levelReady：首帧 snap 落在当前水位，避免切 Tab 回来从 0 涨一遍。
    var levelReady by remember { mutableStateOf(false) }
    val levelRaw by animateFloatAsState(
        targetValue = safe,
        animationSpec = if (!animationEnabled || !levelReady) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "waterLevelRaw"
    )
    // 绘制/气泡一律用安全值，弹簧超调不会画出杯壁或算出负行程。
    val levelSafe = safeLevel(levelRaw)
    LaunchedEffect(Unit) { levelReady = true }

    // ---------- 整体水面振幅脉冲（P1，非局部涟漪） ----------
    val pulse = remember { Animatable(0f) }
    var lastTarget by remember { mutableStateOf(safe) }
    LaunchedEffect(safe) {
        if (safe != lastTarget) {
            lastTarget = safe
            if (animationEnabled) {
                pulse.snapTo(1f)
                pulse.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
            } else {
                pulse.snapTo(0f)
            }
        }
    }
    // 关闭动效：立刻取消进行中的脉冲，避免继续逐帧重绘。
    LaunchedEffect(animationEnabled) {
        if (!animationEnabled) {
            pulse.stop()
            pulse.snapTo(0f)
        }
    }
    // ---------- 无限波浪相位：用 Animatable 手动驱动，关闭时真正 stop ----------
    // 不用 rememberInfiniteTransition：那个在开关关闭后仍持续推进 state，
    // 会造成无意义的逐帧重组。Animatable 关闭后停住，绘制帧才真正静止。
    val wavePhase = remember { Animatable(0f) }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationEnabled) {
        if (animationEnabled) {
            while (isActive) {
                wavePhase.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2800, easing = LinearEasing)
                ) { phase = value }
                wavePhase.snapTo(0f)
                phase = 0f
            }
        } else {
            wavePhase.stop()
            wavePhase.snapTo(0f)
            phase = 0f
        }
    }

    // ---------- 点击反馈：按压缩放 + 触觉 ----------
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && animationEnabled) 0.96f else 1f,
        animationSpec = if (animationEnabled) spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ) else snap(),
        label = "waterCupPress"
    )

    Canvas(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            // 尺寸回写（px）：只在这里更新，触发几何与文字测量缓存失效。
            .then(Modifier.onSizeChanged { size ->
                if (size.width != cupWidth.toInt() || size.height != cupHeight.toInt()) {
                    cupWidth = size.width.toFloat()
                    cupHeight = size.height.toFloat()
                }
            })
    ) {
        // 尺寸首帧还未回写时，Canvas 已有实际 size，直接用它画，避免空白一帧。
        val g = if (geometry.width > 0f && geometry.height > 0f) {
            geometry
        } else {
            cupGeometry(size.width, size.height, density)
        }

        val cupPath = Path().apply {
            moveTo(g.centerX - g.topHalf, g.top)
            lineTo(g.centerX + g.topHalf, g.top)
            lineTo(g.centerX + g.bottomHalf, g.bottom)
            lineTo(g.centerX - g.bottomHalf, g.bottom)
            close()
        }

        val waterTop = waterTopY(g, levelSafe)
        val amp = safeAmplitude(5.0.dp.toPx() * (1f + 3.2f * pulse.value), g)
        val shift = phase * g.waveLen
        val backShift = phase * g.waveLen * 1.18f + g.waveLen * 0.35f

        fun wavePath(yTop: Float, amplitude: Float, sh: Float, len: Float, closeBottom: Boolean): Path {
            val p = Path()
            val n = 24
            p.moveTo(g.centerX - g.topHalf, waveY(yTop, amplitude, g.centerX - g.topHalf, sh, len))
            for (i in 1..n) {
                val x = g.centerX - g.topHalf + (g.topHalf * 2) * i / n
                p.lineTo(x, waveY(yTop, amplitude, x, sh, len))
            }
            if (closeBottom) {
                p.lineTo(g.centerX + g.topHalf, g.bottom)
                p.lineTo(g.centerX - g.bottomHalf, g.bottom)
                p.close()
            }
            return p
        }

        // ---- 杯内：全部裁进锥形杯里 ----
        clipPath(cupPath) {
            if (levelSafe > 0.02f) {
                drawPath(
                    wavePath(waterTop - 2.5.dp.toPx(), amp * 1.15f, backShift, g.waveLen * 0.85f, true),
                    waterFill.copy(alpha = 0.5f)
                )
                drawPath(
                    wavePath(waterTop, amp, shift, g.waveLen, true),
                    waterFill
                )
                drawPath(
                    wavePath(waterTop, amp, shift, g.waveLen, false),
                    surface,
                    style = Stroke(width = g.stroke)
                )
            }

            // 气泡：完整底→面生命周期，接近水面淡出；level=0 时 count=0 直接不进循环。
            if (bubbleSpecs.isNotEmpty() && levelSafe > 0f) {
                val count = (bubbleSpecs.size * levelSafe).toInt()
                repeat(count) { index ->
                    val frame = bubbleFrameAt(bubbleSpecs[index], phase, g, waterTop, density) ?: return@repeat
                    drawCircle(
                        color = bubbleColor.copy(alpha = bubbleColor.alpha * frame.alpha),
                        radius = frame.radius,
                        center = Offset(frame.x, frame.y)
                    )
                }
            }

            // 杯底玻璃高光（P2）：用轮廓色半透明，不是白色粗线；小尺寸自动收窄。
            val foot = footHighlight(g, tintAlpha = 0.5f)
            drawLine(
                color = outline.copy(alpha = foot.alpha),
                start = Offset(foot.startX, foot.startY),
                end = Offset(foot.endX, foot.endY),
                strokeWidth = foot.width,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // ---- 杯口椭圆 + 锥形杯壁描边（原样保留） ----
        drawOval(
            color = outline,
            topLeft = Offset(g.centerX - g.topHalf, g.top - g.mouth / 2),
            size = Size(g.topHalf * 2, g.mouth),
            style = Stroke(width = g.stroke)
        )
        drawLine(color = outline, start = Offset(g.centerX - g.topHalf, g.top), end = Offset(g.centerX - g.bottomHalf, g.bottom), strokeWidth = g.stroke)
        drawLine(color = outline, start = Offset(g.centerX + g.topHalf, g.top), end = Offset(g.centerX + g.bottomHalf, g.bottom), strokeWidth = g.stroke)
        drawLine(color = outline, start = Offset(g.centerX - g.bottomHalf, g.bottom), end = Offset(g.centerX + g.bottomHalf, g.bottom), strokeWidth = g.stroke)

        // ---- 容量文字：用缓存好的布局，绘制帧不做任何 measure ----
        val cached = textLayout
        if (cached != null && levelSafe > 0.1f) {
            val inWater = g.textY > waterTop
            val color = if (inWater) Color.White.copy(alpha = 0.92f) else textNormal.copy(alpha = 0.85f)
            // 7sp 下限仍放不下的极长文字由 clip 兜底，绝不越出杯壁。
            clipPath(cupPath) {
                drawText(
                    textLayoutResult = cached.layout,
                    color = color,
                    topLeft = Offset(g.centerX - cached.layout.size.width / 2f, g.textY - cached.layout.size.height / 2f)
                )
            }
        }
    }
}
