package com.qingji.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较是更新机制的地基，1.0.10 < 1.0.9 这种字典序陷阱必须挡死。 */
class VersionCompareTest {

    @Test
    fun `十位补丁版本不会被字典序坑`() {
        // 字符串比较会把 1.0.10 当成比 1.0.9 旧，这是任务书点名的坑
        assertTrue(VersionCompare.compare("1.0.10", "1.0.9") > 0)
        assertTrue(VersionCompare.compare("1.0.9", "1.0.10") < 0)
    }

    @Test
    fun `带v前缀和段数不齐都能比`() {
        assertEquals(0, VersionCompare.compare("v1.1.0", "1.1"))
        assertEquals(0, VersionCompare.compare("V1.1.0", "1.1.0"))
        assertTrue(VersionCompare.compare("1.2", "1.1.9") > 0)
        assertTrue(VersionCompare.compare("2.0", "1.99.99") > 0)
    }

    @Test
    fun `非数字段解析返回null而不是乱比`() {
        assertNull(VersionCompare.parseOrNull("1.1.0-beta"))
        assertNull(VersionCompare.parseOrNull(""))
        assertNull(VersionCompare.parseOrNull("最新版"))
        assertEquals(listOf(1, 1, 0), VersionCompare.parseOrNull("v1.1.0"))
    }

    @Test
    fun `三段式相同相等`() {
        assertEquals(0, VersionCompare.compare("1.1.0", "1.1.0"))
        assertEquals(0, VersionCompare.compare("1.1", "1.1.0"))
    }
}

/** update.json 是唯一可靠数据源，解析必须严格。 */
class UpdateInfoParsingTest {

    @Test
    fun `标准格式能解析`() {
        val json = """
            {"versionName":"1.2.0","versionCode":120,"apkAssetName":"QingJi-v1.2.0.apk","releaseNotes":"说明"}
        """.trimIndent()
        val info = UpdateInfo.fromJson(json)
        assertEquals("1.2.0", info.versionName)
        assertEquals(120L, info.versionCode)
        assertEquals("QingJi-v1.2.0.apk", info.apkAssetName)
        assertEquals("说明", info.releaseNotes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `versionCode为负数拒绝`() {
        UpdateInfo.fromJson("""{"versionName":"1.2.0","versionCode":-5}""")
    }

    @Test(expected = Exception::class)
    fun `缺versionName拒绝`() {
        UpdateInfo.fromJson("""{"versionCode":120}""")
    }
}
