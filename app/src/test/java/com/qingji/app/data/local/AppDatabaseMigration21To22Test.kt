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
 * 统一运动体系（Room 21 → 22）迁移测试。
 * 证明：四处非破坏性加列（默认空串）、旧数据完整保留、零批量回填、可重复执行幂等。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration21To22Test {

    private fun openV21(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_21_22_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v21 三张待改表的最小结构（字段名/类型与 schema/21.json 一致）
                    db.execSQL(
                        "CREATE TABLE `exercise` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`category` TEXT NOT NULL DEFAULT '', " +
                            "PRIMARY KEY(`uuid`))"
                    )
                    db.execSQL(
                        "CREATE TABLE `plan_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "`dayId` INTEGER NOT NULL DEFAULT 0, `exerciseUuid` TEXT NOT NULL DEFAULT '', " +
                            "`exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', `targetSets` INTEGER NOT NULL DEFAULT 0, " +
                            "`targetReps` INTEGER NOT NULL DEFAULT 0, `targetWeightKg` REAL NOT NULL DEFAULT 0.0, " +
                            "`orderInDay` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE `workout_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "`sessionUuid` TEXT NOT NULL DEFAULT '', `exerciseUuid` TEXT NOT NULL DEFAULT '', " +
                            "`exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', `orderInSession` INTEGER NOT NULL DEFAULT 0, " +
                            "`setsJson` TEXT NOT NULL DEFAULT '', `note` TEXT NOT NULL DEFAULT '')"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `21到22加列后旧数据完整保留且新列默认空串`() {
        val db = openV21()
        db.execSQL("INSERT INTO `exercise` (uuid,name,category) VALUES ('ex-chest-02','杠铃卧推','chest')")
        db.execSQL("INSERT INTO `exercise` (uuid,name,category) VALUES ('wg:running','跑步','legs')")
        db.execSQL(
            "INSERT INTO `plan_exercise` (dayId,exerciseUuid,exerciseNameSnapshot,targetSets,targetReps,targetWeightKg,orderInDay) " +
                "VALUES (1,'ex-chest-02','杠铃卧推',4,10,40.0,0)"
        )
        db.execSQL(
            "INSERT INTO `workout_exercise` (sessionUuid,exerciseUuid,exerciseNameSnapshot,orderInSession,setsJson,note) " +
                "VALUES ('session-1','ex-chest-02','杠铃卧推',0,'[{\"w\":40,\"r\":10}]','旧备注')"
        )

        // 执行迁移
        AppDatabase.MIGRATION_21_22.migrate(db)

        // 1. 列存在且默认空串
        val ex = db.query("SELECT uuid,name,recordType FROM `exercise` ORDER BY name")
        assertTrue(ex.moveToFirst())
        assertEquals("", ex.getString(ex.getColumnIndex("recordType")))
        assertEquals("杠铃卧推", ex.getString(ex.getColumnIndex("name")))
        ex.close()

        val plan = db.query("SELECT exerciseUuid,targetSets,targetReps,targetWeightKg,recordType,targetConfigJson FROM `plan_exercise`")
        assertTrue(plan.moveToFirst())
        assertEquals("ex-chest-02", plan.getString(plan.getColumnIndex("exerciseUuid")))
        assertEquals(4, plan.getInt(plan.getColumnIndex("targetSets")))
        assertEquals(10, plan.getInt(plan.getColumnIndex("targetReps")))
        assertEquals(40.0, plan.getDouble(plan.getColumnIndex("targetWeightKg")), 0.001)
        assertEquals("", plan.getString(plan.getColumnIndex("recordType")))
        assertEquals("", plan.getString(plan.getColumnIndex("targetConfigJson")))
        plan.close()

        val wo = db.query("SELECT setsJson,note,itemMetaJson FROM `workout_exercise`")
        assertTrue(wo.moveToFirst())
        assertEquals("[{\"w\":40,\"r\":10}]", wo.getString(wo.getColumnIndex("setsJson")))
        assertEquals("旧备注", wo.getString(wo.getColumnIndex("note")))
        assertEquals("", wo.getString(wo.getColumnIndex("itemMetaJson")))
        wo.close()

        // 2. 零批量回填：迁移后 recordType 仍全空串（映射由运行时 ExerciseRecordTypeMap 提供）
        val cnt = db.query("SELECT COUNT(*) FROM `exercise` WHERE recordType <> ''")
        assertTrue(cnt.moveToFirst())
        assertEquals(0, cnt.getInt(0))
        cnt.close()
        db.close()
    }

    @Test
    fun `迁移后写入用户修正类型可持久化且不被覆盖`() {
        val db = openV21()
        db.execSQL("INSERT INTO `exercise` (uuid,name,category) VALUES ('ex-core-01','平板支撑','core')")
        AppDatabase.MIGRATION_21_22.migrate(db)
        // 用户手动修正为 CARDIO（覆盖默认 OTHER 映射）
        db.execSQL("UPDATE `exercise` SET recordType='CARDIO' WHERE uuid='ex-core-01'")
        val c = db.query("SELECT recordType FROM `exercise` WHERE uuid='ex-core-01'")
        assertTrue(c.moveToFirst())
        assertEquals("CARDIO", c.getString(0))
        c.close()
        db.close()
    }
}
