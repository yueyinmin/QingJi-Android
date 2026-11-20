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
import com.qingji.app.data.local.dao.UserFoodGroupDao
import com.qingji.app.data.local.dao.UserBrandDao
import com.qingji.app.data.local.dao.UserProfileDao
import com.qingji.app.data.local.dao.WaterLogDao
import com.qingji.app.data.local.dao.WeightLogDao
import com.qingji.app.data.local.dao.ExerciseDao
import com.qingji.app.data.local.dao.LibraryMetaDao
import com.qingji.app.data.local.dao.TrainingPlanDao
import com.qingji.app.data.local.dao.TrainingPlanDayDao
import com.qingji.app.data.local.dao.PlanExerciseDao
import com.qingji.app.data.local.dao.WorkoutSessionDao
import com.qingji.app.data.local.dao.WorkoutExerciseDao
import com.qingji.app.data.local.dao.WorkoutCardioDetailDao
import com.qingji.app.data.local.dao.SelfDisciplineGoalDao
import com.qingji.app.data.local.dao.SelfDisciplinePeriodDao
import com.qingji.app.data.local.dao.SelfDisciplineCheckinDao
import com.qingji.app.data.local.dao.SelfDisciplineUrgeDao
import com.qingji.app.data.local.dao.SelfDisciplineSettingDao
import com.qingji.app.data.local.dao.SelfDisciplineMilestoneDao
import com.qingji.app.data.local.dao.SelfDisciplineInterventionDao
import com.qingji.app.data.local.dao.ExerciseCalorieConfigDao
import com.qingji.app.data.local.dao.ExerciseSessionCalorieDao
import com.qingji.app.data.local.dao.SleepDao
import com.qingji.app.data.local.dao.DailyHealthSummaryDao
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
import com.qingji.app.data.local.entity.UserFoodGroup
import com.qingji.app.data.local.entity.UserFoodGroupItem
import com.qingji.app.data.local.entity.UserBrand
import com.qingji.app.data.local.entity.UserBrandDish
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.local.entity.ExerciseEntity
import com.qingji.app.data.local.entity.LibraryMeta
import com.qingji.app.data.local.entity.TrainingPlan
import com.qingji.app.data.local.entity.TrainingPlanDay
import com.qingji.app.data.local.entity.PlanExercise
import com.qingji.app.data.local.entity.WorkoutSession
import com.qingji.app.data.local.entity.WorkoutExercise
import com.qingji.app.data.local.entity.WorkoutCardioDetail
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.data.local.entity.SelfDisciplineSettingEntity
import com.qingji.app.data.local.entity.SelfDisciplineMilestoneEntity
import com.qingji.app.data.local.entity.SelfDisciplineInterventionEntity
import com.qingji.app.data.local.entity.ExerciseCalorieConfigEntity
import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.data.local.entity.DailyHealthSummaryEntity
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
 * 动表结构的四件套（血泪教训，缺一个都会让 Room 打开失败）：
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
        FoodSearchHistory::class,
        // v1.0.10 训练中心：6 张全新表，旧表一列都不动
        ExerciseEntity::class,
        LibraryMeta::class,
        TrainingPlan::class,
        TrainingPlanDay::class,
        PlanExercise::class,
        WorkoutSession::class,
        WorkoutExercise::class,
        // v1.2.0 自律管理模块（SD-013）：7 张新表（5 张 P0 + 2 张 P1 预留），旧表一列不动
        SelfDisciplineGoalEntity::class,
        SelfDisciplinePeriodEntity::class,
        SelfDisciplineCheckinEntity::class,
        SelfDisciplineUrgeEntity::class,
        SelfDisciplineSettingEntity::class,
        SelfDisciplineMilestoneEntity::class,
        SelfDisciplineInterventionEntity::class,
        // v1.4 健康体系 P0（Room 13）：运动热量参数库 + 统一运动消耗账本，旧表一列不动
        ExerciseCalorieConfigEntity::class,
        ExerciseSessionCalorieEntity::class,
        // v1.4 健康体系 V1.1（Room 14）：睡眠记录（工单 9），旧表一列不动
        SleepRecordEntity::class,
        // v1.4 健康体系 V1.1（Room 15）：每日健康汇总缓存（工单 10.2，可重建），旧表一列不动
        DailyHealthSummaryEntity::class,
        // v1.5 食物创建体系重构（Room 16→17）：用户自建外卖品牌 + 品牌菜品，旧表一列不动
        UserBrand::class,
        UserBrandDish::class,
        // v1.6 食物库整体重构（Room 18→19）：自定义食物组 + 组食物关联，旧表一列不动
        UserFoodGroup::class,
        UserFoodGroupItem::class,
        // 统一训练体系（Room 19→20）：有氧项目明细，旧表一列不动
        WorkoutCardioDetail::class
    ],
    version = 20,
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
    abstract fun userFoodGroupDao(): UserFoodGroupDao
    abstract fun userBrandDao(): UserBrandDao
    abstract fun foodFavoriteDao(): FoodFavoriteDao
    abstract fun foodSearchHistoryDao(): FoodSearchHistoryDao

    // ---------- 训练中心（Room 10） ----------
    abstract fun exerciseDao(): ExerciseDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun trainingPlanDayDao(): TrainingPlanDayDao
    abstract fun planExerciseDao(): PlanExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun workoutCardioDetailDao(): WorkoutCardioDetailDao
    abstract fun libraryMetaDao(): LibraryMetaDao

    // ---------- 自律管理模块（Room 11） ----------
    abstract fun selfDisciplineGoalDao(): SelfDisciplineGoalDao
    abstract fun selfDisciplinePeriodDao(): SelfDisciplinePeriodDao
    abstract fun selfDisciplineCheckinDao(): SelfDisciplineCheckinDao
    abstract fun selfDisciplineUrgeDao(): SelfDisciplineUrgeDao
    abstract fun selfDisciplineSettingDao(): SelfDisciplineSettingDao
    abstract fun selfDisciplineMilestoneDao(): SelfDisciplineMilestoneDao
    abstract fun selfDisciplineInterventionDao(): SelfDisciplineInterventionDao

    // ---------- 健康体系 V1.0（Room 13） ----------
    abstract fun exerciseCalorieConfigDao(): ExerciseCalorieConfigDao
    abstract fun exerciseSessionCalorieDao(): ExerciseSessionCalorieDao
    abstract fun sleepDao(): SleepDao
    abstract fun dailyHealthSummaryDao(): DailyHealthSummaryDao

    companion object {
        /** 整个 App 只保留一个数据库实例，重复创建会出问题。 */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 1 → 2：新增身体维度、进度照片、运动记录三张表。 */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
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
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
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
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
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
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
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

        /**
         * 6 → 7：运动记录补「步数」和「公里数」两列（v1.0.6）。
         *
         * 确认页本来就支持按步数 / 公里记录，但换算成分钟算完热量之后
         * 原始数字被丢掉了，历史里只剩「40 分钟」。现在原样留下来。
         *
         * 老记录一律补 0：它们当时确实没存这个数，不能反推（按步频和配速
         * 反推出来的「步数」是算出来的，不是用户填的，冒充原始值会误导）。
         * DEFAULT 字面量必须和 Entity 的 @ColumnInfo 逐字一致（0 / 0.0）。
         */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercise_log` ADD COLUMN `steps` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercise_log` ADD COLUMN `distanceKm` REAL NOT NULL DEFAULT 0.0")
            }
        }

        /**
         * 7 → 8：user_food 的四列微量营养素改成可空，并补上 sugarPer100g。
         *
         * 为什么要重建表：SQLite 的 ALTER TABLE 不支持把 NOT NULL 列改成可空，
         * 也不支持删 DEFAULT；只能「建新表 → 搬数据 → 删旧表 → 改名 → 重建索引」。
         *
         * 语义怎么处理：老库这三列是 NOT NULL DEFAULT 0.0，但自建食物表单
         * 从来没有录入入口，历史上所有 0 都只是「没填」而不是「测得 0」。
         * 用 NULLIF(x, 0.0) 把这些假的 0 还原成 NULL（未收录）；
         * 万一哪条真有非零值（手改备份导入过），原样保留。
         * 新增的 sugarPer100g 历史无数据，一律 NULL。
         *
         * Entity 现在不写 @ColumnInfo(defaultValue)，所以这里也不能写 DEFAULT，
         * 否则 Room 的 schema 校验会报「列默认值不一致」直接打不开库。
         */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_food_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL, `pinyin` TEXT NOT NULL, `barcode` TEXT,
                        `kcalPer100g` REAL NOT NULL, `proteinPer100g` REAL NOT NULL,
                        `carbPer100g` REAL NOT NULL, `fatPer100g` REAL NOT NULL,
                        `fiberPer100g` REAL, `sodiumMgPer100g` REAL,
                        `sugarPer100g` REAL, `calciumMgPer100g` REAL,
                        `defaultGrams` REAL NOT NULL, `source` TEXT NOT NULL,
                        `category` TEXT NOT NULL, `brand` TEXT NOT NULL,
                        `favorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT INTO `user_food_new` (
                        `id`,`name`,`pinyin`,`barcode`,`kcalPer100g`,`proteinPer100g`,
                        `carbPer100g`,`fatPer100g`,`fiberPer100g`,`sodiumMgPer100g`,
                        `sugarPer100g`,`calciumMgPer100g`,`defaultGrams`,`source`,
                        `category`,`brand`,`favorite`,`createdAt`
                    ) SELECT
                        `id`,`name`,`pinyin`,`barcode`,`kcalPer100g`,`proteinPer100g`,
                        `carbPer100g`,`fatPer100g`,NULLIF(`fiberPer100g`,0.0),
                        NULLIF(`sodiumMgPer100g`,0.0),NULL,
                        NULLIF(`calciumMgPer100g`,0.0),`defaultGrams`,`source`,
                        `category`,`brand`,`favorite`,`createdAt` FROM `user_food`
                """)
                db.execSQL("DROP TABLE `user_food`")
                db.execSQL("ALTER TABLE `user_food_new` RENAME TO `user_food`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_food_name` ON `user_food` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_food_barcode` ON `user_food` (`barcode`)")
            }
        }

        /**
         * 8 → 9：断食记录支持「自由断食」（2026-11-11 专项，单独一条迁移）。
         *
         * 只加两列，不重建表、不动任何旧列：
         *   freeMode      = 1 时历史卡按「自由断食 · 断食 X 小时 Y 分」展示，不显示进食窗口
         *   actualMinutes = 实际断食分钟数；老记录那时记的是「计划」不是「实际」，补 0
         *
         * 旧数据兼容：两个 ALTER 都带 DEFAULT 0，历史行自动补 0，
         * 历史卡的判断是 `freeMode == true` 才走新样式，所以老记录外观完全不变。
         * DEFAULT 字面量必须和 Entity 的 @ColumnInfo(defaultValue = "0") 逐字一致。
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `fasting_log` ADD COLUMN `freeMode` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `fasting_log` ADD COLUMN `actualMinutes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 9 → 10：训练中心新增 6 张表。
         *
         * 所有 SQL 都是照 `schemas/10.json` 里 Room 自己生成的 createSql 原样抄的
         * （只把 ${TABLE_NAME} 换成真实表名），所以 DEFAULT、NOT NULL、列顺序
         * 与 Entity 定义逐字一致，不会在打开数据库时被告知 schema 不匹配。
         *
         * 关键点：一条 ALTER / DROP 都没有，15 张老表和里面的数据完全不动。
         */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `exercise` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '', `primaryMuscles` TEXT NOT NULL DEFAULT '', `secondaryMuscles` TEXT NOT NULL DEFAULT '', `equipment` TEXT NOT NULL DEFAULT '', `steps` TEXT NOT NULL DEFAULT '', `commonMistakes` TEXT NOT NULL DEFAULT '', `isBuiltIn` INTEGER NOT NULL DEFAULT 0, `isFavorite` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uuid`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_category` ON `exercise` (`category`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise` (`name`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `training_plan` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `source` TEXT NOT NULL DEFAULT '', `isActive` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uuid`))")

                db.execSQL("CREATE TABLE IF NOT EXISTS `training_plan_day` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planUuid` TEXT NOT NULL DEFAULT '', `weekday` INTEGER NOT NULL DEFAULT 0, `title` TEXT NOT NULL DEFAULT '', `isRest` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plan_day_planUuid` ON `training_plan_day` (`planUuid`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `plan_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` INTEGER NOT NULL DEFAULT 0, `exerciseUuid` TEXT NOT NULL DEFAULT '', `exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', `targetSets` INTEGER NOT NULL DEFAULT 0, `targetReps` INTEGER NOT NULL DEFAULT 0, `targetWeightKg` REAL NOT NULL DEFAULT 0.0, `orderInDay` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercise_dayId` ON `plan_exercise` (`dayId`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_session` (`uuid` TEXT NOT NULL, `date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL DEFAULT 0, `endedAt` INTEGER NOT NULL DEFAULT 0, `durationMinutes` INTEGER NOT NULL DEFAULT 0, `note` TEXT NOT NULL DEFAULT '', `source` TEXT NOT NULL DEFAULT 'manual', `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`uuid`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_session_date` ON `workout_session` (`date`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `workout_exercise` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionUuid` TEXT NOT NULL DEFAULT '', `exerciseUuid` TEXT NOT NULL DEFAULT '', `exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', `orderInSession` INTEGER NOT NULL DEFAULT 0, `setsJson` TEXT NOT NULL DEFAULT '', `note` TEXT NOT NULL DEFAULT '')")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercise_sessionUuid` ON `workout_exercise` (`sessionUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_exercise_exerciseUuid` ON `workout_exercise` (`exerciseUuid`)")
            }
        }

        /** v1.1.0：迁移测试用 —— 全部历史迁移，按版本顺序（lazy 避免和 MIGRATION_10_11 的顺序依赖）。 */
        internal val ALL_MIGRATIONS: List<Migration> by lazy {
            listOf(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20
            )
        }

        /**
         * 10 → 11：自律管理模块新增 7 张表。
         *
         * 所有 SQL 都照 `schemas/11.json` 里 Room 自己生成的 createSql 原样抄的
         * （只把 ${TABLE_NAME} 换成真实表名），DEFAULT、NOT NULL、列顺序
         * 与 Entity 定义逐字一致，不会在打开数据库时被告知 schema 不匹配。
         *
         * 关键点：一条 ALTER / DROP 都没有，11 张老表和里面的数据完全不动。
         */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_goals` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `goalType` TEXT NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, `startDate` TEXT NOT NULL, " +
                        "`targetDays` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, " +
                        "`reminderMinute` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_goals_isEnabled` ON `self_discipline_goals` (`isEnabled`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_periods` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `startAt` TEXT NOT NULL, " +
                        "`endAt` TEXT, `endReason` TEXT, `status` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_periods_goalId` ON `self_discipline_periods` (`goalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_periods_status` ON `self_discipline_periods` (`status`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_checkins` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `localDate` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `mood` TEXT NOT NULL, " +
                        "`urgeLevel` INTEGER NOT NULL, `triggers` TEXT NOT NULL, " +
                        "`copingMethods` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_self_discipline_checkins_goalId_localDate` " +
                        "ON `self_discipline_checkins` (`goalId`, `localDate`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_urge_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, " +
                        "`intensity` INTEGER NOT NULL, `trigger` TEXT NOT NULL, " +
                        "`copingMethod` TEXT NOT NULL, `intensityAfter` INTEGER NOT NULL, " +
                        "`resolved` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_urge_logs_goalId` ON `self_discipline_urge_logs` (`goalId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_settings` (" +
                        "`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_milestones` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `milestoneDays` INTEGER NOT NULL, " +
                        "`achievedAt` TEXT, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_milestones_goalId` ON `self_discipline_milestones` (`goalId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `self_discipline_interventions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                        "`occurredAt` INTEGER NOT NULL, `beforeIntensity` INTEGER NOT NULL, " +
                        "`afterIntensity` INTEGER NOT NULL, `effective` INTEGER, " +
                        "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_self_discipline_interventions_goalId` ON `self_discipline_interventions` (`goalId`)")
            }
        }

        /**
         * 11 → 12：Workout Guide 302 条动作库。
         *
         * 只用 ALTER TABLE ADD COLUMN 加 6 个新列，并新建 library_meta 表。
         * 不删表、不删列、不改旧列默认值；用户的收藏、自建动作、训练历史、计划全部原样保留。
         * 新列 defaultValue 与 ExerciseEntity 的 @ColumnInfo(defaultValue = ...) 逐字一致。
         */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `englishName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `slug` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `exerciseType` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `isStretch` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `assetPath` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercise` ADD COLUMN `attribution` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS `library_meta` (`key` TEXT NOT NULL, `value` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`key`))")
            }
        }

        /**
         * 12 → 13：健康体系 V1.0 P0（运动热量参数库 + 统一运动消耗账本）。
         *
         * 只新建两张表 + 给参数库灌 12 行类型级 MET 种子，一行旧数据都不碰、不改旧列。
         * 实体的 @ColumnInfo(defaultValue=...) 与下面 SQL 的 DEFAULT 逐字一致
         * （规矩见类头「动表结构四件套」）。
         *
         * 消耗账本的唯一索引 (sourceType, sourceId) 是去重的根基：
         * 同一场训练重复保存、重复结算、备份恢复都只会有一行。
         * MET 种子值 = 工单初始参考值（口径沿用现有 MET×体重÷60×分钟，用户已拍板），
         * metLow/metHigh = 参考值 ×0.85 / ×1.15，与现有强度系数一致。
         */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_calorie_config` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`exerciseId` TEXT NOT NULL DEFAULT '', " +
                        "`activityType` TEXT NOT NULL, " +
                        "`metLow` REAL NOT NULL DEFAULT 0.0, " +
                        "`metModerate` REAL NOT NULL DEFAULT 0.0, " +
                        "`metHigh` REAL NOT NULL DEFAULT 0.0, " +
                        "`source` TEXT NOT NULL DEFAULT '', " +
                        "`version` TEXT NOT NULL DEFAULT '', " +
                        "`isEnabled` INTEGER NOT NULL DEFAULT 1)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_calorie_config_activityType` ON `exercise_calorie_config` (`activityType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_calorie_config_exerciseId` ON `exercise_calorie_config` (`exerciseId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_session_calorie` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceType` TEXT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL DEFAULT 0, " +
                        "`caloriesGross` REAL NOT NULL DEFAULT 0.0, " +
                        "`weightSnapshot` REAL NOT NULL DEFAULT 0.0, " +
                        "`calculationMethod` TEXT NOT NULL DEFAULT '', " +
                        "`calculationVersion` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_session_calorie_date` ON `exercise_session_calorie` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_session_calorie_sourceType_sourceId` ON `exercise_session_calorie` (`sourceType`, `sourceId`)")

                // MET 参数种子（幂等：IGNORE，重复跑迁移/重复启动都不会写重）
                val metSource = "Compendium of Physical Activities 近似值"
                val metVersion = "met-v1"
                val seeds = listOf(
                    // activityType to (moderate) —— low/high 由 moderate ×0.85/×1.15 派生
                    "strength_light" to 3.5,        // 轻度力量训练
                    "strength_moderate" to 5.0,     // 中等力量训练
                    "strength_heavy" to 6.0,        // 高强度力量训练
                    "bodyweight_circuit" to 5.0,    // 自重循环训练
                    "hiit" to 8.0,                  // 高强度间歇训练
                    "flexibility" to 2.3,           // 拉伸、放松
                    "walking_stroll" to 3.5,        // 中速步行
                    "walking_brisk" to 4.3,         // 快走
                    "jogging" to 7.0,               // 慢跑
                    "running" to 8.3,               // 跑步
                    "cycling" to 6.8,               // 骑行
                    "swimming" to 6.0               // 游泳
                )
                seeds.forEach { (type, moderate) ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO `exercise_calorie_config` " +
                            "(`exerciseId`,`activityType`,`metLow`,`metModerate`,`metHigh`,`source`,`version`,`isEnabled`) " +
                            "VALUES ('', ?, ?, ?, ?, ?, ?, 1)",
                        arrayOf(
                            type,
                            Math.round(moderate * 0.85 * 100.0) / 100.0,
                            moderate,
                            Math.round(moderate * 1.15 * 100.0) / 100.0,
                            metSource,
                            metVersion
                        )
                    )
                }
            }
        }

        /**
         * v1.4 健康体系 V1.1（Room 13 → 14）：睡眠记录表（工单 9）。
         * 全新表，只有 CREATE TABLE + 索引，一行旧数据都不碰。
         * defaultValue 与 SleepRecordEntity 的 @ColumnInfo 逐字一致（四件套纪律③）。
         */
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_record` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER NOT NULL, " +
                        "`durationMinutes` INTEGER NOT NULL DEFAULT 0, " +
                        "`quality` INTEGER NOT NULL DEFAULT 0, " +
                        "`morningFeeling` INTEGER NOT NULL DEFAULT 0, " +
                        "`wakeCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`napMinutes` INTEGER NOT NULL DEFAULT 0, " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`sourceType` TEXT NOT NULL DEFAULT 'MANUAL', " +
                        "`externalId` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_record_date` ON `sleep_record` (`date`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_record_sourceType_externalId` ON `sleep_record` (`sourceType`, `externalId`)")
            }
        }

        /**
         * v1.4 健康体系 V1.1（Room 14 → 15）：每日健康汇总缓存表（工单 10.2）。
         * 全新表只有 CREATE TABLE + 唯一索引，一行旧数据都不碰。
         * 表本身是可重建缓存，迁移失败重装也能从原始记录算回来。
         */
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_health_summary` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`calorieIntake` REAL NOT NULL DEFAULT 0.0, " +
                        "`proteinG` REAL NOT NULL DEFAULT 0.0, " +
                        "`carbohydratesG` REAL NOT NULL DEFAULT 0.0, " +
                        "`fatG` REAL NOT NULL DEFAULT 0.0, " +
                        "`exerciseCalories` REAL NOT NULL DEFAULT 0.0, " +
                        "`strengthCalories` REAL NOT NULL DEFAULT 0.0, " +
                        "`cardioCalories` REAL NOT NULL DEFAULT 0.0, " +
                        "`walkingCalories` REAL NOT NULL DEFAULT 0.0, " +
                        "`workoutDurationMinutes` INTEGER NOT NULL DEFAULT 0, " +
                        "`workoutCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`workoutVolumeKg` REAL NOT NULL DEFAULT 0.0, " +
                        "`sleepDurationMinutes` INTEGER NOT NULL DEFAULT 0, " +
                        "`sleepRecordCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`signature` TEXT NOT NULL DEFAULT '', " +
                        "`lastCalculatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_health_summary_date` ON `daily_health_summary` (`date`)")
            }
        }

        /**
         * v1.5 食物创建体系重构（Room 15 → 16）：user_food 加两列。
         *   · nutritionBasis 营养计量基准，老数据统一按「每100g」看待（默认 PER_100G）
         *   · note           用户备注（选填）
         * 两条都是 ALTER TABLE ADD COLUMN + NOT NULL DEFAULT，一行旧数据都不动；
         * 默认值与 UserFood 的 @ColumnInfo(defaultValue=...) 逐字一致（四件套纪律③）。
         * 标准库 food_catalog.db 在另一个文件，这次完全不碰。
         */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_food` ADD COLUMN `nutritionBasis` TEXT NOT NULL DEFAULT 'PER_100G'")
                db.execSQL("ALTER TABLE `user_food` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v1.5 食物创建体系重构（Room 16 → 17）：用户自建外卖品牌 + 品牌菜品。
         * 两张全新表 + 索引，旧表一列不碰、一行不动。
         * 建在用户库而非只读标准库（标准库升级会删库重拷，自建数据会被冲掉）。
         * 字段/默认值与 UserBrand、UserBrandDish 的 @ColumnInfo 逐字一致（四件套纪律）。
         */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_brand` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`icon` TEXT NOT NULL DEFAULT '🏪', " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`sourceLabel` TEXT NOT NULL DEFAULT '', " +
                        "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_brand_name` ON `user_brand` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_brand_dish` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`brandId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL DEFAULT '其他', " +
                        "`kcal` REAL NOT NULL DEFAULT 0.0, " +
                        "`protein` REAL NOT NULL DEFAULT 0.0, " +
                        "`fat` REAL NOT NULL DEFAULT 0.0, " +
                        "`carb` REAL NOT NULL DEFAULT 0.0, " +
                        "`nutritionBasis` TEXT NOT NULL DEFAULT 'PER_100G', " +
                        "`servingGrams` REAL NOT NULL DEFAULT 100.0, " +
                        "`source` TEXT NOT NULL DEFAULT 'USER_BRAND', " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`favorite` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_brand_dish_brandId` ON `user_brand_dish` (`brandId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_brand_dish_name` ON `user_brand_dish` (`name`)")
            }
        }

        /**
         * v1.4.2（Room 17 → 18）：运动日记数据来源统一。
         * 力量训练结束要写一条 exercise_log 进运动日记，需要一个和 workout_session
         * 关联的标记来做防重复 / 编辑复用 / 删除联动。加一列 sourceId（默认空串），
         * 老行全部是空串、行为不变；只加一列，不建索引、不动任何存量数据。
         * 默认值必须和 ExerciseLog.@ColumnInfo(defaultValue = "") 逐字一致。
         */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercise_log` ADD COLUMN `sourceId` TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v1.6（Room 18 → 19）：自定义食物组 + 组食物关联。
         * 两张全新表 + 索引，旧表一列不碰、一行不动（非破坏性迁移）。
         * 建在用户库而非只读标准库（标准库升级会删库重拷，用户建的组会被冲掉）。
         * 字段/默认值/索引与 UserFoodGroup、UserFoodGroupItem 的注解逐字一致（四件套纪律）。
         */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_food_group` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`coverUri` TEXT NOT NULL DEFAULT '', " +
                        "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                        "`updatedAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_food_group_name` ON `user_food_group` (`name`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_food_group_item` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`foodId` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_food_group_item_groupId_foodId` ON `user_food_group_item` (`groupId`, `foodId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_food_group_item_foodId` ON `user_food_group_item` (`foodId`)")
            }
        }

        /**
         * 统一训练体系（Room 19 → 20）：有氧项目明细表 workout_cardio_detail。
         * 一张全新表 + 一个 sessionUuid 索引，旧表一列不碰、一行不动（非破坏性）。
         *
         * 列/默认值/可空性与 WorkoutCardioDetail 的 @ColumnInfo 逐字一致（四件套纪律③）：
         *   - 非空列都有 DEFAULT，且字面量与 Entity 一致（'' / 0 / 0.0）；
         *   - distanceMeters、weightSnapshot 是可空列（Entity 用 Double? 且无 defaultValue），
         *     这里对应写 `REAL`（可空、不写 DEFAULT），不能写 NOT NULL 否则 schema 校验失败。
         *
         * 只建 sessionUuid 普通索引，不建 (sessionUuid, exerciseUuid) 唯一索引——
         * 一场训练允许同一动作重复出现（热身跑 + 间歇跑），工单 §3.2 明确禁止该唯一约束。
         */
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_cardio_detail` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionUuid` TEXT NOT NULL DEFAULT '', " +
                        "`exerciseUuid` TEXT NOT NULL DEFAULT '', " +
                        "`exerciseNameSnapshot` TEXT NOT NULL DEFAULT '', " +
                        "`orderInSession` INTEGER NOT NULL DEFAULT 0, " +
                        "`durationSeconds` INTEGER NOT NULL DEFAULT 0, " +
                        "`distanceMeters` REAL, " +
                        "`calculatedKcal` REAL NOT NULL DEFAULT 0.0, " +
                        "`calculationMethod` TEXT NOT NULL DEFAULT '', " +
                        "`calculationVersion` TEXT NOT NULL DEFAULT '', " +
                        "`weightSnapshot` REAL, " +
                        "`rawInputJson` TEXT NOT NULL DEFAULT '', " +
                        "`createdAt` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_cardio_detail_sessionUuid` " +
                        "ON `workout_cardio_detail` (`sessionUuid`)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "qingji.db"
                )
                    // TASK-08-A：生产和测试共用同一份迁移列表（ALL_MIGRATIONS）。
                    // 以前这里手抄了一遍，加新迁移时漏改这一处 → 单测全过、真机升级直接打不开。
                    // 现在只有一个来源，漏写迁移会让「1→11 全链测试」当场失败。
                    .addMigrations(*ALL_MIGRATIONS.toTypedArray())
                    // 不用 fallbackToDestructiveMigration()。
                    // 那行兜底在「漏写 Migration」或「schema 校验不一致」时会静默清库，
                    // 用户几年的饮食、体重记录一次性没了，而且没有任何提示。
                    // 现在迁移链 1→9 完整，缺迁移就让 Room 明确报错（宁可打不开，也不偷偷删数据），
                    // 开发阶段反而能第一时间发现漏写。
                    // 以后加表结构：version +1 + 写 Migration + defaultValue 逐字一致 + 核对 schemas/N.json。
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
