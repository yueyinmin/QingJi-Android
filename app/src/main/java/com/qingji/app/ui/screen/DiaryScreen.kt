package com.qingji.app.ui.screen

import com.qingji.app.domain.AppClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.random.Random
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.domain.DiaryRules
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.DiaryViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.qingji.app.ui.i18n.s

/**
 * ============================================================
 * 日记（M5，已经全部接上真数据）
 * ------------------------------------------------------------
 * - 近 30 天打卡格子：颜色按当天最高评分分四档
 * - 玻璃罐：每写一篇往里加一颗，攒满 30 篇
 * - 三种排序：日期 / 更新时间 / 打分
 * - 列表：点卡片编辑，点删除二次确认
 * - 空态：「还没有日记，点右上角写一篇」
 *
 * 颜色和排序规则统一放在 DiaryRules（纯 Kotlin，可单元测试）。
 * ============================================================
 */
@Composable
fun DiaryScreen(
    onOpenEdit: (Long) -> Unit,
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** v3.2.4：悬浮栏第二颗换成食物库 */
    onOpenFoodLibrary: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** tabfix：滚动状态由 Root 持有，切 Tab 回来不丢位置。 */
    scrollState: ScrollState = rememberScrollState(),
    vm: DiaryViewModel = viewModel()
) {
    val diaries by vm.diaries.collectAsState()
    val sortMode by vm.sortMode.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Diary?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    // 进页面时先 false、紧接着置 true，右下角按钮就会播一次弹出动画。
    // 直接写 true 的话第一帧就是最终状态，没有动画可播。
    var fabVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fabVisible = true }

    val today = AppClock.today()
    val cells = remember(today) { DiaryRules.cells(today) }
    // 一天可能写多篇，格子取当天最高的那次评分
    val ratingByDate = diaries.groupBy { it.date }
        .mapValues { (_, list) -> list.maxOf { it.rating } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // 和「进展」「我的」一样，用大标题开头，不再用带返回箭头的顶栏。
            // 四个主页面长得一致，切换时才不会觉得像换了另一个 App。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s("日记"),
                    fontSize = TextSizes.PageTitle,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (diaries.isNotEmpty()) {
                    Text(
                        text = s("清空"),
                        fontSize = 13.sp,
                        color = Color(0xFFD64545),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showClearAll = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(12.dp))

            // ---------- 近 30 天 ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = s("近 30 天"), fontSize = TextSizes.CardTitle, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot("未写", DividerColor)
                    Spacer(Modifier.size(6.dp))
                    LegendDot("低", QingJiGreenLight)
                    Spacer(Modifier.size(6.dp))
                    LegendDot("中", QingJiGreen.copy(alpha = 0.55f))
                    Spacer(Modifier.size(6.dp))
                    LegendDot("高", QingJiGreen)
                }
            }

            Spacer(Modifier.height(10.dp))

            // v3.2.4：按真实日历排 —— 7 列、周一开头，第一天先补空格对齐到对应星期。
            // 以前 6 列 × 5 行，日期和星期根本对不上，看起来「时间不对」。
            // 格子压到 14dp、去掉中间的日期数字，整块占屏更小，只靠颜色看打卡情况。
            val firstWeekday = cells.firstOrNull()?.dayOfWeek?.value ?: 1   // 周一=1 … 周日=7
            val leadingBlanks = (firstWeekday - 1 + 7) % 7
            val calendarCells: List<LocalDate?> = List(leadingBlanks) { null } + cells

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 星期标头：一眼能看出每一列是周几
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                calendarCells.chunked(7).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { date ->
                            if (date == null) {
                                Spacer(Modifier.weight(1f))
                                return@forEach
                            }
                            val level = DiaryRules.level(ratingByDate[date] ?: 0)
                            val color = when (level) {
                                DiaryRules.LEVEL_LOW -> QingJiGreenLight
                                DiaryRules.LEVEL_MID -> QingJiGreen.copy(alpha = 0.55f)
                                DiaryRules.LEVEL_HIGH -> QingJiGreen
                                else -> DividerColor
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .clickable {
                                        val existing = diaries.firstOrNull { it.date == date }
                                        onOpenEdit(existing?.id ?: 0L)
                                    }
                            )
                        }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Spacer(Modifier.height(14.dp))

            // ---------- 排序 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val options = listOf(
                    "按日期" to DiaryRules.SortMode.DATE,
                    "按更新" to DiaryRules.SortMode.UPDATED,
                    "按打分" to DiaryRules.SortMode.RATING
                )
                options.forEach { (label, mode) ->
                    val selected = sortMode == mode
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = if (selected) QingJiGreenDark else TextSecondary,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) QingJiGreenLight else Color.Transparent)
                            .clickable { vm.setSort(mode) }
                            .padding(horizontal = 18.dp, vertical = 7.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 列表 / 空态 ----------
            if (diaries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✎", fontSize = 22.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = s("还没有日记，点右下角「写一篇」"), fontSize = 13.sp, color = TextSecondary)
                }
            } else {
                diaries.forEachIndexed { index, diary ->
                    com.qingji.app.ui.components.AppearItem(index = index) {
                        com.qingji.app.ui.components.SwipeToRevealDelete(
                            onDelete = { pendingDelete = diary }
                        ) {
                            DiaryRow(
                                diary = diary,
                                onClick = { onOpenEdit(diary.id) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(Dimens.FabSpace))
        }

        // 和首页一样的三个快捷按钮。
        // 往上挪 84dp，避免和右下角「写一篇」叠在一起。
        QuickActionColumn(
            onWater = { vm.addWater(200) },
            onWeight = onOpenWeight,
            onOpenFoodLibrary = onOpenFoodLibrary,
            onPhoto = onOpenImage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 84.dp)
        )

        // ---------- 右下角固定的「写一篇」 ----------
        // 为什么放右下角：这个页面会越写越长，放右上角要滚回顶部才能点。
        // v1.0.7：按钮块必须放在快捷按钮**之后**组合 —— Compose 后组合的在上层，
        // 以前它在快捷按钮前面，「写一篇」的弹出动画经过快捷按钮区域时会被盖住，看着像被遮挡。
        AnimatedVisibility(
            visible = fabVisible,
            enter = scaleIn(
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
                initialScale = 0.5f
            ) + fadeIn(animationSpec = tween(180)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f)
            ) { height -> height / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            WriteEntryFab(onClick = { onOpenEdit(0L) })
        }
    }

    // ---------- 清空全部确认 ----------
    if (showClearAll) {
        com.qingji.app.ui.components.AppDialog(
            title = "清空全部日记？",
            confirmText = "全部删除",
            onConfirm = {
                scope.launch { vm.clearAllDiaries() }
                showClearAll = false
            },
            dismissText = "取消",
            danger = true,
            onDismiss = { showClearAll = false }
        ) {
            Text("会把所有日记都删掉，删了就找不回来了。建议先导出备份。", fontSize = 14.sp, color = TextPrimary)
        }
    }

    pendingDelete?.let { target ->
        com.qingji.app.ui.components.AppDialog(
            title = "删掉这篇日记？",
            confirmText = "删除",
            onConfirm = {
                scope.launch { vm.delete(target.id) }
                pendingDelete = null
            },
            dismissText = "取消",
            danger = true,
            onDismiss = { pendingDelete = null }
        ) {
            Text("删了就找不回来了，确认一下。", fontSize = 14.sp, color = TextPrimary)
        }
    }
}

/** 玻璃罐：每写一篇加一颗，进度到 30 篇满。 */
@Composable
private fun JarCard(count: Int) {
    val progress by animateFloatAsState(
        targetValue = DiaryRules.jarProgress(count),
        label = "jar"
    )
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s("玻璃罐"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已攒 $count 篇 / 满 ${DiaryRules.JAR_GOAL} 篇",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (count >= DiaryRules.JAR_GOAL) "攒满啦，给自己点个赞" else "每写一篇，往罐里丢一颗",
                    fontSize = 11.sp,
                    color = QingJiGreen
                )
            }
            // 真正的玻璃瓶：细颈 + 宽瓶身 + 瓶里的珠子按篇数增加
            DiaryJar(
                count = count,
                progress = progress,
                modifier = Modifier.size(width = 78.dp, height = 108.dp)
            )
        }
    }
}

/** 一条日记卡片。 */
@Composable
private fun DiaryRow(diary: Diary, onClick: () -> Unit) {
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).pressable { onClick() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${diary.date.monthValue}月${diary.date.dayOfMonth}日",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "★".repeat(diary.rating.coerceIn(0, 5)),
                        fontSize = 12.sp,
                        color = Color(0xFFF5A623)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = DiaryRules.previewText(diary.content, diary.date),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 2
                )
                val images = diary.imagePaths.split(",").filter { it.isNotBlank() }.size
                val extras = buildList {
                    if (images > 0) add("$images 张图")
                    diary.weightKg?.let { add("${it}kg") }
                }
                if (extras.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = extras.joinToString(" · "), fontSize = 11.sp, color = QingJiGreen)
                }
            }
        }
    }
}

/**
 * 右下角的「写一篇」按钮。
 * 做成胶囊形而不是小圆点：上面有字，第一眼就知道是干什么的，
 * 不用像纯图标按钮那样猜。
 *
 * v3.2.6：去掉 10dp 投影。进页面时这颗按钮会播 spring 缩放 + 淡入的弹出动画，
 * 阴影跟着一起缩放、淡入时边缘会抖一下，看着生硬（用户原话「阴影动画很生硬」）。
 * 而且全 App 卡片都是 0 阴影（Common.kt 的 SectionCard），深色胶囊本身已够显眼。
 * 弹入动画保留，只是不再有「阴影动画」这一段。
 */
@Composable
private fun WriteEntryFab(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.PillCorner))
            .background(QingJiGreenDark)
            .pressable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✎", fontSize = 16.sp, color = Color.White)
        Spacer(Modifier.size(8.dp))
        Text(
            text = s("写一篇"),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(start = 3.dp)
        )
    }
}


/**
 * 玻璃瓶本体。
 *
 * 用户说的「玻璃瓶」不是一块卡片的名字，而是**存日记的容器**：
 * 每写一篇，瓶里就多一颗珠子，攒满 30 颗瓶子就满了。
 * 所以这里真的把瓶子画出来——细颈、宽瓶身、透明玻璃感，
 * 珠子按固定伪随机位置散在瓶底，写一篇涨一颗。
 */
@Composable
private fun DiaryJar(count: Int, progress: Float, modifier: Modifier = Modifier) {
    // 最新一颗珠子的掉落动画：篇数变化时从瓶口（0）落到瓶底（1）。
    // spring 带一点过冲，落到瓶底会轻轻弹一下，像真的珠子掉进罐子。
    val drop = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(count) {
        if (count > 0) {
            drop.snapTo(0f)
            drop.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.5f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()

        // 瓶口宽度 / 瓶颈高度 / 瓶身左右留边
        val neckWidth = w * 0.30f
        val neckHeight = h * 0.16f
        val shoulder = neckHeight + h * 0.10f
        val sideInset = 3.dp.toPx()
        val bottomInset = 6.dp.toPx()

        val jarPath = Path().apply {
            moveTo(w / 2 - neckWidth / 2, 0f)
            lineTo(w / 2 + neckWidth / 2, 0f)
            lineTo(w / 2 + neckWidth / 2, neckHeight)
            // 肩膀：从瓶颈平滑过渡到瓶身
            cubicTo(
                w / 2 + neckWidth, shoulder * 0.82f,
                w - sideInset, shoulder * 0.75f,
                w - sideInset, shoulder
            )
            lineTo(w - sideInset, h - bottomInset)
            quadraticTo(w - sideInset, h, w - bottomInset * 1.6f, h)
            lineTo(bottomInset * 1.6f, h)
            quadraticTo(sideInset, h, sideInset, h - bottomInset)
            lineTo(sideInset, shoulder)
            cubicTo(
                sideInset, shoulder * 0.75f,
                w / 2 - neckWidth, shoulder * 0.82f,
                w / 2 - neckWidth / 2, neckHeight
            )
            close()
        }

        // 玻璃底色
        drawPath(jarPath, color = QingJiGreenLight)
        // 玻璃里按篇数上升的淡色（视觉上像"存了多少"）
        clipPath(jarPath) {
            val fillTop = h - (h - shoulder) * progress.coerceIn(0f, 1f)
            drawRect(
                color = QingJiGreen.copy(alpha = 0.16f),
                topLeft = Offset(0f, fillTop),
                size = Size(w, h - fillTop)
            )
        }

        // 珠子：每篇一颗，最多画 30 颗，位置固定（同一篇不会乱跳）
        val beads = count.coerceIn(0, 30)
        clipPath(jarPath) {
            repeat(beads) { index ->
                val random = Random(seed = index * 31 + 7)
                val bx = sideInset + (w - sideInset * 2) * (0.16f + 0.68f * random.nextFloat())
                val by = (h - bottomInset) - (h - shoulder) * (0.08f + 0.82f * random.nextFloat())

                // 最新一颗（index == beads - 1）带掉落动画：从瓶口落到目标位，落底轻弹一下。
                // 首次进入页面也会播一次——写第一篇时瓶子不是「凭空出现一颗」而是掉进去。
                val isNew = index == beads - 1 && drop.value < 1f
                if (isNew) {
                    val fallY = neckHeight + (by - neckHeight) * drop.value
                    drawCircle(
                        color = QingJiGreen.copy(alpha = 0.75f * drop.value.coerceIn(0f, 1f)),
                        radius = (2.2f + random.nextFloat() * 1.2f).dp.toPx(),
                        center = Offset(bx, fallY)
                    )
                } else {
                    drawCircle(
                        color = QingJiGreen.copy(alpha = 0.75f),
                        radius = (2.2f + random.nextFloat() * 1.2f).dp.toPx(),
                        center = Offset(bx, by)
                    )
                }
            }
        }

        // 玻璃反光（一道斜高光，让它看起来像玻璃）
        clipPath(jarPath) {
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(w * 0.24f, shoulder + 4.dp.toPx()),
                end = Offset(w * 0.24f, h - 10.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        }

        // 瓶身描边
        drawPath(jarPath, color = QingJiGreen.copy(alpha = 0.45f), style = Stroke(width = stroke))
    }
}
