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
 * v1.5 阶段二：Room 15 → 16 迁移测试。
 * 证明：user_food 加了两列，旧食物行原样保留，默认值正确（老数据按每100g看待）。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration15To16Test {

    private fun openV15(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_15_16_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 只建本次迁移需要的 user_food（v15 的真实结构）
                    db.execSQL(
                        "CREATE TABLE `user_food` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, `pinyin` TEXT NOT NULL, `barcode` TEXT, " +
                            "`kcalPer100g` REAL NOT NULL, `proteinPer100g` REAL NOT NULL, " +
                            "`carbPer100g` REAL NOT NULL, `fatPer100g` REAL NOT NULL, " +
                            "`fiberPer100g` REAL, `sodiumMgPer100g` REAL, `sugarPer100g` REAL, " +
                            "`calciumMgPer100g` REAL, `defaultGrams` REAL NOT NULL, " +
                            "`source` TEXT NOT NULL, `category` TEXT NOT NULL, `brand` TEXT NOT NULL, " +
                            "`favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `15到16迁移后旧食物行的营养数据原样保留且新列有默认值`() {
        val db = openV15()
        db.execSQL(
            "INSERT INTO user_food (name,pinyin,kcalPer100g,proteinPer100g,carbPer100g,fatPer100g," +
                "defaultGrams,source,category,brand,favorite,createdAt) " +
                "VALUES ('蛋白粉','dbf',379.0,80.0,5.0,5.0,100.0,'user','其他','',0,1000)"
        )

        AppDatabase.MIGRATION_15_16.migrate(db)

        val cols = db.query("PRAGMA table_info(`user_food`)").use { c ->
            val set = mutableSetOf<String>()
            while (c.moveToNext()) set.add(c.getString(c.getColumnIndex("name")))
            set
        }
        assertTrue("nutritionBasis 列缺失", cols.contains("nutritionBasis"))
        assertTrue("note 列缺失", cols.contains("note"))

        db.query(
            "SELECT name, kcalPer100g, proteinPer100g, nutritionBasis, note FROM user_food WHERE name='蛋白粉'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("蛋白粉", c.getString(0))
            assertEquals(379.0, c.getDouble(1), 1e-9)
            assertEquals(80.0, c.getDouble(2), 1e-9)
            assertEquals("PER_100G", c.getString(3))   // 老数据默认按每100g
            assertEquals("", c.getString(4))
        }
        assertEquals(1L, db.query("SELECT COUNT(*) FROM user_food").use { c ->
            c.moveToFirst(); c.getLong(0)
        })
        db.close()
    }
}
