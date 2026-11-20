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
 * AI 多服务（Room 20 → 21）迁移测试。
 * 证明：ai_config 增加 displayName 列（默认空串），旧 id=1 配置的全部字段原样保留，
 * 且可新增不同 id 的多条配置互不覆盖。
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigration20To21Test {

    private fun openV20(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_20_21_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `ai_config` (" +
                            "`id` INTEGER NOT NULL, `provider` TEXT NOT NULL, " +
                            "`baseUrl` TEXT NOT NULL, `textModel` TEXT NOT NULL, " +
                            "`visionModel` TEXT NOT NULL, `temperature` REAL NOT NULL, " +
                            "`timeoutSec` INTEGER NOT NULL, `dailyLimit` INTEGER NOT NULL, " +
                            "`enabled` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `20到21迁移后旧配置完整保留且新增displayName默认空`() {
        val db = openV20()
        db.execSQL(
            "INSERT INTO ai_config (id,provider,baseUrl,textModel,visionModel,temperature,timeoutSec,dailyLimit,enabled,updatedAt) " +
                "VALUES (1,'deepseek','https://api.deepseek.com/v1','deepseek-chat','',0.2,30,100,1,999)"
        )

        AppDatabase.MIGRATION_20_21.migrate(db)

        // 旧行所有字段不变，新列默认空串
        db.query("SELECT id,provider,baseUrl,textModel,enabled,updatedAt,displayName FROM ai_config WHERE id=1").use { c ->
            assert(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals("deepseek", c.getString(1))
            assertEquals("https://api.deepseek.com/v1", c.getString(2))
            assertEquals("deepseek-chat", c.getString(3))
            assertEquals(1, c.getInt(4))
            assertEquals(999L, c.getLong(5))
            assertEquals("", c.getString(6))
        }

        // 可新增不同 id 的多条配置，互不覆盖
        db.execSQL(
            "INSERT INTO ai_config (id,displayName,provider,baseUrl,textModel,visionModel,temperature,timeoutSec,dailyLimit,enabled,updatedAt) " +
                "VALUES (2,'公益站B','custom','https://b.example/v1','model-b','',0.2,30,100,1,1000)"
        )
        db.execSQL(
            "INSERT INTO ai_config (id,displayName,provider,baseUrl,textModel,visionModel,temperature,timeoutSec,dailyLimit,enabled,updatedAt) " +
                "VALUES (3,'公益站C','custom','https://c.example/v1','model-c','',0.2,30,100,1,1001)"
        )
        assertEquals(3L, db.query("SELECT COUNT(*) FROM ai_config").use { c ->
            c.moveToFirst(); c.getLong(0)
        })
        db.query("SELECT displayName, baseUrl FROM ai_config WHERE id=2").use { c ->
            assert(c.moveToFirst())
            assertEquals("公益站B", c.getString(0))
            assertEquals("https://b.example/v1", c.getString(1))
        }
        db.close()
    }

    @Test
    fun `displayName列存在且默认为空不是null`() {
        val db = openV20()
        AppDatabase.MIGRATION_20_21.migrate(db)
        val cols = db.query("PRAGMA table_info(`ai_config`)").use { c ->
            val m = mutableMapOf<String, String>()
            while (c.moveToNext()) {
                m[c.getString(c.getColumnIndex("name"))] = c.getString(c.getColumnIndex("dflt_value"))
            }
            m
        }
        assertTrue("displayName 列缺失", cols.containsKey("displayName"))
        assertEquals("''", cols["displayName"])
        db.close()
    }
}
