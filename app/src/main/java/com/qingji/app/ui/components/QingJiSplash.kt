package com.qingji.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.QingJiThemeState
import kotlinx.coroutines.delay

/**
 * ============================================================
 * 轻记开屏动画（正式版 v1.1）
 * ------------------------------------------------------------
 * 三片叶子分别从三个方向飘入 → 聚拢成完整 Logo → 品牌文字淡入。
 * 纯 Compose Canvas 矢量绘制：不引 Lottie、不放 GIF、没有序列帧，
 * 三条叶片路径与桌面图标 `ic_launcher_foreground.xml` 是同一套数据，
 * 保证开屏最后一帧和桌面图标长一个样。
 *
 * 播放时机：只冷启动播一次（MainActivity 用 rememberSaveable 记住，
 * 旋转 / 重组不会重播）。
 * ============================================================
 */

private val MintLight = Color(0xFFBCEBD9)
private val MintMiddle = Color(0xFF62C9A2)
private val MintDark = Color(0xFF4EB18D)
private val BrandGreen = Color(0xFF187B65)

/** 三层叶片路径，512 × 512 坐标系（与 ic_launcher_foreground.xml 一致）。 */
private fun leafOuter() = Path().apply {
    moveTo(102f, 337f)
    cubicTo(88f, 245f, 122f, 148f, 207f, 117f)
    cubicTo(257f, 99f, 317f, 99f, 363f, 105f)
    cubicTo(381f, 108f, 385f, 116f, 385f, 135f)
    cubicTo(391f, 230f, 354f, 345f, 276f, 382f)
    cubicTo(207f, 415f, 133f, 389f, 102f, 337f)
    close()
}

private fun leafMiddle() = Path().apply {
    moveTo(102f, 337f)
    cubicTo(151f, 282f, 271f, 241f, 385f, 207f)
    cubicTo(381f, 279f, 354f, 345f, 276f, 382f)
    cubicTo(207f, 415f, 133f, 389f, 102f, 337f)
    close()
}

private fun leafBottom() = Path().apply {
    moveTo(102f, 337f)
    cubicTo(167f, 296f, 270f, 288f, 365f, 301f)
    cubicTo(343f, 348f, 310f, 381f, 276f, 382f)
    cubicTo(207f, 415f, 133f, 389f, 102f, 337f)
    close()
}

/**
 * 轻记 Logo 绘制组件。
 *
 * @param progress 0 = 三片叶子分散在三个方向；1 = 完全聚拢成 Logo
 */
@Composable
fun QingJiLeafLogo(
    modifier: Modifier = Modifier,
    progress: Float = 1f
) {
    Canvas(modifier = modifier) {
        val scale = size.width / 512f
        // v1.1.2：叶子几何中心 (246.5,257) 必须对准画布中心，再整体顺时针转 8°
        // （与桌面图标 ic_launcher_foreground.xml 完全同一套变换，主人在六个渲染方案里选定了 8°）
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val centerOffsetX = centerX - 246.5f * scale
        val centerOffsetY = centerY - 257f * scale

        fun drawLeaf(
            path: Path,
            color: Color,
            startX: Float,
            startY: Float
        ) {
            withTransform({
                // 1. 居中：把叶子中心平移到画布中心
                translate(left = centerOffsetX, top = centerOffsetY)
                // 2. 姿态：围绕画布中心（即叶子中心）顺时针转 8°
                rotate(degrees = 8f, pivot = Offset(centerX, centerY))
                // 3. 缩放到画布
                scale(scale, scale, pivot = Offset.Zero)
                // 4. 聚拢动画：progress=0 时叶片分散在各自方向
                translate(
                    left = startX * (1f - progress),
                    top = startY * (1f - progress)
                )
            }) {
                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.25f + 0.75f * progress)
                )
            }
        }

        // 第一层：浅绿，从左上飘入
        drawLeaf(leafOuter(), MintLight, -75f, -55f)
        // 第二层：中绿，从下方飘入
        drawLeaf(leafMiddle(), MintMiddle, 0f, 75f)
        // 第三层：深绿，从右下飘入
        drawLeaf(leafBottom(), MintDark, 75f, 55f)
    }
}

/**
 * 完整开屏页：浅薄荷背景 + 白色圆角底板 + 三叶聚拢 + 品牌文字淡入。
 * 整个开屏约 1.5 秒；深色模式下换成深底浅字，避免冷启动闪一屏白。
 */
@Composable
fun QingJiSplash(
    onAnimationFinished: () -> Unit = {}
) {
    val pageBg = if (QingJiThemeState.darkMode) Color(0xFF0F1512) else Color(0xFFF1F6F3)
    val titleColor = if (QingJiThemeState.darkMode) Color(0xFF8FE0C4) else BrandGreen
    val subColor = if (QingJiThemeState.darkMode) Color(0xFF7E948A) else Color(0xFF84978D)

    // 聚拢进度：0 分散 → 1 聚拢
    val progress = remember { Animatable(0f) }
    var showTitle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing)
        )
        showTitle = true
        delay(400)
        onAnimationFinished()
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "titleAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo 白色圆角底板（深色模式换深灰绿，避免刺眼）
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0xFFB7D8C9),
                        spotColor = Color(0xFFB7D8C9)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (QingJiThemeState.darkMode) Color(0xFF1C2621) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                QingJiLeafLogo(
                    modifier = Modifier.fillMaxSize(),
                    progress = progress.value
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "轻记",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor.copy(alpha = titleAlpha)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "轻一点，记得更好一点",
                fontSize = 14.sp,
                color = subColor.copy(alpha = titleAlpha),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
