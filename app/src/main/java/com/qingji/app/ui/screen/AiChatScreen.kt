package com.qingji.app.ui.screen

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.ui.components.NudgeCard
import com.qingji.app.ui.screen.aichat.AiChatTopBar
import com.qingji.app.ui.screen.aichat.ChatComposer
import com.qingji.app.ui.screen.aichat.ChatMessageList
import com.qingji.app.ui.screen.aichat.ChatMoreSheet
import com.qingji.app.ui.screen.aichat.ChatScrollPolicy
import com.qingji.app.ui.screen.aichat.ConversationListSheet
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.AiChatViewModel

/**
 * 统一 AI 管家对话页（工单 QJ-AI-CHAT-UI-V2 重构后）。
 *
 * 结构从上到下固定四段：
 *   顶栏（56dp，恒定）→ 消息列表（weight 撑满）→ 快捷提问（可隐藏）→ 输入容器
 *
 * 业务能力全部保留，只是换了承载方式：
 * · 会话管理从顶部一行胶囊搬进「更多 → 历史会话」的底部弹层；
 * · 记忆 / 提醒入口从胶囊搬进「更多」；
 * · 图片 / 语音 / 发送从 Material Button 换成固定尺寸图标按钮；
 * · 重试从输入区的独立大按钮搬进失败气泡内部。
 *
 * 键盘适配不用固定 dp：只在 IME 高度超出导航栏高度的部分补 padding，
 * 因为宿主 Scaffold 已经统一给所有页面垫了导航栏高度，再叠一次 imePadding
 * 会出现「双重底部间距」——这也是旧版底部空一块的根因之一。
 */
@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    context: String = "首页",
    onOpenMemory: () -> Unit = {},
    onOpenNudge: () -> Unit = {},
    vm: AiChatViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    var showConversations by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    // 阶段 K：删除会话时顺带处理这段对话产生的记忆
    var deleteTargetMemoryCount by remember { mutableStateOf(0) }
    var deleteTargetDropMemories by remember { mutableStateOf(false) }
    /** 语音识别是系统弹窗，这里给一行「正在打开」的反馈，不假装有录音动效 */
    var voiceHint by remember { mutableStateOf<String?>(null) }
    /** 用户手动发消息时强制贴底（哪怕他刚往上翻） */
    var forceScrollTick by remember { mutableIntStateOf(0) }

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
        voiceHint = null
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
        voiceHint = "正在打开语音识别…"
        runCatching { voiceLauncher.launch(intent) }
            .onFailure {
                voiceHint = null
                vm.onInputChange("这台设备没有语音识别，请手动输入")
            }
    }

    fun sendNow(question: String? = null) {
        vm.send(question)
        forceScrollTick++
    }

    LaunchedEffect(context) { vm.setContext(context) }
    // 阶段 J：进页面才算一次提醒，且只跑本地规则
    LaunchedEffect(context) { vm.loadNudge() }

    val messageCount = state.messages.size
    val streamingLength = state.pendingMessage?.text?.length ?: 0
    // 快捷问题：输入框有内容或正在生成时就收起（工单 6.6），其余时候挂在消息列表末尾
    val showQuickPrompts = state.input.isBlank() && !state.isRunning
    val latestIndex = ChatScrollPolicy.lastItemIndex(
        hasOlderMessages = state.hasOlderMessages,
        messageCount = messageCount,
        quickPromptsAsItem = showQuickPrompts
    )

    // 跟随策略：只有用户本来就贴着底部才跟随；往上翻历史时不再被拽回来
    LaunchedEffect(messageCount, streamingLength) {
        if (messageCount <= 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val follow = ChatScrollPolicy.shouldFollow(
            lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
            totalCount = info.totalItemsCount
        )
        if (follow && latestIndex >= 0) {
            runCatching { listState.scrollToItem(latestIndex) }
        }
    }

    // 手动发送：无条件贴底（等一帧让新消息先完成测量）
    LaunchedEffect(forceScrollTick) {
        if (forceScrollTick == 0) return@LaunchedEffect
        withFrameMillis { }
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) runCatching { listState.animateScrollToItem(last) }
    }

    // 键盘弹出：保证最新一条可见（工单 4.4）
    val imeVisible = WindowInsets.ime
        .asPaddingValues()
        .calculateBottomPadding() > 0.dp
    LaunchedEffect(imeVisible) {
        if (imeVisible && latestIndex >= 0) {
            runCatching { listState.scrollToItem(latestIndex) }
        }
    }

    // 只补「IME 比导航栏多出来的那部分」，避免和宿主 Scaffold 的底部内边距叠加
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeExtra = if (imeBottom > navBottom) imeBottom - navBottom else 0.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        AiChatTopBar(
            title = "轻记 AI 管家",
            onBack = onBack,
            onNewConversation = { vm.newConversation() },
            newEnabled = !state.isRunning,
            onOpenMore = { showMore = true }
        )

        ChatMessageList(
            messages = state.messages,
            context = state.context,
            hasOlderMessages = state.hasOlderMessages,
            isRunning = state.isRunning,
            listState = listState,
            onLoadOlder = { vm.loadOlderMessages() },
            onRetry = { messageId -> vm.retryFrom(messageId) },
            modifier = Modifier.weight(1f),
            quickPrompts = if (showQuickPrompts) QUICK_PROMPTS else emptyList(),
            onQuickPrompt = { question -> sendNow(question) }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = imeExtra)
        ) {
            // 阶段 J：本地规则算出来的主动提醒。一天最多一条，说完就不再出现。
            state.nudge?.let { nudge ->
                NudgeCard(
                    title = nudge.title,
                    body = nudge.body,
                    canAnalyze = nudge.needsAnalysis,
                    enabled = !state.isRunning,
                    onAsk = { vm.askAboutNudge() },
                    onDismiss = { vm.dismissNudge() }
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
                        fontSize = 11.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "管理",
                        fontSize = 11.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(QingJiGreenLight)
                            .clickable { onOpenMemory() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            voiceHint?.let { hint ->
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            ChatComposer(
                input = state.input,
                onInputChange = vm::onInputChange,
                hasAttachment = state.attachedImageBase64 != null,
                onClearAttachment = { vm.clearAttachment() },
                onPickImage = { pickImage.launch("image/*") },
                onStartVoice = { startVoice() },
                isRunning = state.isRunning,
                onSend = { sendNow() },
                onCancel = { vm.cancel() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = DangerRed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }

    if (showMore) {
        ChatMoreSheet(
            historyEnabled = !state.isRunning,
            onOpenHistory = {
                showMore = false
                showConversations = true
            },
            onOpenMemory = {
                showMore = false
                onOpenMemory()
            },
            onOpenNudge = {
                showMore = false
                onOpenNudge()
            },
            onDismiss = { showMore = false }
        )
    }

    if (showConversations) {
        ConversationListSheet(
            conversations = state.conversations,
            activeId = state.activeConversationId,
            canEdit = !state.isRunning,
            onSelect = { id ->
                vm.selectConversation(id)
                showConversations = false
            },
            onRename = { conversation ->
                showConversations = false
                renameTargetId = conversation.id
                renameText = conversation.title
            },
            onDelete = { conversation ->
                showConversations = false
                deleteTargetId = conversation.id
            },
            onDismiss = { showConversations = false }
        )
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
    // 阶段 K：打开删除确认时先查这段对话产生了几条记忆，好让用户知道会连带影响什么
    LaunchedEffect(targetDeleteId) {
        deleteTargetDropMemories = false
        deleteTargetMemoryCount = targetDeleteId?.let { vm.countMemoriesFrom(it) } ?: 0
    }
    if (targetDeleteId != null) {
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("删除这个会话？") },
            text = {
                Column {
                    Text("会话里的消息、工具调用记录会从本机删掉，不能恢复。")
                    Spacer(Modifier.height(8.dp))
                    if (deleteTargetMemoryCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { deleteTargetDropMemories = !deleteTargetDropMemories }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = deleteTargetDropMemories,
                                onCheckedChange = { deleteTargetDropMemories = it }
                            )
                            Text(
                                "同时删除这段对话记住的内容（$deleteTargetMemoryCount 条）",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            if (deleteTargetDropMemories) {
                                "这 $deleteTargetMemoryCount 条长期记忆会一起删掉。"
                            } else {
                                "不勾选也行：记忆会留着，但标成「来源已失效」，不再参与回答。"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            "这段对话没有产生长期记忆，不影响「记忆」里的内容。",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteConversation(targetDeleteId, deleteTargetDropMemories)
                    deleteTargetId = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetId = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 快捷提问内容：沿用改造前页面里已有的三条，不自造新问题
 * （工单要求「具体内容以现有业务实现为准」）。
 */
private val QUICK_PROMPTS = listOf(
    "我今天吃得怎么样？",
    "这周训练怎么样？",
    "我的训练计划是什么？"
)
