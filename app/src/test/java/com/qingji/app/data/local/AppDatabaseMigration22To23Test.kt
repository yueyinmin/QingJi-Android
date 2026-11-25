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

/** 阶段 E：Room 22 → 23 迁移测试（版本源 + 提案权威表 + 幂等唯一索引）。 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration22To23Test {

    private fun openV22(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_22_23_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `training_plan` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`source` TEXT NOT NULL DEFAULT '', `isActive` INTEGER NOT NULL DEFAULT 0, " +
                            "`createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uuid`))"
                    )
                    db.execSQL(
                        "CREATE TABLE `user_profile` (`id` INTEGER NOT NULL, `dailyKcalGoal` INTEGER NOT NULL DEFAULT 0, " +
                            "`proteinGoalG` INTEGER NOT NULL DEFAULT 0, `carbGoalG` INTEGER NOT NULL DEFAULT 0, " +
                            "`fatGoalG` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `22到23加版本列旧数据默认0且提案幂等键唯一`() {
        val db = openV22()
        db.execSQL(
            "INSERT INTO `training_plan` (uuid,name,source,isActive,createdAt) " +
                "VALUES ('plan-old','旧计划','custom',1,1000)"
        )
        db.execSQL(
            "INSERT INTO `user_profile` (id,dailyKcalGoal,proteinGoalG,carbGoalG,fatGoalG,createdAt) " +
                "VALUES (1,1800,100,200,60,1000)"
        )

        AppDatabase.MIGRATION_22_23.migrate(db)

        val plan = db.query("SELECT name,version,updatedAt FROM `training_plan`")
        assertTrue(plan.moveToFirst())
        assertEquals("旧计划", plan.getString(plan.getColumnIndex("name")))
        assertEquals(0, plan.getInt(plan.getColumnIndex("version")))
        assertEquals(0L, plan.getLong(plan.getColumnIndex("updatedAt")))
        plan.close()

        val profile = db.query("SELECT dailyKcalGoal,goalVersion,goalUpdatedAt FROM `user_profile`")
        assertTrue(profile.moveToFirst())
        assertEquals(1800, profile.getInt(profile.getColumnIndex("dailyKcalGoal")))
        assertEquals(0, profile.getInt(profile.getColumnIndex("goalVersion")))
        assertEquals(0L, profile.getLong(profile.getColumnIndex("goalUpdatedAt")))
        profile.close()

        val insert = {
            db.execSQL(
                "INSERT INTO `ai_proposal_execution` " +
                    "(proposalId,proposalType,status,idempotencyKey,createdAt) " +
                    "VALUES ('p1','PLAN_ACTIVATE','PENDING_CONFIRMATION','key-1',2000)"
            )
        }
        insert()
        var duplicateRejected = false
        try {
            insert()
        } catch (_: Exception) {
            duplicateRejected = true
        }
        assertTrue("idempotencyKey 唯一索引必须生效", duplicateRejected)

        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('ai_proposal_execution','ai_operation_log')"
        )
        var count = 0
        while (tables.moveToNext()) count++
        assertEquals(2, count)
        tables.close()
        db.close()
    }
}
