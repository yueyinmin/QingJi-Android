package com.qingji.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release JSON 解析 + 版本判断的单测：直接喂构造好的 JSON 文本，
 * 不起真实网络（真实检查放真机验收，任务书第九节）。
 */
class GitHubReleaseParsingTest {

    /** update.json 的假下载器：测试里不起真实网络，按 URL 关键字回内容。 */
    private val fakeFetcher: (String) -> String = { url ->
        when {
            url.contains("update.json") -> """{"versionName":"1.2.0","versionCode":120,"apkAssetName":"QingJi-v1.2.0.apk","releaseNotes":"测试"}"""
            else -> throw IllegalArgumentException("测试里没准备这个资源：$url")
        }
    }

    private val repo = GitHubReleaseRepository(updateJsonFetcher = fakeFetcher)

    private fun releaseJson(
        tagName: String = "v1.2.0",
        name: String = "轻记 v1.2.0 正式版",
        prerelease: Boolean = false,
        draft: Boolean = false,
        withUpdateJson: Boolean = true,
        updateJsonBody: String = """{"versionName":"1.2.0","versionCode":120,"apkAssetName":"QingJi-v1.2.0.apk","releaseNotes":"测试"}""",
        withApk: Boolean = true,
        apkName: String? = null
    ): String {
        val assets = buildList {
            if (withUpdateJson) add("""{"name":"update.json","browser_download_url":"https://example.com/update.json","size":100}""")
            if (withApk) add("""{"name":"${apkName ?: "QingJi-v1.2.0.apk"}","browser_download_url":"https://example.com/app.apk","size":3284678}""")
        }
        return """
            {
              "tag_name": "$tagName",
              "name": "$name",
              "body": "更新说明正文",
              "published_at": "2026-11-13T08:00:00Z",
              "prerelease": $prerelease,
              "draft": $draft,
              "assets": [${assets.joinToString(",")}]
            }
        """.trimIndent()
    }

    @Test
    fun `发现新版本`() {
        val r = repo.parseAndCompare(releaseJson(), "1.1.0", 111)
        assertTrue(r is UpdateCheckResult.Available)
        r as UpdateCheckResult.Available
        assertEquals("1.2.0", r.info.versionName)
        assertEquals(120L, r.info.versionCode)
        assertEquals("轻记 v1.2.0 正式版", r.releaseTitle)
        assertEquals("https://example.com/app.apk", r.apkDownloadUrl)
        assertEquals(3284678L, r.apkSizeBytes)
    }

    @Test
    fun `版本相同已是最新`() {
        val r = repo.parseAndCompare(releaseJson(), "1.2.0", 120)
        assertTrue(r is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `远端更旧也是已是最新`() {
        val r = repo.parseAndCompare(releaseJson(), "1.3.0", 130)
        assertTrue(r is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `prerelease不算正式更新源`() {
        val r = repo.parseAndCompare(releaseJson(prerelease = true), "1.1.0", 111)
        assertTrue(r is UpdateCheckResult.NoApk)
    }

    @Test
    fun `draft不算正式更新源`() {
        val r = repo.parseAndCompare(releaseJson(draft = true), "1.1.0", 111)
        assertTrue(r is UpdateCheckResult.NoApk)
    }

    @Test
    fun `没有updateJson不做字典序猜测`() {
        val r = repo.parseAndCompare(
            releaseJson(withUpdateJson = false, withApk = true), "1.1.0", 111
        )
        assertTrue(r is UpdateCheckResult.NoApk && !r.hasUpdateJson)
    }

    @Test
    fun `updateJson指定的APK不在assets里按没有APK处理`() {
        val r = repo.parseAndCompare(
            releaseJson(withApk = true, apkName = "QingJi-别的名字.apk"), "1.1.0", 111
        )
        assertTrue(r is UpdateCheckResult.NoApk && r.hasUpdateJson)
    }

    @Test
    fun `release里完全没有APK`() {
        val r = repo.parseAndCompare(releaseJson(withApk = false), "1.1.0", 111)
        assertTrue(r is UpdateCheckResult.NoApk && r.hasUpdateJson)
    }
}
