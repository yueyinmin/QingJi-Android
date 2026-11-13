package com.qingji.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.1.0：历史迁移链自动化验证（任务书 P0）。
 * 空库从 schema 1 开始逐级 runMigrationsAndValidate 到 9：
 * schema 不一致会立刻抛错，漏写迁移也会显式失败，绝不允许静默清库。
 * 放在 androidTest：MigrationTestHelper 读 schema 资产需要 instrumentation 环境
 * （AGP 不会把 test sourceSet 的 assets 合并进 Robolectric 单测）。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun `空库从版本1逐级迁移到9每一步都通过schema校验`() {
        helper.createDatabase(DB_NAME, 1).close()
        var version = 1
        while (version < 9) {
            val migration = AppDatabase.ALL_MIGRATIONS.first { it.startVersion == version }
            helper.runMigrationsAndValidate(DB_NAME, version + 1, true, migration).close()
            version++
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
