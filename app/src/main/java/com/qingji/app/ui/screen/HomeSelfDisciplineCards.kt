package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.components.IconTones
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.container
import com.qingji.app.ui.screen.selfdiscipline.statusLabelOf
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SelfDisciplineViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf


/**
 * 首页「今日自律」菜单（SD-001，2026-11-13 升级为菜单）。
 * 放在今日训练卡下面：上面一行是目标与今日状态，下面三个入口直接可用，
 * 不再需要先点进模块首页再找按钮。没建目标时整块不显示。
 */
@Composable
internal fun SelfDisciplineQuickCard(
    vm: SelfDisciplineViewModel,
    onOpen: () -> Unit,
    onCheckin: () -> Unit,
    onUrge: () -> Unit,
    onHistory: () -> Unit
) {
    val primary by vm.primaryGoal.collectAsState()
    val stats by vm.stats.collectAsState()
    val checkinsFlow = remember(primary?.id) {
        primary?.let { vm.observeCheckins(it.id) } ?: flowOf(emptyList())
    }
    val checkins by checkinsFlow.collectAsState(initial = emptyList())

    val goal = primary ?: return
    val todayStatus = checkins.firstOrNull { it.localDate == LocalDate.now() }?.status

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "今日自律",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(goal.title, fontSize = TextSizes.Caption, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已坚持 ${stats.currentDays} 天",
                        fontSize = TextSizes.Caption,
                        fontWeight = FontWeight.Medium,
                        color = QingJiGreen
                    )
                    Text("  ·  ", fontSize = TextSizes.Caption, color = TextSecondary)
                    Text(
                        if (todayStatus == null) "今天还没记录" else "今天：${statusLabelOf(todayStatus)}",
                        fontSize = TextSizes.Caption,
                        color = if (todayStatus == null) TextSecondary else QingJiGreen
                    )
                }
            }
            Text(
                "查看全部 ›",
                fontSize = TextSizes.Caption,
                color = QingJiGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen() }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelfDisciplineMenuItem("每日打卡", Icons.Outlined.CheckCircle, IconTones.Green, Modifier.weight(1f), onCheckin)
            SelfDisciplineMenuItem("记一次冲动", Icons.Filled.Bolt, IconTones.Orange, Modifier.weight(1f), onUrge)
            SelfDisciplineMenuItem("历史记录", Icons.Outlined.History, IconTones.Blue, Modifier.weight(1f), onHistory)
        }
    }
}


/** 今日自律菜单的一格：浅色圆角底 + 图标 + 名称。 */
@Composable
internal fun SelfDisciplineMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: com.qingji.app.ui.components.IconTone,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tone.container)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tone.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = TextSizes.Tiny, color = TextPrimary, maxLines = 1)
    }
}
