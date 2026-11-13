package com.qingji.app.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * APK 下载器（任务书 3.4）
 * ------------------------------------------------------------
 * 从 Release assets 的真实下载地址流式下载到应用私有目录
 * filesDir/update/，边下边回报进度百分比。失败重试 = 直接再调一次
 * （每次开始前先删掉上次可能留下的半截文件）。
 *
 * 为什么不用 DownloadManager：私有目录 + 进度回调在 Compose 里
 * 自己管更直接，也不用额外注册广播接收器。
 * ============================================================
 */
class ApkDownloadManager(private val context: Context) {

    /** update 目录放下载中的 APK；卸载 App 会连它一起清掉，不留垃圾。 */
    val updateDir: File
        get() = File(context.filesDir, "update").apply { mkdirs() }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 下载 APK。onProgress 回调 0-100 的百分比（contentLength 未知时给 -1，
     * UI 显示不确定进度条）。
     */
    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val target = File(updateDir, fileName)
            if (target.exists()) target.delete()   // 上次失败留下的半截文件，重来前清掉

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "QingJi-App")
                .build()
            client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载失败：HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("下载失败：空响应"))
                val total = body.contentLength()      // 可能是 -1（服务器没给长度）
                var done = 0L
                var lastReported = -1
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(percent.coerceIn(0, 100))
                                }
                            } else {
                                onProgress(-1)
                            }
                        }
                    }
                }
                // 下载完还要验一下文件非空、长度对得上（服务器给了长度时）
                if (!target.exists() || target.length() == 0L) {
                    return@withContext Result.failure(Exception("下载失败：文件为空"))
                }
                if (total > 0 && target.length() != total) {
                    return@withContext Result.failure(Exception("下载不完整：${target.length()}/${total} 字节"))
                }
                Result.success(target)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 清掉下载目录（校验失败 / 用户取消安装后想手动清理时用）。 */
    fun clearDownloads() {
        updateDir.listFiles()?.forEach { it.delete() }
    }
}
