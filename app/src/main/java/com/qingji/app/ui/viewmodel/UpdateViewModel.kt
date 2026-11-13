package com.qingji.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.BuildConfig
import com.qingji.app.data.update.ApkDownloadManager
import com.qingji.app.data.update.ApkSignatureVerifier
import com.qingji.app.data.update.GitHubReleaseRepository
import com.qingji.app.diagnostics.AppLogger
import com.qingji.app.data.update.UpdateCheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 更新页状态机（任务书 3.3 要求的全部分支）
 * ------------------------------------------------------------
 * 检查失败绝不显示为「已是最新」——每种失败单独成状态、单独文案。
 * 下载完成后先做四道安全校验（包名 / versionCode / 签名 / 可读性），
 * 校验不过永远到不了「可安装」状态。
 * ============================================================
 */
class UpdateViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val repo = GitHubReleaseRepository()

    /** App 上下文（getApplication<T> 要求 T 是 Application，这里统一起拿）。 */
    private val appContext: Context get() = getApplication()
    private val downloader = ApkDownloadManager(appContext)

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(
            val title: String,
            val publishedAt: String,
            val notes: String,
            val newVersion: String,
            val newVersionCode: Long,
            val apkUrl: String,
            val apkAssetName: String,
            val apkSizeBytes: Long
        ) : State()

        data class Downloading(val percent: Int) : State()   // -1 = 不确定进度
        data class ReadyToInstall(val apkFile: java.io.File, val newVersion: String) : State()
        data class DownloadFailed(val detail: String) : State()
        data class VerifyFailed(val detail: String) : State()
        data object RateLimited : State()
        data class NetworkError(val detail: String) : State()
        data class NoApk(val hasUpdateJson: Boolean) : State()
        data class ParseError(val detail: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun check() {
        if (_state.value is State.Downloading) return   // 下载中不允许重复检查
        _state.value = State.Checking
        viewModelScope.launch {
            val result = repo.check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
            if (result !is UpdateCheckResult.Available && result !is UpdateCheckResult.UpToDate) {
                // 任务书 4.2：更新检查失败要留档（限流 / 网络 / 解析 / 无 APK）
                AppLogger.w("Update", "UPDATE_CHECK_FAILED", "检查更新未成功：$result")
            }
            _state.value = when (result) {
                is UpdateCheckResult.Available -> State.Available(
                    title = result.releaseTitle,
                    publishedAt = result.publishedAt.take(10),   // 只留日期
                    notes = result.releaseBody,
                    newVersion = result.info.versionName,
                    newVersionCode = result.info.versionCode,
                    apkUrl = result.apkDownloadUrl,
                    apkAssetName = result.info.apkAssetName.ifBlank { "QingJi-update.apk" },
                    apkSizeBytes = result.apkSizeBytes
                )
                UpdateCheckResult.UpToDate -> State.UpToDate
                UpdateCheckResult.RateLimited -> State.RateLimited
                is UpdateCheckResult.NetworkError -> State.NetworkError(result.detail)
                is UpdateCheckResult.NoApk -> State.NoApk(result.hasUpdateJson)
                is UpdateCheckResult.ParseError -> State.ParseError(result.detail)
            }
        }
    }

    fun download(apkUrl: String, fileName: String) {
        val current = _state.value
        if (current !is State.Available) return
        _state.value = State.Downloading(-1)
        viewModelScope.launch {
            val dl = downloader.download(apkUrl, fileName) { percent ->
                _state.value = State.Downloading(percent)
            }
            dl.fold(
                onSuccess = { apk ->
                    // 四道安全校验：读不出 / 包名 / versionCode / 签名，任一不过禁止安装
                    when (val v = ApkSignatureVerifier.verify(appContext, apk, current.newVersionCode)) {
                        is ApkSignatureVerifier.Result.Ok ->
                            _state.value = State.ReadyToInstall(apk, current.newVersion)
                        is ApkSignatureVerifier.Result.Unreadable -> {
                            AppLogger.e("Update", "UPDATE_VERIFY_FAILED", "APK 无法读取：${v.detail}")
                            _state.value = State.VerifyFailed("APK 无法读取：${v.detail}")
                        }
                        is ApkSignatureVerifier.Result.WrongPackage -> {
                            AppLogger.e("Update", "UPDATE_VERIFY_FAILED", "包名不对（${v.actual}），禁止安装")
                            _state.value = State.VerifyFailed("包名不对（${v.actual}），禁止安装")
                        }
                        is ApkSignatureVerifier.Result.WrongVersionCode -> {
                            AppLogger.e("Update", "UPDATE_VERIFY_FAILED",
                                "版本号对不上：期望 ${v.expected}，实际 ${v.actual}")
                            _state.value = State.VerifyFailed(
                                "版本号对不上：期望 ${v.expected}，实际 ${v.actual}，禁止安装"
                            )
                        }
                        ApkSignatureVerifier.Result.SignatureMismatch -> {
                            AppLogger.e("Update", "UPDATE_SIGNATURE_MISMATCH",
                                "下载包签名与当前安装版本不一致，已阻止安装")
                            _state.value = State.VerifyFailed(
                                "签名证书与当前安装的轻记不一致，禁止安装。" +
                                    "这种情况只能从官方 GitHub Release 重新下载；不要卸载重装绕过（会丢数据）。"
                            )
                        }
                    }
                },
                onFailure = {
                    AppLogger.e("Update", "UPDATE_DOWNLOAD_FAILED", "APK 下载失败：${it.message}", it)
                    _state.value = State.DownloadFailed(it.message ?: "下载失败")
                }
            )
        }
    }

    /**
     * 调起系统安装器。返回是否成功调起；false = 没有未知来源安装权限，
     * UI 引导去系统设置（任务书 3.4 第 9 条）。
     */
    fun canRequestInstall(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    fun openUnknownSourceSettings() {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    fun install(apkFile: java.io.File): Boolean {
        val context = appContext
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 回到「发现新版本」状态（下载失败重试用，已下载的半截文件会被清掉）。 */
    fun backToAvailable() {
        val current = _state.value
        if (current is State.Available || current is State.DownloadFailed ||
            current is State.VerifyFailed || current is State.Downloading
        ) {
            downloader.clearDownloads()
            _state.value = State.Idle
            check()
        }
    }
}
