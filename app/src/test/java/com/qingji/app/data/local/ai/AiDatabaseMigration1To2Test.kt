package com.qingji.app.data.local.ai

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 阶段 I：AI 库 1 → 2 迁移（只加记忆表与索引，老会话数据不动）。 */
@RunWith(RobolectricTestRunner::class)
class AiDatabaseMigration1To2Test {

    private fun openV1(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_ai_1_2_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ai_conversation` (" +
                            "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `entryPage` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "`deletedAt` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_conversation_entryPage` ON `ai_conversation` (`entryPage`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_conversation_updatedAt` ON `ai_conversation` (`updatedAt`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `1到2加记忆表且老会话保留`() {
        val db = openV1()
        try {
            db.execSQL(
                "INSERT INTO `ai_conversation` (id,title,entryPage,createdAt,updatedAt,deletedAt,schemaVersion) " +
                    "VALUES ('c1','旧会话','首页',100,200,0,1)"
            )

            AiDatabase.MIGRATION_1_2.migrate(db)

            db.query("SELECT COUNT(*) FROM `ai_conversation`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            // 记忆表可用：写入一行并读回
            db.execSQL(
                "INSERT INTO `ai_memory` (id,category,content,truthType,source,sourceRef,confidence,status,expiresAt,dedupeKey,createdAt,updatedAt) " +
                    "VALUES ('m1','nutrition','我不吃香菜','USER_FACT','USER_INPUT','u1',NULL,'ACTIVE',0,'key-1',10,10)"
            )
            db.query("SELECT category, content, status FROM `ai_memory` WHERE id = 'm1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("nutrition", cursor.getString(0))
                assertEquals("我不吃香菜", cursor.getString(1))
                assertEquals("ACTIVE", cursor.getString(2))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `1到2建了去重唯一索引`() {
        val db = openV1()
        try {
            AiDatabase.MIGRATION_1_2.migrate(db)
            val names = mutableListOf<String>()
            db.query("PRAGMA index_list(`ai_memory`)").use { cursor ->
                while (cursor.moveToNext()) {
                    names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
            }
            assertTrue(names.contains("index_ai_memory_dedupeKey"))
            assertTrue(names.contains("index_ai_memory_category"))
            assertTrue(names.contains("index_ai_memory_status"))
        } finally {
            db.close()
        }
    }
}
