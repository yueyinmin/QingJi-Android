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
        assertEquals(8037, db.omniFoodDao().sampleCount())
        assertEquals(27466, db.omniFoodDao().compositionCount())
    }

    @Test
    fun `中文名英文名别名和拼音都能搜到`() = runBlocking {
        assertTrue(db.omniFoodDao().search("麻婆豆腐").any { it.nameCn == "麻婆豆腐" })
        assertTrue(db.omniFoodDao().search("Mapo tofu").any { it.foodId == "omni:1" })
        assertTrue(db.omniFoodDao().search("mpdf").any { it.foodId == "omni:1" })
        assertTrue(db.omniFoodDao().search("mapodoufu").any { it.foodId == "omni:1" })
    }

    @Test
    fun `样本不出现在普通食物搜索且审核状态不冒充通过`() = runBlocking {
        val results = db.omniFoodDao().search("")
        assertTrue(results.all { it.foodId.startsWith("omni:") })
        assertTrue(results.all { it.auditStatus == "needs_verification" || it.auditStatus == "provisional" })
        assertTrue(results.none { it.auditStatus == "approved" })
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
    fun `严重异常样本不与正式食物混淆`() = runBlocking {
        // 346/347/348/2740 只保留在样本表，不生成正式 food_id
        listOf("346", "347", "348", "2740").forEach { sid ->
            assertTrue(db.omniFoodDao().sampleById(sid)!!.qualityStatus == "quarantined_severe")
            assertTrue(db.omniFoodDao().findById("omni:$sid") == null)
        }
    }
}
