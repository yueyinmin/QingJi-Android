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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

    // 两个 Tab 的月份 / 滚动状态由本页持有，内容常驻后更是天然不会丢。
    var trainingMonth by remember { mutableStateOf(YearMonth.now()) }
    val trainingScrollState = rememberScrollState()
    val density = LocalDensity.current
    val slidePx = with(density) { 16.dp.toPx() }

    // 频闪修复：两个内容常驻组合树，只改绘制层的 alpha / 位移。
    // 旧版用 AnimatedContent，每次切换都会 dispose 旧内容、重建新内容，
    // 公共日历/统计组件跟着反复初始化，动画期间还同时画两份 → 空白帧与重影。
    // 现在切换只有 4 个 Float 动画，读在 graphicsLayer 绘制块里，子组件零重组。
    val spec: androidx.compose.animation.core.AnimationSpec<Float> =
        tween(200, easing = FastOutSlowInEasing)
    // 故意不用 by 解构：.value 读在 graphicsLayer 的绘制块里，
    // 动画每帧只重绘，不触发 DiaryScreen 或子内容的重组。
    val trainAlpha = animateFloatAsState(if (trainingTab) 1f else 0f, spec, label = "trainAlpha")
    val moodAlpha = animateFloatAsState(if (trainingTab) 0f else 1f, spec, label = "moodAlpha")
    val trainShift = animateFloatAsState(if (trainingTab) 0f else -slidePx, spec, label = "trainShift")
    val moodShift = animateFloatAsState(if (trainingTab) slidePx else 0f, spec, label = "moodShift")
    // 非激活那一层虽然透明，但仍会命中点击，必须把事件吞掉
    val blockGestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
                // 一次手势结束就退出，等下一次 awaitEachGesture
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
            DiaryModeSwitch(training = trainingTab, onChange = { onTrainingTabChange(it) })
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 内容区：两个 Tab 常驻，只切换可见性与轻微位移 ----------
        // · 不再用 AnimatedContent：它每次切换会 dispose 旧内容、重建新内容，
        //   公共日历/统计组件反复初始化，动画期间两页同时绘制 → 空白帧和重影（频闪）。
        // · 现在两份内容始终在组合树里，切换只改 graphicsLayer，零重建、零重查；
        //   月份与滚动位置由本页持有，快速连点也只是把动画目标改到最新值。
        // · 顶部标题、切换胶囊、悬浮快捷栏在这层之外，完全不参与动画。
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = trainAlpha.value
                        translationX = trainShift.value
                    }
                    .then(if (trainingTab) Modifier else blockGestures)
            ) {
                TrainingDiaryContent(
                    vm = trainingVm,
                    onStartTraining = onStartTraining,
                    onEditSession = onEditTraining,
                    foodLogs = foodMonth,
                    dailyKcalGoal = profile?.dailyKcalGoal?.takeIf { it > 0 }?.toDouble() ?: 2000.0,
                    month = trainingMonth,
                    onMonthChange = { trainingMonth = it },
                    scrollState = trainingScrollState
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = moodAlpha.value
                        translationX = moodShift.value
                    }
                    .then(if (trainingTab) blockGestures else Modifier)
            ) {
                MoodDiaryContent(
                    diaries = diaries,
                    onOpenEdit = onOpenEdit,
                    onRequestDelete = { pendingDelete = it },
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
