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
 * 老乡鸡堂食入库回归测试（标准库 v0.6 / Room v4）。
 * 直接打开 assets 里的 food_catalog.db，验证：品牌动态数据、堂食 301 条、
 * 溯源关联、旧数据（654 + OmniFood8K）一个不丢。
 */
@RunWith(RobolectricTestRunner::class)
class FoodCatalogLaoxiangjiTest {

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
    fun `主表1281条 老乡鸡301条进主表且全部未审核`() = runBlocking<Unit> {
        openDb("lxj_main.db").use { db ->
            assertEquals(1281, db.queryCount("SELECT COUNT(*) FROM food_catalog"))
            assertEquals(
                301,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji'")
            )
            // 无 LICENSE → 不标已核对（红线：机器结构化 ≠ 人工核对）
            assertEquals(
                301,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND audit_status=0")
            )
            // 旧数据不丢
            assertEquals(
                654,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='CNFCT-APPROX'")
            )
            assertEquals(
                166,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='OmniFood8K' AND audit_status=1")
            )
        }
    }

    @Test
    fun `0千卡整理 40道有出处回填 其余保持零不编造`() = runBlocking<Unit> {
        openDb("lxj_zero_kcal.db").use { db ->
            assertEquals(
                40,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND source_note LIKE '%热量估算%'")
            )
            assertEquals(
                96,
                db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji' AND energy_kcal<=0")
            )
            fun kcalOf(name: String): Double = db.openHelper.readableDatabase.query(
                "SELECT energy_kcal FROM food_catalog WHERE name=? AND source_id='laoxiangji'",
                arrayOf(name)
            ).use { c -> assertTrue(c.moveToFirst()); c.getDouble(0) }
            // 同名匹配：番茄鸡蛋面 = v0.2 标准库 135 kcal/100g
            assertEquals(135.0, kcalOf("番茄鸡蛋面"), 0.0)
            // 无出处：大排面仍是 0，界面显示「暂无热量数据」，不冒充 0 千卡
            assertEquals(0.0, kcalOf("大排面"), 0.0)
        }
    }

    @Test
    fun `品牌由数据库驱动 老乡鸡居首 六大快餐品牌在列`() = runBlocking<Unit> {
        openDb("lxj_brand.db").use { db ->
            val brands = db.brandCatalogDao().findEnabledBrands()
            // 0.8：老乡鸡 + 肯德基/麦当劳/华莱士/塔斯汀/必胜客/达美乐
            assertEquals(7, brands.size)
            assertEquals("laoxiangji", brands[0].brandId)
            assertEquals("老乡鸡", brands[0].name)
            val ids = brands.map { it.brandId }
            assertTrue(ids.containsAll(listOf("kfc", "mcdonalds", "hls", "tastien", "pizzahut", "dominos")))
        }
    }

    @Test
    fun `溯源卡入库 加工等级与报告页码在案`() = runBlocking<Unit> {
        openDb("lxj_trace.db").use { db ->
            assertEquals(176, db.brandCatalogDao().traceCount())
            // 抽查：梅菜扣肉 = 半预制，报告第 129 页，关联到主表行
            val trace = db.openHelper.readableDatabase.query(
                "SELECT t.dishFoodId, t.processingLevel, t.reportPage FROM brand_dish_trace t " +
                    "WHERE t.dishName='梅菜扣肉'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                Triple(c.getString(0), c.getString(1), c.getInt(2))
            }
            assertEquals("SEMI_PREPARED", trace.second)
            assertEquals(129, trace.third)
            val foodName = db.openHelper.readableDatabase.query(
                "SELECT name FROM food_catalog WHERE food_id=?", arrayOf(trace.first)
            ).use { c ->
                assertTrue(c.moveToFirst()); c.getString(0)
            }
            assertTrue("溯源卡应关联到主表的梅菜扣肉，实际: $foodName", foodName.contains("梅菜扣肉"))
            // 原料来源带供应商
            val ings = db.brandCatalogDao().ingredientsForDish(trace.first)
            assertTrue(ings.size >= 2)
            assertTrue(ings.any { it.supplier.contains("公司") })
            // 中央厨房加工流程在案
            assertTrue(db.brandCatalogDao().processingForDish(trace.first).isNotEmpty())
        }
    }

    @Test
    fun `搜索能命中老乡鸡菜品 拼音别名可用`() = runBlocking<Unit> {
        openDb("lxj_search.db").use { db ->
            // 中文名
            assertTrue(db.catalogDao().search("梅菜扣肉").any { it.sourceId == "laoxiangji" })
            // 拼音首字母（dinein 数据自带 pinyin 字段：梅菜扣肉 = méi cài kòu ròu）
            assertTrue(db.catalogDao().search("mckr").any { it.sourceId == "laoxiangji" && it.name.contains("梅菜扣肉") })
        }
    }

    private inline fun <T> FoodCatalogDatabase.use(block: (FoodCatalogDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
