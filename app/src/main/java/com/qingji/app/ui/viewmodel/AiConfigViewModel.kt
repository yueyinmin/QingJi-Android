package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.repository.QingJiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/** AI 服务配置页。 */
class AiConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = QingJiRepository(app)

    val config: StateFlow<AiConfig?> = repo.observeAiConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 是否已经存过 Key。存过就在输入框显示占位提示，避免把密文明文回显。 */
    private val _hasKey = MutableStateFlow(repo.hasApiKey())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    /** 输入框留空时继续用旧的 Key，填了新值才覆盖。 */
    fun effectiveKey(input: String): String = input.trim().ifBlank { repo.getApiKey() }

    suspend fun save(config: AiConfig, keyInput: String) {
        repo.saveAiConfig(config)
        if (keyInput.isNotBlank()) {
            repo.saveApiKey(keyInput)
            _hasKey.value = true
        }
    }

    suspend fun test(config: AiConfig, keyInput: String): Result<String> =
        repo.testConnection(config, effectiveKey(keyInput))
}
