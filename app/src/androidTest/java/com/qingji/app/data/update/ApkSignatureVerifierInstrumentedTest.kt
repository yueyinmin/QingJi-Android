package com.qingji.app.data.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qingji.app.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * APK 安装前校验的真机验证（模拟器跑 androidTest）。
 *
 * 三个场景：
 *   1. 拿「当前装着的 App 自己的 base.apk」→ 四道全过（Ok）；
 *   2. 喂一个错误 versionCode → WrongVersionCode；
 *   3. 喂一个「正式签名但当前装的是 debug 包」的 APK → SignatureMismatch
 *      （任务书红线：签名不一致禁止安装，androidTest 装的是 debug 包，
 *      正式签名的包一定对不上）。
 *
 * 场景 3 用的正式签名包放在 androidTest/assets/release_v110.bin（3.2MB，正式签名包改名避免被 AGP 特殊处理，
 * 与 GitHub Release 上的是同一份），测试时拷到私有目录再校验。
 */
@RunWith(AndroidJUnit4::class)
class ApkSignatureVerifierInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun copyAssetToFiles(assetName: String, targetName: String): File {
        // 注意：测试包自己的资产要用 instrumentation context，
        // ApplicationProvider.getApplicationContext() 拿到的是主 App 的 context（读的是主 APK 的资产）
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val target = File(context.filesDir, targetName)
        testContext.assets.open(assetName).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    @Test
    fun `当前安装包自己校验通过`() {
        // 已装 App 的真实 APK 路径（sourceDir），拷到私有目录模拟「刚下载的更新包」
        val sourceApk = File(context.applicationInfo.sourceDir)
        val copied = File(context.filesDir, "update/self-copy.apk").apply {
            parentFile?.mkdirs()
        }
        sourceApk.copyTo(copied, overwrite = true)

        val result = ApkSignatureVerifier.verify(context, copied, BuildConfig.VERSION_CODE.toLong())
        assertTrue(
            "同一 APK 自己校验必须通过，实际：$result",
            result is ApkSignatureVerifier.Result.Ok
        )
        copied.delete()
    }

    @Test
    fun `versionCode对不上要拦`() {
        val sourceApk = File(context.applicationInfo.sourceDir)
        val copied = File(context.filesDir, "update/self-copy2.apk").apply { parentFile?.mkdirs() }
        sourceApk.copyTo(copied, overwrite = true)

        val result = ApkSignatureVerifier.verify(context, copied, 999L)
        assertTrue(
            "versionCode 不一致必须拦下，实际：$result",
            result is ApkSignatureVerifier.Result.WrongVersionCode
        )
        copied.delete()
    }

    @Test
    fun `签名不一致必须拦下`() {
        // 正式签名的 v1.1.0 包 vs 当前 debug 安装包：证书必然不同
        val apk = copyAssetToFiles("release_v110.bin", "update/release-signed.apk")
        val result = ApkSignatureVerifier.verify(context, apk, 111L)
        assertTrue(
            "签名不一致必须被拦（任务书红线），实际：$result",
            result is ApkSignatureVerifier.Result.SignatureMismatch
        )
        apk.delete()
    }
}
