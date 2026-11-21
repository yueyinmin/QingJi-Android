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
 * 统一训练体系（Room 19 → 20）迁移测试。
 * 证明三件事：
 *   ① 老数据（workout_session / workout_exercise / exercise_log / 账本）一行不动；
 *   ② 新表 workout_cardio_detail 建成、默认值正确、可写可读、可空列真是可空；
 *   ③ sessionUuid 索引存在，且【没有】(sessionUuid, exerciseUuid) 唯一索引
 *      —— 一场训练允许同一动作重复出现（工单 §3.2 硬约束）。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration19To20Test {

    private fun openV19(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_19_20_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `workout_session` (" +
                            "`uuid` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                            "`startedAt` INTEGER NOT NULL DEFAULT 0, `endedAt` INTEGER NOT NULL DEFAULT 0, " +
                            "`durationMinutes` INTEGER NOT NULL DEFAULT 0, `note` TEXT NOT NULL DEFAULT '', " +
                            "`source` TEXT NOT NULL DEFAULT 'manual', `createdAt` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`uuid`))"
                    )
                    db.execSQL(
                        "CREATE TABLE `workout_exercise` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`sessionUuid` TEXT NOT NULL DEFAULT '', `exerciseUuid` TEXT NOT NULL DEFAULT '', " +
                            "`exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', `orderInSession` INTEGER NOT NULL DEFAULT 0, " +
                            "`setsJson` TEXT NOT NULL DEFAULT '', `note` TEXT NOT NULL DEFAULT '')"
                    )
                    db.execSQL(
                        "CREATE TABLE `exercise_log` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `kcal` REAL NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `sourceId` TEXT NOT NULL DEFAULT '')"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `19到20迁移后老数据保留且新表建成`() {
        val db = openV19()
        db.execSQL(
            "INSERT INTO workout_session (uuid,date,durationMinutes,note,source,createdAt) " +
                "VALUES ('sess-old','2026-11-18',30,'老训练','manual',111)"
        )
        db.execSQL(
            "INSERT INTO workout_exercise (sessionUuid,exerciseUuid,exerciseNameSnapshot,orderInSession,setsJson) " +
                "VALUES ('sess-old','ex-1','深蹲',0,'[]')"
        )
        db.execSQL(
            "INSERT INTO exercise_log (date,type,minutes,kcal,createdAt,sourceId) " +
                "VALUES ('2026-11-18','跑步',20,150.0,222,'')"
        )

        AppDatabase.MIGRATION_19_20.migrate(db)

        // ① 老数据一行不丢、字段不变
        db.query("SELECT durationMinutes, note, source FROM workout_session WHERE uuid='sess-old'").use { c ->
            assert(c.moveToFirst())
            assertEquals(30, c.getInt(0))
            assertEquals("老训练", c.getString(1))
            assertEquals("manual", c.getString(2))
        }
        assertEquals(1L, db.query("SELECT COUNT(*) FROM workout_exercise").use { c ->
            c.moveToFirst(); c.getLong(0)
        })
        assertEquals(1L, db.query("SELECT COUNT(*) FROM exercise_log").use { c ->
            c.moveToFirst(); c.getLong(0)
        })

        // ② 新表建成：默认值正确、可空列真是可空
        db.execSQL(
            "INSERT INTO workout_cardio_detail " +
                "(id,sessionUuid,exerciseUuid,exerciseNameSnapshot,orderInSession,durationSeconds," +
                "distanceMeters,calculatedKcal,calculationMethod,calculationVersion,weightSnapshot,rawInputJson,createdAt) " +
                "VALUES (1,'sess-old','ex-run','跑步',0,600,NULL,55.5,'user_direct','v1',NULL,'',333)"
        )
        db.query(
            "SELECT sessionUuid, durationSeconds, calculatedKcal, distanceMeters, weightSnapshot, rawInputJson " +
                "FROM workout_cardio_detail WHERE id=1"
        ).use { c ->
            assert(c.moveToFirst())
            assertEquals("sess-old", c.getString(0))
            assertEquals(600L, c.getLong(1))
            assertEquals(55.5, c.getDouble(2), 0.01)
            assertTrue("distanceMeters 应为可空 NULL", c.isNull(3))
            assertTrue("weightSnapshot 应为可空 NULL", c.isNull(4))
            assertEquals("", c.getString(5))
        }
        // 非空列默认值（只给必填列，其它走 DEFAULT）
        db.execSQL(
            "INSERT INTO workout_cardio_detail (sessionUuid) VALUES ('sess-default')"
        )
        db.query(
            "SELECT exerciseUuid, orderInSession, durationSeconds, calculatedKcal, calculationMethod, rawInputJson " +
                "FROM workout_cardio_detail WHERE sessionUuid='sess-default'"
        ).use { c ->
            assert(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0L, c.getLong(2))
            assertEquals(0.0, c.getDouble(3), 0.01)
            assertEquals("", c.getString(4))
            assertEquals("", c.getString(5))
        }
        db.close()
    }

    @Test
    fun `sessionUuid有索引且同一动作可在一场训练重复出现`() {
        val db = openV19()
        AppDatabase.MIGRATION_19_20.migrate(db)

        // sessionUuid 索引存在
        val hasSessionIndex = db.query(
            "SELECT 1 FROM sqlite_master WHERE type='index' AND name='index_workout_cardio_detail_sessionUuid'"
        ).use { it.moveToFirst() }
        assertTrue("sessionUuid 索引缺失", hasSessionIndex)

        // 没有 (sessionUuid, exerciseUuid) 唯一索引：同一动作插两次都能成功
        val insert = { order: Int ->
            db.execSQL(
                "INSERT INTO workout_cardio_detail (sessionUuid,exerciseUuid,exerciseNameSnapshot,orderInSession,durationSeconds) " +
                    "VALUES ('sess-x','ex-run','跑步',$order,300)"
            )
        }
        insert(0)
        insert(1) // 若误建了唯一索引，这里会抛异常
        assertEquals(2L, db.query("SELECT COUNT(*) FROM workout_cardio_detail WHERE sessionUuid='sess-x'").use { c ->
            c.moveToFirst(); c.getLong(0)
        })
        db.close()
    }
}
