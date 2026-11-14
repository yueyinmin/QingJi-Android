package com.qingji.app.ui.screen.selfdiscipline

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import kotlinx.coroutines.launch

/**
 * 目标管理列表（SD-003）。
 * 支持多个目标并存：新建 / 编辑 / 暂停 / 恢复 / 设为首页 / 删除（二次确认）。
 * 暂停只改 isEnabled，历史周期和打卡一条都不动。
 */
@Composable
fun SelfDisciplineGoalsScreen(
    onBack: () -> Unit,
    vm: SelfDisciplineViewModel,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenCheckin: (Long) -> Unit
) {
    val goals by vm.goals.collectAsState(initial = emptyList())
    val primary by vm.primaryGoal.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<SelfDisciplineGoalEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = "自律目标",
            onBack = onBack,
            actionText = "新建",
            onAction = onCreate
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            if (goals.isEmpty()) {
                SectionCard {
                    Text(
                        "还没有目标",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "一个目标就是一段想坚持的行为改变。可以建多个，首页只突出显示其中一个。",
                        fontSize = TextSizes.Body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "新建目标", onClick = onCreate)
                }
            } else {
                goals.forEach { goal ->
                    GoalCard(
                        goal = goal,
                        isPrimary = primary?.id == goal.id,
                        onCheckin = { onOpenCheckin(goal.id) },
                        onSetPrimary = { scope.launch { vm.setPrimaryGoal(goal.id) } },
                        onToggleEnabled = { scope.launch { vm.setGoalEnabled(goal.id, !goal.isEnabled) } },
                        onEdit = { onEdit(goal.id) },
                        onDelete = { pendingDelete = goal }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "暂停或结束目标都不会删除历史记录；删除才会连同周期和打卡一起清掉。",
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    pendingDelete?.let { goal ->
        AppDialog(
            title = "删除「${goal.title}」？",
            onDismiss = { pendingDelete = null },
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch { vm.deleteGoal(goal.id) }
                pendingDelete = null
            }
        ) {
            Text(
                "这个目标名下的所有周期、打卡、冲动记录都会一起删除，无法恢复。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GoalCard(
    goal: SelfDisciplineGoalEntity,
    isPrimary: Boolean,
    onCheckin: () -> Unit,
    onSetPrimary: () -> Unit,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                goal.title,
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            // 状态胶囊：进行中 / 已暂停
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (goal.isEnabled) QingJiGreenLight else PageBackground)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (goal.isEnabled) QingJiGreen else TextSecondary)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    if (goal.isEnabled) "进行中" else "已暂停",
                    fontSize = TextSizes.Tiny,
                    color = if (goal.isEnabled) QingJiGreen else TextSecondary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(SelfDisciplineContracts.GOAL_TYPE_LABELS[goal.goalType] ?: "自定义目标")
                append(" · 开始于 ")
                append(goal.startDate.displayCn())
                append(" · ")
                append(if (goal.targetDays > 0) "${goal.targetDays} 天目标" else "长期目标")
            },
            fontSize = TextSizes.Caption,
            color = TextSecondary
        )
        if (isPrimary) {
            Spacer(Modifier.height(6.dp))
            Text("首页突出显示", fontSize = TextSizes.Tiny, color = QingJiGreen)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GoalAction("去打卡", QingJiGreen, onCheckin)
            if (goal.isEnabled && !isPrimary) GoalAction("设为首页", QingJiGreen, onSetPrimary)
            GoalAction(if (goal.isEnabled) "暂停" else "恢复", QingJiGreen, onToggleEnabled)
            GoalAction("编辑", QingJiGreen, onEdit)
            GoalAction("删除", DangerRed, onDelete)
        }
    }
}

@Composable
private fun GoalAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = TextSizes.Caption,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}
