package com.qingji.app.data.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.ai.AiDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TASK-04：导出范围文案 / 顶层键集合的回归。
 * note 不参与 parseBackup，改它不影响老备份解析；这里直接拿真实导出文件核对。
 *
 * 阶段 L：改成注入内存库。以前用 `BackupManager(context)` 会走 AppDatabase.get() 全局单例，
 * 全量跑时单例连接已被别的测试类关掉，Robolectric 抛
 * `Illegal connection pointer`（单跑却通过）——这是测试隔离问题，不是导出逻辑问题。
 */
@RunWith(RobolectricTestRunner::class)
class BackupExportScopeTest {

    private lateinit var mainDb: AppDatabase
    private lateinit var aiDb: AiDatabase

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        mainDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        aiDb = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        mainDb.close()
        aiDb.close()
    }

    @Test
    fun `导出 JSON 顶层键集合与 note 文案对齐记录类范围`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = BackupManager(context, dbOverride = mainDb, aiDbOverride = aiDb)
            .exportJson().getOrThrow()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val keys = HashSet<String>()
        val it = root.keys()
        while (it.hasNext()) keys.add(it.next())

        // 记录类段都在（含 TASK-01 纳入的自律段、阶段 L 纳入的 AI 数据段）
        assertTrue(
            keys.containsAll(
                listOf(
                    "app", "version", "exportedAt", "note", "profile", "foodLogs",
                    "weights", "water", "diaries", "bodyMeasures", "photos",
                    "exercises", "fastings", "training", "selfDiscipline",
                    "userBrands", "userBrandDishes", "userFoodGroups", "userFoodGroupItems",
                    "aiConfig", "aiConfigs", "aiData" // v7 单条兼容段 + 多服务数组；v9 AI 数据段
                )
            )
        )
        // v9 = 阶段 L：新增 aiData 段（会话 / 消息 / 记忆 / 提醒）
        assertEquals(9, root.getInt("version"))
        // 多服务数组不含任何 Key 字段
        val arr = root.getJSONArray("aiConfigs")
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            assertFalse(c.has("apiKey"))
            assertFalse(c.has("key"))
            assertFalse(c.has("cipherText"))
            assertTrue(c.has("id") && c.has("displayName"))
        }

        // note 文案不再说「全部数据」，改成「记录类数据」
        assertEquals("不含 API Key；导入会覆盖本机记录类数据", root.getString("note"))
        assertFalse(root.getString("note").contains("全部数据"))

        // 真实未包含项不该作为顶层段出现
        assertFalse(keys.contains("settings"))
        assertFalse(keys.contains("searchHistory"))
        assertFalse(keys.contains("foodSearchHistory"))
        assertFalse(keys.contains("apiKey"))

        // 阶段 K：整份文件扫一遍，任何位置都不许出现密钥痕迹
        val raw = file.readText(Charsets.UTF_8)
        assertFalse("导出文件不应出现 sk- 形式的 Key", raw.contains("sk-"))
        assertFalse("导出文件不应出现 Key 密文字段", raw.contains("cipher_text"))
        assertFalse("导出文件不应出现 iv 字段", raw.contains("\"iv\""))
        assertFalse("导出文件不应出现 Bearer 头", raw.contains("Bearer "))
        assertFalse("导出文件不应出现 Keystore 别名", raw.contains("qingji_ai_api_key"))
    }
}
