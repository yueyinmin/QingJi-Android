package com.qingji.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

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
 * 悬浮底栏 v7（2026-11-14 主人按记账 App 参考图重做）：
 * 全圆角白色胶囊 Dock，左侧四个选项、右侧一颗绿色「+ 记一笔」大按钮。
 * 拥挤和「绿色方块阴影」两个问题一起改：
 * 1. 未选中的选项只留图标，选中的才展开文字 —— 四个槽位一下松了；
 * 2. 选中指示器收成贴合内容的全圆角浅绿胶囊，不再是占满整格的 12dp 方角块；
 * 3. Dock 改回胶囊形，阴影减到 6dp，选项缩放弹跳从 1.06 收到 1.02。
 * 布局仍是定宽几何计算：内边距 8dp×2 + 右侧按钮 88dp + 间隙 10dp，
 * 其余宽度均分给 Tab，指示器槽位不依赖子项测量。
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
 * previewRoute 只是底栏选中态的预览，松手才提交一次。手指划过四个图标时
 * 不会连续加载页面，也不会和转场动画打架。
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
    ) {
        val activeSlot = tabs.indexOfFirst { it.route == displayRoute }.coerceAtLeast(0)
        // 与下面 Row 的 padding(8dp×2) / 按钮 80dp / 间隙 12dp 严格对齐。
        val slotWidth = (maxWidth - 16.dp - 80.dp - 12.dp) / tabs.size
        val animatedSlot by animateFloatAsState(
            targetValue = activeSlot.toFloat(),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
            label = "floatIndicator"
        )
        val pillWidth = slotWidth - 2.dp
        Box(
            modifier = Modifier
                .offset(
                    x = 8.dp + slotWidth * animatedSlot + 1.dp,
                    y = 11.dp
                )
                .width(pillWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreenLight.copy(alpha = 0.7f))
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
                // UI-NAV-03：手势只挂内容区，不覆盖页面滚动区，也不吞普通点击。
                .pointerInput(tabs, currentRoute) {
                    // 坐标系是 padding 之后的内容区：四个 Tab 均分，右侧再留 80dp 按钮 + 12dp 间隙。
                    fun hitRoute(pos: Offset): String? {
                        val buttonPx = 80.dp.toPx()
                        val gapPx = 12.dp.toPx()
                        val tabAreaPx = size.width - buttonPx - gapPx
                        if (tabAreaPx <= 0f) return null
                        if (pos.y < 0f || pos.y > size.height) return null
                        if (pos.x < 0f || pos.x >= tabAreaPx) return null
                        val index = (pos.x / (tabAreaPx / tabs.size))
                            .toInt().coerceIn(0, tabs.size - 1)
                        return tabs[index].route
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
                            // 松手提交：只切一次，目标必须是四个有效导航项之一。
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
            tabs.forEach { tab ->
                FloatingTabItem(
                    tab = tab,
                    selected = displayRoute == tab.route,
                    onClick = {
                        if (suppressClick) suppressClick = false else onTabSelected(tab)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.width(12.dp))
            // 手指落在「记一笔」上松手属于取消选择，绝不能弹出记录面板。
            AddPillButton(onClick = {
                if (suppressClick) suppressClick = false else onAddClick()
            })
        }
    }
}

/** 悬浮款的选项：未选中只留图标，选中才展开文字；v9 收小内容，给胶囊留出余量。 */
@Composable
private fun FloatingTabItem(
    tab: QingJiTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    // v7：弹跳幅度 1.06→1.02。之前整组等比放大时绿底跟着晃，
    // 是「绿色阴影」观感的另一半来源，这里只留一点点落位反馈。
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = if (selected) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        } else {
            tween(durationMillis = com.qingji.app.ui.theme.Motion.Fast)
        },
        label = "floatTabBounce"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) QingJiGreen else TextSecondary,
        animationSpec = com.qingji.app.ui.theme.Motion.fast(),
        label = "floatTabTint"
    )
    Row(
        modifier = modifier
            .height(40.dp)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = com.qingji.app.ui.i18n.s(tab.label),
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(150)) + expandHorizontally(animationSpec = tween(180)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(animationSpec = tween(150))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = com.qingji.app.ui.i18n.s(tab.label),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

/** 右侧的「+ 记一笔」：绿色胶囊大按钮，点了弹记录面板（和经典款中央加号同一个入口）。 */
@Composable
private fun AddPillButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            // v9：88→80dp，再给四个 Tab 让出 8dp，选中项与胶囊之间留出余量。
            .width(80.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(QingJiGreenDark)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = com.qingji.app.ui.i18n.s("记一笔"),
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = com.qingji.app.ui.i18n.s("记一笔"),
            fontSize = 11.sp,
            lineHeight = 14.sp,
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