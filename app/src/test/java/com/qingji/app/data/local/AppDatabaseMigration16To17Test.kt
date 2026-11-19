package com.qingji.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.5 阶段四：Room 16 → 17 迁移测试。
 * 证明：新建 user_brand / user_brand_dish 两张表 + 索引，
 *       旧表（user_food）一行不动，新表可正常写入读取。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration16To17Test {

    private fun openV16(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_16_17_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v16 里本迁移只关心 user_food 不被破坏；建一张最小表即可
                    db.execSQL(
                        "CREATE TABLE `user_food` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, `kcalPer100g` REAL NOT NULL)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `16到17迁移后新建品牌两表且旧数据不丢`() {
        val db = openV16()
        db.execSQL("INSERT INTO user_food (name,kcalPer100g) VALUES ('蛋白粉',379.0)")

        AppDatabase.MIGRATION_16_17.migrate(db)

        // 1) 两张新表存在
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('user_brand','user_brand_dish')"
        ).use { c ->
            val set = mutableSetOf<String>()
            while (c.moveToNext()) set.add(c.getString(0))
            set
        }
        assertTrue("user_brand 表缺失", tables.contains("user_brand"))
        assertTrue("user_brand_dish 表缺失", tables.contains("user_brand_dish"))

        // 2) 唯一索引存在（品牌名不重复）
        val idx = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_user_brand_name'"
        ).use { c -> c.moveToFirst() }
        assertTrue("user_brand.name 唯一索引缺失", idx)

        // 3) 旧数据完好
        assertEquals(1L, db.query("SELECT COUNT(*) FROM user_food").use { c ->
            c.moveToFirst(); c.getLong(0)
        })

        // 4) 新表可写入并用默认值
        db.execSQL("INSERT INTO user_brand (name) VALUES ('老乡鸡')")
        val bid = db.query("SELECT id FROM user_brand WHERE name='老乡鸡'").use { c ->
            c.moveToFirst(); c.getLong(0)
        }
        db.execSQL(
            "INSERT INTO user_brand_dish (brandId,name,kcal) VALUES ($bid,'鸡汤',120.0)"
        )
        db.query(
            "SELECT category, servingGrams, nutritionBasis, source FROM user_brand_dish WHERE name='鸡汤'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("其他", c.getString(0))
            assertEquals(100.0, c.getDouble(1), 1e-9)
            assertEquals("PER_100G", c.getString(2))
            assertEquals("USER_BRAND", c.getString(3))
        }
        db.close()
    }
}
