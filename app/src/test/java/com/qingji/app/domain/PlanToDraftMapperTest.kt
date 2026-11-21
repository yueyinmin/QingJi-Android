package com.qingji.app.domain

import com.qingji.app.data.local.entity.PlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QJ 训练联动：计划目标→草稿映射 + 目标/实际字段持久化兼容的纯单测。
 * 覆盖工单验收：4组目标、改实际不动目标、旧JSON无目标字段读回null、组数不当完成数。
 */
class PlanToDraftMapperTest {

    private fun plan(sets: Int, reps: Int, kg: Double) = PlanExercise(
        dayId = 1L,
        exerciseUuid = "ex-uuid",
        exerciseNameSnapshot = "杠铃卧推",
        targetSets = sets,
        targetReps = reps,
        targetWeightKg = kg,
        orderInDay = 2
    )

    @Test
    fun `计划4组12次50kg生成4组且每组目标独立保存`() {
        val mapped = PlanToDraftMapper.map(plan(4, 12, 50.0))
        assertEquals("ex-uuid", mapped.exerciseUuid)
        assertEquals("杠铃卧推", mapped.name)
        assertEquals(4, mapped.sets.size)
        mapped.sets.forEach {
            assertEquals(12, it.targetReps)
            assertEquals(50.0, it.targetWeightKg!!, 0.001)
        }
        // 实际值预填计划值，但完成状态必须 false（目标组数≠已完成组数）
        assertTrue(mapped.sets.all { !it.completed })
        assertTrue(mapped.sets.all { it.reps == 12 && it.weightKg == 50.0 })
    }

    @Test
    fun `改实际重量次数不覆盖目标`() {
        val set = PlanToDraftMapper.map(plan(1, 12, 50.0)).sets.first()
        val changed = set.copy(weightKg = 55.0, reps = 10, completed = true)
        assertEquals(50.0, changed.targetWeightKg!!, 0.001)
        assertEquals(12, changed.targetReps)
        assertEquals(55.0, changed.weightKg, 0.001)
        assertEquals(10, changed.reps)
        assertTrue(changed.completed)
    }

    @Test
    fun `未指定重量或次数的计划目标为空`() {
        val mapped = PlanToDraftMapper.map(plan(2, 10, 0.0))
        assertEquals(2, mapped.sets.size)
        assertNull(mapped.sets.first().targetWeightKg)
        assertEquals(10, mapped.sets.first().targetReps)
    }

    @Test
    fun `目标组数非法时至少生成一组`() {
        assertEquals(1, PlanToDraftMapper.map(plan(0, 10, 20.0)).sets.size)
        assertEquals(1, PlanToDraftMapper.map(plan(-3, 10, 20.0)).sets.size)
    }

    @Test
    fun `新旧JSON序列化往返保留目标字段`() {
        val set = WorkoutSet(
            weightKg = 55.0, reps = 10, completed = true,
            targetReps = 12, targetWeightKg = 50.0
        )
        val json = TrainingMath.encodeSets(listOf(set))
        assertTrue(json.contains("tr"))
        assertTrue(json.contains("tw"))
        val back = TrainingMath.decodeSets(json).first()
        assertEquals(12, back.targetReps)
        assertEquals(50.0, back.targetWeightKg!!, 0.001)
        assertEquals(55.0, back.weightKg, 0.001)
        assertEquals(10, back.reps)
        assertTrue(back.completed)
    }

    @Test
    fun `旧JSON无目标字段读回null且不报错`() {
        // 旧格式：只有 w/r/rpe/rest/done，没有 tr/tw
        val oldJson = "[{\"w\":40.0,\"r\":8,\"rpe\":0,\"rest\":0,\"done\":true}]"
        val back = TrainingMath.decodeSets(oldJson).first()
        assertNull(back.targetReps)
        assertNull(back.targetWeightKg)
        assertEquals(40.0, back.weightKg, 0.001)
        assertEquals(8, back.reps)
        assertTrue(back.completed)
    }

    @Test
    fun `手工空白训练的目标字段默认为空`() {
        val set = WorkoutSet(20.0, 15)
        assertNull(set.targetReps)
        assertNull(set.targetWeightKg)
        val json = TrainingMath.encodeSets(listOf(set))
        assertFalse(json.contains("tr"))
        assertFalse(json.contains("tw"))
    }
}
