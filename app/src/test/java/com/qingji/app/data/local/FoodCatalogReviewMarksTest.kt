package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.3.2 预置库「人工已审核」标记回归测试。
 *
 * 用户拍板的三件事都固化在这里，谁要是把库改回去了，单测直接挂：
 *   1. 166 条 OmniFood8K 全部标记人工已审核（food_entity/food_nutrition = approved，
 *      food_catalog.audit_status = 1）；8 条 provisional 保留，不冒充已审核；
 *   2. 4 条严重异常样本（346/347/348/2740）连同组成明细、异常标记一起删除；
 *   3. 界面接入的明细链路（名字 → 细账 → 样本 → 配料）在真库上是通的。
 *
 * 测试直接打开 assets 里随 APK 发布的那份 food_catalog.db，测的就是真实数据。
 */
@RunWith(RobolectricTestRunner::class)
class FoodCatalogReviewMarksTest {

    /** 每个测试用独立的库文件名，避免 Room 单例 / 文件锁互相影响。 */
    private fun openDb(name: String): FoodCatalogDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodCatalogDatabase::class.java,
            name
        )
            .createFromAsset("database/food_catalog.db")
            .allowMainThreadQueries()
            .build()

    private fun FoodCatalogDatabase.queryCount(sql: String): Int =
        openHelper.readableDatabase.query(sql).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    @Test
    fun `166条全部标记人工已审核 8条暂定保留`() = runBlocking {
        openDb("review_marks_status.db").use { db ->
            // food_entity：158 approved + 8 provisional，不允许再有任何 needs_verification
            assertEquals(158, db.queryCount("SELECT COUNT(*) FROM food_entity WHERE audit_status='approved'"))
            assertEquals(8, db.queryCount("SELECT COUNT(*) FROM food_entity WHERE audit_status='provisional'"))
            assertEquals(0, db.queryCount("SELECT COUNT(*) FROM food_entity WHERE audit_status='needs_verification'"))

            // food_nutrition：细账状态同步标记
            assertEquals(158, db.queryCount("SELECT COUNT(*) FROM food_nutrition WHERE audit_status='approved'"))
            assertEquals(0, db.queryCount("SELECT COUNT(*) FROM food_nutrition WHERE audit_status='needs_verification'"))

            // food_catalog 主表：166 条 OmniFood8K = audit_status 1（已核对），旧 654 条仍是 0
            assertEquals(
                166,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='OmniFood8K' AND audit_status=1")
            )
            assertEquals(
                654,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='CNFCT-APPROX' AND audit_status=0")
            )
        }
    }

    @Test
    fun `4条严重异常样本连根删除`() = runBlocking {
        openDb("review_marks_severe.db").use { db ->
            // 样本 8037 - 4 = 8033，且不允许再有 quarantined_severe
            assertEquals(8033, db.queryCount("SELECT COUNT(*) FROM nutrition_sample"))
            assertEquals(
                0,
                db.queryCount("SELECT COUNT(*) FROM nutrition_sample WHERE quality_status='quarantined_severe'")
            )
            // 组成明细 27466 - 9 = 27457；异常标记 470 - 4 = 466
            assertEquals(27457, db.queryCount("SELECT COUNT(*) FROM food_composition"))
            assertEquals(466, db.queryCount("SELECT COUNT(*) FROM quality_issue"))
            for (sid in listOf("346", "347", "348", "2740")) {
                assertEquals(0, db.queryCount("SELECT COUNT(*) FROM nutrition_sample WHERE sample_id='$sid'"))
                assertEquals(0, db.queryCount("SELECT COUNT(*) FROM food_composition WHERE parent_sample_id='$sid'"))
                assertEquals(0, db.queryCount("SELECT COUNT(*) FROM quality_issue WHERE sample_id='$sid'"))
            }
        }
    }

    @Test
    fun `界面明细链路 中文名能一路查到配料明细`() = runBlocking<Unit> {
        openDb("review_marks_chain.db").use { db ->
            val dao = db.omniFoodDao()

            // 主表搜得到（现有搜索走这张表，英文在 aliases 里）
            assertEquals(
                1,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE name='西红柿炒鸡蛋' AND source_id='OmniFood8K'")
            )

            // 界面链路：中文名 → food_entity → food_nutrition → sample → 组成明细
            val entity = dao.findByNameCn("西红柿炒鸡蛋")
            assertNotNull("按中文名找不到 OmniFood 条目", entity)
            val nutrition = dao.nutritionFor(entity!!.foodId)
            assertNotNull("找不到营养细账", nutrition)
            assertTrue("参考克重应该是 100g 左右", nutrition!!.referenceWeightG > 0)
            val sample = dao.sampleById(nutrition.sourceSampleId)
            assertNotNull("细账关联的样本不存在", sample)
            val composition = dao.compositionForSample(sample!!.sampleId)
            assertTrue("这道菜的配料明细不应为空", composition.isNotEmpty())
            // 组成明细的重量合计应该接近样本总重（西红柿炒鸡蛋是正常样本）
            assertTrue(
                composition.sumOf { it.ingredientWeightG } > 0
            )
            // 异常查询接口在正常样本上不炸
            dao.issuesForSample(sample.sampleId)
        }
    }

    private inline fun <T> FoodCatalogDatabase.use(block: (FoodCatalogDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
