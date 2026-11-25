package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * 顶栏「更多」面板（工单 3.2）。
 *
 * 记忆、提醒、历史会话这三个入口原来和会话胶囊挤在同一行里，
 * 现在收进这里：顶栏只留「新建会话」和「更多」，视线不再被一排胶囊切碎。
 * 三个入口一个没删，只是换了位置。
 */
@Composable
fun ChatMoreSheet(
    historyEnabled: Boolean,
    onOpenHistory: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenNudge: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        title = "更多",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MoreRow(
                icon = Icons.Outlined.History,
                title = "历史会话",
                subtitle = "切换、改名或删除以前的对话",
                enabled = historyEnabled,
                onClick = onOpenHistory
            )
            MoreRow(
                icon = Icons.Outlined.Psychology,
                title = "长期记忆",
                subtitle = "管家记住的内容，可纠正或删除",
                enabled = true,
                onClick = onOpenMemory
            )
            MoreRow(
                icon = Icons.Outlined.Notifications,
                title = "管家提醒",
                subtitle = "开关、静默时段与提醒历史",
                enabled = true,
                onClick = onOpenNudge
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(QingJiGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = QingJiGreenDark,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}
