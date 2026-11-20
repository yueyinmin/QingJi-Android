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
 * v1.4.2：Room 17 → 18 迁移测试。
 * 证明：exercise_log 加一列 sourceId（默认空串），老的手工运动记录一行不丢、
 * sourceId 全部为空串（不会被误当成训练日志）。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration17To18Test {

    private fun openV17(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_17_18_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
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
    fun `17到18迁移后老运动记录保留且sourceId为空串`() {
        val db = openV17()
        db.execSQL(
            "INSERT INTO exercise_log (date,type,minutes,kcal,createdAt) VALUES ('2026-11-18','跑步',30,250.0,1000)"
        )

        AppDatabase.MIGRATION_17_18.migrate(db)

        db.query("SELECT type, minutes, kcal, sourceId FROM exercise_log").use { c ->
            assert(c.moveToFirst())
            assertEquals("跑步", c.getString(0))
            assertEquals(30, c.getInt(1))
            assertEquals(250.0, c.getDouble(2), 0.01)
            // 关键：老手工记录 sourceId 必须是空串，不能被误判为训练日志
            assertEquals("", c.getString(3))
        }
        db.close()
    }

    @Test
    fun `17到18迁移后新训练日志可写入并按sourceId查到`() {
        val db = openV17()
        AppDatabase.MIGRATION_17_18.migrate(db)

        db.execSQL(
            "INSERT INTO exercise_log (date,type,minutes,kcal,sourceId,createdAt) " +
                "VALUES ('2026-11-19','力量',45,300.0,'session-uuid-1',2000)"
        )
        db.query("SELECT id, sourceId, kcal FROM exercise_log WHERE sourceId = 'session-uuid-1'").use { c ->
            assert(c.moveToFirst())
            assertEquals("session-uuid-1", c.getString(1))
            assertEquals(300.0, c.getDouble(2), 0.01)
        }
        db.close()
    }
}
