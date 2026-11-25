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

/** 阶段 J：AI 库 2 → 3 迁移（只加提醒记录表与索引，会话与记忆都不动）。 */
@RunWith(RobolectricTestRunner::class)
class AiDatabaseMigration2To3Test {

    private fun openV2(): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name("migration_ai_2_3_test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 只建 v2 里跟本次迁移相关的两张表就够：验证「老数据不动」+「新表可用」
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ai_conversation` (" +
                            "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `entryPage` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                            "`deletedAt` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ai_memory` (" +
                            "`id` TEXT NOT NULL, `category` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                            "`truthType` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceRef` TEXT NOT NULL, " +
                            "`confidence` REAL, `status` TEXT NOT NULL, `expiresAt` INTEGER NOT NULL, " +
                            "`dedupeKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_memory_dedupeKey` ON `ai_memory` (`dedupeKey`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return factory.create(config).writableDatabase
    }

    @Test
    fun `2到3加提醒表且老数据保留`() {
        val db = openV2()
        try {
            db.execSQL(
                "INSERT INTO `ai_conversation` (id,title,entryPage,createdAt,updatedAt,deletedAt,schemaVersion) " +
                    "VALUES ('c1','旧会话','首页',100,200,0,1)"
            )
            db.execSQL(
                "INSERT INTO `ai_memory` (id,category,content,truthType,source,sourceRef,confidence,status,expiresAt,dedupeKey,createdAt,updatedAt) " +
                    "VALUES ('m1','nutrition','我不吃香菜','USER_FACT','USER_INPUT','u1',NULL,'ACTIVE',0,'key-1',10,10)"
            )

            AiDatabase.MIGRATION_2_3.migrate(db)

            db.query("SELECT COUNT(*) FROM `ai_conversation`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM `ai_memory`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }

            db.execSQL(
                "INSERT INTO `ai_nudge` (id,kind,reason,dateKey,title,body,factsJson,needsAnalysis,outcome,shownAt,updatedAt) " +
                    "VALUES ('n1','record_incomplete','food_log_missing','2026-11-25','今天还没怎么记','今天还没有饮食记录。','{}',0,'SHOWN',1000,1000)"
            )
            db.query("SELECT kind, dateKey, outcome FROM `ai_nudge` WHERE id = 'n1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("record_incomplete", cursor.getString(0))
                assertEquals("2026-11-25", cursor.getString(1))
                assertEquals("SHOWN", cursor.getString(2))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `2到3的同类同日唯一索引会拦住第二次写入`() {
        val db = openV2()
        try {
            AiDatabase.MIGRATION_2_3.migrate(db)

            val names = mutableListOf<String>()
            db.query("PRAGMA index_list(`ai_nudge`)").use { cursor ->
                while (cursor.moveToNext()) {
                    names += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
            }
            assertTrue(names.contains("index_ai_nudge_kind_dateKey"))
            assertTrue(names.contains("index_ai_nudge_kind"))
            assertTrue(names.contains("index_ai_nudge_dateKey"))

            val insert = "INSERT OR IGNORE INTO `ai_nudge` " +
                "(id,kind,reason,dateKey,title,body,factsJson,needsAnalysis,outcome,shownAt,updatedAt) " +
                "VALUES (?, 'record_incomplete','food_log_missing','2026-11-25','标题','正文','{}',0,'SHOWN',1000,1000)"
            db.execSQL(insert, arrayOf("n1"))
            // 同类同日再写一条（不同 id）应被唯一索引忽略
            db.execSQL(insert, arrayOf("n2"))

            db.query("SELECT COUNT(*) FROM `ai_nudge`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }
}
