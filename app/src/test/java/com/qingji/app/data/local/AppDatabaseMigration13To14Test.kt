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
 * Room 13 → 14 迁移：睡眠记录表（健康体系 V1.1，工单 9）。
 * 只建表 + 索引，老数据一行不碰；(sourceType, externalId) 唯一索引防设备重复导入。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration13To14Test {

    /** 手建一个 v13 的最小库（只放一张老表占位），塞老数据再升到 14。 */
    private fun openV13(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_13_14_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `exercise_log` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`date` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                            "`minutes` INTEGER NOT NULL, `kcal` REAL NOT NULL, " +
                            "`steps` INTEGER NOT NULL DEFAULT 0, " +
                            "`distanceKm` REAL NOT NULL DEFAULT 0.0, " +
                            "`createdAt` INTEGER NOT NULL)"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `13 到 14 迁移后 睡眠表建立 老数据原样`() {
        val db = openV13()
        db.execSQL(
            "INSERT INTO exercise_log (date,type,minutes,kcal,steps,distanceKm,createdAt) " +
                "VALUES ('2026-11-16','走路',30,120.0,3000,0.0,1000)"
        )

        AppDatabase.MIGRATION_13_14.migrate(db)

        // ---- 睡眠表建出来了，列齐全 ----
        val found = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sleep_record'")
            .use { c -> c.moveToFirst() }
        assertTrue("sleep_record 表未创建", found)

        // ---- 老数据原样 ----
        db.query("SELECT COUNT(*) FROM exercise_log").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // ---- 手动插入一条跨午夜记录，验证列默认值和唯一索引生效 ----
        db.execSQL(
            "INSERT INTO sleep_record (date, startTime, endTime) " +
                "VALUES ('2026-11-17', 1794855000000, 1794883800000)"
        )
        db.query("SELECT durationMinutes, quality, sourceType, externalId FROM sleep_record").use { c ->
            c.moveToFirst()
            // durationMinutes 按默认值 0 存（迁移不做计算，应用层负责），其余默认值到位
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertEquals("MANUAL", c.getString(2))
            assertEquals("", c.getString(3))
        }

        // ---- 唯一索引：同 (sourceType, externalId) 再插一条必须被拒 ----
        var rejected = false
        try {
            db.execSQL(
                "INSERT INTO sleep_record (date, startTime, endTime) " +
                    "VALUES ('2026-11-17', 1794855000000, 1794883800000)"
            )
        } catch (e: Exception) {
            rejected = true
        }
        assertTrue("唯一索引没生效：同来源同外部ID不应重复插入", rejected)
    }
}
