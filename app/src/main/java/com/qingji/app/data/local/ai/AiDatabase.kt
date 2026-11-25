package com.qingji.app.data.local.ai

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** 独立 AI 库。生产与正式业务库分离，避免 AI 功能影响主库可用性。 */
@Database(
    entities = [
        AiConversationEntity::class,
        AiMessageEntity::class,
        AiToolCallEntity::class,
        AiProposalEntity::class,
        AiTokenUsageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AiDatabase : RoomDatabase() {

    abstract fun aiChatDao(): AiChatDao

    companion object {
        @Volatile
        private var INSTANCE: AiDatabase? = null

        fun get(context: Context): AiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AiDatabase::class.java,
                    "qingji_ai.db"
                ).build().also { INSTANCE = it }
            }
    }
}
