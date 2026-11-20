package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 健康体系 V1.1：每日健康聚合口径测试（工单 10.2）。
 * 红线：训练容量 kg 与运动消耗 kcal 是两个独立字段，绝不混算。
 */
class DailyHealthAggregatorTest {

    private val date: LocalDate = LocalDate.of(2026, 11, 18)

    private fun raw(
        foodCount: Int = 0, kcal: Double = 0.0, protein: Double = 0.0,
        carb: Double = 0.0, fat: Double = 0.0,
        ledger: List<DailyHealthAggregator.LedgerRow> = emptyList(),
        workoutCount: Int = 0, workoutMinutes: Int = 0,
        sets: List<Pair<Double, Int>> = emptyList(),
        sleepCount: Int = 0, sleepMinutes: Int = 0
    ) = DailyHealthAggregator.RawDay(
        foodLogCount = foodCount, foodKcal = kcal, foodProteinG = protein,
        foodCarbG = carb, foodFatG = fat,
        ledger = ledger,
        workoutCount = workoutCount, workoutDurationMinutes = workoutMinutes,
        workoutSets = sets,
        sleepRecordCount = sleepCount, sleepDurationMinutes = sleepMinutes
    )

    @Test
    fun `空日期 全0但签名有效`() {
        val row = DailyHealthAggregator.aggregate(date, raw(), now = 1L)
        assertEquals(0.0, row.calorieIntake, 0.001)
        assertEquals(0.0, row.exerciseCalories, 0.001)
        assertEquals(0, row.workoutCount)
        assertEquals(0, row.sleepRecordCount)
        assertTrue(row.signature.isNotBlank())
    }

    @Test
    fun `运动消耗按来源分类 总和包含其他来源`() {
        val row = DailyHealthAggregator.aggregate(
            date,
            raw(ledger = listOf(
                DailyHealthAggregator.LedgerRow("STRENGTH_TRAINING", 150.0),
                DailyHealthAggregator.LedgerRow("CARDIO", 200.0),
                DailyHealthAggregator.LedgerRow("WALKING", 80.0),
                DailyHealthAggregator.LedgerRow("MANUAL_EXERCISE", 70.0)
            )),
            now = 1L
        )
        assertEquals(150.0, row.strengthCalories, 0.001)
        assertEquals(200.0, row.cardioCalories, 0.001)
        assertEquals(80.0, row.walkingCalories, 0.001)
        // 总消耗 = 全部来源之和（其他来源也计入，工单 10.2）
        assertEquals(500.0, row.exerciseCalories, 0.001)
    }

    @Test
    fun `训练容量kg与消耗kcal分开且容量是重量乘次数`() {
        // 场次 1 场 40 分钟；两组：40kg×10 次 + 20kg×8 次 → 容量 560 kg
        val row = DailyHealthAggregator.aggregate(
            date,
            raw(
                workoutCount = 1, workoutMinutes = 40,
                sets = listOf(40.0 to 10, 20.0 to 8),
                ledger = listOf(DailyHealthAggregator.LedgerRow("STRENGTH_TRAINING", 120.0))
            ),
            now = 1L)
        assertEquals(560.0, row.workoutVolumeKg, 0.001)
        assertEquals(120.0, row.strengthCalories, 0.001)  // 消耗独立字段
        assertEquals(40, row.workoutDurationMinutes)
        assertEquals(1, row.workoutCount)
    }

    @Test
    fun `徒手组重量0不计容量`() {
        val row = DailyHealthAggregator.aggregate(
            date,
            raw(sets = listOf(0.0 to 15, 30.0 to 5)),
            now = 1L)
        assertEquals(150.0, row.workoutVolumeKg, 0.001)
    }

    @Test
    fun `饮食与睡眠求和`() {
        val row = DailyHealthAggregator.aggregate(
            date,
            raw(foodCount = 3, kcal = 1850.5, protein = 96.2, sleepCount = 2, sleepMinutes = 420 + 30),
            now = 1L)
        assertEquals(1850.5, row.calorieIntake, 0.001)
        assertEquals(96.2, row.proteinG, 0.001)
        assertEquals(450, row.sleepDurationMinutes)
        assertEquals(2, row.sleepRecordCount)
    }

    @Test
    fun `原始数据变化签名必变`() {
        val s1 = DailyHealthAggregator.signatureOf(raw(kcal = 100.0))
        val s2 = DailyHealthAggregator.signatureOf(raw(kcal = 200.0))
        val s3 = DailyHealthAggregator.signatureOf(raw(kcal = 100.0, sleepCount = 1, sleepMinutes = 480))
        assertTrue(s1 != s2)
        assertTrue(s1 != s3)
        // 轻微数值抖动（0.05 以内四舍五入同值）不影响签名，避免无意义重建
        val s4 = DailyHealthAggregator.signatureOf(raw(kcal = 100.04))
        assertEquals(s1, s4)
    }

    // ---- QJ-GOV-2026-01 / P1-01：签名必须覆盖全部参与聚合的输入 ----

    @Test
    fun `单独改碳水签名变化`() {
        assertTrue(DailyHealthAggregator.signatureOf(raw(carb = 10.0)) !=
            DailyHealthAggregator.signatureOf(raw(carb = 20.0)))
    }

    @Test
    fun `单独改脂肪签名变化`() {
        assertTrue(DailyHealthAggregator.signatureOf(raw(fat = 10.0)) !=
            DailyHealthAggregator.signatureOf(raw(fat = 20.0)))
    }

    @Test
    fun `单独改训练时长签名变化`() {
        assertTrue(DailyHealthAggregator.signatureOf(raw(workoutMinutes = 30)) !=
            DailyHealthAggregator.signatureOf(raw(workoutMinutes = 45)))
    }

    @Test
    fun `只改某组次数组数不变签名也变化`() {
        val a = DailyHealthAggregator.signatureOf(raw(sets = listOf(20.0 to 10, 30.0 to 5)))
        val b = DailyHealthAggregator.signatureOf(raw(sets = listOf(20.0 to 12, 30.0 to 5)))
        assertTrue(a != b)
    }

    @Test
    fun `账本总量不变但来源分类变化签名变化`() {
        val a = DailyHealthAggregator.signatureOf(raw(ledger = listOf(
            DailyHealthAggregator.LedgerRow("STRENGTH_TRAINING", 100.0)
        )))
        val b = DailyHealthAggregator.signatureOf(raw(ledger = listOf(
            DailyHealthAggregator.LedgerRow("CARDIO", 100.0)
        )))
        assertTrue(a != b)
    }

    @Test
    fun `明细条数相同内容不同签名变化`() {
        val a = DailyHealthAggregator.signatureOf(raw(sets = listOf(10.0 to 2, 20.0 to 3)))
        val b = DailyHealthAggregator.signatureOf(raw(sets = listOf(10.0 to 3, 20.0 to 2)))
        assertTrue(a != b)
    }

    @Test
    fun `同一输入重复计算签名稳定`() {
        val r = raw(foodCount = 2, kcal = 300.0, carb = 40.0, fat = 10.0,
            ledger = listOf(
                DailyHealthAggregator.LedgerRow("CARDIO", 50.0),
                DailyHealthAggregator.LedgerRow("STRENGTH_TRAINING", 60.0)
            ), sets = listOf(20.0 to 10, 30.0 to 5))
        assertEquals(DailyHealthAggregator.signatureOf(r), DailyHealthAggregator.signatureOf(r))
        // 账本/组明细插入顺序不同 → 排序后签名一致
        val shuffled = r.copy(
            ledger = r.ledger.reversed(),
            workoutSets = r.workoutSets.reversed()
        )
        assertEquals(DailyHealthAggregator.signatureOf(r), DailyHealthAggregator.signatureOf(shuffled))
    }

    @Test
    fun `落表签名与签名函数一致`() {
        val r = raw(kcal = 100.0, carb = 20.0, workoutMinutes = 30,
            sets = listOf(20.0 to 10))
        val row = DailyHealthAggregator.aggregate(date, r, now = 1L)
        assertEquals(DailyHealthAggregator.signatureOf(r), row.signature)
    }
}
