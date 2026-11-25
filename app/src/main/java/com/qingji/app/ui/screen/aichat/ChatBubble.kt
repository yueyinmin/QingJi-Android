package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ai.agent.TrustLabel
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.ChatMessageUi
import com.qingji.app.ui.viewmodel.ChatRole

/**
 * 单条消息气泡（工单 4.1）。
 *
 * 和旧实现的差别：
 * 1. 用户消息改成右侧浅色气泡 + 自己这侧收一个小圆角，不再带「我」这种多余标签；
 * 2. AI 消息同样收角，名字用 11sp 次要色，正文 14sp / 21sp 行高；
 * 3. 失败消息的重试入口搬进气泡内部（见 [RetryAction]），不再往输入区插一个大按钮。
 */
@Composable
fun ChatBubble(
    message: ChatMessageUi,
    maxBubbleWidth: Dp,
    canRetry: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .clip(
                    if (isUser) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
                )
                .background(if (isUser) ChatUserBubble else ChatAiBubble)
                .padding(horizontal = 12.dp, vertical = if (isUser) 10.dp else 11.dp)
        ) {
            if (!isUser) {
                Text(
                    text = "AI 管家",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = message.text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = if (message.isError) DangerRed else TextPrimary
            )

            if (message.tools.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已查 " + message.tools.size + " 项 · " +
                        message.tools.joinToString("、") { it.name },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary
                )
            }

            message.conclusions.forEach { conclusion ->
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(QingJiGreenLight)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = conclusion.trust.name,
                            fontSize = 9.sp,
                            color = if (
                                conclusion.trust == TrustLabel.FACT ||
                                conclusion.trust == TrustLabel.CALCULATED
                            ) QingJiGreenDark else TextSecondary
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = conclusion.text + conclusion.dataRange?.let { "（$it）" }.orEmpty(),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextPrimary
                    )
                }
            }

            if (message.warnings.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = message.warnings.joinToString("；"),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary
                )
            }

            if (message.isError && canRetry) {
                RetryAction(enabled = retryEnabled, onRetry = onRetry)
            }
        }
    }
}

/** 失败气泡内的紧凑重试入口：文字型小胶囊，不改动输入区结构。 */
@Composable
private fun RetryAction(enabled: Boolean, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = "重试",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) QingJiGreenDark else TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreenLight)
                .clickable(enabled = enabled) { onRetry() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
