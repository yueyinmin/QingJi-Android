package com.qingji.app.ui.screen

import androidx.compose.foundation.ScrollState
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

        // ---------- 内容区：两个 Tab 从同一位置开始 ----------
        Box(modifier = Modifier.weight(1f)) {
            if (trainingTab) {
                TrainingDiaryContent(
                    vm = trainingVm,
                    onStartTraining = onStartTraining,
                    onEditSession = onEditTraining,
                    foodLogs = foodMonth,
                    dailyKcalGoal = profile?.dailyKcalGoal?.takeIf { it > 0 }?.toDouble() ?: 2000.0
                )
            } else {
                MoodDiaryContent(
                    diaries = diaries,
                    onOpenEdit = onOpenEdit,
                    onRequestDelete = { pendingDelete = it },
                    scrollState = scrollState
                )
            }
            // 右下角悬浮快捷按钮：两个 Tab 都显示，位置和其它页完全一致
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
