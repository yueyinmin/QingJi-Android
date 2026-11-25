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
import com.qingji.app.ai.tools.ReadonlyToolFactory
import com.qingji.app.data.ai.AiChatStore
import com.qingji.app.data.ai.AiConversationSummary
import com.qingji.app.data.ai.PersistedChatMessage
import com.qingji.app.data.ai.RoomAiChatStore
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.remote.ai.AiError
import com.qingji.app.data.remote.ai.OpenAiCompatibleProvider
import com.qingji.app.data.repository.HealthRepository
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.data.repository.TrainingRepository
import com.qingji.app.domain.AppClock
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
    val isRunning: Boolean = false,
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
    injectedClock: (() -> LocalDate)?,
    marker: Unit = Unit
) : AndroidViewModel(app) {

    private val repo by lazy { QingJiRepository(app) }
    private val store: AiChatStore = injectedStore ?: RoomAiChatStore(AiDatabase.get(app))
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
    private val runner: suspend (String, String) -> AgentResult =
        injectedRunner ?: { question, context -> runDefault(question, context) }

    private var job: Job? = null
    private var messagesJob: Job? = null

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState

    constructor(app: Application) : this(app, null, null, null, Unit)

    /** 测试注入点：替换 Agent 调用与存储，避免测试依赖网络或数据库。 */
    constructor(
        app: Application,
        testStore: AiChatStore,
        testRunner: suspend (String, String) -> AgentResult,
        testClock: () -> LocalDate
    ) : this(app, testStore, testRunner, testClock, Unit)

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

    fun newConversation() {
        viewModelScope.launch {
            val created = store.createConversation(_uiState.value.context)
            selectConversationInternal(created.id)
        }
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

            try {
                val result = runner(question, context)
                store.appendAssistantMessage(pendingId, conversationId, result)
            } catch (e: CancellationException) {
                store.appendErrorMessage(pendingId, conversationId, "已取消")
            } catch (e: Throwable) {
                store.appendErrorMessage(pendingId, conversationId, readableError(e))
            } finally {
                _uiState.update {
                    it.copy(isRunning = false, pendingMessage = null)
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
        val result = agent.run(
            AgentRequest(
                config = config,
                key = key,
                question = question,
                taskType = "ai_chat",
                date = date,
                page = context,
                recentSummary = repo.buildDailyFacts(date)
            )
        )
        // 一次助手任务只扣一次额度，不按 Agent 内部轮次重复扣。
        repo.recordAiCall(date)
        return result
    }

    private fun observeMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            store.observeMessages(conversationId).collect { messages ->
                _uiState.update { it.copy(persistedMessages = messages.map { message -> toUi(message) }) }
            }
        }
    }

    private fun selectConversationInternal(id: String) {
        _uiState.update { it.copy(activeConversationId = id, pendingMessage = null, error = null) }
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

    private fun readableError(e: Throwable): String = when (e) {
        is AiError -> e.message ?: e.code
        is com.qingji.app.data.remote.AiException -> e.message ?: "AI 服务异常"
        else -> e.message?.take(120) ?: "AI 服务异常，请稍后重试"
    }
}
