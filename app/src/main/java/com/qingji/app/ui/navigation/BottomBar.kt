package com.qingji.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * 悬浮底栏 v3（2026-11-14 主人给参考图定版）：
 * 白色胶囊 Dock，左侧四个「图标 + 文字」横排选项，右侧一颗绿色「+ 记一笔」大按钮；
 * 选中项底下垫一条浅绿胶囊，切换时从旧选项溜到新选项（与经典款同一套弹簧参数）。
 * 布局是定宽的：Dock 内边距 10dp + 右侧按钮 96dp + 间隙 8dp，剩下的宽度均分给 Tab，
 * 这样指示器的槽位是纯几何计算，不依赖子项测量。
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
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .fillMaxWidth()
                // v6：26dp 在 60dp 高的 Dock 上跟整圆（30dp）几乎没差别，
                // 主人看着仍是「完全圆的」；收到 16dp——四角是明确的圆角、
                // 上下有大段直边，和参考图的悬浮菜单一个观感。
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
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

@Composable
private fun FloatingBarSlots(
    tabs: List<QingJiTab>,
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val activeSlot = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
        val slotWidth = (maxWidth - 20.dp - 96.dp - 8.dp) / tabs.size
        val animatedSlot by animateFloatAsState(
            targetValue = activeSlot.toFloat(),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 900f),
            label = "floatIndicator"
        )
        val pillWidth = slotWidth - 4.dp
        Box(
            modifier = Modifier
                .offset(
                    x = 10.dp + slotWidth * animatedSlot + 2.dp,
                    y = 10.dp
                )
                .width(pillWidth)
                .height(40.dp)
                // v6：选中高亮同参考图——12dp 圆角矩形，不再和 Dock 抢「全圆」的观感。
                .clip(RoundedCornerShape(12.dp))
                .background(QingJiGreenLight)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                FloatingTabItem(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.width(8.dp))
            AddPillButton(onClick = onAddClick)
        }
    }
}

/** 悬浮款的选项：图标 + 文字横排，选中整组弹一下，配色跟选中态走。 */
@Composable
private fun FloatingTabItem(
    tab: QingJiTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
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
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = com.qingji.app.ui.i18n.s(tab.label),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}

/** 右侧的「+ 记一笔」：绿色胶囊大按钮，点了弹记录面板（和经典款中央加号同一个入口）。 */
@Composable
private fun AddPillButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .width(96.dp)
            .height(44.dp)
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
        Spacer(Modifier.width(4.dp))
        Text(
            text = com.qingji.app.ui.i18n.s("记一笔"),
            fontSize = 12.sp,
            lineHeight = 16.sp,
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