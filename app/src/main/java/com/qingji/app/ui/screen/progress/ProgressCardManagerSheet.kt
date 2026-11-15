package com.qingji.app.ui.screen.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.progress.ProgressCardCatalog
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * 进展页「管理卡片」弹层（TASK-07-B，从 ProgressScreen.kt 搬出来）。
 *
 * 每张卡一行：名称 + 显示开关 + 拖动手柄（长按拖动调顺序）；右上角「重置」恢复默认。
 * 行高固定 52dp（间距 8），拖拽交换按行距 60dp 计算。
 *
 * TASK-07-D 写入时机（本次唯一的行为调整，已按工单要求「拖拽结束统一提交」）：
 * 改造前每跨过半行就 commit 一次，一次长拖能写十几次 DataStore；
 * 现在拖动过程只改本地 [localOrder]，松手（onDragEnd）才提交一次。
 * 最终顺序、落位手感、开关、重置、下滑关闭后仍会提交 —— 全部保持原语义。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressCardManagerSheet(
    order: List<String>,
    hidden: Set<String>,
    defaultOrder: List<String> = ProgressCardCatalog.DEFAULT_ORDER,
    onCommit: (List<String>, Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val names = mapOf(
        ProgressCardCatalog.EXERCISE to s("本周运动消耗"),
        ProgressCardCatalog.FORECAST to s("目标达成预估"),
        ProgressCardCatalog.CHART to s("体重走势"),
        ProgressCardCatalog.WATER to s("近一周饮水"),
        ProgressCardCatalog.NUTRITION to s("近一周营养摄入"),
        ProgressCardCatalog.MEASURE to s("身体维度"),
        ProgressCardCatalog.PHOTOS to s("进度照片")
    )
    var localOrder by remember { mutableStateOf(order) }
    var localHidden by remember { mutableStateOf(hidden) }
    var dragFrom by remember { mutableStateOf(-1) }
    var dragY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowPitch = with(density) { 60.dp.toPx() }

    fun commit() = onCommit(localOrder, localHidden)

    ModalBottomSheet(
        onDismissRequest = { commit(); onDismiss() },
        containerColor = CardWhite
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = s("管理卡片"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = s("重置"),
                    fontSize = 13.sp,
                    color = QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            localOrder = defaultOrder
                            localHidden = emptySet()
                            commit()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = s("拖动手柄调整顺序，关闭开关可隐藏"),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                localOrder.forEachIndexed { index, id ->
                    val currentIndex by rememberUpdatedState(index)
                    val isDragging = dragFrom == index
                    Row(
                        modifier = Modifier
                            .height(52.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PageBackground)
                            .graphicsLayer { translationY = if (isDragging) dragY else 0f }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = names[id] ?: id,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (id in localHidden) TextSecondary else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = id !in localHidden,
                            onCheckedChange = { checked ->
                                localHidden = if (checked) localHidden - id else localHidden + id
                                commit()
                            }
                        )
                        Icon(
                            imageVector = Icons.Outlined.DragHandle,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragFrom = currentIndex
                                            dragY = 0f
                                        },
                                        onDrag = { change, amount ->
                                            if (dragFrom == currentIndex) {
                                                dragY += amount.y
                                                change.consume()
                                                // 跨过半行就与相邻项交换，松手即落位；拖动中不写盘
                                                while (dragY > rowPitch / 2 && dragFrom < localOrder.lastIndex) {
                                                    localOrder = localOrder.toMutableList().apply {
                                                        add(dragFrom + 1, removeAt(dragFrom))
                                                    }
                                                    dragFrom += 1
                                                    dragY -= rowPitch
                                                }
                                                while (dragY < -rowPitch / 2 && dragFrom > 0) {
                                                    localOrder = localOrder.toMutableList().apply {
                                                        add(dragFrom - 1, removeAt(dragFrom))
                                                    }
                                                    dragFrom -= 1
                                                    dragY += rowPitch
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            // 松手统一提交一次
                                            if (dragFrom >= 0) commit()
                                            dragFrom = -1
                                            dragY = 0f
                                        },
                                        onDragCancel = {
                                            dragFrom = -1
                                            dragY = 0f
                                        }
                                    )
                                }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
