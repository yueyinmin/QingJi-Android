package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.ai.ToolExecutor
import com.qingji.app.ai.agent.AgentConclusion
import com.qingji.app.ai.agent.AgentRequest
import com.qingji.app.ai.agent.AgentResult
import com.qingji.app.ai.agent.AgentToolExecution
import com.qingji.app.ai.agent.QingJiAgent
import com.qingji.app.ai.memory.MemoryManager
import com.qingji.app.ai.nudge.NudgeManager
import com.qingji.app.ai.nudge.RepositoryNudgeFactsSource
import com.qingji.app.ai.tools.ReadonlyToolFactory
import com.qingji.app.data.ai.AiChatStore
import com.qingji.app.data.ai.AiConversationSummary
import com.qingji.app.data.ai.MemoryStore
import com.qingji.app.data.ai.PersistedChatMessage
import com.qingji.app.data.ai.RoomAiChatStore
import com.qingji.app.data.ai.RoomMemoryStore
import com.qingji.app.data.ai.RoomNudgeStore
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.prefs.AiMemoryPrefs
import com.qingji.app.data.prefs.NudgePrefs
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.OpenAiCompatibleProvider
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.nudge.NudgeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

enum class ChatRole { USER, ASSISTANT }

data class ChatMessageUi(
    val id: String,
    val role: ChatRole,
    val text: String,
    val conclusions: List<AgentConclusion> = emptyList(),
    val tools: List<AgentToolExecution> = emptyList(),
    val isError: Boolean = false,
    val pending: Boolean = false,
    val warnings: List<String> = emptyList()
)

data class AiChatUiState(
    val persistedMessages: List<ChatMessageUi> = emptyList(),
    val pendingMessage: ChatMessageUi? = null,
    val conversations: List<AiConversationSummary> = emptyList(),
    val activeConversationId: String? = null,
    val input: String = "",
    val context: String = "首页",
    val attachedImageBase64: String? = null,
    val attachedImageMime: String = "image/jpeg",
    val isRunning: Boolean = false,
    val hasOlderMessages: Boolean = false,
    /** 刚记住的内容，展示一行提示后自动消失；null = 没有新记忆 */
    val justRemembered: String? = null,
    /** 阶段 J：本地规则算出的主动提醒；null = 这次没什么要说的 */
    val nudge: NudgeRecord? = null,
    val error: String? = null
) {
    val messages: List<ChatMessageUi>
        get() = if (persistedMessages.isEmpty() && pendingMessage == null) {
            listOf(WELCOME)
        } else {
            persistedMessages + listOfNotNull(pendingMessage)
        }

    companion object {
        val WELCOME = ChatMessageUi(
            id = "welcome",
            role = ChatRole.ASSISTANT,
            text = "你好，我是轻记 AI 管家。可以问我今天的饮食、训练或计划，我会先查本地记录再回答。"
        )
    }
}

/** AI 管家会话 ViewModel。会话与消息持久化在独立 qingji_ai.db。 */
class AiChatViewModel private constructor(
    app: Application,
    injectedStore: AiChatStore?,
    injectedRunner: (suspend (String, String) -> AgentResult)?,
    injectedStreamRunner: (suspend (String, String, (String) -> Unit) -> AgentResult)?,
    injectedClock: (() -> LocalDate)?,
    marker: Unit = Unit
) : AndroidViewModel(app) {

    private val repo by lazy { QingJiRepository(app) }
    private val store: AiChatStore = injectedStore ?: RoomAiChatStore(AiDatabase.get(app))
    /** 阶段 I：长期记忆与 AI 会话同一个库（qingji_ai.db），会话删了记忆不跟着删。 */
    private val memoryStore: MemoryStore by lazy { RoomMemoryStore(AiDatabase.get(app)) }
    /** 测试注入存储时不碰真实库，避免单测依赖 Room。 */
    private val usesRealStore: Boolean = injectedStore == null
    private val agent by lazy {
        val toolFactory = ReadonlyToolFactory(
            qingJiRepository = repo,
            healthRepository = HealthRepository(app),
            trainingRepository = TrainingRepository(app)
        )
        val registry = toolFactory.createRegistry()
        QingJiAgent(
            provider = OpenAiCompatibleProvider(),
            registry = registry,
            executor = ToolExecutor(registry, maxConcurrency = 2)
        )
    }

    private val clock: () -> LocalDate = injectedClock ?: { AppClock.today() }
    /**
     * 阶段 J：主动管家。只在真实存储下启用——测试注入的 store 意味着不碰 Room，
     * 这时如果去查提醒会顺带把真库拉起来，测试就不纯粹了。
     */
    private val nudgeManager: NudgeManager? by lazy {
        if (!usesRealStore) {
            null
        } else {
            NudgeManager(
                store = RoomNudgeStore(AiDatabase.get(app)),
                factsSource = RepositoryNudgeFactsSource(
                    repo = repo,
                    training = TrainingRepository(app)
                ),
                policySource = { NudgePrefs.policy(app) }
            )
        }
    }
    /** 提醒每次进页面只算一次，避免来回切页重复判断。 */
    private var nudgeLoaded = false
    private val legacyRunner = injectedRunner
    /** 三参 runner 支持流式片段；旧测试注入的两参 runner 自动降级为一次性结果。 */
    private val runner: suspend (String, String, (String) -> Unit) -> AgentResult =
        { question, context, onDelta ->
            injectedStreamRunner?.invoke(question, context, onDelta)
                ?: legacyRunner?.invoke(question, context)
                ?: runDefaultStreaming(question, context, onDelta)
        }

    private var job: Job? = null
    private var messagesJob: Job? = null
    private var messageLimit = 100

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState

    constructor(app: Application) : this(app, null, null, null, null, Unit)

    /** 测试注入点：替换 Agent 调用与存储，避免测试依赖网络或数据库。 */
    constructor(
        app: Application,
        testStore: AiChatStore,
        testRunner: suspend (String, String) -> AgentResult,
        testClock: () -> LocalDate
    ) : this(app, testStore, testRunner, null, testClock, Unit)

    /** 流式测试注入点。 */
    constructor(
        app: Application,
        testStore: AiChatStore,
        testStreamRunner: suspend (String, String, (String) -> Unit) -> AgentResult,
        testClock: () -> LocalDate,
        streamMarker: Boolean = true
    ) : this(app, testStore, null, testStreamRunner, testClock, Unit)

    init {
        viewModelScope.launch {
            store.observeConversations().collect { conversations ->
                _uiState.update { state ->
                    val activeId = state.activeConversationId
                        ?.takeIf { id -> conversations.any { it.id == id } }
                        ?: conversations.firstOrNull()?.id
                    state.copy(conversations = conversations, activeConversationId = activeId)
                }
                val activeId = _uiState.value.activeConversationId
                if (activeId != null && messagesJob == null) observeMessages(activeId)
                if (conversations.isEmpty()) {
                    val created = store.ensureConversation(_uiState.value.context)
                    selectConversationInternal(created.id)
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    /** 图片只留在内存里随本次请求发送，不写库、不进日志。 */
    fun attachImage(base64: String, mime: String = "image/jpeg") {
        if (base64.isBlank()) return
        if (base64.length > MAX_IMAGE_BASE64_CHARS) {
            _uiState.update { it.copy(error = "图片太大，请换一张小一些的") }
            return
        }
        _uiState.update {
            it.copy(
                attachedImageBase64 = base64,
                attachedImageMime = mime,
                error = null
            )
        }
    }

    /** 系统语音识别结果直接填进输入框，用户可改后再发。 */
    fun onVoiceResult(text: String) {
        if (text.isBlank()) return
        _uiState.update { it.copy(input = listOf(it.input, text).filter { v -> v.isNotBlank() }.joinToString(" ")) }
    }

    /**
     * 阶段 J：进页面时算一次主动提醒。
     *
     * 注意这里**只跑本地规则**，不联网；而且只在用户真正打开管家页时才跑，
     * 不做后台定时检查——不主动弹窗、不推通知，避免变成骚扰。
     */
    fun loadNudge() {
        val manager = nudgeManager ?: return
        if (nudgeLoaded) return
        nudgeLoaded = true
        viewModelScope.launch {
            val record = runCatching { manager.checkRecord() }.getOrNull()
            _uiState.update { it.copy(nudge = record) }
        }
    }

    /** 用户点「知道了」：只改反馈状态，不删历史。 */
    fun dismissNudge() {
        val record = _uiState.value.nudge ?: return
        _uiState.update { it.copy(nudge = null) }
        viewModelScope.launch { runCatching { nudgeManager?.dismiss(record.id) } }
    }

    /**
     * 用户点「让管家看看」：把提醒里已经算好的数字变成一个问题发出去。
     * 这是唯一会因此产生网络请求的入口，必须用户亲手点。
     */
    fun askAboutNudge() {
        val record = _uiState.value.nudge ?: return
        val manager = nudgeManager ?: return
        _uiState.update { it.copy(nudge = null) }
        viewModelScope.launch { runCatching { manager.markActed(record.id) } }
        send(manager.analysisPrompt(record))
    }

    fun setContext(context: String) {
        if (context.isBlank()) return
        _uiState.update { it.copy(context = context) }
        val active = _uiState.value.conversations.firstOrNull { it.id == _uiState.value.activeConversationId }
        if (active == null || active.entryPage != context) {
            viewModelScope.launch {
                val target = _uiState.value.conversations.firstOrNull { it.entryPage == context }
                    ?: store.createConversation(context)
                selectConversationInternal(target.id)
            }
        }
    }

    fun selectConversation(id: String) {
        selectConversationInternal(id)
    }

    fun loadOlderMessages() {
        messageLimit += 100
        val id = _uiState.value.activeConversationId ?: return
        observeMessages(id)
    }

    fun newConversation() {
        viewModelScope.launch {
            val created = store.createConversation(_uiState.value.context)
            selectConversationInternal(created.id)
        }
    }

    fun renameConversation(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { store.renameConversation(id, title) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            store.deleteConversation(id)
            if (_uiState.value.activeConversationId == id) {
                val next = _uiState.value.conversations.firstOrNull { it.id != id }
                if (next != null) selectConversationInternal(next.id)
                else {
                    val created = store.createConversation(_uiState.value.context)
                    selectConversationInternal(created.id)
                }
            }
        }
    }

    fun send(overrideQuestion: String? = null) {
        if (_uiState.value.isRunning) return
        val question = (overrideQuestion ?: _uiState.value.input).trim()
        if (question.isBlank()) {
            _uiState.update { it.copy(error = "先说想问什么") }
            return
        }

        val userId = UUID.randomUUID().toString()
        val pendingId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                input = "",
                error = null,
                justRemembered = null,
                isRunning = true,
                pendingMessage = ChatMessageUi(pendingId, ChatRole.ASSISTANT, "正在查询本地记录…", pending = true)
            )
        }

        job?.cancel()
        job = viewModelScope.launch {
            val conversationId = _uiState.value.activeConversationId
                ?: store.ensureConversation(_uiState.value.context).id.also { selectConversationInternal(it) }
            val context = _uiState.value.context
            store.appendUserMessage(userId, conversationId, question)

            // 阶段 I：只有用户明说「记住…」才写长期记忆；聊天正文不会被自动收集
            if (usesRealStore) {
                runCatching {
                    val disabled = AiMemoryPrefs.disabled(getApplication())
                    MemoryManager.captureExplicit(
                        store = memoryStore,
                        text = question,
                        sourceRef = userId,
                        disabled = disabled
                    )
                }.getOrNull()?.let { record ->
                    _uiState.update { it.copy(justRemembered = record.content) }
                }
            }

            try {
                var streamStarted = false
                val result = runner(question, context) { delta ->
                    if (delta.isNotEmpty()) {
                        _uiState.update { state ->
                            val current = state.pendingMessage ?: return@update state
                            val text = if (streamStarted) current.text + delta else delta
                            streamStarted = true
                            state.copy(pendingMessage = current.copy(text = text, pending = true))
                        }
                    }
                }
                store.appendAssistantMessage(pendingId, conversationId, result)
            } catch (e: CancellationException) {
                store.appendErrorMessage(pendingId, conversationId, "已取消")
            } catch (e: Throwable) {
                store.appendErrorMessage(pendingId, conversationId, readableError(e))
            } finally {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        pendingMessage = null,
                        attachedImageBase64 = null,
                        attachedImageMime = "image/jpeg"
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun retry() {
        if (_uiState.value.isRunning) return
        val state = _uiState.value
        val lastUser = state.persistedMessages.lastOrNull { it.role == ChatRole.USER }
        if (lastUser == null) {
            _uiState.update { it.copy(error = "没有可重试的问题") }
            return
        }
        state.persistedMessages.lastOrNull { it.isError }?.let { errorMessage ->
            viewModelScope.launch { store.deleteMessage(errorMessage.id) }
        }
        send(lastUser.text)
    }

    fun clear() {
        newConversation()
    }

    /** 测试/页面销毁时清理会话观察与进行中的任务。 */
    fun close() {
        job?.cancel()
        messagesJob?.cancel()
        job = null
        messagesJob = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private suspend fun runDefault(question: String, context: String): AgentResult {
        val date = clock()
        val config = repo.ensureAiAvailable(date)
        val key = repo.getApiKeyFor(config.id)
        if (key.isBlank()) throw AiError.Configuration("还没填 API Key，去 设置 → AI 服务配置")
        val result = agent.run(agentRequest(date, config, key, question, context))
        // 一次助手任务只扣一次额度，不按 Agent 内部轮次重复扣。
        repo.recordAiCall(date)
        return result
    }

    private suspend fun runDefaultStreaming(
        question: String,
        context: String,
        onDelta: (String) -> Unit
    ): AgentResult {
        val date = clock()
        val config = repo.ensureAiAvailable(date)
        val key = repo.getApiKeyFor(config.id)
        if (key.isBlank()) throw AiError.Configuration("还没填 API Key，去 设置 → AI 服务配置")
        val result = agent.runStreaming(agentRequest(date, config, key, question, context), onDelta)
        repo.recordAiCall(date)
        return result
    }

    private suspend fun agentRequest(
        date: LocalDate,
        config: com.qingji.app.data.local.entity.AiConfig,
        key: String,
        question: String,
        context: String
    ): AgentRequest {
        // 阶段 I：按页面上下文取少量相关记忆；记忆读取失败不影响回答主流程
        val memories = runCatching { MemoryManager.retrieve(memoryStore, context).notes }
            .getOrDefault(emptyList())
        return AgentRequest(
            config = config,
            key = key,
            question = question,
            taskType = "ai_chat",
            date = date,
            page = context,
            recentSummary = repo.buildDailyFacts(date),
            imageBase64 = _uiState.value.attachedImageBase64,
            imageMime = _uiState.value.attachedImageMime,
            memories = memories
        )
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            store.observeMessagesPaged(conversationId, messageLimit).collect { messages ->
                val total = store.countMessages(conversationId)
                _uiState.update {
                    it.copy(
                        persistedMessages = messages.map { message -> toUi(message) },
                        hasOlderMessages = total > messages.size
                    )
                }
            }
        }
    }

    private fun selectConversationInternal(id: String) {
        messageLimit = 100
        _uiState.update {
            it.copy(
                activeConversationId = id,
                pendingMessage = null,
                error = null,
                hasOlderMessages = false
            )
        }
        observeMessages(id)
    }

    private fun toUi(message: PersistedChatMessage) = ChatMessageUi(
        id = message.id,
        role = if (message.role == "user") ChatRole.USER else ChatRole.ASSISTANT,
        text = message.text,
        conclusions = message.conclusions,
        tools = message.tools,
        isError = message.isError,
        warnings = message.warnings
    )

    private companion object {
        const val MAX_IMAGE_BASE64_CHARS = 2_000_000
    }

    private fun readableError(e: Throwable): String = when (e) {
        is AiError -> e.message ?: e.code
        is com.qingji.app.data.remote.AiException -> e.message ?: "AI 服务异常"
        else -> e.message?.take(120) ?: "AI 服务异常，请稍后重试"
    }
}
