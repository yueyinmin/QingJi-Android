package com.qingji.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * ============================================================
 * 入场动画（v2.3）
 * ------------------------------------------------------------
 * 列表和卡片不要「啪」地一下全出来。让它们错开 45ms 依次浮上来，
 * 眼睛就有节奏可循，比整体一闪要柔和得多。
 *
 * 用法：
 * ```
 * items.forEachIndexed { index, item ->
 *     AppearItem(index = index) { MyCard(item) }
 * }
 * ```
 * 错开只算到第 8 项（见 Motion.MaxStaggerIndex），
 * 否则滚到列表底部时，后面的项要等太久才出现。
 * ============================================================
 */
@Composable
fun AppearItem(
    modifier: Modifier = Modifier,
    index: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val wait = index.coerceAtMost(Motion.MaxStaggerIndex) * Motion.StaggerStep
        if (wait > 0) delay(wait)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        // 只做入场，不做退场：列表项是被移除的，不需要慢慢消失
        exit = ExitTransition.None,
        enter = fadeIn(
            animationSpec = tween(durationMillis = Motion.Slow, easing = Motion.decelerate)
        ) + slideInVertically(
            animationSpec = tween(durationMillis = Motion.Slow, easing = Motion.decelerate)
        ) { height -> height / 8 },
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 数字变化时「滚一下」。
 *
 * 直接换数字（2313 → 1890）会显得生硬，
 * 让旧数字往上滑走、新数字从下滑进来，能明确传达「这个值变了」。
 */
@Composable
fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    suffix: String = ""
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (
                    slideInVertically(
                        animationSpec = tween(
                            durationMillis = Motion.Normal,
                            easing = Motion.decelerate
                        )
                    ) { height -> height } + fadeIn(
                        animationSpec = tween(durationMillis = Motion.Fast)
                    )
                    ) togetherWith (
                    slideOutVertically(
                        animationSpec = tween(
                            durationMillis = Motion.Normal,
                            easing = Motion.accelerate
                        )
                    ) { height -> -height } + fadeOut(
                        animationSpec = tween(durationMillis = Motion.Fast)
                    )
                    )
            },
            label = "number"
        ) { target ->
            Text(text = "$target", style = style, color = color)
        }
        if (suffix.isNotBlank()) {
            Text(
                text = suffix,
                fontSize = 13.sp,
                color = color.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp),
                fontWeight = FontWeight.Normal
            )
        }
    }
}
