package com.qingji.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable

/**
 * ============================================================
 * 动画规范（v2.3）
 * ------------------------------------------------------------
 * 为什么要有这个文件：
 * 以前每个页面各写各的 —— 这里 tween(400)、那里 tween(180)、
 * 有的用默认缓动、有的完全没有。结果就是「动是动了，但感觉散」：
 * 快慢不统一、方向不一致，看着就像拼起来的。
 *
 * 现在把时长、缓动、位移量全部收在这里，全 App 只认这一套：
 *
 *   时长    Instant 90 / Fast 150 / Normal 220 / Slow 320
 *   缓动    进入用「先快后慢」(decelerate)，退出用「先慢后快」(accelerate)
 *   位移    页面 1/28 屏高，卡片 1/6 自身高度，都很克制
 *
 * 三条原则：
 * 1. **进场慢一点、退场快一点** —— 退场拖沓会让人觉得卡。
 * 2. **位移要小** —— 超过 1/10 屏高的滑动会晃眼，手机上尤其明显。
 * 3. **同屏多个元素错开 45ms** —— 一起动像「闪一下」，错开才有层次。
 * ============================================================
 */
object Motion {

    // ---------- 时长（毫秒） ----------
    /** 按下、微反馈这种，几乎要「无感」 */
    const val Instant = 90

    /** 颜色、透明度变化 */
    const val Fast = 150

    /** 常规尺寸/位置变化 */
    const val Normal = 220

    /** 整页切换这种大范围位移 */
    const val Slow = 320

    /** 同屏多个元素之间的错开间隔 */
    const val StaggerStep = 45L

    /** 最多错开到第几项（再多就不加了，避免最后几项等太久） */
    const val MaxStaggerIndex = 7

    // ---------- 缓动 ----------
    /** 进出场的标准缓动：起步快、收尾稳 */
    val standard: Easing = FastOutSlowInEasing

    /** 入场专用：减速进入，落位更自然 */
    val decelerate: Easing = LinearOutSlowInEasing

    /** 退场专用：加速离开，显得利落 */
    val accelerate: Easing = FastOutLinearInEasing

    /** 轻微的「过冲」缓动，用在强调性元素上 */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ---------- 常用规格 ----------
    fun <T> fast(): FiniteAnimationSpec<T> = tween(durationMillis = Fast, easing = standard)

    fun <T> normal(): FiniteAnimationSpec<T> = tween(durationMillis = Normal, easing = standard)

    fun <T> slow(): FiniteAnimationSpec<T> = tween(durationMillis = Slow, easing = decelerate)

    /** 带回一点弹性的弹簧，用在 Tab 选中、按钮回弹 */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 平滑弹簧，用在尺寸/位置这类不该弹的地方 */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow
    )

    // ---------- 页面转场 ----------
    // 新页面：淡入 + 从下往上浮 1/28 屏高。
    // 位移故意做得很小 —— 手机上滑太多会晃眼，只要够让人「顺着动效锁定新页面」就行。
    val pageEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = Normal, easing = decelerate)) +
            slideInVertically(
                animationSpec = tween(durationMillis = Slow, easing = decelerate)
            ) { height -> height / 28 }

    // 退场：快速淡出，不做位移（退了就别拖泥带水）
    val pageExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = Fast, easing = accelerate))

    // 返回时：新页面从上方微落，退出的页面往下微沉 —— 方向感是「回去」
    val popEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = Normal, easing = decelerate)) +
            slideInVertically(
                animationSpec = tween(durationMillis = Slow, easing = decelerate)
            ) { height -> -height / 28 }

    val popExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = Fast, easing = accelerate)) +
            slideOutVertically(
                animationSpec = tween(durationMillis = Normal, easing = accelerate)
            ) { height -> height / 40 }

    // ---------- 二级页面切换（设置页里列表 ↔ 详情） ----------
    val detailEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = Normal, easing = decelerate)) +
            slideInVertically(
                animationSpec = tween(durationMillis = Slow, easing = decelerate)
            ) { height -> height / 20 }

    val detailExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = Fast, easing = accelerate))
}

// ============================================================
// 按压反馈
// ============================================================

/**
 * 按下时轻微缩小到 96%。
 *
 * 以前按钮只有系统自带的涟漪（很多自定义 Box 按钮根本看不到涟漪），
 * 按下去没有任何反应，像点在一张图上。
 * 现在换成「轻微缩小 + 回弹」，和 Tab 图标的弹簧手感一致。
 *
 * 用法：把原来的 `.clickable { ... }` 换成 `.pressable { ... }`。
 * 注意这会去掉涟漪（indication = null），缩放本身就是反馈。
 */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = androidx.compose.runtime.remember {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}
