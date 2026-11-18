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
 * Room 12 → 13 迁移：健康体系两张新表（运动热量参数库 + 统一运动消耗账本）。
 * 只建表 + 灌种子，老数据一行不碰；账本带 (sourceType, sourceId) 唯一索引去重。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration12To13Test {

    /** 手建一个 v12 的最小库（只有老表中最常用的一张），往里塞老数据再升级。 */
    private fun openV12(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_12_13_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
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
    fun `12 到 13 迁移后 两张新表建立 种子齐全 老数据原样`() {
        val db = openV12()
        // 老数据先进去，迁移后必须原样还在
        db.execSQL(
            "INSERT INTO exercise_log (date,type,minutes,kcal,steps,distanceKm,createdAt) " +
                "VALUES ('2026-11-16','走路',30,120.0,3000,0.0,1000)"
        )

        AppDatabase.MIGRATION_12_13.migrate(db)

        // ---- 两张新表都建出来了 ----
        listOf("exercise_calorie_config", "exercise_session_calorie").forEach { table ->
            val found = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
                .use { c -> c.moveToFirst() }
            assertTrue("表未创建: $table", found)
        }

        // ---- 参数库种子：12 个类型级默认，MET 三档 = 工单参考值 ×0.85 / 1 / ×1.15 ----
        assertEquals(
            12L,
            db.query("SELECT COUNT(*) FROM exercise_calorie_config WHERE exerciseId=''").use { c ->
                c.moveToFirst(); c.getLong(0)
            }
        )
        db.query(
            "SELECT metLow, metModerate, metHigh FROM exercise_calorie_config WHERE activityType='strength_moderate'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4.25, c.getDouble(0), 0.001)
            assertEquals(5.0, c.getDouble(1), 0.001)
            assertEquals(5.75, c.getDouble(2), 0.001)
        }
        db.query(
            "SELECT metModerate FROM exercise_calorie_config WHERE activityType='flexibility'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2.3, c.getDouble(0), 0.001)
        }

        // ---- 消耗账本：唯一索引存在，重复 (sourceType, sourceId) 插不进去 ----
        val uniqueIndex = db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=" +
                "'index_exercise_session_calorie_sourceType_sourceId'"
        ).use { c -> c.moveToFirst(); c.getLong(0) }
        assertEquals("账本唯一索引缺失", 1L, uniqueIndex)

        db.execSQL(
            "INSERT INTO exercise_session_calorie " +
                "(sourceType,sourceId,date,durationSeconds,caloriesGross,weightSnapshot," +
                "calculationMethod,calculationVersion,createdAt,updatedAt) " +
                "VALUES ('STRENGTH_TRAINING','sess-1','2026-11-16',1800,150.0,70.0,'met_segmented','v1',1,1)"
        )
        var conflictCaught = false
        try {
            db.execSQL(
                "INSERT INTO exercise_session_calorie " +
                    "(sourceType,sourceId,date,durationSeconds,caloriesGross,weightSnapshot," +
                    "calculationMethod,calculationVersion,createdAt,updatedAt) " +
                    "VALUES ('STRENGTH_TRAINING','sess-1','2026-11-16',1800,999.0,70.0,'met_segmented','v1',2,2)"
            )
        } catch (e: Exception) {
            conflictCaught = true
        }
        assertTrue("唯一索引没生效：同一场训练插进去了两行", conflictCaught)
        assertEquals(
            1L,
            db.query("SELECT COUNT(*) FROM exercise_session_calorie").use { c ->
                c.moveToFirst(); c.getLong(0)
            }
        )

        // ---- 老表数据一个字节不动 ----
        db.query("SELECT type, minutes, kcal, steps FROM exercise_log WHERE id=1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("走路", c.getString(0))
            assertEquals(30, c.getInt(1))
            assertEquals(120.0, c.getDouble(2), 0.001)
            assertEquals(3000, c.getInt(3))
        }
        db.close()
    }
}
