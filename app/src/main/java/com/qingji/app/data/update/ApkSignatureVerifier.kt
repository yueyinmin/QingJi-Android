package com.qingji.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/**
 * ============================================================
 * APK 安装前安全校验（任务书 3.4 第 4-7 条）
 * ------------------------------------------------------------
 * 绝不能仅凭 APK 文件名判断文件安全。装之前必须四道全过：
 *   1. APK 能读出包信息（下载损坏 / 假 APK 挡住）；
 *   2. 包名 = com.qingji.app（挡住同名别的 App）；
 *   3. versionCode = update.json 里说的那个（挡住旧包冒充）；
 *   4. 签名证书 SHA-256 = 当前已装版本的证书（挡住被替换的假包）。
 *
 * 第 4 条是任务书红线：签名不一致时禁止安装，也绝不提示「卸载重装」绕过，
 * 只如实报错。
 * ============================================================
 */
object ApkSignatureVerifier {

    const val EXPECTED_PACKAGE = "com.qingji.app"

    sealed class Result {
        /** 四道校验全过，可以调起安装。 */
        data object Ok : Result()

        /** APK 信息读不出来：下载损坏或不是合法 APK。 */
        data class Unreadable(val detail: String) : Result()

        /** 包名不对。 */
        data class WrongPackage(val actual: String) : Result()

        /** versionCode 跟 update.json 对不上。 */
        data class WrongVersionCode(val expected: Long, val actual: Long) : Result()

        /** 签名证书与当前安装版本不一致（任务书红线：禁止安装）。 */
        data object SignatureMismatch : Result()
    }

    fun verify(context: Context, apkFile: File, expectedVersionCode: Long): Result {
        val pm = context.packageManager

        // ① 能不能读出包信息（不挂签名标志先读基本信息）
        val info = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (info == null) {
            return Result.Unreadable("读不出包信息，文件可能下载损坏")
        }

        // ② 包名
        if (info.packageName != EXPECTED_PACKAGE) {
            return Result.WrongPackage(info.packageName)
        }

        // ③ versionCode 与 update.json 一致
        val archiveCode = info.longVersionCode
        if (archiveCode != expectedVersionCode) {
            return Result.WrongVersionCode(expectedVersionCode, archiveCode)
        }

        // ④ 签名证书：下载包的证书 SHA-256 必须和「当前已装的这个 App」完全一致
        val archiveSigners = pm.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        )?.signingInfo?.apkContentsSigners
        val currentSigners = try {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } catch (_: Exception) {
            null
        }
        val archiveSha = archiveSigners?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        val currentSha = currentSigners?.firstOrNull()?.let { sha256Hex(it.toByteArray()) }
        if (archiveSha == null || currentSha == null) {
            return Result.Unreadable("读不出签名证书")
        }
        if (archiveSha != currentSha) {
            return Result.SignatureMismatch
        }

        return Result.Ok
    }

    /** 证书的 SHA-256 十六进制小写串。 */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
