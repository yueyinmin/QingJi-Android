package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 服务配置页（多服务）。
 * 配置本体在 Room、Key 在按 ID 加密的 SP、当前激活 ID 在 DataStore——
 * ViewModel 只负责把三条数据源汇给 UI，不自行保存任何「当前服务商」状态。
 */
class AiConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    /** 全部已保存服务。 */
    val configs: StateFlow<List<AiConfig>> = repo.observeAiConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前激活配置（随切换实时刷新）。 */
    val activeConfig: StateFlow<AiConfig?> = repo.observeAiConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 每个服务是否已存 Key（用配置 id 区分，绝不回显明文）。 */
    val keyStatus: StateFlow<Map<Int, Boolean>> = configs.map { list ->
        list.associate { it.id to repo.hasApiKey(it.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 最近一次测试结果（按配置 id），仅在本次页面生命周期内保留。 */
    private val _testResults = MutableStateFlow<Map<Int, String>>(emptyMap())
    val testResults: StateFlow<Map<Int, String>> = _testResults.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** 当前激活 id（供 UI 高亮）。 */
    val activeId: StateFlow<Int> = activeConfig.map { it?.id ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun hasKey(id: Int): Boolean = keyStatus.value[id] == true

    /** 输入框留空 → 继续用「正在编辑配置」的旧 Key；由调用方传 id 明确指定。 */
    suspend fun effectiveKey(configId: Int, input: String): String =
        input.trim().ifBlank { if (configId > 0) repo.getApiKeyFor(configId) else "" }

    /** 保存（新增或编辑）。id=0 新增；keyInput 非空才覆盖该配置的 Key。 */
    suspend fun save(config: AiConfig, keyInput: String) {
        repo.saveAiConfig(config, keyInput.ifBlank { null })
        _message.value = "已保存「${config.displayName.ifBlank { config.provider }}」"
    }

    /** 删除服务（连带该配置的 Key），当前服务自动改选。 */
    suspend fun delete(id: Int) {
        repo.deleteAiConfig(id)
        _message.value = "已删除该服务"
    }

    /** 切换当前服务。要求目标已保存 Key 且 enabled，否则提示先完善配置。 */
    fun switchTo(config: AiConfig) {
        viewModelScope.launch {
            if (!repo.hasApiKey(config.id)) {
                _message.value = "该服务还没有保存 API Key，先编辑填好再切换"
                return@launch
            }
            if (!config.enabled) {
                _message.value = "该服务未启用，先打开启用开关"
                return@launch
            }
            repo.setActiveAiConfig(config.id)
            _message.value = "已切换到「${config.displayName.ifBlank { config.provider }}」"
        }
    }

    /** 测试连接（用正在编辑的配置 + 其 Key），不改激活服务、不计入正常调用额度。 */
    suspend fun test(config: AiConfig, keyInput: String): Result<String> {
        val key = effectiveKey(config.id, keyInput)
        val result = repo.testConnection(config, key)
        result.onSuccess {
            _testResults.value = _testResults.value + (config.id to it)
        }
        return result
    }
}
