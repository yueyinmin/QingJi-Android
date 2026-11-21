package com.qingji.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.dao.ExerciseSessionCalorieDao
import com.qingji.app.data.local.entity.ExerciseSessionCalorieEntity
import com.qingji.app.domain.WorkoutCalorieCalculator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 统一训练体系账本幂等（P0）端到端测试。
 * 账本幂等的根基是 (sourceType, sourceId) 唯一索引 + upsert（REPLACE）。
 * 这里用内存库证明：同一场训练（同一 sourceId）重复结算永远只有一行，
 * 且数值是最后一次写入的整场值——保存重试/双击/编辑都不产生第二条账本。
 */
@RunWith(RobolectricTestRunner::class)
class UnifiedLedgerIdempotencyTest {

    private lateinit var db: AppDatabase
    private lateinit var ledger: ExerciseSessionCalorieDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        ledger = db.exerciseSessionCalorieDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun row(kcal: Double, method: String) = ExerciseSessionCalorieEntity(
        sourceType = "STRENGTH_TRAINING",
        sourceId = "session-uuid-001",
        date = LocalDate.parse("2026-11-20"),
        durationSeconds = 1800,
        caloriesGross = kcal,
        weightSnapshot = 65.0,
        calculationMethod = method,
        calculationVersion = WorkoutCalorieCalculator.VERSION
    )

    @Test
    fun `同一会话重复结算只有一条账本且更新为整场值`() = runBlocking {
        ledger.upsert(row(80.0, WorkoutCalorieCalculator.METHOD_STRENGTH_ONLY))
        // 编辑后重新结算：力量+有氧汇总成 215.5，同 sourceId upsert
        ledger.upsert(row(215.5, WorkoutCalorieCalculator.METHOD_MIXED))

        // findBySource 返回单行；总行数必须是 1,证明重复结算没有新增行
        val found = ledger.findBySource("STRENGTH_TRAINING", "session-uuid-001")
        assertEquals(1, ledger.findAll().size)
        assertEquals(215.5, found!!.caloriesGross, 0.01)
        assertEquals(WorkoutCalorieCalculator.METHOD_MIXED, found.calculationMethod)
    }

    @Test
    fun `不同会话各自一条账本互不覆盖`() = runBlocking {
        ledger.upsert(row(80.0, WorkoutCalorieCalculator.METHOD_STRENGTH_ONLY))
        ledger.upsert(row(55.5, WorkoutCalorieCalculator.METHOD_CARDIO_ONLY).copy(sourceId = "session-uuid-002"))

        assertEquals(2, ledger.findAll().size)
        assert(ledger.findBySource("STRENGTH_TRAINING", "session-uuid-001") != null)
        assert(ledger.findBySource("STRENGTH_TRAINING", "session-uuid-002") != null)
    }

    @Test
    fun `insertMissing不覆盖已有结算值`() = runBlocking {
        ledger.upsert(row(215.5, WorkoutCalorieCalculator.METHOD_MIXED))
        // 历史补算走 insertMissing：已存在的行不动
        ledger.insertMissing(row(80.0, WorkoutCalorieCalculator.METHOD_STRENGTH_ONLY))

        assertEquals(1, ledger.findAll().size)
        assertEquals(215.5, ledger.findBySource("STRENGTH_TRAINING", "session-uuid-001")!!.caloriesGross, 0.01)
    }

    @Test
    fun `删除会话来源账本后不留行`() = runBlocking {
        ledger.upsert(row(215.5, WorkoutCalorieCalculator.METHOD_MIXED))
        ledger.deleteBySource("STRENGTH_TRAINING", "session-uuid-001")
        assertEquals(0, ledger.findAll().size)
    }
}
