package com.qingji.app.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.QuickActionColumn
import com.qingji.app.ui.screen.training.MoodDiaryContent
import com.qingji.app.ui.screen.training.TrainingDiaryContent
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.viewmodel.DiaryViewModel
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 日记页（v1.0.10 改造）
 * ------------------------------------------------------------
 * 主人要求：训练日记是默认页；心情日记复用训练日记的结构和规则，
 * 只是记录心情好坏。所以两个视图共用同一套骨架：
 *   顶部「心情日记 / 训练日记」胶囊 → 主操作按钮 → 本月总结 → 月历 → 历史列表 → 明细弹层
 * 训练日记读训练会话，心情日记读 Diary 并按 DiaryRules.level 四档着色。
 *
 * 原来心情日记那套「近 30 天小格子 + 玻璃罐 + 排序胶囊」已整体替换，
 * 心情评分规则（1-2 低 / 3 中 / 4-5 高）仍是 domain/DiaryRules.kt 那一套，没重写。
 * ============================================================
 */
@Composable
fun DiaryScreen(
    onOpenEdit: (Long) -> Unit,
    onOpenWeight: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    /** v3.2.4：悬浮栏第二颗换成食物库 */
    onOpenFoodLibrary: () -> Unit = {},
    /** 默认进训练日记（主人 2026-11-13 指定） */
    trainingTab: Boolean = true,
    onTrainingTabChange: (Boolean) -> Unit = {},
    onStartTraining: () -> Unit = {},
    onEditTraining: () -> Unit = {},
    /** 与首页 / 训练页共用同一个实例，草稿状态不打架 */
    trainingVm: TrainingViewModel = viewModel(),
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    vm: DiaryViewModel = viewModel()
) {
    val diaries by vm.diaries.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Diary?>(null) }
    var showClearAll by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        if (trainingTab) {
            TrainingDiaryContent(
                vm = trainingVm,
                onStartTraining = onStartTraining,
                onEditSession = onEditTraining,
                showModeSwitch = true,
                onSwitchToMood = { onTrainingTabChange(false) }
            )
        } else {
            MoodDiaryContent(
                diaries = diaries,
                onOpenEdit = onOpenEdit,
                onSwitchToTraining = { onTrainingTabChange(true) },
                onClearAll = { showClearAll = true },
                onRequestDelete = { pendingDelete = it },
                scrollState = scrollState
            )
            // 心情日记保留原来的三个快捷按钮（水 / 体重 / 食物库 / 照片）
            QuickActionColumn(
                onWater = { vm.addWater(200) },
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
