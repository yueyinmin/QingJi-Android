package com.qingji.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.1.0：历史迁移链自动化验证（任务书 P0）。
 * 空库从 schema 1 开始逐级 runMigrationsAndValidate 到 10：
 * schema 不一致会立刻抛错，漏写迁移也会显式失败，绝不允许静默清库。
 * 放在 androidTest：MigrationTestHelper 读 schema 资产需要 instrumentation 环境
 * （AGP 不会把 test sourceSet 的 assets 合并进 Robolectric 单测）。
 *
 * v1.0.10：Room 9→10 加训练中心 6 张表后，除了逐级校验，
 * 还补一条「先放一条老数据、迁移后原样读回」的断言 —— 只 CREATE 新表不算数，
 * 得证明旧表一列没动、一行没丢。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `空库从版本1逐级迁移到11每一步都通过schema校验`() {
        helper.createDatabase(DB_NAME, 1).close()
        var version = 1
        while (version < 11) {
            val migration = AppDatabase.ALL_MIGRATIONS.first { it.startVersion == version }
            helper.runMigrationsAndValidate(DB_NAME, version + 1, true, migration).close()
            version++
        }
    }

    @Test
    fun `9到10迁移后旧表数据原样保留且新表可用`() {
        val name = "migration-9-10.db"
        helper.createDatabase(name, 9).apply {
            execSQL(
                "INSERT INTO `weight_log` (`date`,`weightKg`,`bodyFat`,`note`,`createdAt`) " +
                    "VALUES ('2026-11-13', 70.5, NULL, '迁移前', 123)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name, 10, true, AppDatabase.MIGRATION_9_10
        )
        try {
            val cursor = migrated.query(
                "SELECT `weightKg`, `note` FROM `weight_log` WHERE `date`='2026-11-13'"
            )
            assertTrue("迁移后旧体重记录不该丢", cursor.moveToFirst())
            assertEquals(70.5, cursor.getDouble(0), 0.001)
            assertEquals("迁移前", cursor.getString(1))
            cursor.close()

            // 6 张新表都要能查（空表也算建成功）
            listOf(
                "exercise", "training_plan", "training_plan_day",
                "plan_exercise", "workout_session", "workout_exercise"
            ).forEach { table ->
                val c = migrated.query("SELECT count(*) FROM `$table`")
                assertTrue("$table 应该已建好", c.moveToFirst())
                assertEquals(0, c.getInt(0))
                c.close()
            }
        } finally {
            migrated.close()
        }
    }

    /**
     * v1.1.1（自律管理 P0）：10 → 11 只新增 7 张自律表，旧表一列不动。
     * 断言两件事：迁移前的体重记录原样读得回来；7 张新表都能查。
     */
    @Test
    fun `10到11迁移后旧数据保留且七张自律表可用`() {
        val name = "migration-10-11.db"
        helper.createDatabase(name, 10).apply {
            execSQL(
                "INSERT INTO `weight_log` (`date`,`weightKg`,`bodyFat`,`note`,`createdAt`) " +
                    "VALUES ('2026-11-13', 70.5, NULL, '迁移前', 123)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name, 11, true, AppDatabase.MIGRATION_10_11
        )
        try {
            val cursor = migrated.query(
                "SELECT `weightKg`, `note` FROM `weight_log` WHERE `date`='2026-11-13'"
            )
            assertTrue("迁移后旧体重记录不该丢", cursor.moveToFirst())
            assertEquals(70.5, cursor.getDouble(0), 0.001)
            assertEquals("迁移前", cursor.getString(1))
            cursor.close()

            listOf(
                "self_discipline_goals", "self_discipline_periods",
                "self_discipline_checkins", "self_discipline_urge_logs",
                "self_discipline_settings", "self_discipline_milestones",
                "self_discipline_interventions"
            ).forEach { table ->
                val c = migrated.query("SELECT count(*) FROM `$table`")
                assertTrue("$table 应该已建好", c.moveToFirst())
                assertEquals(0, c.getInt(0))
                c.close()
            }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}