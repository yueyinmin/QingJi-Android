package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * AI 管家专用顶栏（工单 QJ-AI-CHAT-UI-V2 · 3.1）。
 *
 * 和全 App 的 [com.qingji.app.ui.components.PageTopBar] 的区别：
 * 右侧要放两个图标按钮（新建会话 + 更多），文字型 actionText 放不下；
 * 而且高度必须恒定，不能因为键盘或内容变化而抖动。
 */
@Composable
fun AiChatTopBar(
    title: String,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
    newEnabled: Boolean,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ChatDimens.BarHeight)
            .background(CardWhite)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChatBarIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "返回",
            enabled = true,
            onClick = onBack
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        ChatBarIcon(
            icon = Icons.Outlined.Add,
            description = "新建会话",
            enabled = newEnabled,
            onClick = onNewConversation
        )
        ChatBarIcon(
            icon = Icons.Outlined.MoreVert,
            description = "更多",
            enabled = true,
            onClick = onOpenMore
        )
    }
}

/** 顶栏图标按钮：固定 40dp 触控区，图标 20dp，禁用时变次要色。 */
@Composable
private fun ChatBarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ChatDimens.ActionSize)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.size(ChatDimens.ActionIcon)
        )
    }
}
