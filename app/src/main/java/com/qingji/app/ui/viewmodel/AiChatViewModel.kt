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
    val messages: List<ChatMessageUi> = listOf(
        ChatMessageUi(
            id = "welcome",
            role = ChatRole.ASSISTANT,
            text = "你好，我是轻记 AI 管家。可以问我今天的饮食、训练或计划，我会先查本地记录再回答。"
        )
    ),
    val input: String = "",
    val context: String = "首页",
    val isRunning: Boolean = false,
    val error: String? = null
)

/** AI 管家会话 ViewModel。阶段 D 先做单会话内存态，不引入 AI 库。 */
class AiChatViewModel(app: Application) : AndroidViewModel(app) {

    private val repo by lazy { QingJiRepository(app) }
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

    private lateinit var clock: () -> LocalDate
    private lateinit var runner: suspend (String, String) -> AgentResult

    private var job: Job? = null

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState

    init {
        clock = { AppClock.today() }
        runner = { question, context -> runDefault(question, context) }
    }

    /** 测试注入点：替换 Agent 调用，避免测试依赖网络或数据库。 */
    constructor(
        app: Application,
        testRunner: suspend (String, String) -> AgentResult,
        testClock: () -> LocalDate
    ) : this(app) {
        runner = testRunner
        clock = testClock
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun setContext(context: String) {
        if (context.isBlank()) return
        _uiState.update { it.copy(context = context) }
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
                messages = it.messages +
                    ChatMessageUi(userId, ChatRole.USER, question) +
                    ChatMessageUi(pendingId, ChatRole.ASSISTANT, "正在查询本地记录…", pending = true)
            )
        }

        job?.cancel()
        job = viewModelScope.launch {
            val context = _uiState.value.context
            try {
                val result = runner(question, context)
                replacePending(
                    pendingId,
                    ChatMessageUi(
                        id = pendingId,
                        role = ChatRole.ASSISTANT,
                        text = result.answer,
                        conclusions = result.conclusions,
                        tools = result.toolExecutions,
                        warnings = result.warnings
                    )
                )
            } catch (e: CancellationException) {
                replacePending(
                    pendingId,
                    ChatMessageUi(
                        id = pendingId,
                        role = ChatRole.ASSISTANT,
                        text = "已取消",
                        isError = true
                    )
                )
            } catch (e: Throwable) {
                replacePending(
                    pendingId,
                    ChatMessageUi(
                        id = pendingId,
                        role = ChatRole.ASSISTANT,
                        text = readableError(e),
                        isError = true
                    )
                )
            } finally {
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                messages = state.messages.map { message ->
                    if (message.pending) {
                        message.copy(text = "已取消", pending = false, isError = true)
                    } else message
                }
            )
        }
    }

    fun retry() {
        if (_uiState.value.isRunning) return
        val question = _uiState.value.messages.lastOrNull { it.role == ChatRole.USER }?.text
        if (question.isNullOrBlank()) {
            _uiState.update { it.copy(error = "没有可重试的问题") }
            return
        }
        _uiState.update { state ->
            state.copy(
                input = question,
                messages = state.messages.dropLastWhile {
                    it.role == ChatRole.ASSISTANT && (it.isError || it.pending)
                }
            )
        }
        send(question)
    }

    fun clear() {
        job?.cancel()
        job = null
        _uiState.value = AiChatUiState(context = _uiState.value.context)
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

    private fun replacePending(id: String, replacement: ChatMessageUi) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { if (it.id == id) replacement else it })
        }
    }

    private fun readableError(e: Throwable): String = when (e) {
        is AiError -> e.message ?: e.code
        is com.qingji.app.data.remote.AiException -> e.message ?: "AI 服务异常"
        else -> e.message?.take(120) ?: "AI 服务异常，请稍后重试"
    }
}
