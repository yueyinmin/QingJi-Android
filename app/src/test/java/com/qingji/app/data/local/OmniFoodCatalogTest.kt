package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 预置 food_catalog.db 的 OmniFood8K 接入自检。 */
@RunWith(RobolectricTestRunner::class)
class OmniFoodCatalogTest {

    private lateinit var db: FoodCatalogDatabase

    @Before
    fun setUp() {
        db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodCatalogDatabase::class.java,
            "omnifood_test.db"
        )
            .createFromAsset("database/food_catalog.db")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `预置库保留老数据并接入 166 条命名食物`() = runBlocking {
        assertEquals(820, db.catalogDao().count())
        assertEquals(166, db.omniFoodDao().foodCount())
        // v1.3.2：4 条严重异常样本（346/347/348/2740）按用户指示删除，
        // 样本 8037→8033、组成明细 27466→27457，旧 654 条与 166 条命名食物不变
        assertEquals(8033, db.omniFoodDao().sampleCount())
        assertEquals(27457, db.omniFoodDao().compositionCount())
    }

    @Test
    fun `中文名英文名别名和拼音都能搜到`() = runBlocking {
        assertTrue(db.omniFoodDao().search("麻婆豆腐").any { it.nameCn == "麻婆豆腐" })
        assertTrue(db.omniFoodDao().search("Mapo tofu").any { it.foodId == "omni:1" })
        assertTrue(db.omniFoodDao().search("mpdf").any { it.foodId == "omni:1" })
        assertTrue(db.omniFoodDao().search("mapodoufu").any { it.foodId == "omni:1" })
    }

    @Test
    fun `审核状态按用户拍板落地 全部人工已审核暂定条目如实保留`() = runBlocking {
        val results = db.omniFoodDao().search("")
        assertTrue(results.all { it.foodId.startsWith("omni:") })
        // v1.3.2：用户授权 166 条全部标记人工已审核（approved），
        // 8 条 provisional 如实保留暂定状态，不再需要 needs_verification
        assertTrue(results.all { it.auditStatus == "approved" || it.auditStatus == "provisional" })
        assertTrue(results.none { it.auditStatus == "needs_verification" })
    }

    @Test
    fun `营养保留原始千焦与换算千卡 且每100g一致`() = runBlocking {
        val n = db.omniFoodDao().nutritionFor("omni:1")!!
        assertEquals(2413.061, n.energyKjOriginal, 0.001)
        assertEquals(n.energyKjOriginal / 4.184, n.energyKcal, 0.001)
        assertEquals(n.energyKcal / n.referenceWeightG * 100.0, n.energyKcalPer100g, 0.01)
        assertEquals("per_100g", n.nutritionBasis)
    }

    @Test
    fun `严重异常样本已整体删除 不与正式食物混淆`() = runBlocking {
        // v1.3.2：346/347/348/2740 四条严重异常样本连样本表、组成明细、异常标记一起删除
        listOf("346", "347", "348", "2740").forEach { sid ->
            assertTrue("样本 $sid 应已删除", db.omniFoodDao().sampleById(sid) == null)
            assertTrue("不应有正式食物 omni:$sid", db.omniFoodDao().findById("omni:$sid") == null)
        }
    }
}
