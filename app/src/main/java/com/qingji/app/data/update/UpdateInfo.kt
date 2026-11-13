package com.qingji.app.data.update

import org.json.JSONObject

/**
 * ============================================================
 * 版本比较工具（任务书 3.2 红线：绝不只用字符串字典序比较版本）
 * ------------------------------------------------------------
 * 为什么不能直接比字符串：「1.0.10」字典序小于「1.0.9」（'1' < '9'），
 * 字符串比较会把 1.0.10 误判成旧版本。
 *
 * 规则（按可靠度从高到低）：
 *   1. 首选 versionCode 整数比较（update.json 里带）；
 *   2. 没有才退回 versionName 三段式比较：按「.」拆开逐段转整数比，
 *      段数不齐的补 0（1.1 == 1.1.0）；
 *   3. 任何一段不是纯数字 → 抛 IllegalArgumentException，调用方按
 *      「无法判断版本」处理，绝不猜测。
 * ============================================================
 */
object VersionCompare {

    /** 拆成整数段：v1.2.3 / 1.2.3 / 1.2 都能拆；非数字段直接抛错。 */
    fun parse(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        require(cleaned.isNotBlank()) { "版本号为空" }
        return cleaned.split('.').map { segment ->
            require(segment.isNotEmpty() && segment.all { it.isDigit() }) {
                "版本段不是纯数字：$version"
            }
            segment.toInt()
        }
    }

    /** 解析不了返回 null（不抛异常的版本），供调用方判断「这个版本号格式不可信」。 */
    fun parseOrNull(version: String): List<Int>? = try {
        parse(version)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** 三段式比较：>0 = a 更新，<0 = b 更新，0 = 相同。段数不齐补 0。 */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

/**
 * 从 Release 的 update.json 资产里解析出来的更新信息。
 * 这是 App 判断「要不要更新」的可靠数据源（任务书 3.2 建议）。
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val apkAssetName: String,
    val releaseNotes: String
) {
    companion object {
        fun fromJson(text: String): UpdateInfo {
            val obj = JSONObject(text)
            val name = obj.getString("versionName").trim()
            val code = obj.getLong("versionCode")
            require(name.isNotEmpty()) { "update.json 缺 versionName" }
            require(code > 0) { "update.json versionCode 必须为正数" }
            return UpdateInfo(
                versionName = name,
                versionCode = code,
                apkAssetName = obj.optString("apkAssetName").trim(),
                releaseNotes = obj.optString("releaseNotes").trim()
            )
        }
    }
}

/**
 * 「检查更新」的最终结论。检查失败绝不显示为已是最新（任务书 3.3 红线），
 * 所以失败也各占一种状态，UI 分别提示。
 */
sealed class UpdateCheckResult {
    /** 远端比本地新，可以下载。 */
    data class Available(
        val info: UpdateInfo,
        val releaseTitle: String,
        val publishedAt: String,
        val releaseBody: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long
    ) : UpdateCheckResult()

    /** 远端不比本地新（含相等）。 */
    data object UpToDate : UpdateCheckResult()

    /** GitHub 返回 403/429 → 未认证配额 60 次/小时用完。 */
    data object RateLimited : UpdateCheckResult()

    /** 网络不通 / 超时 / 非 200 的其他错误。 */
    data class NetworkError(val detail: String) : UpdateCheckResult()

    /** Release 正常但 assets 里没有 APK（或没有 update.json，versionCode 无从可靠判断）。 */
    data class NoApk(val hasUpdateJson: Boolean) : UpdateCheckResult()

    /** update.json 存在但解析不了 / 版本号格式非法。 */
    data class ParseError(val detail: String) : UpdateCheckResult()
}
