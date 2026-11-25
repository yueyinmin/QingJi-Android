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
 * - v2（阶段 I）：新增 ai_memory 长期记忆表。
 */
@Database(
    entities = [
        AiConversationEntity::class,
        AiMessageEntity::class,
        AiToolCallEntity::class,
        AiProposalEntity::class,
        AiTokenUsageEntity::class,
        AiMemoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AiDatabase : RoomDatabase() {

    abstract fun aiChatDao(): AiChatDao

    abstract fun aiMemoryDao(): AiMemoryDao

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

        @Volatile
        private var INSTANCE: AiDatabase? = null

        fun get(context: Context): AiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "qingji_ai.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
