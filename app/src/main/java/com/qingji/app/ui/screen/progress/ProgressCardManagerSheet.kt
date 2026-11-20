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
import androidx.compose.runtime.mutableStateMapOf
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
 * 进展页「管理卡片」弹层（QJ-UI-PROGRESS-DRAG-001 · P0 拖拽修复 + 交互优化）
 * ------------------------------------------------------------
 * 修复的核心 Bug：拖动卡片手柄时 BottomSheet 跟着上下移动、手势冲突、
 * 列表无独立滚动区、拖后索引错位、取消不回滚、长列表无边缘自动滚动。
 *
 * 本次做法（不是调动画参数糊弄，是重构手势与布局结构）：
 * 1. 【弹层固定】skipPartiallyExpanded=true 消除中间态；confirmStateChange
 *    拒绝一切 →Hidden（禁掉下拉关闭）；dragHandle=null；内容外层 nestedScroll
 *    吃掉滚到边缘后本该传给 sheet 的 available.y → sheet 收不到拖动量。
 *    遮罩点击 / 系统返回键仍走 onDismissRequest，右上角另有明确关闭 ✕。
 * 2. 【列表独立滚动】普通 Column → LazyColumn + 独立 LazyListState +
 *    key=卡片ID + heightIn(max) 固定最大高度；标题/重置/说明固定在列表外。
 * 3. 【稳定拖拽身份】用 draggingCardId（不再用 dragFrom 索引）+ dragStartOrder
 *    + dragOffsetY；通过 ID 反查索引，列表重排后拖谁不会错位。
 * 4. 【边缘自动滚动】拖拽中手指贴近列表上下边缘 → 协程按比例 scrollBy，
 *    并把滚动量同步进 dragOffsetY，保证自动滚动与排序状态一致。
 * 5. 【状态管理】松手提交一次；onDragCancel 回滚到 dragStartOrder。
 * 6. 【视觉】被拖卡片 1.02 倍 + 阴影 + 提升层级；其他卡片 animateItem() 平滑换位。
 *
 * 数据兼容：order/hidden 仍来自外部（ProgressScreen 解析好的），
 * 本组件只在本地改副本、commit 时一次性回传，不碰 DataStore 结构、不碰 Catalog。
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

    // ---------- 拖拽状态（稳定 ID 身份） ----------
    var draggingCardId by remember { mutableStateOf<String?>(null) }
    var dragStartOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 手指在窗口中的绝对 Y（用于边缘判断）：拖拽开始后用累计位移更新，不受重排影响
    var fingerWindowY by remember { mutableFloatStateOf(0f) }
    var pointerYInit by remember { mutableStateOf(false) }
    // 每张卡手柄的窗口 Y（按 ID 各自维护，避免多卡重组时序下读到别卡的旧值）；
    // 只在拖拽首帧用它定位手指绝对 Y，之后靠累计位移推进。
    val handleWindowYById = remember { mutableStateMapOf<String, Float>() }
    // LazyColumn 在窗口中的范围（用于边缘判断）
    var listBounds by remember { mutableStateOf(Rect.Zero) }

    // 卡片圆角统一常量：graphicsLayer 的 shape 与背景圆角共用同一来源，
    // 避免变换层与卡片轮廓各写一份 12.dp 导致缩放/阴影/裁切不同步（QJ-UI-PROGRESS-DRAG-002）
    val cardShape = RoundedCornerShape(12.dp)

    val density = LocalDensity.current
    val rowPitchPx = with(density) { 60.dp.toPx() }          // 52dp 行高 + 8dp 间距
    val edgeBandPx = with(density) { 52.dp.toPx() }          // 边缘感应带
    val autoScrollSpeedPx = with(density) { 520.dp.toPx() } * 0.016f  // 每帧最大滚动量

    val listState = rememberLazyListState()

    // sheet 固定：skipPartiallyExpanded 消除中间态；confirmStateChange 拒绝 →Hidden，
    // 禁止下拉/手势关闭（遮罩与返回键走 onDismissRequest，不受影响）。
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden }
    )

    // 内容外层拦截滚到边缘后传给 sheet 的剩余量，让 sheet 完全不跟随内容拖动
    val blockSheetScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = if (source == NestedScrollSource.UserInput) {
                Offset(0f, available.y) // 吃掉纵向剩余（手指拖动产生的溢出），sheet 收不到
            } else Offset.Zero
        }
    }

    fun commit() = onCommit(localOrder, localHidden)

    /** 累计拖拽位移并按稳定 ID 重排：目标索引 = 起始索引 + 位移/行距，与中间交换次数无关。 */
    fun applyDragDelta(deltaY: Float) {
        dragOffsetY += deltaY
        val id = draggingCardId ?: return
        val startIndex = dragStartOrder.indexOf(id)
        if (startIndex < 0) return
        val targetIndex = (startIndex + (dragOffsetY / rowPitchPx).roundToInt())
            .coerceIn(0, localOrder.lastIndex)
        // 走 domain 纯函数：按稳定 ID 换位，非法入参原样返回，不会丢卡/重卡
        val moved = ProgressCardCatalog.moveToIndex(localOrder, id, targetIndex)
        if (moved !== localOrder) localOrder = moved
    }

    // ---------- 边缘自动滚动：拖拽中手指贴近上下边缘时按距离比例滚动 ----------
    LaunchedEffect(draggingCardId) {
        if (draggingCardId == null) return@LaunchedEffect
        while (true) {
            val y = fingerWindowY
            val top = listBounds.top
            val bottom = listBounds.bottom
            var delta = 0f
            if (bottom > top) {
                when {
                    y in (top - 24f)..(top + edgeBandPx) -> {
                        val intensity = ((top + edgeBandPx - y) / edgeBandPx).coerceIn(0f, 1f)
                        delta = -autoScrollSpeedPx * intensity
                    }
                    y in (bottom - edgeBandPx)..(bottom + 24f) -> {
                        val intensity = ((y - (bottom - edgeBandPx)) / edgeBandPx).coerceIn(0f, 1f)
                        delta = autoScrollSpeedPx * intensity
                    }
                }
            }
            if (delta != 0f) {
                // scroll 的 lambda 拿到 ScrollScope，scrollBy 返回实际滚动量（到边缘会小于请求量）
                var actuallyScrolled = 0f
                listState.scroll { actuallyScrolled = scrollBy(delta) }
                // 内容滚动后，等效手指相对内容反向位移，同步进拖拽偏移保证排序一致
                if (actuallyScrolled != 0f) applyDragDelta(actuallyScrolled)
            }
            delay(16L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { commit(); onDismiss() },
        sheetState = sheetState,
        containerColor = CardWhite,
        dragHandle = null,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true)
    ) {
        // 拦截挂在内容根 Column（sheet 内部 swipe 连接的内层）：列表滚到边缘的溢出量
        // 先被这里吃掉，sheet 收不到 → sheet 不会跟随列表拖动/位移。
        // 标题 / 说明 / 重置 / 关闭：全部固定在列表之外，不参与滚动与排序。
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
                // 明确关闭入口（下拉被禁用，这里给一个确定的关闭方式）
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { commit(); onDismiss() }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = s("关闭"),
                        tint = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = s("长按右侧手柄调整顺序，关闭开关可隐藏，松手保存"),
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            // ---------- 独立滚动列表 ----------
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
                    val startIndex = if (isDragging) dragStartOrder.indexOf(id) else -1
                    val currentIndex = localOrder.indexOf(id)
                    // 被拖卡片：视觉上跟手（总位移 - 已通过换位消化的位移）
                    val residualY = if (isDragging && startIndex >= 0) {
                        dragOffsetY - (currentIndex - startIndex) * rowPitchPx
                    } else 0f
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.02f else 1f,
                        label = "dragScale"
                    )

                    Row(
                        modifier = Modifier
                            .animateItem()
                            .height(52.dp)
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 2f else 0f)
                            // QJ-UI-PROGRESS-DRAG-002：graphicsLayer 必须放在 clip/background 之前，
                            // 让 scale/位移/阴影作用于整张卡片（含背景、圆角、轮廓），而不是只缩放内部内容。
                            // Compose Modifier 从左到右由外到内包裹：graphicsLayer 在外层 → 变换作用于它
                            // 之后所有的绘制（背景+裁切+内容）；放在 background 之后则只能影响内容。
                            // shape + clip=true 让阴影按卡片圆角生成，避免矩形阴影黑边；
                            // 故移除原 .clip()，不再重复裁切（graphicsLayer 的 clip 已覆盖同一圆角）。
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationY = residualY
                                shadowElevation = if (isDragging) 10.dp.toPx() else 0f
                                shape = cardShape
                                clip = true
                            }
                            .background(PageBackground, cardShape)
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
                                // 只切显隐，不改排序
                                localHidden = if (checked) localHidden - id else localHidden + id
                                commit()
                            }
                        )
                        Icon(
                            imageVector = Icons.Outlined.DragHandle,
                            contentDescription = s("拖动排序"),
                            tint = TextSecondary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .onGloballyPositioned { coords ->
                                    handleWindowYById[id] = coords.positionInWindow().y
                                }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingCardId = id
                                            dragStartOrder = localOrder
                                            dragOffsetY = 0f
                                            pointerYInit = false
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            // 首帧用该卡手柄位置+相对坐标定位手指绝对 Y；之后用累计位移
                                            if (!pointerYInit) {
                                                fingerWindowY =
                                                    (handleWindowYById[id] ?: listBounds.top) + change.position.y
                                                pointerYInit = true
                                            } else {
                                                fingerWindowY += amount.y
                                            }
                                            applyDragDelta(amount.y)
                                        },
                                        onDragEnd = {
                                            // 松手：先对齐残余位移，再提交一次，最后清状态
                                            val endId = draggingCardId
                                            if (endId != null) {
                                                val sIdx = dragStartOrder.indexOf(endId)
                                                val eIdx = localOrder.indexOf(endId)
                                                if (sIdx >= 0 && eIdx >= 0) {
                                                    dragOffsetY = (eIdx - sIdx) * rowPitchPx
                                                }
                                                if (localOrder != dragStartOrder) commit()
                                            }
                                            draggingCardId = null
                                            dragOffsetY = 0f
                                            pointerYInit = false
                                        },
                                        onDragCancel = {
                                            // 取消：回滚到拖拽前顺序，清状态和偏移，不写盘
                                            localOrder = dragStartOrder
                                            draggingCardId = null
                                            dragOffsetY = 0f
                                            pointerYInit = false
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
