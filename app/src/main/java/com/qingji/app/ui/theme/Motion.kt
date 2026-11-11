package com.qingji.app.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
 *   位移    卡片 1/6 自身高度；**页面转场零位移**（见 101 行说明）
 *
 * 三条原则：
 * 1. **进场慢一点、退场快一点** —— 退场拖沓会让人觉得卡（仅用于非页面元素）。
 * 2. **位移要小** —— 超过 1/10 屏高的滑动会晃眼；底部 Tab 切换已改成零位移。
 * 3. **同屏多个元素错开 20ms** —— 一起动像「闪一下」，错开才有层次。
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

    /**
     * 同屏多个元素之间的错开间隔。
     * tabfix：原 45ms × 最多 7 项 = 315ms，再加 320ms 入场，
     * 页面切完近半秒内容还在陆续冒出来，和页面转场叠在一起就是「抖」。
     */
    const val StaggerStep = 20L

    /** 最多错开到第几项（再多就不加了，避免最后几项等太久） */
    const val MaxStaggerIndex = 4

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
    // tabfix：底部 Tab 切换改成「原地淡入淡出」。
    //
    // 旧参数：进场 fadeIn 220 + slideInVertically 320（1/28 屏高），
    //         退场只有 fadeOut 150 且完全没有位移。
    // 三个问题叠在一起就是录屏里的抖动：
    //   1) 旧页 150ms 就淡没了，新页还要再滑 170ms，中间露出底层背景 → 闪一下；
    //   2) 进入有位移、退出没有，两页在转场期间位置错开 → 重影；
    //   3) slide 的位移量按「内容区高度 / 28」算，而 Scaffold 的 insets
    //      在切换瞬间会变（见 QingJiAppRoot），高度一变目标位移也跟着变 → 跳。
    //
    // 现在：进出同为 150ms 线性淡变、零位移、零缩放、无弹簧过冲，
    // 两页始终占同一块区域；pop（返回）也复用同一套，方向感不做特殊处理。
    /** 页面淡变统一用线性缓动：进出曲线完全对称，叠加时亮度不塌。 */
    private val pageFade: Easing = LinearEasing

    val pageEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = Fast, easing = pageFade))

    val pageExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = Fast, easing = pageFade))

    // 返回时不再做「从上方落回 / 往下沉」的方向动画，进出节奏完全一致。
    val popEnter: EnterTransition = pageEnter
    val popExit: ExitTransition = pageExit

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
