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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import java.time.LocalDate

/**
 * 自律管理首页 Dashboard（SD-002）。
 * 顶栏「自律管理」+ 目标入口；无目标时引导设定；有目标时展示计时 / 今日 / 快捷 / 成长。
 * 按工单要求保持紧凑：默认只放计时、今日状态和入口，不堆说明小字。
 */
@Composable
fun SelfDisciplineDashboardScreen(
    onBack: () -> Unit,
    vm: SelfDisciplineViewModel,
    onOpenGoals: () -> Unit,
    onOpenCheckin: (Long) -> Unit,
    onOpenUrge: (Long) -> Unit,
    onOpenDeviation: (Long) -> Unit,
    onOpenHistory: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val primary by vm.primaryGoal.collectAsState()
    val stats by vm.stats.collectAsState()

    // 目标变了才重建 Flow，避免每次重组都换一个 Flow 让 collect 重启
    val checkinsFlow = remember(primary?.id) {
        primary?.let { vm.observeCheckins(it.id) } ?: flowOf(emptyList())
    }
    val checkins by checkinsFlow.collectAsState(initial = emptyList())

    // 周期列表：用来判断「上一轮是不是因为偏离结束了」，好给撤销入口
    val periodsFlow = remember(primary?.id) {
        primary?.let { vm.observePeriods(it.id) } ?: flowOf(emptyList())
    }
    val periods by periodsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val today = LocalDate.now()
    val todayCheckin = checkins.firstOrNull { it.localDate == today }

    Column(
        modifier = Modifier.fillMaxSize().background(PageBackground)
    ) {
        PageTopBar(
            title = "自律管理",
            onBack = onBack,
            actionText = "目标",
            onAction = onOpenGoals
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            if (primary == null) {
                // 无目标：引导
                SectionCard {
                    Text(
                        "还没有自律目标",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "设定一个想管理的行为目标，每天记录、冲动时干预，慢慢建立更好的习惯。",
                        fontSize = TextSizes.Body,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = "设定第一个目标", onClick = onOpenGoals)
                }
            } else {
                val goal = primary!!

                // A. 核心计时卡片
                SectionCard {
                    Text(
                        goal.title,
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${stats.currentDays}",
                            fontSize = TextSizes.HugeNumber,
                            fontWeight = FontWeight.Bold,
                            color = QingJiGreen
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("天", fontSize = TextSizes.SectionTitle, color = TextSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "开始于 ${goal.startDate.displayCn()}",
                            fontSize = TextSizes.Tiny, color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MilestoneTimeline(currentDays = stats.currentDays)
                }
                Spacer(Modifier.height(12.dp))

                // 上一轮因偏离结束：给「重新开始」和「撤销」两个出口。
                // 撤销入口常驻在这里，不依赖刚才那一次保存的页面，隔天回来也能改。
                val lastDeviation = periods
                    .filter {
                        it.endReason == SelfDisciplineContracts.REASON_DEVIATION && it.endAt != null
                    }
                    .maxByOrNull { it.endAt!! }
                if (stats.currentDays == 0 && lastDeviation != null) {
                    val endedAt = lastDeviation.endAt!!
                    SectionCard {
                        Text(
                            "本轮周期已结束",
                            fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "结束于 ${endedAt.displayCn()}（目标偏离）。历史记录都保留着。",
                            fontSize = TextSizes.Body,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = "从今天重新开始",
                            onClick = { scope.launch { vm.restartPeriod(goal.id) } }
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Dimens.ItemCorner))
                                .clickable {
                                    scope.launch {
                                        vm.undoDeviation(lastDeviation.id, endedAt, goal.id)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "记错了，撤销这次偏离",
                                fontSize = TextSizes.Body,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // B. 今日记录
                SectionCard {
                    Text(
                        "今日记录",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    if (todayCheckin == null) {
                        Text("今天还没记录", fontSize = TextSizes.Body, color = TextSecondary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColorOf(todayCheckin.status))
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                statusLabelOf(todayCheckin.status),
                                fontSize = TextSizes.Body, color = TextPrimary
                            )
                            if (todayCheckin.mood.isNotBlank()) {
                                Text(
                                    " · ${todayCheckin.mood}",
                                    fontSize = TextSizes.Body, color = TextSecondary
                                )
                            }
                            if (todayCheckin.urgeLevel > 0) {
                                Text(
                                    " · 冲动 ${todayCheckin.urgeLevel} 级",
                                    fontSize = TextSizes.Body, color = TextSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(
                            label = if (todayCheckin == null) "去打卡" else "改打卡",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCheckin(goal.id) }
                        )
                        ActionPill(
                            label = "记冲动",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenUrge(goal.id) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // C. 快捷功能
                SectionCard {
                    Text(
                        "快捷功能",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTile("每日打卡", Modifier.weight(1f)) { onOpenCheckin(goal.id) }
                        QuickTile("冲动记录", Modifier.weight(1f)) { onOpenUrge(goal.id) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTile("目标偏离", Modifier.weight(1f)) { onOpenDeviation(goal.id) }
                        QuickTile("历史记录", Modifier.weight(1f)) { onOpenHistory(goal.id) }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // D. 成长统计 + 最近 7 天
                SectionCard {
                    Text(
                        "成长统计",
                        fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    RecentSevenDays(checkins = checkins, today = today)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        GrowthItem("${stats.currentDays}", "当前连续", Modifier.weight(1f))
                        GrowthItem("${stats.bestDays}", "历史最长", Modifier.weight(1f))
                        GrowthItem("${stats.onTargetDays}", "累计符合", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        GrowthItem("${stats.deviationCount}", "偏离次数", Modifier.weight(1f))
                        GrowthItem("${stats.urgeCount}", "冲动次数", Modifier.weight(1f))
                        GrowthItem("${stats.streak}", "连续记录", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // E. 设置入口
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenSettings() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("模块设置", fontSize = TextSizes.Body, color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    Text("隐私 / 提醒 / 数据", fontSize = TextSizes.Tiny, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

/** 最近 7 天的小圆点，比一堆文字更省地方。 */
@Composable
private fun RecentSevenDays(
    checkins: List<SelfDisciplineCheckinEntity>,
    today: LocalDate
) {
    val byDate = checkins.associateBy { it.localDate }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    Column {
        Text("最近 7 天", fontSize = TextSizes.Tiny, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEach { d ->
                val status = byDate[d]?.status
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            // 未记录画浅灰实心点，和「暂不记录」的深灰也能区分
                            .background(if (status == null) DividerColor.copy(alpha = 0.35f) else statusColorOf(status))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${d.dayOfMonth}", fontSize = TextSizes.Tiny, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            .background(QingJiGreenLight)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = TextSizes.Body, color = QingJiGreen) }
}

@Composable
private fun ActionPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            .background(QingJiGreenLight)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = TextSizes.Body, color = QingJiGreen) }
}

@Composable
private fun GrowthItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}
