package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 更新提示与「版本信息」共用同一份数据，改文案时这里能兜住。 */
class AppChangeLogTest {

    @Test
    fun `当前版本有对应的更新记录`() {
        val r = AppChangeLog.current(110)
        assertEquals("1.0.10", r?.versionName)
        assertTrue(r?.items.orEmpty().isNotEmpty())
    }

    @Test
    fun `跨一个版本升级只列新版本`() {
        val list = AppChangeLog.since(109, 110)
        assertEquals(1, list.size)
        assertEquals("1.0.10", list.first().versionName)
    }

    @Test
    fun `连跳几个版本时一次把中间更新全列出且按新到旧`() {
        val list = AppChangeLog.since(108, 110)
        assertEquals(2, list.size)
        assertEquals("1.0.10", list[0].versionName)
        assertEquals("1.0.9", list[1].versionName)
    }

    @Test
    fun `同版本重复打开不弹`() {
        assertTrue(AppChangeLog.since(110, 110).isEmpty())
        assertTrue(AppChangeLog.since(111, 110).isEmpty())
    }

    @Test
    fun `版本信息里能查到更新提示里的每一项`() {
        val text = AppChangeLog.versionInfoText()
        AppChangeLog.releases.forEach { r ->
            assertTrue(text.contains("v${r.versionName}"))
            r.items.forEach { item -> assertTrue(text.contains(item)) }
        }
    }
}
