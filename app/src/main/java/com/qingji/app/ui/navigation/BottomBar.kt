package com.qingji.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.prefs.QingJiSettings
import com.qingji.app.ui.theme.AccentThemes
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.QingJiThemeState
import com.qingji.app.ui.theme.TextPrimary
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

/** 选中态展开动画统一 200ms（工单要求保留原节奏）。 */
private const val SelectMotionMs = 200

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
 * 悬浮款内部布局 + UI-NAV-03 长按拖动导航。
 *
 * 状态分两层：currentRoute 由 NavController 管理（真实页面），拖动期间绝不改；
 * previewRoute 只是底栏选中态的预览，松手才提交一次。
 * 预览态和普通选中态走同一个 displayRoute → selected 分支，所以长按拖动时
 * 胶囊的尺寸变化与点击完全一致，不存在两套视觉效果。
 */
@Composable
private fun FloatingBarSlots(
    tabs: List<QingJiTab>,
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit
) {
    var previewRoute by remember { mutableStateOf<String?>(null) }
    // 长按拖动结束后，子项 clickable 可能补发一次点击；用这个开关吞掉，
    // 下一次新手势开始时自动复位，不会误伤后续普通点击。
    var suppressClick by remember { mutableStateOf(false) }
    val displayRoute = previewRoute ?: currentRoute
    val haptic = LocalHapticFeedback.current

    // 每个槽位的权重各自动画；总和恒定，底栏总宽不变、不会跳动。
    val weights = ArrayList<Float>(tabs.size)
    for (tab in tabs) {
        weights += animatedTabWeight(displayRoute == tab.route)
    }
    // pointerInput 的 lambda 不随重组重建，用 State 把最新权重送进手势协程。
    val weightsState = rememberUpdatedState(weights)

    // 选中态配色跟着当前主题走（v12 恢复换肤联动），松绿那套仍是精确值。
    val pillBg = dockPillBackground()
    val pillContent = dockPillContent()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(DockHeight)) {
        // 窄屏（<360dp）先收留白与间距，再压按钮宽度；图标和文字一律不缩。
        val narrow = maxWidth < 360.dp
        val hPad = if (narrow) DockHorizontalPaddingNarrow else DockHorizontalPadding
        val addWidth = if (narrow) AddButtonWidthNarrow else AddButtonWidth
        val gap = if (narrow) AddButtonGapNarrow else AddButtonGap

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = hPad, end = hPad)
                // UI-NAV-03：手势只挂内容区，不覆盖页面滚动区，也不吞普通点击。
                // 按钮宽度和间距进 key：换屏宽导致它们变化时手势协程按新值重建，
                // 保证「视觉已切换、拖动却命中上一个 Tab」不会发生。
                .pointerInput(tabs, currentRoute, addWidth, gap) {
                    // 命中区按「和视觉完全相同的权重」分段，而不是简单四等分。
                    // 否则图标随权重平滑移动后，手指按图标位置拖动会判错 Tab。
                    fun hitRoute(pos: Offset): String? {
                        val tabAreaPx = size.width - addWidth.toPx() - gap.toPx()
                        if (tabAreaPx <= 0f) return null
                        if (pos.y < 0f || pos.y > size.height) return null
                        if (pos.x < 0f || pos.x >= tabAreaPx) return null
                        val w = weightsState.value
                        val total = w.sum()
                        if (total <= 0f) return null
                        var right = 0f
                        for (i in tabs.indices) {
                            right += tabAreaPx * w[i] / total
                            if (pos.x < right) return tabs[i].route
                        }
                        return tabs.last().route
                    }
                    awaitEachGesture {
                        // 新手势开始先复位，保证上一次拖动不会吞掉这次普通点击。
                        suppressClick = false
                        // 不消费 down：短按仍落到子项 clickable，普通点击行为零改动。
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startRoute = hitRoute(down.position)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) return@awaitEachGesture

                        // 长按约 400ms 成功：进入拖动预览 + 轻微触觉反馈。
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        suppressClick = true
                        previewRoute = startRoute ?: currentRoute
                        var finalRoute: String? = previewRoute
                        try {
                            drag(down.id) { change ->
                                val target = hitRoute(change.position)
                                previewRoute = target   // null = 按钮区 / 栏外，预览回到当前页
                                finalRoute = target
                            }
                            // 松手提交：只切一次，目标必须是有效导航项之一。
                            // 拖到「记一笔」或栏外时 finalRoute 为 null，本次选择取消。
                            if (finalRoute != null && finalRoute != currentRoute) {
                                tabs.firstOrNull { it.route == finalRoute }?.let(onTabSelected)
                            }
                        } finally {
                            // 正常松手或手势被系统中断，都恢复真实页面的选中态；
                            // 子项补发的点击由 suppressClick 拦截，不会重复跳转。
                            previewRoute = null
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in tabs.indices) {
                val tab = tabs[i]
                FloatingTabItem(
                    tab = tab,
                    selected = displayRoute == tab.route,
                    pillBackground = pillBg,
                    pillContent = pillContent,
                    onClick = {
                        if (suppressClick) suppressClick = false else onTabSelected(tab)
                    },
                    modifier = Modifier.weight(weights[i])
                )
            }
            Spacer(Modifier.width(gap))
            // 手指落在「记一笔」上松手属于取消选择，绝不能弹出记录面板。
            AddPillButton(
                width = addWidth,
                onClick = {
                    if (suppressClick) suppressClick = false else onAddClick()
                }
            )
        }
    }
}

/** 单个槽位的权重动画：选中展开、取消收窄，与胶囊尺寸动画同一套参数。 */
@Composable
private fun animatedTabWeight(selected: Boolean): Float {
    val weight by animateFloatAsState(
        targetValue = if (selected) SelectedTabWeight else NormalTabWeight,
        animationSpec = tween(durationMillis = SelectMotionMs),
        label = "tabWeight"
    )
    return weight
}

/**
 * 悬浮款选项（v12）：未选中只有 22dp 灰色图标、无背景；选中展开成
 * 46dp 高、最宽 82dp 的浅色胶囊，21dp 图标 + 13sp 文字横排。
 *
 * 胶囊宽度不再由内边距反推，而是「铺满槽位、封顶 82dp」：槽位宽度由动态权重
 * （选中 1.5 : 其余 1.0）决定，393dp 屏约 78dp、412dp 及以上封顶 82dp，
 * 正好落在工单要求的 76~82dp。窄屏放不下时优先收内边距，图标与文字不缩。
 */
@Composable
private fun FloatingTabItem(
    tab: QingJiTab,
    selected: Boolean,
    pillBackground: Color,
    pillContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .height(DockHeight)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // 内容自然宽：21dp 图标 + 5dp 间距 + 两个汉字(13sp≈26dp) = 52dp。
        val contentWidth = PillIconSize + PillIconTextGap + 26.dp
        // 上限：铺满槽位（左右各留 1dp 呼吸位），但不超过 82dp。
        val fitWidth = minOf(PillMaxWidth, maxWidth - 2.dp)
        // 下限：内容 + 8dp 内边距；槽位实在太小就以槽位为准，宁可收内边距也不裁字。
        val minWidth = minOf(contentWidth + PillHorizontalPadding, maxWidth)
        val targetPillWidth = if (selected) maxOf(fitWidth, minWidth) else 22.dp

        val pillWidth by animateDpAsState(
            targetValue = targetPillWidth,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "pillWidth"
        )
        val pillHeight by animateDpAsState(
            targetValue = if (selected) PillHeight else 22.dp,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "pillHeight"
        )
        val bgAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "pillBgAlpha"
        )
        val iconSize by animateDpAsState(
            targetValue = if (selected) PillIconSize else NormalIconSize,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "tabIconSize"
        )
        val tint by animateColorAsState(
            targetValue = if (selected) pillContent else TextSecondary,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "tabTint"
        )
        val textColor by animateColorAsState(
            targetValue = pillContent,
            animationSpec = tween(durationMillis = SelectMotionMs),
            label = "tabText"
        )

        // 底层胶囊：形状恒定 50% 圆角，宽度/高度/背景透明度同时动画，
        // 任何中间帧都是完整胶囊，不会变矩形或出现圆角突变。
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(pillHeight)
                .clip(DockPillShape)
                .background(pillBackground.copy(alpha = bgAlpha))
        )
        // 上层内容始终垂直居中，图标中心不随展开上下移动。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = tab.icon,
                contentDescription = com.qingji.app.ui.i18n.s(tab.label),
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
            AnimatedVisibility(
                visible = selected,
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
                        color = textColor,
                        maxLines = 1
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

/** 一条很细的分割线，页面里到处要用。 */
@Composable
fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerColor)
    )
}

/** 通用文字样式用不到时留个口子，方便以后统一改色。 */
val QingJiTextPrimary = TextPrimary