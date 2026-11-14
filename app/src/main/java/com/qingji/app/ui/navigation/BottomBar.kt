package com.qingji.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
 * 底部导航栏。
 * 结构：首页 | 进展 | ⊕ | 日记 | 我的
 * 中央的加号是"记一笔"入口，不切页面，而是弹出记录面板。
 *
 * M7：三个 Tab 可以在设置里关掉（首页永远保留），所以这里不再写死 5 个，
 * 而是按设置拼出来。少一个 Tab，剩下几个自动平分宽度。
 */
@Composable
fun QingJiBottomBar(
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    onAddClick: () -> Unit,
    settings: QingJiSettings = QingJiSettings()
) {
    val leftTabs = listOfNotNull(
        QingJiTab.Home,
        QingJiTab.Progress.takeIf { settings.showProgressTab }
    )
    val rightTabs = listOfNotNull(
        QingJiTab.Diary.takeIf { settings.showDiaryTab },
        QingJiTab.Mine.takeIf { settings.showMineTab }
    )

    Surface(color = CardWhite, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // ⚠️ 顺序很关键：先 navigationBarsPadding() 再 height()。
                // 写反了（height 在前）会变成「总高 60dp 里再扣掉手势条高度」，
                // 手势导航的手机上内容区只剩十几像素，图标被裁掉、按钮点不到。
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftTabs.forEach { tab ->
                BottomTabItem(tab, currentRoute, onTabSelected, Modifier.weight(1f))
            }

            // 中间加号
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
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

            rightTabs.forEach { tab ->
                BottomTabItem(tab, currentRoute, onTabSelected, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: QingJiTab,
    currentRoute: String,
    onTabSelected: (QingJiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = currentRoute == tab.route

    // 选中时图标轻轻放大，颜色平滑过渡。
    // tabfix：原来用 bouncy() 弹簧（dampingRatio 0.55）会过冲回弹，
    // 快速连点 Tab 时图标来回弹、和页面转场同时开始，看着像整条底栏在抖。
    // 换成和颜色/胶囊同款的 150ms tween，无过冲，底栏视觉始终稳定。
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = com.qingji.app.ui.theme.Motion.fast(),
        label = "tabScale"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) QingJiGreen else TextSecondary,
        animationSpec = com.qingji.app.ui.theme.Motion.fast(),
        label = "tabTint"
    )
    // 选中项背后浮出一块浅绿胶囊，配合图标放大，
    // 一眼就能看出「我现在在这一页」，不用去读页面内容反推。
    // barfix：胶囊改用 snap 瞬时切换。原来跟图标一样走 150ms 颜色渐变，
    // 转场期间新旧两个 Tab 的胶囊同时半透明可见，底栏上像多了两条「阴影」
    // （慢放录屏确认：日记/我的两个 Tab 各挂一条灰胶囊）。胶囊任何时刻
    // 只出现一个；图标的颜色/大小仍走 150ms 渐变保持柔和。
    val pillColor by animateColorAsState(
        targetValue = if (selected) QingJiGreenLight else Color.Transparent,
        animationSpec = snap(),
        label = "tabPill"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTabSelected(tab) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillColor)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = com.qingji.app.ui.i18n.s(tab.label),
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
        Text(
            text = com.qingji.app.ui.i18n.s(tab.label),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            modifier = Modifier.padding(top = 2.dp)
        )
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
