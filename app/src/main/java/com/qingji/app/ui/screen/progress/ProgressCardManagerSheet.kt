package com.qingji.app.ui.screen.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.qingji.app.domain.progress.ProgressCardCatalog
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * ============================================================
 * 进展页「管理卡片」弹层（QJ-UI-PROGRESS-DRAG-003 · P0 深度修复）
 * ------------------------------------------------------------
 * 根因（003 审计）：旧实现让【同一张被拖卡片】同时受三套机制控制位置——
 *   ① applyDragDelta 按累计位移直接改 localOrder（列表换位）；
 *   ② animateItem() 对换位播放布局动画（被拖卡片处于动画中间态）；
 *   ③ graphicsLayer.translationY = residualY 再用「最终索引差」补偿。
 * ②的插值位置 + ③的最终补偿叠加 = 重复位移 → 跳动 / 重叠 / 回弹。
 * residualY 只是补丁，无法消除三者的时序竞争。
 *
 * 本次重构（核心原则：浮动卡片的位置只由手势控制，列表只管占位和让位）：
 * 1. 【独立浮动卡片】拖动时列表里的原卡片换成等高占位项；完整卡片在 LazyColumn
 *    之外的 Box Overlay 里绘制，绝对定位到手指，独立于列表布局、animateItem、滚动。
 *    不再有 translationY/residualY 补偿 —— 浮动层位置 = 手指位置，单一数据源。
 * 2. 【目标索引用真实布局边界】从 LazyListState.layoutInfo 取可见项的 offset/size，
 *    用手指 Y 与各中心点比较求插入位；跨过中心才换位（抗抖），支持跨多项目与首尾限界。
 * 不再用「累计位移 / 固定 60dp」推算。
 * 3. 【统一坐标系】全部换算到 Overlay 容器局部坐标（窗口 Y − 容器窗口 Y），
 *    绝不混用窗口坐标 / 列表内容坐标 / 手柄局部坐标。
 * 4. 【边缘自动滚动】滚动后按新 layoutInfo 重算目标索引；不把 scroll 量塞回拖拽位移
 *    （否则同一位移被列表滚动和浮动卡片各计一次）。到边界自动停。
 * 5. 【状态单一】dragStartOrder / draggingCardId / targetIndex / floatTopY / localOrder
 *    职责清晰；拖动中只更新必要的目标索引，松手一次性提交，取消完整回滚。
 * 6. 【手势隔离】沿用 skipPartiallyExpanded + confirmValueChange + dragHandle=null
 *    + 内容外层 nestedScroll 吃掉溢出，只有长按右侧手柄启动拖拽。
 *
 * 数据兼容：order/hidden 仍来自外部，只在本地改副本、commit 一次性回传；
 * 不碰 DataStore 结构、不碰 Catalog、不改默认顺序与显隐业务。
 * ============================================================
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
        ProgressCardCatalog.FORECAST to s("目标达成预估"),
        ProgressCardCatalog.CHART to s("体重走势"),
        ProgressCardCatalog.WATER to s("近一周饮水"),
        ProgressCardCatalog.NUTRITION to s("近一周营养摄入"),
        ProgressCardCatalog.MEASURE to s("身体维度"),
        ProgressCardCatalog.PHOTOS to s("进度照片"),
        ProgressCardCatalog.SLEEP_DIARY to s("睡眠日记"),
        ProgressCardCatalog.EXERCISE_DIARY to s("运动日记"),
        ProgressCardCatalog.FOOD_DIARY to s("饮食日记")
    )

    // 本地副本：打开弹层时初始化，操作只改副本，commit 才回传
    var localOrder by remember(order) { mutableStateOf(order) }
    var localHidden by remember(hidden) { mutableStateOf(hidden) }

    // ---------- 拖拽状态（职责单一，见类注释 5） ----------
    /** 当前拖动卡片稳定 ID；null = 未在拖拽。 */
    var draggingCardId by remember { mutableStateOf<String?>(null) }
    /** 拖动开始时的排序快照；取消时回滚到它。 */
    var dragStartOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    /** 当前目标插入索引（0..lastIndex），只在跨过项目中心时更新。 */
    var targetIndex by remember { mutableIntStateOf(-1) }
    /** 浮动卡片顶部相对【Overlay 容器】顶部的 Y（唯一拖动位置源）。拖拽始为卡片原顶部，之后累加位移。 */
    var floatTopY by remember { mutableFloatStateOf(0f) }

    // LazyColumn 在窗口中的范围（边缘判断）；Overlay 容器在窗口中的 Y（坐标换算基准）
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    var containerWindowY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val cardHeightPx = with(density) { 52.dp.toPx() }     // 与卡片 .height(52.dp) 一致
    val edgeBandPx = with(density) { 52.dp.toPx() }
    val autoScrollSpeedPx = with(density) { 520.dp.toPx() } * 0.016f

    val listState = rememberLazyListState()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )

    // 吃掉滚到边缘后传给 sheet 的剩余量 → sheet 完全不跟随内容拖动
    val blockSheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (source == NestedScrollSource.UserInput) {
                Offset(0f, available.y)
            } else Offset.Zero
        }
    }

    fun commit() = onCommit(localOrder, localHidden)

    /**
     * 用真实布局边界重算目标索引（工单 §3.4）。
     * 手指在列表内容坐标系的 Y = (floatTopY + 卡片半高) - (listBounds.top - containerWindowY)。
     * 遍历可见项：第一个「手指 Y < 该项中心」的位置即插入位；都没找到则插到末尾。
     * 因列表项等高（52dp + 8dp 间距），中心点天然提供滞回 —— 手指不跨过中心不换位，
     * 轻微抖动不会反复 reorder。
     */
    fun recomputeTargetIndex() {
        val id = draggingCardId ?: return
        val listTopInContainer = listBounds.top - containerWindowY
        // 手指近似在浮动卡片垂直中心；用卡片中心与各项中心比较，插入判定稳定
        val fingerInList = (floatTopY + cardHeightPx / 2f) - listTopInContainer
        // 真实布局边界 → 抽象成纯函数入参（测试覆盖的就是这段计算）
        val bounds = listState.layoutInfo.visibleItemsInfo.map { info ->
            com.qingji.app.domain.progress.ProgressCardDragMath.ItemBounds(
                index = info.index,
                offset = info.offset.toFloat(),
                size = info.size.toFloat()
            )
        }
        val newTarget = com.qingji.app.domain.progress.ProgressCardDragMath
            .targetIndexFor(fingerInList, bounds)
            .coerceIn(0, localOrder.lastIndex)
        if (com.qingji.app.domain.progress.ProgressCardDragMath
                .shouldReorder(newTarget, targetIndex, localOrder.size)
        ) {
            targetIndex = newTarget
            // 一次性把被拖卡片放到目标位：展示顺序稳定由 startOrder + target 推导，
            // 用当前 localOrder 作基准可避免跨多项目时逐格 move 的抖动。
            val moved = ProgressCardCatalog.moveToIndex(localOrder, id, newTarget)
            if (moved !== localOrder) localOrder = moved
        }
    }

    // ---------- 边缘自动滚动：滚动后重算目标索引，不回灌 dragOffset ----------
    LaunchedEffect(draggingCardId) {
        if (draggingCardId == null) return@LaunchedEffect
        while (true) {
            val fingerInWindow = floatTopY + cardHeightPx / 2f + containerWindowY
            val top = listBounds.top
            val bottom = listBounds.bottom
            var delta = 0f
            if (bottom > top) {
                when {
                    fingerInWindow in (top - 24f)..(top + edgeBandPx) -> {
                        val intensity = ((top + edgeBandPx - fingerInWindow) / edgeBandPx).coerceIn(0f, 1f)
                        delta = -autoScrollSpeedPx * intensity
                    }
                    fingerInWindow in (bottom - edgeBandPx)..(bottom + 24f) -> {
                        val intensity = ((fingerInWindow - (bottom - edgeBandPx)) / edgeBandPx).coerceIn(0f, 1f)
                        delta = autoScrollSpeedPx * intensity
                    }
                }
            }
            if (delta != 0f) {
                var actuallyScrolled = 0f
                listState.scroll { actuallyScrolled = scrollBy(delta) }
                // 列表滚动改变了项目布局位置 → 用新 layoutInfo 重算；
                // 不再 applyDragDelta(actuallyScrolled)，避免位移双重计入。
                if (actuallyScrolled != 0f) recomputeTargetIndex()
            }
            delay(16L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerWindowY = it.positionInWindow().y }
    ) {
        // ---------- 基础内容（sheet 内部，固定 + 列表） ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(blockSheetScroll)
                .padding(horizontal = 18.dp)
        ) {
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
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { commit(); onDismiss() }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = s("关闭"), tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = s("长按右侧手柄调整顺序，关闭开关可隐藏，松手保存"),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .onGloballyPositioned { coords ->
                        val p = coords.positionInWindow()
                        listBounds = Rect(p.x, p.y, p.x + coords.size.width, p.y + coords.size.height)
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = localOrder, key = { it }) { id ->
                    val isDragging = draggingCardId == id
                    // 被拖卡片在列表里只留等高占位项（完整卡片在 Overlay 绘制）。
                    // 占位项同样 animateItem → 其他卡片平滑让位；占位项不参与任何位移补偿。
                    if (isDragging) {
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .height(52.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PageBackground.copy(alpha = 0.5f))
                        )
                    } else {
                        CardRow(
                            id = id,
                            name = names[id] ?: id,
                            checked = id !in localHidden,
                            isDragging = false,
                            onCheckedChange = { checked ->
                                localHidden = if (checked) localHidden - id else localHidden + id
                                commit()
                            },
                            itemModifier = Modifier.animateItem(),
                            dragHandleModifier = Modifier.pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingCardId = id
                                        dragStartOrder = localOrder
                                        targetIndex = localOrder.indexOf(id)
                                        // 浮动卡片初始顶部 = 该项在列表中的真实位置（layoutInfo），
                                        // 换算到容器局部坐标：列表窗口 Y + item offset - 容器窗口 Y。
                                        val item = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { info -> info.key == id }
                                        floatTopY = listBounds.top + (item?.offset ?: 0).toFloat() - containerWindowY
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        // 浮动层位置唯一来源：累加的手指位移
                                        floatTopY += amount.y
                                        recomputeTargetIndex()
                                    },
                                    onDragEnd = {
                                        // 松手：localOrder 已在拖动中稳定到目标位，一次提交
                                        if (localOrder != dragStartOrder) commit()
                                        draggingCardId = null
                                        targetIndex = -1
                                    },
                                    onDragCancel = {
                                        // 取消：回滚快照、清状态、不写盘
                                        localOrder = dragStartOrder
                                        draggingCardId = null
                                        targetIndex = -1
                                    }
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---------- 浮动卡片 Overlay：唯一位置源 = 手指，脱离列表布局与动画 ----------
        val dragId = draggingCardId
        if (dragId != null) {
            val floatY = floatTopY
            // 左右 18dp 与列表内容对齐；Y 直接取容器局部坐标的浮动位置。
            // 这里不套 animateItem、不套 translationY 补偿 —— 位置只来自手指。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, floatY.roundToInt()) }
                    .padding(horizontal = 18.dp)
                    .zIndex(5f)
            ) {
                CardRow(
                    id = dragId,
                    name = names[dragId] ?: dragId,
                    checked = dragId !in localHidden,
                    isDragging = true,
                    onCheckedChange = { /* 拖拽中不接受开关操作 */ },
                    dragHandleModifier = Modifier // 浮动层手柄不再注册手势，避免二次拖拽
                )
            }
        }
    }
}

/**
 * 卡片行：普通列表项与浮动 Overlay 共用同一套绘制，保证悬浮时完整保留
 * 背景、圆角、文字、开关与拖动手柄（工单 §七：变换作用于完整卡片表面）。
 * 阴影/缩放只在 isDragging=true 时启用，且 graphicsLayer 在 background 之前 →
 * 变换作用于整张卡片而非仅内容。
 */
@Composable
private fun CardRow(
    id: String,
    name: String,
    checked: Boolean,
    isDragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dragHandleModifier: Modifier,
    itemModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(targetValue = if (isDragging) 1.02f else 1f, label = "dragScale")
    Row(
        modifier = modifier
            .then(itemModifier)
            .height(52.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .background(PageBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (checked) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = s("拖动排序"),
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .then(dragHandleModifier)
        )
    }
}
