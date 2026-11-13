package com.qingji.app.ui.screen.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.viewmodel.TrainingViewModel

/**
 * 「我的 → 训练记录」全屏页。
 * 存在的意义：底部栏里「日记」那一栏是可以在设置里隐藏的，
 * 隐藏之后训练日记仍然要有地方进得去（需求手册第五节漏了这条）。
 */
@Composable
fun TrainingHistoryScreen(
    vm: TrainingViewModel,
    onBack: () -> Unit,
    onStartTraining: () -> Unit,
    onEditSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "训练日记", onBack = onBack)
        TrainingDiaryContent(
            vm = vm,
            onStartTraining = onStartTraining,
            onEditSession = onEditSession,
            showModeSwitch = false,
            modifier = Modifier.weight(1f)
        )
    }
}
