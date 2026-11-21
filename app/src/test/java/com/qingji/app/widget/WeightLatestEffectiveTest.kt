package com.qingji.app.widget

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.Converters
import com.qingji.app.data.local.dao.WeightLogDao
import com.qingji.app.data.local.entity.WeightLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 工单 E1.3/E1.4/E1.5：Widget 当前体重必须取「<= 今天的最近有效记录」。
 * 用内存 Room 真实跑 DAO SQL，覆盖无记录 / 仅历史 / 未来日期 / 同日多条四种场景。
 * 不改动 [WeightLogDao.latest] 的既有语义。
 */
@RunWith(RobolectricTestRunner::class)
class WeightLatestEffectiveTest {

    @Database(entities = [WeightLog::class], version = 1, exportSchema = false)
    @TypeConverters(Converters::class)
    abstract class TestDb : RoomDatabase() {
        abstract fun weightLogDao(): WeightLogDao
    }

    private lateinit var dao: WeightLogDao
    private val today = LocalDate.of(2026, 11, 21)

    @Before
    fun setUp() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            TestDb::class.java
        ).allowMainThreadQueries().build()
        dao = db.weightLogDao()
    }

    @Test
    fun `no_records_returns_null`() = runBlocking {
        assertNull(dao.latestEffective(today))
    }

    @Test
    fun `only_history_returns_latest_history`() = runBlocking {
        dao.save(WeightLog(date = today.minusDays(3L), weightKg = 70.0, createdAt = 100))
        dao.save(WeightLog(date = today.minusDays(1L), weightKg = 69.5, createdAt = 200))
        assertEquals(69.5, (dao.latestEffective(today)?.weightKg ?: Double.NaN), 0.001)
    }

    @Test
    fun `future_date_record_does_not_override_today`() = runBlocking {
        dao.save(WeightLog(date = today.minusDays(1L), weightKg = 69.0, createdAt = 100))
        dao.save(WeightLog(date = today.plusDays(5L), weightKg = 88.8, createdAt = 999))
        val picked = dao.latestEffective(today)
        assertEquals(69.0, picked?.weightKg ?: Double.NaN, 0.001)
        assertEquals(today.minusDays(1L), picked?.date)
    }

    @Test
    fun `same_day_repeated_save_keeps_last_written_value`() = runBlocking {
        // WeightLog.date 有唯一索引；同日重复保存走 REPLACE，库里只保留最后一次写入。
        // 因此「同日多条」在本项目数据约束下不可能共存，有效记录就是最后写入的那条。
        dao.save(WeightLog(date = today, weightKg = 68.0, createdAt = 100))
        dao.save(WeightLog(date = today, weightKg = 67.5, createdAt = 300))
        dao.save(WeightLog(date = today, weightKg = 67.8, createdAt = 200))
        assertEquals(67.8, (dao.latestEffective(today)?.weightKg ?: Double.NaN), 0.001)
        assertEquals(1, dao.findAll().size)
    }

    @Test
    fun `existing_latest_semantics_unchanged_future_still_wins_in_latest`() = runBlocking {
        dao.save(WeightLog(date = today, weightKg = 68.0, createdAt = 100))
        dao.save(WeightLog(date = today.plusDays(5L), weightKg = 88.8, createdAt = 200))
        // 证明 latest() 保持原语义，Widget 专用查询才有未来日期过滤。
        assertEquals(88.8, (dao.latest()?.weightKg ?: Double.NaN), 0.001)
        assertEquals(68.0, (dao.latestEffective(today)?.weightKg ?: Double.NaN), 0.001)
    }
}
