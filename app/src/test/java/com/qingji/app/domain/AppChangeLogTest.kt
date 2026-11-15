package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新提示与「版本信息」共用同一份数据，改文案时这里能兜住。
 *
 * v1.2.0 把 1.1.0~1.2.0 的更新合并成一条 120（不再出现小版本号），
 * 这组断言同步更新；测试语义不变，只换基准版本号。
 */
class AppChangeLogTest {

    @Test
    fun `当前版本有对应的更新记录`() {
        val r = AppChangeLog.current(120)
        assertEquals("1.2.0", r?.versionName)
        assertTrue(r?.items.orEmpty().isNotEmpty())
    }

    @Test
    fun `跨一个版本升级只列新版本`() {
        val list = AppChangeLog.since(109, 120)
        assertEquals(1, list.size)
        assertEquals("1.2.0", list.first().versionName)
    }

    @Test
    fun `连跳几个版本时一次把中间更新全列出且按新到旧`() {
        val list = AppChangeLog.since(108, 120)
        assertEquals(2, list.size)
        assertEquals("1.2.0", list[0].versionName)
        assertEquals("1.0.9", list[1].versionName)
    }

    @Test
    fun `同版本重复打开不弹`() {
        assertTrue(AppChangeLog.since(120, 120).isEmpty())
        assertTrue(AppChangeLog.since(121, 120).isEmpty())
    }

    @Test
    fun `合并后的 1_2_0 记录不出现任何小版本号`() {
        val text = AppChangeLog.versionInfoText()
        // 主人明确要求：更新提示里只讲 v1.2.0，不贴 1.1.x 小版本号
        assertTrue(!text.contains("v1.1."))
        assertTrue(text.contains("v1.2.0"))
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
