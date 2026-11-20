package com.qingji.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.AccentThemes
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiThemeState
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * ============================================================
 * 食物库悬浮 Dock（v1.6 · TASK-06）
 * ------------------------------------------------------------
 * 复用首页 QingJiFloatingBar 的视觉规范（白色胶囊底板 + 轻阴影 + 换肤配色 +
 * 选中展开浅绿胶囊、未选中只图标），但**不碰首页那套四 tab 路由绑定代码**，
 * 独立实现食物库自己的三个 tab（标准/自定义/吃过的）+ 右侧独立绿色心形收藏按钮。
 *
 * 交互：点击切换；长按 tab 后左右拖动预选、松手确认、划出区域取消；
 * 右侧收藏按钮独立，不参与拖动导航。
 * ============================================================
 */
data class FoodDockItem(val label: String, val icon: ImageVector)

@Composable
fun FoodLibraryDock(
    items: List<FoodDockItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    favoriteActive: Boolean,
    onFavoriteClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val softBg = AccentThemes.dockSoftOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)
    val softFg = AccentThemes.dockContentOf(QingJiThemeState.accentKey, QingJiThemeState.darkMode)
    // 拖动预选：-1 = 没拖。松手时提交。
    var dragPreview by remember { mutableIntStateOf(-1) }
    val effectiveSelected = if (dragPreview >= 0) dragPreview else selectedIndex
    val count = items.size.coerceAtLeast(1)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .height(60.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(CardWhite)
                .pointerInput(count) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slotW = size.width.toFloat() / count
                        fun indexAt(x: Float) = (x / slotW).toInt().coerceIn(0, count - 1)

                        // 自绘长按（照首页 awaitDockLongPress 范式，避免 restricted suspension 限制）
                        val activated = awaitFoodDockLongPress(down, 200L)
                        if (!activated) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        dragPreview = indexAt(down.position.x)
                        var committed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val target = indexAt(change.position.x)
                                if (target != selectedIndex) onSelect(target)
                                committed = true
                                break
                            }
                            val idx = indexAt(change.position.x)
                            if (idx != dragPreview) dragPreview = idx
                            change.consume()
                        }
                        if (!committed) dragPreview = -1 else dragPreview = -1
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, item ->
                val selected = effectiveSelected == i
                val weight by animateFloatAsState(
                    targetValue = if (selected) 1.5f else 1f,
                    animationSpec = tween(200),
                    label = "foodDockWeight$i"
                )
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (selected) softBg else Color.Transparent)
                        .pressable { onSelect(i) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) softFg else TextSecondary,
                            modifier = Modifier.size(21.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = item.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = softFg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // 右侧独立收藏按钮：不参与拖动，单独切换收藏页
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (favoriteActive) QingJiGreen else softBg)
                    .pressable { onFavoriteClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "只看收藏",
                    tint = if (favoriteActive) CardWhite else softFg,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 自绘长按：按满 [timeoutMs] 且位移没超过阈值才算激活。
 * 做成 AwaitPointerEventScope 的扩展函数，才能在 awaitEachGesture 的受限挂起里调用。
 */
private suspend fun AwaitPointerEventScope.awaitFoodDockLongPress(
    down: PointerInputChange,
    timeoutMs: Long
): Boolean {
    val ok = withTimeoutOrNull(timeoutMs) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
            if (!change.pressed) return@withTimeoutOrNull false
            if (abs(change.position.x - down.position.x) > 12f) return@withTimeoutOrNull false
        }
        true
    }
    return ok == true
}
