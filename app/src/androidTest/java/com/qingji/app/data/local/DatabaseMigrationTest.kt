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

    // ============================================================
    // TASK-08-B：三段「有数据搬迁」的迁移逐字段保真测试
    // ------------------------------------------------------------
    // 只跑 runMigrationsAndValidate 只能证明「表结构对得上」，
    // 证明不了「旧数据搬过去还是原样」。下面三段都是建旧库 → 插代表性旧数据 →
    // 执行迁移 → 读新库 → 逐字段比。
    // ============================================================

    @Test
    fun `5到6迁移后用户自建食物原样搬入且预置数据被丢弃`() {
        val name = "migration-5-6.db"
        helper.createDatabase(name, 5).apply {
            // 用户自建（source != preset）：必须整行搬进 user_food
            execSQL(
                "INSERT INTO `food` (`id`,`name`,`pinyin`,`barcode`,`kcalPer100g`," +
                    "`proteinPer100g`,`carbPer100g`,`fatPer100g`,`fiberPer100g`," +
                    "`sodiumMgPer100g`,`calciumMgPer100g`,`defaultGrams`,`source`," +
                    "`category`,`brand`,`favorite`,`createdAt`) VALUES " +
                    "(1,'自制鸡胸','zzjx','6901234567890',133.0,29.0,2.5,2.0,0.0,0.0,0.0," +
                    "100.0,'custom','肉类','自制',1,111)"
            )
            // 预置库（source = preset）：迁移后应随旧表一起消失，不进 user_food
            execSQL(
                "INSERT INTO `food` (`id`,`name`,`pinyin`,`barcode`,`kcalPer100g`," +
                    "`proteinPer100g`,`carbPer100g`,`fatPer100g`,`fiberPer100g`," +
                    "`sodiumMgPer100g`,`calciumMgPer100g`,`defaultGrams`,`source`," +
                    "`category`,`brand`,`favorite`,`createdAt`) VALUES " +
                    "(2,'预置米饭','yzmf',NULL,116.0,2.6,25.9,0.3,0.3,2.0,7.0," +
                    "150.0,'preset','主食','',0,222)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6)
        try {
            // 只剩自建那一条
            migrated.query("SELECT count(*) FROM `user_food`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("预置食物不该被搬进用户自建库", 1, c.getInt(0))
            }
            migrated.query(
                "SELECT `name`,`pinyin`,`barcode`,`kcalPer100g`,`proteinPer100g`," +
                    "`carbPer100g`,`fatPer100g`,`fiberPer100g`,`sodiumMgPer100g`," +
                    "`calciumMgPer100g`,`defaultGrams`,`source`,`category`,`brand`," +
                    "`favorite`,`createdAt` FROM `user_food` WHERE `id`=1"
            ).use { c ->
                assertTrue("自建食物必须还在", c.moveToFirst())
                assertEquals("自制鸡胸", c.getString(0))
                assertEquals("zzjx", c.getString(1))
                assertEquals("6901234567890", c.getString(2))
                assertEquals(133.0, c.getDouble(3), 0.001)
                assertEquals(29.0, c.getDouble(4), 0.001)
                assertEquals(2.5, c.getDouble(5), 0.001)
                assertEquals(2.0, c.getDouble(6), 0.001)
                assertEquals(0.0, c.getDouble(7), 0.001)
                assertEquals(100.0, c.getDouble(10), 0.001)
                assertEquals("custom", c.getString(11))
                assertEquals("肉类", c.getString(12))
                assertEquals("自制", c.getString(13))
                assertEquals(1, c.getInt(14))
                assertEquals(111L, c.getLong(15))
            }
            // 收藏按名字搬过去
            migrated.query("SELECT `name` FROM `food_favorite`").use { c ->
                assertTrue("收藏过的食物要跟着搬", c.moveToFirst())
                assertEquals("自制鸡胸", c.getString(0))
                assertEquals(1, c.count)
            }
            // 旧表已删
            migrated.query(
                "SELECT count(*) FROM `sqlite_master` WHERE `type`='table' AND `name`='food'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("旧 food 表应当被 DROP", 0, c.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun `7到8迁移后0值转成NULL而非零值且非零值逐字保留`() {
        val name = "migration-7-8.db"
        helper.createDatabase(name, 7).apply {
            // 第 1 条：三个可空列都是 0.0，迁移时应被 NULLIF 转成 NULL
            execSQL(
                "INSERT INTO `user_food` (`id`,`name`,`pinyin`,`barcode`,`kcalPer100g`," +
                    "`proteinPer100g`,`carbPer100g`,`fatPer100g`,`fiberPer100g`," +
                    "`sodiumMgPer100g`,`calciumMgPer100g`,`defaultGrams`,`source`," +
                    "`category`,`brand`,`favorite`,`createdAt`) VALUES " +
                    "(1,'零值食物','lzsw',NULL,100.0,1.0,2.0,3.0,0.0,0.0,0.0,50.0," +
                    "'custom','其他','',0,1000)"
            )
            // 第 2 条：三个可空列都有真实值，迁移后必须原样保留
            execSQL(
                "INSERT INTO `user_food` (`id`,`name`,`pinyin`,`barcode`,`kcalPer100g`," +
                    "`proteinPer100g`,`carbPer100g`,`fatPer100g`,`fiberPer100g`," +
                    "`sodiumMgPer100g`,`calciumMgPer100g`,`defaultGrams`,`source`," +
                    "`category`,`brand`,`favorite`,`createdAt`) VALUES " +
                    "(2,'有值食物','yzsw','123',200.0,10.5,20.5,5.5,3.2,120.0,45.0," +
                    "80.0,'ai','肉类','品牌A',1,2000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 8, true, AppDatabase.MIGRATION_7_8)
        try {
            migrated.query("SELECT count(*) FROM `user_food`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("重建表不能丢行", 2, c.getInt(0))
            }
            // 0 → NULL
            migrated.query(
                "SELECT `fiberPer100g`,`sodiumMgPer100g`,`calciumMgPer100g`,`sugarPer100g` " +
                    "FROM `user_food` WHERE `id`=1"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertTrue("0.0 应被转成 NULL（代表未收录）", c.isNull(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue("新列 sugarPer100g 对老数据应为 NULL", c.isNull(3))
            }
            // 非零原样
            migrated.query(
                "SELECT `name`,`barcode`,`kcalPer100g`,`fiberPer100g`,`sodiumMgPer100g`," +
                    "`calciumMgPer100g`,`defaultGrams`,`source`,`brand`,`favorite`,`createdAt` " +
                    "FROM `user_food` WHERE `id`=2"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("有值食物", c.getString(0))
                assertEquals("123", c.getString(1))
                assertEquals(200.0, c.getDouble(2), 0.001)
                assertEquals(3.2, c.getDouble(3), 0.001)
                assertEquals(120.0, c.getDouble(4), 0.001)
                assertEquals(45.0, c.getDouble(5), 0.001)
                assertEquals(80.0, c.getDouble(6), 0.001)
                assertEquals("ai", c.getString(7))
                assertEquals("品牌A", c.getString(8))
                assertEquals(1, c.getInt(9))
                assertEquals(2000L, c.getLong(10))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun `8到9迁移后自由断食两列补0且旧记录原样保留`() {
        val name = "migration-8-9.db"
        helper.createDatabase(name, 8).apply {
            execSQL(
                "INSERT INTO `fasting_log` (`date`,`planHours`,`eatStartMinute`," +
                    "`eatEndMinute`,`completed`,`createdAt`) VALUES " +
                    "('2026-11-10',16,720,1020,1,333)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 9, true, AppDatabase.MIGRATION_8_9)
        try {
            migrated.query(
                "SELECT `date`,`planHours`,`eatStartMinute`,`eatEndMinute`,`completed`," +
                    "`createdAt`,`freeMode`,`actualMinutes` FROM `fasting_log`"
            ).use { c ->
                assertTrue("旧断食记录不该丢", c.moveToFirst())
                assertEquals("2026-11-10", c.getString(0))
                assertEquals(16, c.getInt(1))
                assertEquals(720, c.getInt(2))
                assertEquals(1020, c.getInt(3))
                assertEquals(1, c.getInt(4))
                assertEquals(333L, c.getLong(5))
                // 老记录那时记的是「计划」不是「实际」，两列都必须补 0 而不是瞎推
                assertEquals("历史记录 freeMode 必须是 0（非自由断食）", 0, c.getInt(6))
                assertEquals("历史记录 actualMinutes 必须是 0（不能反推）", 0, c.getInt(7))
                assertEquals(1, c.count)
            }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}