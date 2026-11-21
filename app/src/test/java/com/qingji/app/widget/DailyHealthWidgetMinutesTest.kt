package com.qingji.app.widget

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.Converters
import com.qingji.app.data.local.dao.SleepDao
import com.qingji.app.data.local.dao.WorkoutSessionDao
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.data.local.entity.WorkoutSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 验收补充工单 P1 §2 / §6：训练与睡眠独立可用。
 *
 * DailyHealthRepository.workoutMinutesFor / sleepMinutesFor 是对
 * WorkoutSessionDao.dayAggregate / SleepDao.dayAggregate 的直接委托，
 * 口径与 summaryFor 完全一致。这里用内存库真实跑这两条聚合 SQL，验证：
 * - 两个数据源各自独立返回分钟数，一个源的数据不影响另一个；
 * - 空表返回 count=0 / minutesSum=0（真实 0，非失败），不抛异常；
 *   查询异常才由 loadFresh 的 runCatching 映射为 FAILED。
 *
 * 用 allowMainThreadQueries 规避 Robolectric 下 Room 单例跨线程连接指针限制。
 */
@RunWith(RobolectricTestRunner::class)
class DailyHealthWidgetMinutesTest {

    @Database(
        entities = [WorkoutSession::class, SleepRecordEntity::class],
        version = 1,
        exportSchema = false
    )
    @TypeConverters(Converters::class)
    abstract class TestDb : RoomDatabase() {
        abstract fun workoutSessionDao(): WorkoutSessionDao
        abstract fun sleepDao(): SleepDao
    }

    private lateinit var workoutDao: WorkoutSessionDao
    private lateinit var sleepDao: SleepDao
    private val date = LocalDate.of(1999, 1, 13)

    @Before
    fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            TestDb::class.java
        ).allowMainThreadQueries().build()
        workoutDao = db.workoutSessionDao()
        sleepDao = db.sleepDao()
    }

    @Test
    fun `no_records_both_zero_real_value`() = runBlocking {
        // 空表：SQL SUM+COALESCE 返回 count=0 / sum=0，是「真的没有记录」的真实 0。
        assertEquals(0, workoutDao.dayAggregate(date.toString())?.durationSum)
        assertEquals(0, sleepDao.dayAggregate(date.toString())?.minutesSum)
    }

    @Test
    fun `workout_only_then_sleep_only_independent`() = runBlocking {
        // 只写训练：训练有值，睡眠仍是真实 0 —— 一个源不影响另一个
        workoutDao.insert(WorkoutSession(uuid = "w1", date = date, durationMinutes = 45))
        assertEquals(45, workoutDao.dayAggregate(date.toString())?.durationSum)
        assertEquals(0, sleepDao.dayAggregate(date.toString())?.minutesSum)

        // 只写睡眠（另一天）：训练真实 0，睡眠有值
        val sleepDate = date.plusDays(1)
        sleepDao.insert(
            SleepRecordEntity(
                date = sleepDate, startTime = 0L, endTime = 7 * 60 * 60 * 1000L,
                durationMinutes = 420
            )
        )
        assertEquals(0, workoutDao.dayAggregate(sleepDate.toString())?.durationSum)
        assertEquals(420, sleepDao.dayAggregate(sleepDate.toString())?.minutesSum)
    }
}
