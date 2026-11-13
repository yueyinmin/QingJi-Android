package com.qingji.app.ui.screen.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.prefs.WorkoutDraftStore
import com.qingji.app.domain.TrainingMath
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.TrainingViewModel
import kotlinx.coroutines.delay

/**
 * 进行中训练页。
 * 计时只依赖 startedAt 时间戳 + 草稿里的暂停累计，所以杀进程回来能对上；
 * 每改一组都同步落草稿，不会「练了半小时、接个电话全没了」。
 */
@Composable
fun TrainingActiveScreen(
    vm: TrainingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft by vm.draft.collectAsState()
    val now by vm.now.collectAsState()
    val unit by vm.unit.collectAsState()
    val library by vm.exercises.collectAsState()
    val message by vm.message.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // 进页面：没有草稿就开一次新训练；每秒刷一次计时显示
    LaunchedEffect(Unit) {
        vm.startNew()
        // P1：动作详情「用这个动作开始训练」带过来的动作，第一帧就加进草稿
        vm.consumePendingExercise()?.let { vm.addExercise(it) }
        while (true) {
            vm.tick()
            delay(1000)
        }
    }
    // 草稿里的备注同步到输入框（编辑旧会话时要用）
    LaunchedEffect(draft?.sessionUuid) {
        noteText = draft?.note.orEmpty()
    }

    val d = draft
    val allSets = d?.exercises.orEmpty().flatMap { it.sets }
    val volume = TrainingMath.sessionVolume(allSets)
    val paused = (d?.pausedAt ?: 0L) > 0L

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = if (d?.editingUuid.isNullOrEmpty()) "开始训练" else "修改训练", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(8.dp))
            if (message != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .padding(10.dp)
                ) {
                    Text(message.orEmpty(), fontSize = TextSizes.Caption, color = QingJiGreen)
                }
                Spacer(Modifier.height(8.dp))
            }

            if (d != null) {
                WorkoutTimerBar(
                    elapsedSeconds = d.elapsedSeconds(now),
                    paused = paused,
                    editing = d.editingUuid.isNotEmpty(),
                    onTogglePause = { vm.togglePause() }
                )
                Spacer(Modifier.height(10.dp))

                // kg / lb 只影响显示与输入，库里始终存 kg
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("重量单位", fontSize = TextSizes.Caption, color = TextSecondary,
                        modifier = Modifier.weight(1f))
                    listOf(WorkoutDraftStore.UNIT_KG, WorkoutDraftStore.UNIT_LB).forEach { u ->
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(Dimens.PillCorner))
                                .background(if (unit == u) QingJiGreen else QingJiGreenLight)
                                .clickable { vm.setUnit(u) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(u.uppercase(), fontSize = TextSizes.Caption,
                                color = if (unit == u) CardWhite else QingJiGreen)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                d.exercises.forEach { e ->
                    SetEditorCard(
                        name = e.name,
                        sets = e.sets,
                        unit = unit,
                        fromLast = e.fromLast,
                        onUpdate = { index, set -> vm.updateSet(e.key, index, set) },
                        onAdd = { vm.addSet(e.key) },
                        onRemoveSet = { index -> vm.removeSet(e.key, index) },
                        onRemoveExercise = { vm.removeExercise(e.key) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ 添加动作", fontSize = TextSizes.Body)
                }
                Spacer(Modifier.height(12.dp))

                SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                    Text("训练备注（可不填）", fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = noteText,
                        onValueChange = {
                            noteText = it
                            vm.setNote(it)
                        },
                        textStyle = TextStyle(fontSize = TextSizes.Body, color = TextPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.TinyCorner))
                            .background(DividerColor.copy(alpha = 0.45f))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            if (noteText.isEmpty()) Text("今天状态怎么样？", fontSize = TextSizes.Caption, color = TextSecondary)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))

                SectionCard(contentPadding = Dimens.CardPaddingCompact) {
                    Text("本次小结", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("${d.exercises.size} 个动作 · ${allSets.size} 组 · 训练量 %.1f kg".format(volume),
                        fontSize = TextSizes.Body, color = TextPrimary)
                    Text("训练量是估算值，仅供参考，不是医学结论。",
                        fontSize = TextSizes.Tiny, color = TextSecondary)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // 底部固定操作条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                // 实测：手势导航下，按钮贴近屏幕底边时会有一截点不到。
                // 主动给底部导航条让位，把整排按钮抬到手势区之上。
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { showDiscard = true }, modifier = Modifier.weight(1f)) {
                Text("放弃", fontSize = TextSizes.Body)
            }
            Button(
                onClick = { vm.save { ok -> if (ok) onBack() } },
                modifier = Modifier.weight(2f)
            ) {
                Text("保存训练", fontSize = TextSizes.Body)
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = library,
            onPick = { vm.addExercise(it) },
            onAddCustom = { name, category -> vm.addCustomExercise(name, category) },
            onDismiss = { showPicker = false }
        )
    }

    if (showDiscard) {
        AppDialog(
            title = "放弃这次训练？",
            confirmText = "放弃",
            danger = true,
            onConfirm = {
                vm.discard()
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false }
        ) {
            Text("还没保存的动作和组数据都会清掉，已经保存过的训练不受影响。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }
}
