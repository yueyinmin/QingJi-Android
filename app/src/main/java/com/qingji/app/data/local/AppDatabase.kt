package com.qingji.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qingji.app.data.local.dao.AiConfigDao
import com.qingji.app.data.local.dao.BodyMeasureDao
import com.qingji.app.data.local.dao.DiaryDao
import com.qingji.app.data.local.dao.ExerciseLogDao
import com.qingji.app.data.local.dao.ExercisePresetDao
import com.qingji.app.data.local.dao.FastingLogDao
import com.qingji.app.data.local.dao.FoodDao
import com.qingji.app.data.local.dao.FoodLogDao
import com.qingji.app.data.local.dao.ProgressPhotoDao
import com.qingji.app.data.local.dao.UsageStatDao
import com.qingji.app.data.local.dao.UserProfileDao
import com.qingji.app.data.local.dao.WaterLogDao
import com.qingji.app.data.local.dao.WeightLogDao
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ExercisePreset
import com.qingji.app.data.local.entity.FastingLog
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import java.time.LocalDate

/**
 * 日期和数据库之间的翻译官。
 * 数据库只会存文本，LocalDate 要转成 "2026-11-07" 这种格式才能存。
 */
class Converters {
    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }
}

/**
 * ============================================================
 * 数据库总入口
 * ------------------------------------------------------------
 * version = 1 表示第一版表结构。以后改表结构要把版本号加 1，
 * 并写一段 Migration（怎么把老数据搬到新表），否则用户升级会崩。
 * ============================================================
 */
@Database(
    entities = [
        UserProfile::class,
        WeightLog::class,
        Food::class,
        FoodLog::class,
        WaterLog::class,
        Diary::class,
        AiConfig::class,
        UsageStat::class,
        BodyMeasure::class,
        ProgressPhoto::class,
        ExerciseLog::class,
        FastingLog::class,
        ExercisePreset::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun diaryDao(): DiaryDao
    abstract fun aiConfigDao(): AiConfigDao
    abstract fun usageStatDao(): UsageStatDao
    abstract fun bodyMeasureDao(): BodyMeasureDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun fastingLogDao(): FastingLogDao
    abstract fun exercisePresetDao(): ExercisePresetDao

    companion object {
        /** 整个 App 只保留一个数据库实例，重复创建会出问题。 */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 1 → 2：新增身体维度、进度照片、运动记录三张表。
         * 只建新表，不动老表，所以用户已经记的饮食和体重一条都不会丢。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `body_measure` (
                        `date` TEXT NOT NULL,
                        `waistCm` REAL,
                        `chestCm` REAL,
                        `hipCm` REAL,
                        `thighCm` REAL,
                        `armCm` REAL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `progress_photo` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_progress_photo_date` ON `progress_photo` (`date`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercise_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `minutes` INTEGER NOT NULL,
                        `kcal` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_log_date` ON `exercise_log` (`date`)")
            }
        }

        /**
         * 2 → 3：食物库加分类/品牌/收藏三列，新增断食记录表。
         * 全部是 ALTER TABLE ADD COLUMN + CREATE TABLE，老数据一条不动。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food` ADD COLUMN `category` TEXT NOT NULL DEFAULT '其他'")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `brand` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fasting_log` (
                        `date` TEXT NOT NULL,
                        `planHours` INTEGER NOT NULL,
                        `eatStartMinute` INTEGER NOT NULL,
                        `eatEndMinute` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fasting_log_date` ON `fasting_log` (`date`)")
            }
        }

        /** 3 → 4：新增运动库表（预置运动 + MET 值）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exercise_preset` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `met` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_preset_name` ON `exercise_preset` (`name`)")
            }
        }

        /**
         * 4 → 5：营养素目标细分（v3.5.0）。
         * 三个表各加列，全是「NOT NULL + DEFAULT」； Entity 上同时标了 @ColumnInfo(defaultValue=...)，
         * 两边的 DEFAULT 字面量必须逐字一致（0.0 / 25 / 2000 / 800），否则 Room 运行时校验
         * schema 不匹配会触发清库兜底。改任何一边都要同步另一边并核对 schemas/5.json。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 食物库：每 100g 的膳食纤维（g）/ 钠（mg）/ 钙（mg）；现有清单暂无数据，先落 0
                db.execSQL("ALTER TABLE `food` ADD COLUMN `fiberPer100g` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `sodiumMgPer100g` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `calciumMgPer100g` REAL NOT NULL DEFAULT 0.0")
                // 饮食记录：与 Food 对应三列
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `fiberG` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `sodiumMg` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `calciumMg` REAL NOT NULL DEFAULT 0.0")
                // 用户档案：三个每日目标，默认按《中国居民膳食指南》成人参考值
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `fiberGoalG` INTEGER NOT NULL DEFAULT 25")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `sodiumGoalMg` INTEGER NOT NULL DEFAULT 2000")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `calciumGoalMg` INTEGER NOT NULL DEFAULT 800")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qingji.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    // 兜底：万一日后某个版本的迁移没写，宁可清表也不能让 App 打不开。
                    // 每加一次表结构，务必同时补 Migration，别依赖这一行。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
