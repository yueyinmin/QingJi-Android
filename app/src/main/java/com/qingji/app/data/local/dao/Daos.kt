package com.qingji.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.BodyMeasure
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.ProgressPhoto
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.local.entity.ExercisePreset
import com.qingji.app.data.local.entity.FastingLog
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UsageStat
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
import com.qingji.app.data.local.entity.WeightLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * ============================================================
 * 所有数据库操作入口
 * ------------------------------------------------------------
 * 规矩：界面层不许直接写 SQL，一切读写都从这里走。
 * 返回 Flow 的表示"数据变了会自动通知界面"，返回普通值的表示一次性查询。
 * ============================================================
 */

/** 用户档案：只有一行，读出来若为空说明还没初始化。 */
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: UserProfile)

    @Query("DELETE FROM user_profile")
    suspend fun clear()
}

/** 体重记录。 */
@Dao
interface WeightLogDao {
    @Query("SELECT * FROM weight_log ORDER BY date DESC")
    fun observeAll(): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_log WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_log ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(log: WeightLog)

    @Query("DELETE FROM weight_log WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    /** 备份导出用：一次读全部（数据量小，几千条也就几毫秒）。 */
    @Query("SELECT * FROM weight_log ORDER BY date ASC")
    suspend fun findAll(): List<WeightLog>

    /** 导入前清空（覆盖恢复）。 */
    @Query("DELETE FROM weight_log")
    suspend fun clearAll()
}

/** 食物库。 */
@Dao
interface FoodDao {
    /** 模糊搜索：名字包含关键词，或拼音首字母以关键词开头。 */
    @Query(
        """
        SELECT * FROM food
        WHERE name LIKE '%' || :keyword || '%'
           OR pinyin LIKE :keyword || '%'
        ORDER BY name
        LIMIT 50
        """
    )
    suspend fun search(keyword: String): List<Food>

    @Query("SELECT * FROM food WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Food?

    @Query("SELECT COUNT(*) FROM food")
    suspend fun count(): Int

    /**
     * 食物库条数的「可订阅」版本。
     * 为什么需要：预置食物是 App 启动后在后台线程灌进去的，如果搜索页只查一次，
     * 灌完也不会自动刷新——用户看到的就是一个永远空的列表（以为是空壳）。
     * 订阅这个 Flow，食物库一有变化，搜索结果就重查。
     */
    @Query("SELECT COUNT(*) FROM food")
    fun observeCount(): Flow<Int>

    /** 升级 App 后按名字补齐新增的预置食物，不重复插、也不覆盖用户自建的。 */
    @Query("SELECT name FROM food")
    suspend fun allNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Food>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Food): Long

    /** 备份导出用。 */
    @Query("SELECT * FROM food ORDER BY name")
    suspend fun findAll(): List<Food>

    /** 食物库：订阅全量，界面里按分类/品牌分组 */
    @Query("SELECT * FROM food ORDER BY name")
    fun observeAll(): Flow<List<Food>>

    /** 食物库「自定义」页签：用户自己加的 */
    @Query("SELECT * FROM food WHERE source = 'user' ORDER BY createdAt DESC")
    fun observeUserFoods(): Flow<List<Food>>

    /** 收藏 / 取消收藏 */
    @Query("UPDATE food SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** 删除单个食物（自定义食物用） */
    @Query("DELETE FROM food WHERE id = :id")
    suspend fun deleteFood(id: Long)

    /** 导入前清空（预置食物会在导入后按名字补回来）。 */
    @Query("DELETE FROM food")
    suspend fun clearAll()
}

/** 一天的热量汇总，字段可空是因为一条都没记时 SUM 会返回 null。 */
data class DailySummary(
    val totalKcal: Double?,
    val totalProtein: Double?,
    val totalCarb: Double?,
    val totalFat: Double?
)

/** 按餐次分组的小计。 */
data class MealSummary(
    val mealType: String,
    val kcal: Double?,
    val itemCount: Int
)

/** 饮食记录。 */
@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_log WHERE date = :date ORDER BY createdAt ASC")
    fun observeByDate(date: LocalDate): Flow<List<FoodLog>>

    /** 周报用：取一段日期内的全部记录，在仓库层聚合，不在 SQL 里堆复杂统计。 */
    @Query("SELECT * FROM food_log WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodLog>>

    /** 常吃食物置顶（M3.5）：最近 30 天出现过的食物名，按名字返回，界面层计数排序。 */
    @Query("SELECT foodName FROM food_log WHERE date >= :from")
    fun observeRecentNames(from: LocalDate): Flow<List<String>>

    @Query(
        """
        SELECT SUM(kcal) AS totalKcal, SUM(proteinG) AS totalProtein,
               SUM(carbG) AS totalCarb, SUM(fatG) AS totalFat
        FROM food_log WHERE date = :date
        """
    )
    fun observeDailySummary(date: LocalDate): Flow<DailySummary?>

    @Query(
        """
        SELECT mealType, SUM(kcal) AS kcal, COUNT(*) AS itemCount
        FROM food_log WHERE date = :date GROUP BY mealType
        """
    )
    fun observeMealSummary(date: LocalDate): Flow<List<MealSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: FoodLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<FoodLog>)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 用餐详情：把这一餐的几条记录整体挪到别的日期/餐次。 */
    @Query("UPDATE food_log SET date = :newDate, mealType = :newMealType WHERE id IN (:ids)")
    suspend fun moveLogs(ids: List<Long>, newDate: LocalDate, newMealType: String)

    /** 用餐详情：重新识别前先清掉这一餐的旧记录。 */
    @Query("DELETE FROM food_log WHERE date = :date AND mealType = :mealType")
    suspend fun deleteByMeal(date: LocalDate, mealType: String)

    /** 备份导出 / 成就统计用：全部记录。 */
    @Query("SELECT * FROM food_log ORDER BY date ASC, createdAt ASC")
    suspend fun findAll(): List<FoodLog>

    @Query("SELECT COUNT(*) FROM food_log")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(DISTINCT foodName) FROM food_log")
    suspend fun countDistinctNames(): Int

    @Query("SELECT DISTINCT date FROM food_log")
    suspend fun distinctDates(): List<LocalDate>

    @Query("SELECT COUNT(*) FROM food_log WHERE date = :date")
    suspend fun countOn(date: LocalDate): Int

    @Query("DELETE FROM food_log")
    suspend fun clearAll()
}

/** 饮水记录。 */
@Dao
interface WaterLogDao {
    @Query("SELECT SUM(ml) FROM water_log WHERE date = :date")
    fun observeTodayMl(date: LocalDate): Flow<Int?>

    /** 进展页「近一周饮水」：按日期区间取原始记录，在仓库层按天汇总。 */
    @Query("SELECT * FROM water_log WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<WaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: WaterLog)

    @Query("SELECT * FROM water_log ORDER BY date ASC")
    suspend fun findAll(): List<WaterLog>

    @Query("SELECT COUNT(*) FROM water_log")
    suspend fun countAll(): Int

    @Query("DELETE FROM water_log")
    suspend fun clearAll()
}

/** 日记。 */
@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary ORDER BY date DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Diary>>

    @Query("SELECT * FROM diary WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Diary>>

    /** 编辑页打开时读一次旧内容。 */
    @Query("SELECT * FROM diary WHERE id = :id")
    suspend fun findById(id: Long): Diary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(diary: Diary): Long

    @Query("DELETE FROM diary WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM diary ORDER BY date ASC")
    suspend fun findAll(): List<Diary>

    @Query("SELECT COUNT(*) FROM diary")
    suspend fun countAll(): Int

    @Query("DELETE FROM diary")
    suspend fun clearAll()
}

/** AI 配置。 */
@Dao
interface AiConfigDao {
    @Query("SELECT * FROM ai_config WHERE id = 1")
    fun observe(): Flow<AiConfig?>

    @Query("SELECT * FROM ai_config WHERE id = 1")
    suspend fun get(): AiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: AiConfig)

    @Query("DELETE FROM ai_config")
    suspend fun clearAll()
}

/** 调用次数统计。 */
@Dao
interface UsageStatDao {
    @Query("SELECT * FROM usage_stat WHERE date = :date")
    fun observeByDate(date: LocalDate): Flow<List<UsageStat>>

    /** 「我的」页八格统计要累计历史所有记录，所以全表读出来在界面层按类型求和。 */
    @Query("SELECT * FROM usage_stat")
    fun observeAll(): Flow<List<UsageStat>>

    /** 备份导出用。 */
    @Query("SELECT * FROM usage_stat")
    suspend fun findAll(): List<UsageStat>

    /** 「我的」页要看「本月 AI 调用多少次」，按日期区间求和。 */
    @Query("SELECT SUM(count) FROM usage_stat WHERE type = :type AND date BETWEEN :from AND :to")
    suspend fun sumByType(type: String, from: LocalDate, to: LocalDate): Int?

    @Query("DELETE FROM usage_stat")
    suspend fun clearAll()

    /** 查某个类型今天用了几次：AI 单日调用上限熔断要用。没有记录时返回 null。 */
    @Query("SELECT count FROM usage_stat WHERE date = :date AND type = :type")
    suspend fun countByType(date: LocalDate, type: String): Int?

    /** 先尝试插入一行 0，已存在就忽略（配合下面的 +1 用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stat: UsageStat)

    @Query("UPDATE usage_stat SET count = count + 1 WHERE date = :date AND type = :type")
    suspend fun increase(date: LocalDate, type: String)

    /**
     * 计数 +1。
     * 没用 SQL 的 UPSERT 语法，因为那要求手机系统里的数据库版本较新，
     * 老安卓（8.0 / 9.0）不支持会直接崩。分两步最稳，而且包在事务里。
     */
    @Transaction
    suspend fun increment(date: LocalDate, type: String) {
        insertIfAbsent(UsageStat(date = date, type = type, count = 0))
        increase(date, type)
    }
}

/**
 * 身体维度（围度）。
 * 一天一条：同一天再记直接覆盖，和体重一个规矩。
 */
@Dao
interface BodyMeasureDao {
    @Query("SELECT * FROM body_measure WHERE date = :date LIMIT 1")
    fun observe(date: LocalDate): Flow<BodyMeasure?>

    @Query("SELECT * FROM body_measure ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyMeasure>>

    @Query("SELECT * FROM body_measure WHERE date = :date LIMIT 1")
    suspend fun get(date: LocalDate): BodyMeasure?

    /** 最近一次量围度是哪天。进展页展示「最新围度」用。 */
    @Query("SELECT * FROM body_measure ORDER BY date DESC LIMIT 1")
    suspend fun latest(): BodyMeasure?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(item: BodyMeasure)

    @Query("SELECT * FROM body_measure ORDER BY date ASC")
    suspend fun findAll(): List<BodyMeasure>
}

/** 进度照片。一天可以放多张，按时间倒序展示。 */
@Dao
interface ProgressPhotoDao {
    @Query("SELECT * FROM progress_photo ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<ProgressPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProgressPhoto): Long

    @Query("DELETE FROM progress_photo WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM progress_photo ORDER BY date ASC")
    suspend fun findAll(): List<ProgressPhoto>

    @Query("DELETE FROM progress_photo")
    suspend fun clearAll()
}

/** 运动记录。进展页只看最近一周，所以按区间查。 */
@Dao
interface ExerciseLogDao {
    @Query("SELECT * FROM exercise_log WHERE date BETWEEN :from AND :to ORDER BY date ASC, createdAt ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ExerciseLog>>

    @Query("SELECT * FROM exercise_log WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun findRange(from: LocalDate, to: LocalDate): List<ExerciseLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExerciseLog): Long

    @Query("DELETE FROM exercise_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM exercise_log ORDER BY date ASC")
    suspend fun findAll(): List<ExerciseLog>
}

/** 断食记录：一天一条，按日期主键覆盖。 */
@Dao
interface FastingLogDao {
    @Query("SELECT * FROM fasting_log WHERE date = :date LIMIT 1")
    fun observe(date: LocalDate): Flow<FastingLog?>

    @Query("SELECT * FROM fasting_log WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FastingLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(log: FastingLog)

    @Query("SELECT * FROM fasting_log ORDER BY date DESC")
    suspend fun findAll(): List<FastingLog>
}

/** 运动库 DAO。 */
@Dao
interface ExercisePresetDao {
    @Query("SELECT * FROM exercise_preset ORDER BY favorite DESC, name")
    fun observeAll(): Flow<List<ExercisePreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ExercisePreset>)

    @Query("UPDATE exercise_preset SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM exercise_preset")
    suspend fun count(): Int
}
