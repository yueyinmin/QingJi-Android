package com.qingji.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.screen.training.DiaryModeSwitch
import com.qingji.app.ui.screen.training.MoodDiaryContent
import com.qingji.app.ui.screen.training.TrainingDiaryContent
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.viewmodel.DiaryViewModel
import com.qingji.app.ui.viewmodel.ProgressViewModel
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * ============================================================
 * 日记页（v1.0.10 改造 / 2026-11-13 再调）
 * ------------------------------------------------------------
 * 顶部结构固定在这里，不再由两个 Tab 各自画：
 *   大标题「日记」 → 「训练日记 / 心情日记」胶囊 → 内容区
 * 以前「清空」和胶囊写在心情日记内部，导致切到心情日记时整块菜单比
 * 训练日记低一行（主人反馈「不统一」）。现在标题和胶囊提到外层，
 * 两个 Tab 从同一高度开始，只切换下面的内容。
 * ============================================================
 */
@Composable
fun DiaryScreen(
    onOpenEdit: (Long) -> Unit,
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** v3.2.4：悬浮栏第二颗换成食物库 */
    onOpenFoodLibrary: () -> Unit = {},
    /** V2 工单：点水滴只打开「记录饮水」弹层，不再直接 addWater(200)。 */
    onOpenWaterSheet: () -> Unit = {},
    /** 默认进训练日记（主人 2026-11-13 指定） */
    trainingTab: Boolean = true,
    onTrainingTabChange: (Boolean) -> Unit = {},
    onStartTraining: () -> Unit = {},
    onEditTraining: () -> Unit = {},
    /** 与首页 / 训练页共用同一个实例，草稿状态不打架 */
    trainingVm: TrainingViewModel = viewModel(),
    /** 训练日记里的「饮食 30 天」用进展页的数据源，保证两边口径一致 */
    progressVm: ProgressViewModel = viewModel(),
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    vm: DiaryViewModel = viewModel()
) {
    val diaries by vm.diaries.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Diary?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    // 两个 Tab **共用同一个月份状态**：旧版训练页的月份在外层、心情页自己
    // remember 一份，翻过月再切回来日历会跳到另一月，看起来像换了一整个页面。
    var diaryMonth by remember { mutableStateOf(YearMonth.now()) }
    val trainingScrollState = rememberScrollState()

    // ========== 日记 Tab 切换 · 唯一动画源（专项工单 2026-11-16）==========
    // · 全页只有一个 progress：胶囊位移、内容层位移全部从这一个值推导，
    //   以前「胶囊 200ms + 内容 250ms 各跑各的」导致胶囊和页面永远对不齐，
    //   现在物理上同源，不可能不同步。
    // · 200ms / FastOutSlowInEasing：落在工单要求的 180~220ms 区间。
    val progress = animateFloatAsState(
        targetValue = if (trainingTab) 0f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "diaryTabProgress"
    )
    // 非激活那一层虽然在屏幕外，但仍可能命中点击，把事件吞掉
    val blockGestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                if (!event.changes.any { it.pressed }) break
            }
        }
    }

    val foodMonth by progressVm.foodMonth.collectAsState()
    val profile by progressVm.profile.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        // ---------- 固定头部：标题 + 胶囊 ----------
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding)) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s("日记"),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // 清空只对心情日记有意义，放在标题右侧，不占额外一行
                if (!trainingTab && diaries.isNotEmpty()) {
                    Text(
                        text = "清空",
                        fontSize = 13.sp,
                        color = Color(0xFFD64545),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .clickable { showClearAll = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 专项工单：胶囊和内容共用同一个 progress（上面那一个动画源），
            // 胶囊走到哪一帧、内容就走到哪一帧，节奏完全一致
            DiaryModeSwitch(
                training = trainingTab,
                progress = progress.value,
                onChange = { onTrainingTabChange(it) }
            )
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 内容区：两个 Tab 常驻，覆盖式轻微横移过渡（专项工单）----------
        // · 旧方案是 30% 容器宽度的大横移：切换中段屏幕一半是旧页一半是新页，
        //   被感知为「右侧残留内容」「横向错位」；且动画中途反方向点击时 zIndex
        //   瞬间翻转，顶层带着大偏移突然出现 → 「闪烁、短暂回退」。
        // · 新方案（覆盖式）：
        //   1) 选中层 zIndex 恒为顶层，且**完全不透明**——从第一帧就盖住旧页，
        //      不存在任何透明度叠加，重影在数学上不可能发生；
        //   2) 只滑入容器宽度的 4%（约 16dp）的轻微位移，滑到中段旧页最多露出
        //      2% 宽的窄条，肉眼几乎不可见；
        //   3) clipToBounds() 把滑动层裁在内容区边界内，任何东西都不会越界绘制；
        //   4) 快速连点 = 同一个 progress 反向走，顶层永远不透明、永远盖得住，
        //      无跳变、无回退、无白空白。
        // · 两个 Tab 的日历、主按钮、三栏统计用同一套组件 + 同一个 month，
        //   位置尺寸字号完全一致，切换时上半部分静止不动。
        // · 顶部标题、切换胶囊、悬浮快捷栏在这层之外，完全不参与动画。
        Box(modifier = Modifier
            .weight(1f)
            .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (trainingTab) 1f else 0f)
                    .graphicsLayer {
                        val w = size.width
                        // 训练层：未选中时停在左侧 4% 处等待，选中时滑回原位
                        translationX = -progress.value * w * 0.04f
                    }
                    .then(if (trainingTab) Modifier else blockGestures)
            ) {
                TrainingDiaryContent(
                    vm = trainingVm,
                    onStartTraining = onStartTraining,
                    onEditSession = onEditTraining,
                    onBackfill = onStartTraining,
                    foodLogs = foodMonth,
                    dailyKcalGoal = profile?.dailyKcalGoal?.takeIf { it > 0 }?.toDouble() ?: 2000.0,
                    month = diaryMonth,
                    onMonthChange = { diaryMonth = it },
                    scrollState = trainingScrollState
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (trainingTab) 0f else 1f)
                    .graphicsLayer {
                        val w = size.width
                        // 心情层：未选中时停在右侧 4% 处等待，选中时滑回原位
                        translationX = (1f - progress.value) * w * 0.04f
                    }
                    .then(if (trainingTab) blockGestures else Modifier)
            ) {
                MoodDiaryContent(
                    diaries = diaries,
                    onOpenEdit = onOpenEdit,
                    onRequestDelete = { pendingDelete = it },
                    month = diaryMonth,
                    onMonthChange = { diaryMonth = it },
                    scrollState = scrollState
                )
            }
            // 右下角悬浮快捷按钮：两个 Tab 都显示，位置和其它页完全一致
            QuickActionColumn(
                onWater = onOpenWaterSheet,
                onWeight = onOpenWeight,
                onOpenFoodLibrary = onOpenFoodLibrary,
                onPhoto = onOpenImage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
            )
        }
    }

    // ---------- 清空全部确认 ----------
    if (showClearAll) {
        AppDialog(
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
            Text("会把所有心情日记都删掉，删了就找不回来了。建议先导出备份。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }

    // ---------- 删除单篇确认 ----------
    pendingDelete?.let { target ->
        AppDialog(
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
