package com.qingji.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth

/**
 * ============================================================
 * 备份页的数据管家（TASK-06-A）
 * ------------------------------------------------------------
 * 改造前 BackupScreen.kt 里同时放着：
 *
 *   - 4 个记忆状态（busy / lastFile / importResult / pendingUri）；
 *   - 3 个协程任务（导出 JSON、导出 CSV、导入 JSON），每个都在 UI 里
 *     `scope.launch { backup.xxx().onSuccess { Toast(...) } }`；
 *   - 以及直接调用系统分享。
 *
 * 结果是「界面文件里做业务」，转屏、进后台、进程回收时状态全丢，
 * 也没法单独测「导出失败时状态怎么变」。
 *
 * 现在状态和任务都收到这里，UI 只读 [busy] / [lastFile] / [importResult] /
 * [message] 并触发方法调用。行为（含文案与 Toast 时长）与改造前一致：
 * 成功提示是短 Toast、失败提示是长 Toast，所以消息里带 [UiMessage.long]。
 * ============================================================
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    /** 一条要弹给用户看的提示（text + 是否用长 Toast）。 */
    data class UiMessage(val text: String, val long: Boolean = false)

    private val context = app.applicationContext
    private val manager = BackupManager(context)

    /** 有任务在跑：导出 / 导入按钮统一置灰。 */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 最近一次导出的文件，用来显示「文件名 + 大小 + 分享出去」。 */
    private val _lastFile = MutableStateFlow<File?>(null)
    val lastFile: StateFlow<File?> = _lastFile.asStateFlow()

    /** 导入完成后的一句话摘要（成功或失败原因）。 */
    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    /** 一次性提示，UI 弹完要调 [consumeMessage] 清掉，避免转屏重弹。 */
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- 导出 ----------

    fun exportJson() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            manager.exportJson()
                .onSuccess { file ->
                    _lastFile.value = file
                    _message.value = UiMessage("已导出：${file.name}")
                }
                .onFailure { _message.value = UiMessage("导出失败：${it.message}", long = true) }
            _busy.value = false
        }
    }

    /** CSV 导出的筛选范围（null / 空集合 = 不限），语义与改造前一致。 */
    fun exportCsv(month: YearMonth?, meals: Set<String>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            manager.exportCsv(month, meals)
                .onSuccess { file ->
                    _lastFile.value = file
                    _message.value = UiMessage("已导出：${file.name}")
                }
                .onFailure { _message.value = UiMessage("导出失败：${it.message}", long = true) }
            _busy.value = false
        }
    }

    // ---------- 导入 ----------

    /** 覆盖恢复。调用前 UI 必须已让用户二次确认（本方法不做确认）。 */
    fun importJson(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            manager.importJson(uri)
                .onSuccess { summary ->
                    _importResult.value = summary
                    _message.value = UiMessage("导入完成")
                }
                .onFailure {
                    _importResult.value = "导入失败：${it.message ?: "文件格式不对"}"
                }
            _busy.value = false
        }
    }

    // ---------- 只读小工具（原样转给 BackupManager，UI 不用再 new 一个） ----------

    fun backupDirPath(): String = manager.backupDirPath()

    fun sizeText(bytes: Long): String = manager.sizeText(bytes)

    fun cacheSize(): Long = manager.cacheSize()
}
