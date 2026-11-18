package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 外卖快餐独立体系回归（标准库 v0.7 / Room v5）。
 * 验证：8 张外卖表存在且全空（红线 15 不造假）、旧数据一行不丢、
 * 品牌堂食菜单能按品牌/分类/拼音读、溯源能按 dishFoodId 聚合。
 */
@RunWith(RobolectricTestRunner::class)
class FoodCatalogTakeawayTest {

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
            c.moveToFirst(); c.getInt(0)
        }

    private inline fun <T> FoodCatalogDatabase.use(block: (FoodCatalogDatabase) -> T): T =
        try { block(this) } finally { close() }

    @Test
    fun `八张外卖表存在且全空 无假商品`() = runBlocking<Unit> {
        openDb("tw_tables.db").use { db ->
            val dao = db.takeawayDao()
            assertEquals(0, dao.productCount())
            assertEquals(0, dao.variantCount())
            assertEquals(0, dao.comboCount())
            assertEquals(0, dao.comboItemCount())
            assertEquals(0, dao.nutritionCount())
            assertEquals(0, dao.platformCount())
            assertEquals(0, dao.storeScopeCount())
            assertEquals(0, dao.dataSourceCount())
            // 品牌隔离查询在空表上不报错、返回空
            assertTrue(dao.products("laoxiangji", "").isEmpty())
        }
    }

    @Test
    fun `Room版本5身份正确 旧数据一行不丢`() = runBlocking<Unit> {
        openDb("tw_migration.db").use { db ->
            assertEquals(1121, db.queryCount("SELECT COUNT(*) FROM food_catalog"))
            assertEquals(301, db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='laoxiangji'"))
            assertEquals(654, db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='CNFCT-APPROX'"))
            assertEquals(166, db.queryCount("SELECT COUNT(*) FROM food_catalog WHERE source_id='OmniFood8K'"))
            assertEquals(8033, db.queryCount("SELECT COUNT(*) FROM nutrition_sample"))
            assertEquals(27457, db.queryCount("SELECT COUNT(*) FROM food_composition"))
            assertEquals(176, db.queryCount("SELECT COUNT(*) FROM brand_dish_trace"))
            assertEquals(1, db.queryCount("SELECT COUNT(*) FROM brand"))
        }
    }

    @Test
    fun `品牌堂食菜单按品牌分类拼音读取`() = runBlocking<Unit> {
        openDb("tw_menu.db").use { db ->
            val dao = db.catalogDao()
            // 只出老乡鸡，不混入普通食物
            val brandFoods = dao.byBrandAndCategory("laoxiangji", "")
            assertEquals(301, brandFoods.size)
            assertTrue(brandFoods.all { it.sourceId == "laoxiangji" })
            // 分类非空且动态生成
            val cats = dao.categoriesForBrand("laoxiangji")
            assertTrue(cats.isNotEmpty())
            // 某分类过滤后全是该分类
            val cat = cats.first()
            assertTrue(dao.byBrandAndCategory("laoxiangji", cat).all { it.category == cat })
            // 品牌维度拼音搜索
            assertTrue(dao.searchByBrand("laoxiangji", "mckr").any { it.name.contains("梅菜扣肉") })
            // 别的品牌为空（品牌隔离）
            assertTrue(dao.byBrandAndCategory("kfc", "").isEmpty())
        }
    }

    @Test
    fun `溯源按菜聚合 中文标签与报告页码在案`() = runBlocking<Unit> {
        openDb("tw_trace.db").use { db ->
            val dao = db.brandCatalogDao()
            assertEquals(176, dao.traceCount())
            // 梅菜扣肉：半预制、报告第 129 页，能关联回主表同名菜
            val row = db.openHelper.readableDatabase.query(
                "SELECT dishFoodId, processingLevel, reportPage FROM brand_dish_trace WHERE dishName='梅菜扣肉'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                Triple(c.getString(0), c.getString(1), c.getInt(2))
            }
            assertEquals("SEMI_PREPARED", row.second)
            assertEquals(129, row.third)
            val name = db.openHelper.readableDatabase.query(
                "SELECT name FROM food_catalog WHERE food_id=?", arrayOf(row.first)
            ).use { c -> assertTrue(c.moveToFirst()); c.getString(0) }
            assertTrue(name.contains("梅菜扣肉"))
            // 原料 + 加工流程可聚合
            assertTrue(dao.ingredientsForDish(row.first).isNotEmpty())
            assertTrue(dao.processingForDish(row.first).isNotEmpty())
        }
    }
}
