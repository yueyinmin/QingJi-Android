package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.ai.AiConversationSummary
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史会话列表（工单 3.2）。
 *
 * 改造前：会话、改名、删除全挤在页面顶部一行横向滚动的小胶囊里，
 * 会话一多就变成一条找不着的长条；而且「改名 / 删除」永远只对当前会话生效，
 * 想改上一个会话必须先切过去。
 *
 * 现在：列表单独放进底部弹层，当前会话有明确选中态，改名 / 删除挂到每一行的
 * 「更多」里——能力一条没少，只是从顶部挪到了它该在的地方。
 * 顶栏的新建按钮是唯一入口，这里不再放第二个「新会话」。
 */
@Composable
fun ConversationListSheet(
    conversations: List<AiConversationSummary>,
    activeId: String?,
    canEdit: Boolean,
    onSelect: (String) -> Unit,
    onRename: (AiConversationSummary) -> Unit,
    onDelete: (AiConversationSummary) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(
        title = "历史会话",
        subtitle = "点一条切过去；右边的「⋮」可改名或删除",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            conversations.forEach { conversation ->
                ConversationRow(
                    conversation = conversation,
                    selected = conversation.id == activeId,
                    canEdit = canEdit,
                    // 只剩一个会话时不允许删（旧逻辑：至少留一个）
                    canDelete = conversations.size > 1,
                    onSelect = { onSelect(conversation.id) },
                    onRename = { onRename(conversation) },
                    onDelete = { onDelete(conversation) }
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: AiConversationSummary,
    selected: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) QingJiGreenLight else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = canEdit) { onSelect() }
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) QingJiGreenDark else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = conversation.entryPage + " · " + formatTime(conversation.updatedAt),
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(enabled = canEdit) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "会话操作",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("改名", fontSize = 14.sp) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "删除",
                            fontSize = 14.sp,
                            color = if (canDelete) DangerRed else TextSecondary.copy(alpha = 0.5f)
                        )
                    },
                    enabled = canDelete,
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "" else runCatching { timeFormat.format(Date(millis)) }.getOrDefault("")
