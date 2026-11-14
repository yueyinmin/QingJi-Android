package com.qingji.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TASK-04：导出范围文案 / 顶层键集合的回归。
 * note 不参与 parseBackup，改它不影响老备份解析；这里直接拿真实导出文件核对。
 */
@RunWith(RobolectricTestRunner::class)
class BackupExportScopeTest {

    @Test
    fun `导出 JSON 顶层键集合与 note 文案对齐记录类范围`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = BackupManager(context).exportJson().getOrThrow()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val keys = HashSet<String>()
        val it = root.keys()
        while (it.hasNext()) keys.add(it.next())

        // 记录类段都在（含 TASK-01 纳入的自律段）
        assertTrue(
            keys.containsAll(
                listOf(
                    "app", "version", "exportedAt", "note", "profile", "foodLogs",
                    "weights", "water", "diaries", "bodyMeasures", "photos",
                    "exercises", "fastings", "training", "selfDiscipline"
                )
            )
        )
        assertEquals(4, root.getInt("version"))

        // note 文案不再说「全部数据」，改成「记录类数据」
        assertEquals("不含 API Key；导入会覆盖本机记录类数据", root.getString("note"))
        assertFalse(root.getString("note").contains("全部数据"))

        // 真实未包含项不该作为顶层段出现
        assertFalse(keys.contains("settings"))
        assertFalse(keys.contains("searchHistory"))
        assertFalse(keys.contains("foodSearchHistory"))
        assertFalse(keys.contains("apiKey"))
    }
}
