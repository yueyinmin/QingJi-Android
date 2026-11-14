package com.qingji.app.ui.screen.selfdiscipline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
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
 * 自律管理设置（SD-012，P0 轻量版）。
 * 模块开关 / 默认目标 / 隐私显示 / 清空数据。
 * 所有数据只在本机数据库，不上传、不同步；清空必须二次确认。
 */
@Composable
fun SelfDisciplineSettingsScreen(
    onBack: () -> Unit,
    vm: SelfDisciplineViewModel,
    onOpenGoals: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val goals by vm.goals.collectAsState(initial = emptyList())
    val primary by vm.primaryGoal.collectAsState()

    var enabled by remember { mutableStateOf(true) }
    var privacyHidden by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabled = (vm.getSetting(SelfDisciplineContracts.SETTING_ENABLED) ?: "1") != "0"
        privacyHidden = (vm.getSetting(SelfDisciplineContracts.SETTING_PRIVACY_HIDDEN) ?: "0") == "1"
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "自律管理设置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            SectionCard {
                SettingSwitchRow(
                    title = "启用自律管理",
                    subtitle = "关掉后入口卡片不再显示，数据全部保留",
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        scope.launch {
                            vm.setSetting(
                                SelfDisciplineContracts.SETTING_ENABLED,
                                if (it) "1" else "0"
                            )
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow(
                    title = "隐私显示",
                    subtitle = "在公共场合隐藏具体行为词，只显示天数",
                    checked = privacyHidden,
                    onCheckedChange = {
                        privacyHidden = it
                        scope.launch {
                            vm.setSetting(
                                SelfDisciplineContracts.SETTING_PRIVACY_HIDDEN,
                                if (it) "1" else "0"
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "默认展示目标",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "管理目标",
                        fontSize = TextSizes.Caption,
                        color = QingJiGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenGoals() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (goals.isEmpty()) {
                    Text("还没有目标", fontSize = TextSizes.Body, color = TextSecondary)
                } else {
                    goals.forEach { goal ->
                        val selected = primary?.id == goal.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.ItemCorner))
                                .background(if (selected) QingJiGreenLight else PageBackground)
                                .clickable(enabled = goal.isEnabled) {
                                    if (!selected) scope.launch { vm.setPrimaryGoal(goal.id) }
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                goal.title,
                                fontSize = TextSizes.Body,
                                color = if (goal.isEnabled) TextPrimary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when {
                                    selected -> "当前显示"
                                    !goal.isEnabled -> "已暂停"
                                    else -> "设为显示"
                                },
                                fontSize = TextSizes.Tiny,
                                color = if (selected) QingJiGreen else TextSecondary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(
                    "隐私说明",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "所有记录只保存在本机，不上传、不同步。",
                    "提醒文案是中性提示，锁屏不显示具体行为。",
                    "不申请与功能无关的权限。",
                    "清空数据需要二次确认。"
                ).forEach {
                    Text("· $it", fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .clickable(enabled = goals.isNotEmpty()) { showClearDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "清空自律数据",
                            fontSize = TextSizes.Body,
                            color = if (goals.isEmpty()) TextSecondary else DangerRed
                        )
                        Text(
                            "删除全部目标、打卡、冲动与周期记录",
                            fontSize = TextSizes.Tiny,
                            color = TextSecondary
                        )
                    }
                    Text(
                        if (goals.isEmpty()) "无数据" else "${goals.size} 个目标",
                        fontSize = TextSizes.Tiny,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }

    if (showClearDialog) {
        AppDialog(
            title = "清空全部自律数据？",
            onDismiss = { showClearDialog = false },
            confirmText = "全部删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    goals.forEach { vm.deleteGoal(it.id) }
                }
                showClearDialog = false
            }
        ) {
            Text(
                "所有目标、周期、打卡、冲动记录都会被删除，无法恢复。这个操作只影响自律管理模块，其他数据不受影响。",
                fontSize = TextSizes.Body,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = TextSizes.Body, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = TextSizes.Tiny, color = TextSecondary)
        }
        Spacer(Modifier.height(0.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CardWhite,
                checkedTrackColor = QingJiGreen
            )
        )
    }
}
