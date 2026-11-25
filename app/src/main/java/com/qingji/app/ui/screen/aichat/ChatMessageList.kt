package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.ChatMessageUi

/**
 * 消息列表（工单 4.1~4.4）。
 *
 * 气泡最大宽度在这里算一次（BoxWithConstraints），再传给每条气泡——
 * 避免每个 item 各自套一层 BoxWithConstraints 造成额外的 subcomposition。
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessageUi>,
    context: String,
    hasOlderMessages: Boolean,
    isRunning: Boolean,
    listState: LazyListState,
    onLoadOlder: () -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 快捷问题作为列表最后一项挂出来（欢迎态和对话中都只有这一处）：
     * 不再固定占据输入框上方的空间，也不会在「首条消息发出」时从列表跳到底部。
     */
    quickPrompts: List<String> = emptyList(),
    onQuickPrompt: (String) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxBubbleWidth = maxWidth * ChatDimens.BubbleMaxWidthAi
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ChatDimens.ListHPad,
                end = ChatDimens.ListHPad,
                top = 6.dp,
                bottom = 6.dp
            ),
            verticalArrangement = Arrangement.spacedBy(ChatDimens.ListGap)
        ) {
            if (hasOlderMessages) {
                item(key = "older") {
                    OlderMessagesRow(enabled = !isRunning, onLoadOlder = onLoadOlder)
                }
            }
            // 来源标签：从「单独占一行的大胶囊」压成一行小字，保留信息但不割裂（工单 4.3）
            item(key = "context") {
                Text(
                    text = "来源 · $context",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    maxBubbleWidth = maxBubbleWidth,
                    canRetry = message.isError,
                    retryEnabled = !isRunning,
                    onRetry = { onRetry(message.id) }
                )
            }
            if (quickPrompts.isNotEmpty()) {
                item(key = "quick-prompts") {
                    // 列表内左右留白已由 LazyColumn 的 contentPadding 提供，这里不再叠加
                    QuickPrompts(
                        prompts = quickPrompts,
                        enabled = !isRunning,
                        onPick = onQuickPrompt,
                        modifier = Modifier.padding(top = 4.dp),
                        contentPadding = PaddingValues(0.dp)
                    )
                }
            }
        }
    }
}

/** 「加载更早消息」：保留原能力，只是从大按钮改成居中小胶囊。 */
@Composable
private fun OlderMessagesRow(enabled: Boolean, onLoadOlder: () -> Unit) {
    Text(
        text = "加载更早的消息",
        fontSize = 12.sp,
        color = QingJiGreenDark,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(QingJiGreenLight)
            .clickable(enabled = enabled) { onLoadOlder() }
            .padding(vertical = 7.dp)
    )
}
