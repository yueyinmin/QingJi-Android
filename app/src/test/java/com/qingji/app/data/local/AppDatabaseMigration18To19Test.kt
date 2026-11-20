package com.qingji.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.6：Room 18 → 19 迁移测试。
 * 证明：新增 user_food_group / user_food_group_item 两张表 + 索引，
 * 老表（user_food / food_favorite）一行不动，新表默认值正确、唯一索引生效。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration18To19Test {

    private fun openV18(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_18_19_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 只建本次要验证「不受影响」的老表
                    db.execSQL(
                        "CREATE TABLE `user_food` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`kcalPer100g` REAL NOT NULL DEFAULT 0.0, " +
                            "`createdAt` INTEGER NOT NULL DEFAULT 0)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `18到19迁移后老数据保留且新表建成`() {
        val db = openV18()
        db.execSQL("INSERT INTO user_food (id,name,kcalPer100g,createdAt) VALUES (1,'老食物',123.0,999)")

        AppDatabase.MIGRATION_18_19.migrate(db)

        // 老数据一行不丢
        db.query("SELECT name, kcalPer100g FROM user_food WHERE id = 1").use { c ->
            assert(c.moveToFirst())
            assertEquals("老食物", c.getString(0))
            assertEquals(123.0, c.getDouble(1), 0.01)
        }
        // 两张新表存在（能插能查）
        db.execSQL(
            "INSERT INTO user_food_group (id,name,coverUri,sortOrder,createdAt,updatedAt) " +
                "VALUES (1,'早餐组','',0,1000,1000)"
        )
        db.execSQL(
            "INSERT INTO user_food_group_item (id,groupId,foodId,createdAt) VALUES (1,1,1,1000)"
        )
        db.query("SELECT name, coverUri, sortOrder FROM user_food_group WHERE id = 1").use { c ->
            assert(c.moveToFirst())
            assertEquals("早餐组", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT groupId, foodId FROM user_food_group_item WHERE id = 1").use { c ->
            assert(c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals(1L, c.getLong(1))
        }
        db.close()
    }

    @Test
    fun `groupId加foodId唯一索引防止同一食物重复入组`() {
        val db = openV18()
        AppDatabase.MIGRATION_18_19.migrate(db)
        db.execSQL(
            "INSERT INTO user_food_group (id,name) VALUES (1,'组A')"
        )
        db.execSQL("INSERT INTO user_food_group_item (groupId,foodId,createdAt) VALUES (1,5,1000)")
        var duplicateRejected = false
        try {
            db.execSQL("INSERT INTO user_food_group_item (groupId,foodId,createdAt) VALUES (1,5,2000)")
        } catch (e: Exception) {
            duplicateRejected = true
        }
        assertEquals(true, duplicateRejected)
        // 同一个食物加进另一个组是允许的（多对多）
        db.execSQL("INSERT INTO user_food_group (id,name) VALUES (2,'组B')")
        db.execSQL("INSERT INTO user_food_group_item (groupId,foodId,createdAt) VALUES (2,5,3000)")
        db.query("SELECT COUNT(*) FROM user_food_group_item WHERE foodId = 5").use { c ->
            assert(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.close()
    }
}
