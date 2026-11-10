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
import com.qingji.app.data.local.dao.FoodFavoriteDao
import com.qingji.app.data.local.dao.FoodLogDao
import com.qingji.app.data.local.dao.FoodSearchHistoryDao
import com.qingji.app.data.local.dao.ProgressPhotoDao
import com.qingji.app.data.local.dao.UsageStatDao
import com.qingji.app.data.local.dao.UserFoodDao
import com.qingji.app.data.local.dao.UserProfileDao
import com.qingji.app.data.local.dao.WaterLogDao
import com.qingji.app.data.local.dao.WeightLogDao
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ExercisePreset
import com.qingji.app.data.local.entity.FastingLog
import com.qingji.app.data.local.entity.FoodFavorite
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.FoodSearchHistory
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserFood
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
 * 用户数据库总入口 qingji.db
 * ------------------------------------------------------------
 * v1.0.4 起拆成两个数据库文件：
 *   · 这里只放用户自己的数据（饮食、体重、自建食物、收藏、搜索历史…）
 *   · 标准食物库在 FoodCatalogDatabase（food_catalog.db，只读预置）
 *
 * version 改成表结构要 +1，并写一段 Migration 把老数据搬过去。
 * 动表结构的四件套（血泪教训，缺一个都会触发 Room 清库兜底）：
 *   ① version +1
 *   ② 写 MIGRATION_N_N+1
 *   ③ Entity 的 @ColumnInfo(defaultValue=...) 与迁移 SQL 的 DEFAULT 逐字一致
 *   ④ 核对 app/schemas/N+1.json
 * ============================================================
 */
@Database(
    entities = [
        UserProfile::class,
        WeightLog::class,
        FoodLog::class,
        WaterLog::class,
        Diary::class,
        AiConfig::class,
        UsageStat::class,
        BodyMeasure::class,
        ProgressPhoto::class,
        ExerciseLog::class,
        FastingLog::class,
        ExercisePreset::class,
        // v1.0.4：food 表拆走，换成下面三张用户侧的表
        UserFood::class,
        FoodFavorite::class,
        FoodSearchHistory::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun weightLogDao(): WeightLogDao
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
    abstract fun userFoodDao(): UserFoodDao
    abstract fun foodFavoriteDao(): FoodFavoriteDao
    abstract fun foodSearchHistoryDao(): FoodSearchHistoryDao

    companion object {
        /** 整个 App 只保留一个数据库实例，重复创建会出问题。 */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 1 → 2：新增身体维度、进度照片、运动记录三张表。 */
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

        /** 2 → 3：旧食物库加分类/品牌/收藏三列，新增断食记录表。 */
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
         * DEFAULT 字面量必须和 Entity 的 @ColumnInfo 逐字一致（0.0 / 25 / 2000 / 800）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food` ADD COLUMN `fiberPer100g` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `sodiumMgPer100g` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food` ADD COLUMN `calciumMgPer100g` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `fiberG` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `sodiumMg` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `food_log` ADD COLUMN `calciumMg` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `fiberGoalG` INTEGER NOT NULL DEFAULT 25")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `sodiumGoalMg` INTEGER NOT NULL DEFAULT 2000")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `calciumGoalMg` INTEGER NOT NULL DEFAULT 800")
            }
        }

        /**
         * 5 → 6：把标准食物库拆成独立的只读数据库（v1.0.4）。
         *
         * 旧 food 表里混着四种东西：654 条标准食物、老预置、用户自建、扫码缓存。
         * 这一步只把「用户自己的」搬到 user_food，收藏按名字搬到 food_favorite，
         * 然后删掉整张旧表；标准食物改由 assets 里的 food_catalog.db 提供。
         *
         * 顺序很关键：先建表 → 再搬数据 → 最后 DROP，中途失败还有旧表兜底。
         * user_food 的列和旧 food 表逐字对齐，所以能整行 INSERT ... SELECT。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_food` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `pinyin` TEXT NOT NULL,
                        `barcode` TEXT,
                        `kcalPer100g` REAL NOT NULL,
                        `proteinPer100g` REAL NOT NULL,
                        `carbPer100g` REAL NOT NULL,
                        `fatPer100g` REAL NOT NULL,
                        `fiberPer100g` REAL NOT NULL DEFAULT 0.0,
                        `sodiumMgPer100g` REAL NOT NULL DEFAULT 0.0,
                        `calciumMgPer100g` REAL NOT NULL DEFAULT 0.0,
                        `defaultGrams` REAL NOT NULL,
                        `source` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_food_name` ON `user_food` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_food_barcode` ON `user_food` (`barcode`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_favorite` (
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `food_search_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_food_search_history_keyword` " +
                        "ON `food_search_history` (`keyword`)"
                )

                // 用户自建 / AI 新增 / 扫码缓存：整行搬走，一条不丢
                db.execSQL(
                    """
                    INSERT INTO `user_food`
                      (`id`,`name`,`pinyin`,`barcode`,`kcalPer100g`,`proteinPer100g`,
                       `carbPer100g`,`fatPer100g`,`fiberPer100g`,`sodiumMgPer100g`,
                       `calciumMgPer100g`,`defaultGrams`,`source`,`category`,`brand`,
                       `favorite`,`createdAt`)
                    SELECT
                       `id`,`name`,`pinyin`,`barcode`,`kcalPer100g`,`proteinPer100g`,
                       `carbPer100g`,`fatPer100g`,`fiberPer100g`,`sodiumMgPer100g`,
                       `calciumMgPer100g`,`defaultGrams`,`source`,`category`,`brand`,
                       `favorite`,`createdAt`
                    FROM `food` WHERE `source` <> 'preset'
                    """.trimIndent()
                )

                // 收藏按名字记：标准库的 id 以后扩库会变，名字才稳定
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `food_favorite` (`name`,`createdAt`)
                    SELECT `name`, `createdAt` FROM `food` WHERE `favorite` = 1
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS `food`")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qingji.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6
                    )
                    // 兜底：万一日后某个版本的迁移没写，宁可清表也不能让 App 打不开。
                    // 每加一次表结构，务必同时补 Migration，别依赖这一行。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
