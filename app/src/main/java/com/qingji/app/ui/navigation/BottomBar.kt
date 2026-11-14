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
import androidx.compose.foundation.layout.fillMaxHeight
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
                onAddClick = onAddClick,
                floating = false
            )
        }
    }
}

/** 悬浮底栏：左右 18dp、底部悬空 10dp 的圆角 Dock，自身带投影。 */
@Composable
fun QingJiFloatingBar(
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit,
    settings: QingJiSettings = QingJiSettings()
) {
    val slots = buildBarSlots(settings)
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite)
        ) {
            BarSlots(
                slots = slots,
                activeSlot = activeBarSlot(slots, currentRoute),
                currentRoute = currentRoute,
                barHeight = 64.dp,
                onTabSelected = onTabSelected,
                onAddClick = onAddClick,
                floating = true
            )
        }
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
    onAddClick: () -> Unit,
    floating: Boolean
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
            animationSpec = com.qingji.app.ui.theme.Motion.smooth(),
            label = "barIndicator"
        )
        Box(
            modifier = Modifier
                .offset(x = slotWidth * animatedSlot)
                .width(slotWidth)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = if (floating) 9.dp else 7.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreenLight)
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slots.forEach { slot ->
                if (slot == null) {
                    BarPlusSlot(onAddClick, Modifier.weight(1f), floating)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTabSelected(tab) }
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
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun BarPlusSlot(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .offset(y = if (floating) (-8).dp else 0.dp)
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