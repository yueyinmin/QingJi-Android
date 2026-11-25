package com.qingji.app.data.local.ai

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 独立 AI 库。生产与正式业务库分离，避免 AI 功能影响主库可用性。
 *
 * 版本历史：
 * - v1：会话 / 消息 / 工具调用 / 提案展示 / Token 用量；
 * - v2（阶段 I）：新增 ai_memory 长期记忆表；
 * - v3（阶段 J）：新增 ai_nudge 主动提醒发送记录表。
 */
@Database(
    entities = [
        AiConversationEntity::class,
        AiMessageEntity::class,
        AiToolCallEntity::class,
        AiProposalEntity::class,
        AiTokenUsageEntity::class,
        AiMemoryEntity::class,
        AiNudgeEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AiDatabase : RoomDatabase() {

    abstract fun aiChatDao(): AiChatDao

    abstract fun aiMemoryDao(): AiMemoryDao

    abstract fun aiNudgeDao(): AiNudgeDao

    companion object {
        /**
         * v1 → v2：只加一张记忆表与三个索引，不动老表。
         * 字段名与 AiMemoryEntity 保持一致，Room 运行时校验靠它。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_memory` (" +
                        "`id` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`content` TEXT NOT NULL, " +
                        "`truthType` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`sourceRef` TEXT NOT NULL, " +
                        "`confidence` REAL, " +
                        "`status` TEXT NOT NULL, " +
                        "`expiresAt` INTEGER NOT NULL, " +
                        "`dedupeKey` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_memory_category` ON `ai_memory` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_memory_status` ON `ai_memory` (`status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_memory_dedupeKey` ON `ai_memory` (`dedupeKey`)")
            }
        }

        /**
         * v2 → v3：只加主动提醒记录表与三个索引。
         *
         * (kind, dateKey) 是唯一索引，「一天同一种提醒只发一次」靠它兜底，
         * 不依赖调用方加锁；另外两个单列索引给「按类看历史」「按日期统计额度」用。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_nudge` (" +
                        "`id` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`dateKey` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`factsJson` TEXT NOT NULL, " +
                        "`needsAnalysis` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, " +
                        "`shownAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_nudge_kind` ON `ai_nudge` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_nudge_dateKey` ON `ai_nudge` (`dateKey`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_nudge_kind_dateKey` " +
                        "ON `ai_nudge` (`kind`, `dateKey`)"
                )
            }
        }

        @Volatile
        private var INSTANCE: AiDatabase? = null

        fun get(context: Context): AiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "qingji_ai.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
