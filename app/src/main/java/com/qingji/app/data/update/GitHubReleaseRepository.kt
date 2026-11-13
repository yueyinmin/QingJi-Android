package com.qingji.app.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ============================================================
 * GitHub Releases 更新检查（任务书 3.1）
 * ------------------------------------------------------------
 * 数据来源只有一个：官方 Releases API 的 releases/latest。
 * 不用第三方更新服务器，不写死 APK 下载链接——APK 地址一律从
 * Release assets 的真实 browser_download_url 里取。
 *
 * releases/latest 本身就只返回正式 Release（draft / prerelease 自动排除），
 * 响应里再校验一遍这两个标志，双保险。
 * ============================================================
 */
class GitHubReleaseRepository(
    private val repoApiUrl: String = "https://api.github.com/repos/yueyinmin/QingJi-Android/releases/latest",
    /**
     * update.json 下载器：默认走 HTTP，单测可以注入假实现，
     * 不用起真实网络（任务书第九节：解析逻辑单测覆盖）。
     */
    private val updateJsonFetcher: (String) -> String = { url -> downloadTextHttp(url) }
) {

    /** 检查更新：网络请求 + 解析 + 版本判断，全部在 IO 线程。 */
    suspend fun check(currentVersionName: String, currentVersionCode: Long): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(repoApiUrl)
                // GitHub API 约定的两个头： Accept 决定返回格式，UA 缺了会被拒
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "QingJi-App")
                .build()

            val code: Int
            val body: String
            try {
                client().newCall(request).execute().use { response ->
                    code = response.code
                    body = response.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                // 超时 / 断网 / DNS 失败都归到网络异常
                return@withContext UpdateCheckResult.NetworkError(e.message ?: e.javaClass.simpleName)
            }

            when {
                code == 403 || code == 429 -> return@withContext UpdateCheckResult.RateLimited
                code != 200 -> return@withContext UpdateCheckResult.NetworkError("HTTP $code")
            }

            try {
                parseAndCompare(body, currentVersionName, currentVersionCode)
            } catch (e: Exception) {
                UpdateCheckResult.ParseError(e.message ?: e.javaClass.simpleName)
            }
        }

    /**
     * 解析 Release JSON 并做版本判断。单独抽出来是为了单测能直接喂 JSON，
     * 不用起网络。
     */
    fun parseAndCompare(releaseJson: String, currentVersionName: String, currentVersionCode: Long): UpdateCheckResult {
        val rel = JSONObject(releaseJson)
        // 双保险：draft / prerelease 一律不算正式更新源
        if (rel.optBoolean("draft", false) || rel.optBoolean("prerelease", false)) {
            return UpdateCheckResult.NoApk(hasUpdateJson = false)
        }

        val assets = rel.optJSONArray("assets") ?: org.json.JSONArray()
        var updateJsonAssetUrl: String? = null
        var apkAssetName: String? = null
        var apkDownloadUrl: String? = null
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when {
                a.optString("name") == "update.json" -> updateJsonAssetUrl = a.optString("browser_download_url")
                a.optString("name").endsWith(".apk", ignoreCase = true) -> {
                    apkAssetName = a.optString("name")
                    apkDownloadUrl = a.optString("browser_download_url")
                    apkSize = a.optLong("size", 0L)
                }
            }
        }

        if (updateJsonAssetUrl == null) {
            // 没有 update.json 就没有可靠的 versionCode，按任务书 3.2 不做字典序猜测
            return UpdateCheckResult.NoApk(hasUpdateJson = false)
        }

        val info: UpdateInfo
        try {
            info = UpdateInfo.fromJson(updateJsonFetcher(updateJsonAssetUrl))
        } catch (e: Exception) {
            return UpdateCheckResult.ParseError("update.json 解析失败：${e.message}")
        }

        if (info.apkAssetName.isNotEmpty()) {
            // update.json 里指定的 APK 名必须在 assets 里找得到，找不到按没有 APK 处理
            val match = assets.let { arr ->
                (0 until arr.length()).firstOrNull { arr.getJSONObject(it).optString("name") == info.apkAssetName }
            }
            if (match == null) return UpdateCheckResult.NoApk(hasUpdateJson = true)
            val a = assets.getJSONObject(match)
            apkDownloadUrl = a.optString("browser_download_url")
            apkSize = a.optLong("size", 0L)
        }
        if (apkDownloadUrl.isNullOrBlank()) {
            return UpdateCheckResult.NoApk(hasUpdateJson = true)
        }

        // 版本判断：versionCode 优先（可靠），其次 versionName 三段式比较
        val newer = when {
            info.versionCode > currentVersionCode -> true
            info.versionCode < currentVersionCode -> false
            else -> {
                if (VersionCompare.parseOrNull(info.versionName) == null ||
                    VersionCompare.parseOrNull(currentVersionName) == null
                ) {
                    throw IllegalStateException("versionCode 相同且版本号无法解析：${info.versionName}")
                }
                VersionCompare.compare(info.versionName, currentVersionName) > 0
            }
        }
        if (!newer) return UpdateCheckResult.UpToDate

        return UpdateCheckResult.Available(
            info = info,
            releaseTitle = rel.optString("name").ifBlank { rel.optString("tag_name") },
            publishedAt = rel.optString("published_at"),
            releaseBody = rel.optString("body"),
            apkDownloadUrl = apkDownloadUrl,
            apkSizeBytes = apkSize
        )
    }

    companion object {
        private fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** 小文件（update.json 只有几百字节）同步拉文本，已在上层 IO 线程里。 */
        private fun downloadTextHttp(url: String): String {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "QingJi-App")
                .build()
            client().newCall(request).execute().use { response ->
                require(response.isSuccessful) { "HTTP ${response.code}" }
                return response.body?.string().orEmpty()
            }
        }
    }
}
