package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * 统一输入容器（工单 5.1~5.5）。
 *
 * 结构：一个圆角容器里，上面是自动增高的输入框，下面是一排固定尺寸的图标按钮。
 * 为什么这么改：旧实现是「输入框 + 一行 Material Button」两个独立控件，
 * 四个按钮各占 weight，窄屏下「停止生成」这种四字标签会被挤成两行，
 * 整个操作区高度一变，聊天区就被顶得跳一下。
 *
 * 现在：
 * · 图标按钮固定 40dp 触控区，永远不会被文字撑开；
 * · 输入框 maxLines 到时内部滚动，不会把容器无限拉高；
 * · 重试不再放在这里（见 ChatBubble 里的气泡内入口）。
 */
@Composable
fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    hasAttachment: Boolean,
    onClearAttachment: () -> Unit,
    onPickImage: () -> Unit,
    onStartVoice: () -> Unit,
    isRunning: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(ChatDimens.ComposerCorner)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardWhite)
            .border(
                width = 1.dp,
                color = if (focused) QingJiGreen.copy(alpha = 0.45f) else ChatComposerBorder,
                shape = shape
            )
            .padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        if (hasAttachment) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已附图片",
                    fontSize = 11.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "移除",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = !isRunning) { onClearAttachment() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = TextPrimary
            ),
            cursorBrush = SolidColor(QingJiGreen),
            maxLines = ChatDimens.ComposerMaxLines,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isRunning) onSend() }),
            decorationBox = { innerTextField ->
                Box {
                    if (input.isEmpty()) {
                        Text(
                            text = "问一句今天的饮食、训练或计划…",
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ComposerIconButton(
                icon = Icons.Outlined.Image,
                description = "选择图片",
                enabled = !isRunning,
                onClick = onPickImage
            )
            ComposerIconButton(
                icon = Icons.Outlined.Mic,
                description = "语音输入",
                enabled = !isRunning,
                onClick = onStartVoice
            )
            Spacer(Modifier.weight(1f))
            SendButton(
                isRunning = isRunning,
                enabled = input.isNotBlank(),
                onSend = onSend,
                onCancel = onCancel
            )
        }
    }
}

/** 输入区左侧图标按钮：40dp 触控区 / 20dp 图标，禁用态变淡。 */
@Composable
private fun ComposerIconButton(
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
            tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(ChatDimens.ActionIcon)
        )
    }
}

/**
 * 圆形发送按钮：三种状态
 * · 空输入 → 灰底禁用
 * · 有输入 → 主题色可点
 * · 生成中 → 主题色 + 停止图标（点一下取消，能力与旧「停止生成」一致）
 */
@Composable
private fun SendButton(
    isRunning: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val active = isRunning || enabled
    Box(
        modifier = Modifier
            .size(ChatDimens.SendSize)
            .clip(CircleShape)
            .background(if (active) QingJiGreen else DividerColor)
            .clickable {
                if (isRunning) onCancel() else onSend()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
            contentDescription = if (isRunning) "停止生成" else "发送",
            tint = if (active) androidx.compose.ui.graphics.Color.White else TextSecondary,
            modifier = Modifier.size(ChatDimens.SendIcon)
        )
    }
}
