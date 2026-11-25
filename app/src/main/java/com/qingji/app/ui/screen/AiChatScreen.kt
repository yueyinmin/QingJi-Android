package com.qingji.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.ai.agent.TrustLabel
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.AiChatUiState
import com.qingji.app.ui.viewmodel.AiChatViewModel
import com.qingji.app.ui.viewmodel.ChatMessageUi
import com.qingji.app.ui.viewmodel.ChatRole

/** 统一 AI 管家对话页。会话与长期记忆都放在独立 AI 库，删除会话不影响记忆。 */
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    context: String = "首页",
    onOpenMemory: () -> Unit = {},
    vm: AiChatViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val quickScroll = rememberScrollState()
    val conversationScroll = rememberScrollState()
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    val appContext = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            vm.onInputChange("图片读取失败，请换一张")
        } else {
            val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            vm.attachImage(Base64.encodeToString(bytes, Base64.NO_WRAP), mime)
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            vm.onVoiceResult(text)
        }
    }

    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想问的，识别后可修改")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { vm.onInputChange("这台设备没有语音识别，请手动输入") }
    }

    LaunchedEffect(context) { vm.setContext(context) }
    LaunchedEffect(state.messages.size, state.pendingMessage?.text) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(
            title = "轻记 AI 管家",
            onBack = onBack,
            actionText = "新会话",
            onAction = { vm.newConversation() }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(conversationScroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话",
                fontSize = 12.sp,
                color = TextSecondary
            )
            state.conversations.forEach { conversation ->
                val selected = conversation.id == state.activeConversationId
                Text(
                    text = conversation.title,
                    fontSize = 12.sp,
                    color = if (selected) Color.White else QingJiGreenDark,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) QingJiGreenDark else QingJiGreenLight)
                        .clickable(enabled = !state.isRunning) {
                            vm.selectConversation(conversation.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            val activeTitle = state.conversations.firstOrNull {
                it.id == state.activeConversationId
            }?.title
            if (activeTitle != null) {
                Text(
                    text = "改名",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .clickable(enabled = !state.isRunning) {
                            renameTargetId = state.activeConversationId
                            renameText = activeTitle
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    text = "删除",
                    fontSize = 12.sp,
                    color = Color(0xFFC0392B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .clickable(enabled = !state.isRunning && state.conversations.size > 1) {
                            deleteTargetId = state.activeConversationId
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            // 阶段 I：记忆入口常驻；会话删了记忆还在，所以入口不能只在会话菜单里
            Text(
                text = "记忆",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .clickable(enabled = !state.isRunning) { onOpenMemory() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.hasOlderMessages) {
                item {
                    Text(
                        text = "加载更早消息",
                        fontSize = 12.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(QingJiGreenLight)
                            .clickable(enabled = !state.isRunning) { vm.loadOlderMessages() }
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上下文：${state.context}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(QingJiGreenLight)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            items(state.messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(quickScroll)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("我今天吃得怎么样？", "这周训练怎么样？", "我的训练计划是什么？").forEach { question ->
                Text(
                    text = question,
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .clickable(enabled = !state.isRunning) {
                            vm.onInputChange(question)
                            vm.send(question)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (state.attachedImageBase64 != null) {
            Text(
                text = "已附图片 · 发送前可继续补充问题",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // 阶段 I：刚记住的内容给一行反馈，并可跳到记忆管理去改
        state.justRemembered?.let { content ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已记住：$content",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "管理",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .clickable { onOpenMemory() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = vm::onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("问一句今天的饮食、训练或计划…", fontSize = 14.sp) },
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { vm.send() })
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { pickImage.launch("image/*") },
                enabled = !state.isRunning,
                modifier = Modifier.weight(0.6f)
            ) {
                Text("图片")
            }
            Button(
                onClick = { startVoice() },
                enabled = !state.isRunning,
                modifier = Modifier.weight(0.6f)
            ) {
                Text("语音")
            }
            Button(
                onClick = { if (state.isRunning) vm.cancel() else vm.send() },
                enabled = state.isRunning || state.input.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isRunning) "停止生成" else "发送")
            }
            if (!state.isRunning && state.messages.any { it.isError }) {
                Button(onClick = { vm.retry() }, modifier = Modifier.weight(1f)) {
                    Text("重试")
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                fontSize = 12.sp,
                color = Color(0xFFC0392B),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }

    val targetRenameId = renameTargetId
    if (targetRenameId != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text("修改会话名称") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text("给这段对话起个名字") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameConversation(targetRenameId, renameText)
                    renameTargetId = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) { Text("取消") }
            }
        )
    }

    val targetDeleteId = deleteTargetId
    if (targetDeleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除这个会话？") },
            text = { Text("会话会从列表里移除。删除会话不会删除长期记忆。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteConversation(targetDeleteId)
                    deleteTargetId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessageUi) {
    val isUser = message.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(if (isUser) 18.dp else 14.dp))
                .background(if (isUser) QingJiGreenLight else CardWhite)
                .padding(12.dp)
        ) {
            Text(
                text = if (isUser) "我" else "AI 管家",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUser) QingJiGreenDark else TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(text = message.text, fontSize = 14.sp, color = TextPrimary)

            if (message.tools.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已查询 ${message.tools.size} 项：" +
                        message.tools.joinToString { it.name },
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            message.conclusions.forEach { conclusion ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(QingJiGreenLight)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = conclusion.trust.name,
                            fontSize = 10.sp,
                            color = if (
                                conclusion.trust == TrustLabel.FACT ||
                                conclusion.trust == TrustLabel.CALCULATED
                            ) QingJiGreenDark else TextSecondary
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = conclusion.text + conclusion.dataRange?.let { "（$it）" }.orEmpty(),
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }

            if (message.warnings.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.warnings.joinToString("；"),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
