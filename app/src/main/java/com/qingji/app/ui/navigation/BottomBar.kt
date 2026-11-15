package com.qingji.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.ui.theme.AccentThemes
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.QingJiThemeState
import com.qingji.app.ui.theme.TextSecondary

/**
 * 悬浮底栏的胶囊形状规范：选中导航项与「记一笔」按钮共用同一个 shape。
 * RoundedCornerShape(50%) 等价于 CircleShape —— 圆角半径取短边的一半，
 * 宽高比再怎么变都是完整胶囊，展开/收缩的中间帧也不会出现方角或椭圆。
 */
private val DockPillShape = RoundedCornerShape(percent = 50)

/**
 * 选中胶囊与「记一笔」的配色（v12 起恢复换肤联动）。
 *
 * 取值走 AccentThemes.dockSoftOf / dockContentOf：五套主题各用自己的一套，
 * 松绿那套仍是主人指定的精确值 #E5F3EF / #23866B（写在 Spec.dockSoft/dockContent 里），
 * 深色模式自动换成各主题的深色档，不再像写死色值时那样在暗底上刺眼。
 */
@Composable
private fun dockPillBackground(): Color =
    AccentThemes.dockSoftOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)

@Composable
private fun dockPillContent(): Color =
    AccentThemes.dockContentOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)

/** 底栏几何：Dock 高度固定，切换页面与选中态变化都不改它，页尾让位也不用动。 */
private val DockHeight = 62.dp

/**
 * 底栏左右留白 / 「记一笔」宽度 / 间距：常规一套、窄屏一套。
 * 工单要求「窄屏优先减少胶囊内边距和导航间距，其次适当压缩记一笔宽度」，
 * 所以 <360dp 时先收留白 10→6dp、间距 8→6dp，再把按钮 116→104dp，
 * 图标和文字一个都不缩。
 *
 * v12.1（真机实测后微调）：外层 Dock 还有 16dp 外边距，内层若按 14dp 算，
 * 411dp 屏上胶囊只有 72dp，达不到工单 76~82dp 的目标；内层收到 10dp、
 * 间距收到 8dp 后同一块屏实测 76dp，正好进区间，图标与文字仍未缩。
 */
private val DockHorizontalPadding = 10.dp
private val DockHorizontalPaddingNarrow = 6.dp
private val AddButtonWidth = 116.dp
private val AddButtonWidthNarrow = 104.dp
private val AddButtonHeight = 48.dp
private val AddButtonGap = 8.dp
private val AddButtonGapNarrow = 6.dp

/** 选中胶囊（v12 缩小）：46dp 高，宽度跟着槽位走、封顶 82dp。 */
private val PillHeight = 46.dp
private val PillMaxWidth = 82.dp
private val PillHorizontalPadding = 8.dp
private val PillIconSize = 21.dp
private val NormalIconSize = 22.dp
private val PillIconTextGap = 5.dp

/**
 * 选中项拿走更多横向空间、未选中项收窄让位（v12 由 2.6 : 1 收到 1.5 : 1）。
 * 四项权重总和恒定，所以「旧项收窄 + 新项展开」同步发生，底栏总宽不变。
 */
private const val SelectedTabWeight = 1.5f
private const val NormalTabWeight = 1f
// 窄屏保底：选中槽位目标宽（胶囊 76dp + 2dp 呼吸位 + 1dp 抗锯齿余量）与其权重上限（v12.2）。
// 78 实测会掉到 75.8dp（差 1 像素），抬到 79 后稳定落在 76.6dp。
private const val PillTargetSlot = 79f
private const val MaxSelectedTabWeight = 2.8f

/** 选中态展开动画统一 200ms（工单要求保留原节奏）。 */
private const val SelectMotionMs = 200

// ============================================================
// v13 dockref：手势与吸附参数
// ============================================================

/**
 * 长按激活阈值。工单要求 180~220ms，取中值 200ms。
 * 系统默认（ViewConfiguration.longPressTimeoutMillis）是 400ms，钝一倍，
 * 所以才自己实现计时（见 awaitDockLongPress）。
 */
private const val DockLongPressMs = 200L

/** 拖动激活瞬间胶囊「被拿起」的轻微缩放，松手复位。参数刻意很小，不要弹跳感。 */
private const val DragLiftScale = 1.06f

/**
 * 拖动期间胶囊收窄到的宽度。
 *
 * 为什么必须收窄：常态胶囊是 82dp（图标+两个汉字），而拖动经过的槽位只有 50dp 左右。
 * 82dp 的胶囊滑过去会同时压住旁边两枚图标，看起来像一块绿布在飘；收窄到 54dp
 * 正好卡在两枚 22dp 图标之间，是「一个指示器在图标下方滑过」的观感。
 */
private val DragPillWidth = 54.dp

/** 宽胶囊 ⇄ 小胶囊的交叉淡变时长。只在开始拖 / 松手两个瞬间发生，拖动过程中宽度恒定。 */
private const val DragCondenseMs = 130

/** 拖动时文字淡出的时长。位置不动、只淡内容，避免图标被文字消失拽着横跳。 */
private const val DragLabelFadeMs = 120

/** 高亮项图标的轻微放大，同样是绘制阶段缩放，不参与布局。 */
private const val HighlightedIconScale = 1.06f

/** 胶囊容纳「图标+间距+两个汉字+内边距」所需的最小宽度，窄屏兜底用。 */
private val PillContentWidth = NormalIconSize + PillIconTextGap + 26.dp + PillHorizontalPadding

/**
 * 松手吸附 / 点击归位的弹簧：阻尼 0.9 几乎不过冲，只留一点点收尾的柔。
 * 工单明确要求「低振荡 Spring」——不能用 0.5 那种会来回晃的。
 */
private fun dockSnapSpec() = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

/**
 * 底部导航栏（两条皮肤共用一套内部结构）
 *
 * 经典款 QingJiBottomBar：通栏 Surface + 顶部投影，贴着屏幕底边。
 * 悬浮款 QingJiFloatingBar：左右留边、圆角 Dock、自身投影、底部悬空。
 * 开关在「设置 → 显示与外观 → 悬浮底栏」，默认关（经典款）。
 *
 * barfx（2026-11-14 主人指定）：
 * 1. 「选中时的阴影」不再由每个 Tab 自己画背景胶囊（瞬现/瞬失，上次还改过 snap），
 *    改成一条**共享的绿色胶囊指示器** —— 点别的 Tab，它从旧槽位平滑滑到新槽位；
 * 2. 目标 Tab 的图标+文字用带过冲的弹簧放大（先冲过目标值再回落），最后停在当前项；
 * 3. 取消选中的旧图标走 150ms tween 平滑缩回，不跟着弹。
 */

/** 槽位列表：null = 中央加号。加号永远占中间一格，Tab 按设置拼在两侧。 */
private fun buildBarSlots(settings: QingJiSettings): List<QingJiTab?> {
    val left = listOfNotNull(
        QingJiTab.Home,
        QingJiTab.Progress.takeIf { settings.showProgressTab }
    )
    val right = listOfNotNull(
        QingJiTab.Diary.takeIf { settings.showDiaryTab },
        QingJiTab.Mine.takeIf { settings.showMineTab }
    )
    return left + listOf<QingJiTab?>(null) + right
}

private fun activeBarSlot(slots: List<QingJiTab?>, currentRoute: String): Int =
    slots.indexOfFirst { it?.route == currentRoute }.coerceAtLeast(0)

@Composable
fun QingJiBottomBar(
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit,
    settings: QingJiSettings = QingJiSettings()
) {
    val slots = buildBarSlots(settings)
    Surface(color = CardWhite, shadowElevation = 8.dp) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            BarSlots(
                slots = slots,
                activeSlot = activeBarSlot(slots, currentRoute),
                currentRoute = currentRoute,
                barHeight = 60.dp,
                onTabSelected = onTabSelected,
                onAddClick = onAddClick
            )
        }
    }
}

/**
 * 悬浮底栏 v11（选中项尺寸强化）：
 * 全圆角白色胶囊 Dock，左侧四个选项、右侧一颗绿色「+ 记一笔」大按钮。
 * 1. 未选中只有 22dp 灰色图标、无背景；选中展开成 52dp 浅薄荷绿胶囊，
 *    24dp 图标 + 14sp 文字横排，明显比未选中大；
 * 2. 两项共用 DockPillShape（RoundedCornerShape(50%)），只靠深浅分层级；
 * 3. 四个 Tab 用动态权重：选中项拿更多横向空间，其余收窄让位，
 *    这样 52dp 大胶囊既放得下文字，也不遮挡别的图标、不挤压按钮。
 *
 * Dock 高度固定 62dp、按钮固定 80×42dp，所有放大都发生在容器内部，
 * 页面切换和选中态变化都不会撑高底栏或让页尾让位失效。
 */
@Composable
fun QingJiFloatingBar(
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit,
    settings: QingJiSettings = QingJiSettings()
) {
    // 悬浮款没有中央加号槽——「记一笔」独立成右侧按钮，Tab 列表不含 null。
    val tabs = buildBarSlots(settings).filterNotNull()
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                // v8：主人反馈「底栏太低」，整条 Dock 上提 10dp。
                // 四个一级页的页尾让位同步 106→116dp（首页 / 我的 / 进展 / 日记两个 Tab）。
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
                .fillMaxWidth()
                // v9：阴影 6→4dp，白条更轻，不再像一块压在页面上的方牌。
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(31.dp))
                .clip(RoundedCornerShape(31.dp))
                .background(CardWhite)
        ) {
            FloatingBarSlots(
                tabs = tabs,
                currentRoute = currentRoute,
                onTabSelected = onTabSelected,
                onAddClick = onAddClick
            )
        }
    }
}

/**
 * 悬浮款内部布局 + 长按拖动导航（v13 dockref：交互与动画重构）。
 *
 * 旧实现（v12）的根本问题：把「选中背景」做成了**每个 Tab 自己的背景**，
 * 拖动时改的是路由 → 四条权重同时跑 200ms tween → 整条 Row 每帧全量重排，
 * 胶囊永远慢手指 200ms 才到位。所谓「不跟手」，就是这么来的。
 *
 * 新版把三件事彻底拆到三条互不相干的通道上：
 *   1. **布局**（槽位宽窄）——唯一驱动是 selIndex，读在**测量阶段**（见下方 Layout 的
 *      measure 块）。换选中项时只重跑 measure/place，一次重组都不发生；
 *      拖动期间根本不碰它 → 槽位完全冻结，图标不会在手指底下乱跑。
 *   2. **胶囊位置**——独立 pillX。拖动时 snapTo（直接坐标映射，零动画），
 *      松手才 animateTo（低振荡 spring 吸附）。读在 graphicsLayer 的绘制块里，
 *      只重绘，不重组、不重排。
 *   3. **高亮**（图标配色与缩放）——previewIndex，只进配色和 graphicsLayer 缩放，
 *      永远不进布局。
 *
 * 三条通道没有任何共享状态，所以「图标 / 文字 / 选中背景动画不同步」从结构上不可能发生。
 */
@Composable
private fun FloatingBarSlots(
    tabs: List<QingJiTab>,
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit
) {
    val pillBg = dockPillBackground()
    val pillContent = dockPillContent()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    // 手势协程不会因为换页而重建（key 里没有路由），所以它拿到的 activeIndex 是旧值。
    // 用 rememberUpdatedState 让它每次都读到最新的。
    val latestActive by rememberUpdatedState(activeIndex)
    // 同样的坑还有回调本身：onTabSelected 是 QingJiAppRoot 每次组合新建的 lambda，
    // 里面闭包着**当时的** currentRoute。而手势协程的 key 里没有路由、不会重建，
    // 所以它会一直握着首次组合那个 lambda —— 那个 lambda 认为「当前页永远是首页」，
    // 于是「拖回首页」会被判成「已经在首页」直接忽略。
    // 实测现象：右拖正常、左拖回首页永远无效。用 rememberUpdatedState 取最新实例。
    val latestOnTabSelected by rememberUpdatedState(onTabSelected)

    /** 拖动高亮目标；-1 = 没在拖动。 */
    var previewIndex by remember { mutableIntStateOf(-1) }
    var dragging by remember { mutableStateOf(false) }
    /**
     * 状态源分工（回归工单要求，四个概念不再混在一起）：
     * - currentTab  = activeIndex，只来自 currentRoute，代表**真实页面**；
     * - pressedTab  = pressedIndex，手指刚按下、还没判定成长按时的轻反馈；
     * - draggingTab = previewIndex，拖动经过的槽位，只做预选；
     * - selectedTab = 松手确认后由 onTabSelected 写回路由，再由 currentRoute 回来。
     * 三者都是临时态，手势一结束（含取消/系统中断）全部清零，不留残留选中效果。
     */
    var pressedIndex by remember { mutableIntStateOf(-1) }
    // 长按拖动结束后子项 clickable 可能补发一次点击，用它吞掉；新手势开始自动复位。
    var suppressClick by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(DockHeight)) {
        // 窄屏（<360dp）先收留白与间距，再压按钮宽度；图标和文字一律不缩。
        val narrow = maxWidth < 360.dp
        val hPad = if (narrow) DockHorizontalPaddingNarrow else DockHorizontalPadding
        val addWidth = if (narrow) AddButtonWidthNarrow else AddButtonWidth
        val gap = if (narrow) AddButtonGapNarrow else AddButtonGap

        // 四个槽位能分到的总宽（按钮和间距已经扣掉）。
        val tabArea = maxWidth - hPad * 2 - addWidth - gap
        val selWeight = selectedTabWeightFor(tabArea, tabs.size - 1)
        // 每个槽位「被选中时」的宽度都是同一个值，所以胶囊宽度是常量 —— 不需要宽度动画。
        // 宽度一变就要重新测量，那正是旧版每帧卡顿的来源之一。
        val slotW = tabArea * selWeight / (selWeight + (tabs.size - 1))
        val pillW = minOf(PillMaxWidth, slotW - 2.dp)
            .coerceAtLeast(minOf(PillContentWidth, slotW))

        val tabAreaPx = with(density) { tabArea.toPx() }
        val pillWPx = with(density) { pillW.toPx() }

        // ---------- 几何：全部是「进度 + 权重」的纯函数，不依赖实际测量 ----------
        // 所以组合阶段就能知道每个槽位中心，胶囊首帧直接落在正确位置，不会从 0 飞过来。
        fun widthOf(i: Int, progress: Float): Float {
            var sum = 0f
            for (k in tabs.indices) sum += weightAt(progress, k, selWeight)
            if (sum <= 0f) return 0f
            return tabAreaPx * weightAt(progress, i, selWeight) / sum
        }

        fun centerOf(i: Int, progress: Float): Float {
            var x = 0f
            for (k in 0 until i) x += widthOf(k, progress)
            return x + widthOf(i, progress) / 2f
        }

        fun indexAt(x: Float, progress: Float): Int {
            var acc = 0f
            for (k in tabs.indices) {
                acc += widthOf(k, progress)
                if (x < acc) return k
            }
            return tabs.lastIndex
        }

        // 归位目标按「最终权重」算，和松手后布局的终点严格一致，落位不会差半格。
        // 这里存的是「胶囊中心」而不是左边缘：拖动时胶囊会收窄成小胶囊，
        // 若按左边缘定位，收窄那一瞬中心会平移半个差值，看起来像被人拽了一下。
        val restCenter = centerOf(activeIndex, activeIndex.toFloat())
        val dragWPx = with(density) { DragPillWidth.toPx() }

        val selIndex = remember { Animatable(activeIndex.toFloat()) }
        // 胶囊中心用普通 Float 状态，不用 Animatable：
        // awaitEachGesture 的 lambda 是 restricted suspension，
        // 里面只能调 AwaitPointerEventScope 的成员，调不了 Animatable.snapTo。
        // 直接写状态既绕开了这个限制，也让「拖动跟手」变成一次纯赋值 —— 最快的写法。
        val pillCenter = remember { mutableFloatStateOf(restCenter) }
        val pillLift = remember { Animatable(1f) }
        // 0 = 常态宽胶囊，1 = 拖动小胶囊。两种胶囊的宽度都是布局常量，
        // 「变窄」用交叉淡变表达 —— 于是整个拖动过程零测量、零重组。
        val compact = remember { Animatable(0f) }

        // 换选中项：布局进度动画（只驱动 measure，不驱动重组）
        LaunchedEffect(activeIndex) {
            selIndex.animateTo(activeIndex.toFloat(), tween(SelectMotionMs))
        }
        LaunchedEffect(dragging) {
            compact.animateTo(if (dragging) 1f else 0f, tween(DragCondenseMs))
        }
        // 唯一的归位入口：首次组合、换页、松手、取消、以及「开始新一次拖动」都汇到这里。
        // dragging 进 key：一开始拖就取消正在跑的吸附，松手再从当前值接着吸附过去。
        LaunchedEffect(restCenter, dragging) {
            if (dragging) return@LaunchedEffect
            if (pillCenter.floatValue == restCenter) return@LaunchedEffect
            animate(pillCenter.floatValue, restCenter, animationSpec = dockSnapSpec()) { value, _ ->
                pillCenter.floatValue = value
            }
        }

        // ---------- 1. 胶囊层：宽胶囊 + 拖动小胶囊，同一中心交叉淡变 ----------
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = hPad)) {
            // 常态胶囊（选中项 + 文字）。拖动开始时淡出，位置照样跟着走，不留在原地。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(pillW)
                    .height(PillHeight)
                    .graphicsLayer {
                        // 只在这里读 pillCenter / compact：属于绘制阶段，
                        // 拖动时每帧只重绘这一层，不触发重组、不触发重新测量。
                        translationX = pillCenter.floatValue - pillWPx / 2f
                        alpha = 1f - compact.value
                    }
                    .clip(DockPillShape)
                    .background(pillBg)
            )
            // 拖动小胶囊：宽度是常量，跟着手指 1:1 移动，滑过图标下方不压住邻居。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(DragPillWidth)
                    .height(PillHeight)
                    .graphicsLayer {
                        translationX = pillCenter.floatValue - dragWPx / 2f
                        val lift = pillLift.value
                        scaleX = lift
                        scaleY = lift
                        alpha = compact.value
                    }
                    .clip(DockPillShape)
                    .background(pillBg)
            )
        }

        // ---------- 2. Tab 层：宽度由权重决定，权重读在测量阶段 ----------
        Layout(
            content = {
                for (i in tabs.indices) {
                    val tab = tabs[i]
                    FloatingTabItem(
                        tab = tab,
                        // 文字是否参与布局只认真实页面：拖动期间它不变，
                        // 所以槽位宽度、图标位置在拖动全程一动不动。
                        expanded = currentRoute == tab.route,
                        // 拖动时只把文字「淡掉」，不撤掉它的占位 —— 否则图标会被
                        // 文字消失拽着往槽位中心横跳十几个 dp，长按那一瞬非常明显。
                        labelVisible = currentRoute == tab.route && !dragging,
                        // 高亮跟拖动预览：只影响配色和图标缩放，不影响布局
                        highlighted = (if (previewIndex >= 0) previewIndex else activeIndex) == i,
                        // 按压 / 预选两种临时反馈都和图标在同一个 Composable 里绘制，
                        // 由同一帧的同一组状态驱动 —— 不会再出现「图标已选中、背景还是灰色」。
                        pressed = pressedIndex == i && !dragging,
                        preview = dragging && previewIndex == i && i != activeIndex,
                        pillContent = pillContent,
                        onClick = {
                            if (suppressClick) suppressClick = false else onTabSelected(tab)
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hPad)
                // 手势只挂 Tab 内容区，不覆盖页面滚动区，也不吞普通点击。
                // key 里**没有** currentRoute —— 旧版把它放进来，换页瞬间手势协程被取消重建，
                // 正好发生在松手那一帧，是「快速拖动后行为诡异」的隐藏原因之一。
                .pointerInput(tabs, hPad, addWidth, gap) {
                    awaitEachGesture {
                        // 新手势开始复位，保证上一次拖动不会吞掉这次普通点击。
                        suppressClick = false
                        // 不消费 down：短按仍然落到子项 clickable，普通点击行为零改动。
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 按下先给一个「轻按压」反馈；落在 Tab 区域外就不给。
                        val downInStrip = down.position.x >= 0f &&
                            down.position.x < tabAreaPx &&
                            down.position.y >= 0f &&
                            down.position.y <= size.height.toFloat()
                        pressedIndex = if (downInStrip) indexAt(down.position.x, selIndex.value) else -1
                        if (!awaitDockLongPress(down, DockLongPressMs)) {
                            // 短按 / 提前划走：交还给子项 clickable，按压态立刻清掉
                            pressedIndex = -1
                            return@awaitEachGesture
                        }

                        // ===== 激活：触觉 + 「拿起」缩放 + 胶囊立刻贴到手指 =====
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        suppressClick = true
                        dragging = true
                        pressedIndex = -1   // 进入拖动后改由 preview 表达，按压态不再叠加
                        // 布局进度此刻是冻结值（拖动期间不写 selIndex），
                        // 所以命中分段和屏幕上的图标位置完全一致，不会出现「按着 A 跳到 B」。
                        val frozen = selIndex.value
                        val startIndex = indexAt(down.position.x, frozen)
                        previewIndex = startIndex
                        scope.launch {
                            pillLift.animateTo(
                                DragLiftScale,
                                spring(dampingRatio = 0.6f, stiffness = 520f)
                            )
                        }

                        // 边界按「图标中心」算，不是按胶囊边缘算：
                        // 小胶囊的中心跑到第一/最后一枚图标中心之外就没有意义了。
                        val lo = centerOf(0, frozen)
                        val hi = centerOf(tabs.lastIndex, frozen)
                        // 激活瞬间先把胶囊对准「抓起的那枚图标」，而不是手指落点：
                        // 用户可能按在文字上（偏中心十几个 dp），对准图标才像「把这一项拿起来」。
                        // 之后每一次 MOVE 才严格 1:1 跟手。
                        pillCenter.floatValue = centerOf(startIndex, frozen).coerceIn(lo, hi)
                        val stripHeight = size.height.toFloat()
                        fun hit(x: Float, y: Float): Int? =
                            if (x < 0f || x >= tabAreaPx || y < 0f || y > stripHeight) null
                            else indexAt(x, frozen)

                        var hovered: Int? = startIndex
                        // lifted = 正常抬手；false 表示手势被系统/其他检测器打断，本次取消
                        var lifted = false
                        // 记下真正松手那一刻的坐标（见 finally）
                        var upX = Float.NaN
                        var upY = Float.NaN
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) break
                                if (!change.pressed) {
                                    lifted = true
                                    upX = change.position.x
                                    upY = change.position.y
                                    break
                                }
                                if (change.isConsumed) break
                                // 直接坐标映射：不经过任何动画，手指到哪胶囊到哪。
                                // 这里绝不能用 animateTo / spring —— 那正是旧版慢 200ms 的原因。
                                pillCenter.floatValue = change.position.x.coerceIn(lo, hi)
                                val idx = hit(change.position.x, change.position.y)
                                if (idx != hovered) {
                                    hovered = idx
                                    previewIndex = idx ?: -1
                                }
                                change.consume()
                            }
                        } finally {
                            // 无论正常松手、取消还是系统中断，三个临时态全部清零，
                            // 不允许残留「图标已选中但页面没切」的效果。
                            previewIndex = -1
                            dragging = false
                            pressedIndex = -1
                            scope.launch {
                                pillLift.animateTo(
                                    1f,
                                    spring(dampingRatio = 0.7f, stiffness = 420f)
                                )
                            }
                            // 旧版直接用 hovered（最后一次 MOVE 的命中）作为最终目标：
                            // 快速甩动时 UP 的坐标可能已经越过槽位，或者 UP 落在无效区，
                            // 就会出现「拖到别的页、松手又回到原页」。
                            // 现在以**松手那一刻的坐标**重新算一次命中，取消则一律不提交。
                            val target = if (lifted && !upX.isNaN()) hit(upX, upY) else null
                            if (lifted && target != null && target != latestActive) {
                                latestOnTabSelected(tabs[target])
                            }
                        }
                    }
                }
        ) { measurables, constraints ->
            // 权重在这里读 —— 属于测量阶段。selIndex 变化只会让测量/放置重跑，
            // 不会让 RestartableGroup 重组，这是本版最关键的一处性能改动。
            val h = constraints.maxHeight
            val progress = selIndex.value
            val widths = FloatArray(measurables.size) { widthOf(it, progress) }
            val placeables = measurables.mapIndexed { i, measurable ->
                val w = widths[i].roundToInt().coerceAtLeast(1)
                measurable.measure(Constraints.fixed(w, h))
            }
            layout(constraints.maxWidth, h) {
                var x = 0
                for (p in placeables) {
                    p.placeRelative(x, 0)
                    x += p.width
                }
            }
        }

        // ---------- 3. 「记一笔」：独立成员，不参与胶囊的位置计算 ----------
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = hPad)
        ) {
            AddPillButton(
                width = addWidth,
                onClick = {
                    if (suppressClick) suppressClick = false else onAddClick()
                }
            )
        }
    }
}

/**
 * 槽位权重：以 [progress] 为中心的线性凸起。
 *
 * progress 正好落在某个槽位上时，该槽位拿满 [selectedWeight]，其余为 1；
 * 落在两项之间时，两个邻项各分一半。因为凸起是线性的，
 * **权重总和对任何 progress 都不变** —— 槽位只是重新分配宽度，底栏总宽恒定，
 * 所以布局动画期间不会出现整条底栏忽宽忽窄的呼吸感。
 */
private fun weightAt(progress: Float, i: Int, selectedWeight: Float): Float {
    val d = kotlin.math.abs(progress - i).coerceAtMost(1f)
    return 1f + (selectedWeight - 1f) * (1f - d)
}

/**
 * 自定义长按激活。
 *
 * 系统的 awaitLongPressOrCancellation 走 ViewConfiguration.longPressTimeoutMillis，
 * Android 默认 400ms —— 工单要求 180~220ms，钝了将近一倍，这就是「长按半天没反应」。
 * 这里自己计时：按满 [timeoutMs] 且位移没超过 touchSlop 才算激活。
 *
 * 返回 true = 进入拖动；false = 提前抬手或已经划走，交还给子项 clickable 处理。
 */
private suspend fun AwaitPointerEventScope.awaitDockLongPress(
    down: PointerInputChange,
    timeoutMs: Long
): Boolean {
    val slop = viewConfiguration.touchSlop
    // withTimeoutOrNull 超时返回 null → 一直按住没动 = 激活；
    // 提前退出（抬手 / 超 slop）会返回 true → 不激活。
    val aborted = withTimeoutOrNull<Boolean>(timeoutMs) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) return@withTimeoutOrNull true
            if ((change.position - down.position).getDistance() > slop) {
                return@withTimeoutOrNull true
            }
        }
        @Suppress("UNREACHABLE_CODE") true
    }
    return aborted == null
}

/**
 * 选中槽位的权重。
 *
 * 工单标准值是 1.5:1，但那是按 412dp 及以上屏宽定的：屏越窄，1.5 分到的槽位越少。
 * 实测（420dpi 模拟器改分辨率）：
 *   412dp → 胶囊 76.6dp（达标）　393dp → 70.1dp　360dp → 66.7dp　320dp → 54.9dp（都不达标）
 * 原因是可用宽度 = 屏宽 − 176dp 左右，选中只占其中的 1/3。
 *
 * 所以这里做「保底」处理：先用 1.5 试算，算出来不够 76dp 就按
 * 「选中槽位刚好 78dp（76 + 2dp 呼吸位）」反推权重，上限 2.8，其余槽位仍是 1。
 * 412dp 及以上算出来的权重本来就 ≤1.5，等于原样保持工单值，宽屏行为零变化。
 */
private fun selectedTabWeightFor(tabArea: Dp, normalCount: Int): Float {
    if (normalCount <= 0) return SelectedTabWeight
    if (tabArea.value <= PillTargetSlot) return MaxSelectedTabWeight
    val need = normalCount * PillTargetSlot / (tabArea.value - PillTargetSlot)
    return need.coerceIn(SelectedTabWeight, MaxSelectedTabWeight)
}

/**
 * 悬浮款选项（v13）。
 *
 * 旧版这个组件里同时跑了 7 个动画：宽、高、背景透明度、图标尺寸、图标色、文字色，
 * 外加 AnimatedVisibility 的横向展开 —— 全部 tween 200ms，起步时刻还不一样，
 * 合起来就是「图标、文字、选中背景各走各的」。
 *
 * 现在胶囊搬到外面统一管理，这里只剩两件事：
 *   1. 配色（[highlighted] → tint）：150ms 颜色渐变；
 *   2. 图标轻微放大（[highlighted] → emphasis）：读在 graphicsLayer 里，
 *      属于绘制阶段，不参与布局，也不触发重组。
 * 尺寸相关的东西一个都不剩 —— 槽位宽度由父级按权重测量，这里只负责填满。
 */
@Composable
private fun FloatingTabItem(
    tab: QingJiTab,
    expanded: Boolean,
    labelVisible: Boolean,
    highlighted: Boolean,
    /** 手指刚按下、还没进入拖动：很轻的按压反馈。 */
    pressed: Boolean,
    /** 拖动中经过且不是当前页：明确的预选反馈，刻意区别于最终选中的宽胶囊。 */
    preview: Boolean,
    pillContent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val tint by animateColorAsState(
        targetValue = if (highlighted) pillContent else TextSecondary,
        animationSpec = tween(durationMillis = SelectMotionMs),
        label = "tabTint"
    )
    val emphasis by animateFloatAsState(
        targetValue = if (highlighted) HighlightedIconScale else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "tabEmphasis"
    )
    // 只淡不撤：文字始终占着它那份宽度（expanded 才占），拖动时把 alpha 降到 0。
    // 这里刻意用 State 不做 by 解构 —— .value 读在 graphicsLayer 的绘制块里，
    // 淡出过程只重绘那一段文字，不触发重组，也不会牵动图标位置。
    val labelAlpha = animateFloatAsState(
        targetValue = if (labelVisible) 1f else 0f,
        animationSpec = tween(DragLabelFadeMs),
        label = "labelAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 按压 / 预选背景和图标在**同一个组件、同一帧**里绘制：
        // 旧版只有图标 tint 跟着 previewIndex 变，胶囊还在做交叉淡变，
        // 于是出现「图标已经选中、背景还是灰色」的不同步。
        // 预选用半透明浅色小底、按压用更淡的灰底，最终选中仍是外层宽胶囊，
        // 三种状态视觉上互不混淆。
        if (preview || pressed) {
            Box(
                modifier = Modifier
                    .size(width = if (preview) 54.dp else 46.dp, height = PillHeight)
                    .clip(DockPillShape)
                    .background(
                        if (preview) pillContent.copy(alpha = 0.20f)
                        else TextSecondary.copy(alpha = 0.10f)
                    )
            )
        }
        // 内容始终垂直居中，图标中心不随展开上下移动。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = tab.icon,
                contentDescription = com.qingji.app.ui.i18n.s(tab.label),
                tint = tint,
                modifier = Modifier
                    .size(NormalIconSize)
                    .graphicsLayer {
                        scaleX = emphasis
                        scaleY = emphasis
                    }
            )
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(SelectMotionMs)) +
                    expandHorizontally(animationSpec = tween(SelectMotionMs)),
                exit = fadeOut(tween(180)) + shrinkHorizontally(animationSpec = tween(180))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(PillIconTextGap))
                    Text(
                        text = com.qingji.app.ui.i18n.s(tab.label),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha.value }
                    )
                }
            }
        }
    }
}

/**
 * 右侧的「+ 记一笔」：深青绿完整胶囊，48dp 高、常规 116dp 宽（窄屏 104dp）。
 * v12 起它是底栏最醒目的操作 —— 加宽而不是单纯加高，横向占比明显超过导航项。
 */
@Composable
private fun AddPillButton(width: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .width(width)
            .height(AddButtonHeight)
            .clip(DockPillShape)
            .background(QingJiGreenDark)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = com.qingji.app.ui.i18n.s("记一笔"),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = com.qingji.app.ui.i18n.s("记一笔"),
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * 槽位渲染：一条共享的绿色胶囊指示器垫在底层，
 * 切换时从旧槽位滑到新槽位；图标和文字浮在上面。
 */
@Composable
private fun BarSlots(
    slots: List<QingJiTab?>,
    activeSlot: Int,
    currentRoute: String,
    barHeight: Dp,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val slotWidth = maxWidth / slots.size
        // 指示器位置用平滑弹簧滑动：弹簧自带速度曲线，比 tween 更「自然」。
        // 首帧直接落在当前槽位（animateFloatAsState 首次组合不打动画），不会开场乱飞。
        val animatedSlot by animateFloatAsState(
            targetValue = activeSlot.toFloat(),
            // v2：0.9/400 的软弹簧拖着走不跟手；0.85/900 滑得快、收得稳，
            // 既保留一点滑行的「溜过去」感，又不会让人觉得黏。
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
            label = "barIndicator"
        )
        // v2：胶囊收小成「图标底下的绿色阴影」——和最早的单 Tab 选中胶囊同款
        //（46×28、全圆角、QingJiGreenLight），切换时从旧图标底下溜到新图标底下。
        // 上一版是整槽大胶囊（约 54×46），宽高接近正方形，看起来像一块
        // 「方形阴影」，绿色糊满整格效果很差；小胶囊只衬图标，干净得多。
        // 纵向：胶囊中心对准图标中心（图标 + 2dp + 文字 垂直居中后，
        // 图标中心在槽位上沿往下约 21/23dp 处）。
        val pillWidth = 46.dp
        val pillHeight = 28.dp
        Box(
            modifier = Modifier
                .offset(
                    x = slotWidth * animatedSlot + (slotWidth - pillWidth) / 2,
                    y = 7.dp
                )
                .width(pillWidth)
                .height(pillHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreenLight)
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slots.forEach { slot ->
                if (slot == null) {
                    BarPlusSlot(onAddClick, Modifier.weight(1f))
                } else {
                    BarTabItem(slot, currentRoute, onTabSelected, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BarTabItem(
    tab: QingJiTab,
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = currentRoute == tab.route

    // 目标项「放大后缩小、最后停住」= 带过冲的弹簧（MediumBouncy ≈ dampingRatio 0.5）：
    // 先冲过 1.12 再回落落位；取消选中用 150ms tween 平滑缩回，旧图标不参与弹跳。
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = if (selected) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            tween(durationMillis = com.qingji.app.ui.theme.Motion.Fast)
        },
        label = "tabBounce"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) QingJiGreen else TextSecondary,
        animationSpec = com.qingji.app.ui.theme.Motion.fast(),
        label = "tabTint"
    )

    // v2：按压涟漪关掉 —— 方块形的灰波纹是「方形阴影」的另一个来源，
    // 而且和滑动的胶囊叠在一起显得乱。选中反馈 = 胶囊溜过来 + 图标弹一下，够了。
    val tabInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = tabInteraction,
                indication = null
            ) { onTabSelected(tab) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = com.qingji.app.ui.i18n.s(tab.label),
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = com.qingji.app.ui.i18n.s(tab.label),
            fontSize = 11.sp,
            // v5：行高必须钉死。中文在默认字体下行高实测 ~27dp，竖排内容被撑高，
            // 图标在绿色胶囊里整体偏上（主人反馈「图标没有居中在选中阴影中」）。
            // 16sp 行高 → 内容 22+2+16 = 40dp，图标中心正好落在胶囊中心。
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BarPlusSlot(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                // v2：去掉上浮。52dp 圆在 64dp Dock 里上浮 8dp，顶部会超出
                // Dock 圆角被裁掉（主人反馈「加号显示不全」），老老实实居中。
                .size(52.dp)
                .clip(CircleShape)
                .background(QingJiGreenDark)
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = com.qingji.app.ui.i18n.s("记一笔"),
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
