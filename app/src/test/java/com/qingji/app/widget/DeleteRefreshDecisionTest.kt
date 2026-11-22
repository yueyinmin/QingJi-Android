package com.qingji.app.widget

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.Converters
import com.qingji.app.data.local.dao.ExerciseLogDao
import com.qingji.app.data.local.dao.FoodLogDao
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.FoodLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 工单 E1.1/E1.2：删除刷新严格依赖「实际删除行数」。
 * deleteByIdCount 返回 0（记录不存在/重复删除）时不发刷新副作用，
 * 返回 1（真实删除）才刷 OVERVIEW —— 这里验证判定数据源的真实 SQL 语义。
 */
@RunWith(RobolectricTestRunner::class)
class DeleteRefreshDecisionTest {

    @Database(
        entities = [FoodLog::class, ExerciseLog::class],
        version = 1,
        exportSchema = false
    )
    @TypeConverters(Converters::class)
    abstract class TestDb : RoomDatabase() {
        abstract fun foodLogDao(): FoodLogDao
        abstract fun exerciseLogDao(): ExerciseLogDao
    }

    private lateinit var foodDao: FoodLogDao
    private lateinit var exerciseDao: ExerciseLogDao

    @Before
    fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            TestDb::class.java
        ).allowMainThreadQueries().build()
        foodDao = db.foodLogDao()
        exerciseDao = db.exerciseLogDao()
    }

    @Test
    fun `delete_missing_food_returns_zero_no_refresh`() = runBlocking {
        assertEquals(0, foodDao.deleteByIdCount(999L))
    }

    @Test
    fun `delete_existing_food_returns_one_then_zero`() = runBlocking {
        val id = foodDao.insert(
            FoodLog(date = LocalDate.of(2026, 11, 21), mealType = "lunch", foodName = "测试", grams = 100.0, kcal = 100.0)
        )
        assertEquals(1, foodDao.deleteByIdCount(id))
        assertEquals(0, foodDao.deleteByIdCount(id)) // 重复删除不再刷新
    }

    @Test
    fun `delete_existing_exercise_returns_one_then_zero`() = runBlocking {
        val id = exerciseDao.insert(
            ExerciseLog(
                date = LocalDate.of(2026, 11, 21), type = "走路", minutes = 30, kcal = 120.0
            )
        )
        assertEquals(1, exerciseDao.deleteByIdCount(id))
        assertEquals(0, exerciseDao.deleteByIdCount(id))
    }
}
