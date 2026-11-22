package com.qingji.app.domain

import com.qingji.app.data.local.entity.PlanExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一训练打通：训练计划有氧目标 → 训练草稿的映射测试。 */
class CardioPlanToDraftTest {

    @Test
    fun `力量计划仍走组次kg且无有氧配置`() {
        val plan = PlanExercise(
            dayId = 1, exerciseUuid = "wg:bench", exerciseNameSnapshot = "卧推",
            targetSets = 3, targetReps = 10, targetWeightKg = 40.0, orderInDay = 0
        )
        val m = PlanToDraftMapper.map(plan)
        assertEquals(ExerciseRecordType.STRENGTH, m.recordType)
        assertEquals(3, m.sets.size)
        assertEquals(10, m.sets.first().reps)
        assertNull(m.cardioConfig)
    }

    @Test
    fun `有氧计划按targetConfigJson生成有氧目标而非组次kg`() {
        val cfg = TargetConfig(
            type = ExerciseRecordType.CARDIO,
            durationSec = 1800L,
            distanceMeters = 5000.0,
            intensity = "中"
        )
        val plan = PlanExercise(
            dayId = 1, exerciseUuid = "wg:running", exerciseNameSnapshot = "跑步",
            targetSets = 3, targetReps = 10, targetWeightKg = 0.0,
            recordType = ExerciseRecordType.CARDIO.key,
            targetConfigJson = TargetConfigJson.encode(cfg), orderInDay = 0
        )
        val m = PlanToDraftMapper.map(plan)
        assertEquals(ExerciseRecordType.CARDIO, m.recordType)
        assertTrue("有氧动作不应生成力量组", m.sets.isEmpty())
        val out = m.cardioConfig
        assertNotNull(out)
        assertEquals(1800L, out!!.durationSec)
        assertEquals(5000.0, out.distanceMeters ?: 0.0, 0.001)
        assertEquals("中", out.intensity)
    }

    @Test
    fun `只有recordType没有配置时给空配置不伪造目标`() {
        val plan = PlanExercise(
            dayId = 1, exerciseUuid = "wg:walking", exerciseNameSnapshot = "快走",
            targetSets = 3, targetReps = 10, targetWeightKg = 0.0,
            recordType = ExerciseRecordType.CARDIO.key, orderInDay = 0
        )
        val m = PlanToDraftMapper.map(plan)
        assertEquals(ExerciseRecordType.CARDIO, m.recordType)
        assertNull(m.cardioConfig!!.durationSec)
        assertNull(m.cardioConfig!!.distanceMeters)
    }

    @Test
    fun `targetConfigJson往返保留强度与时长`() {
        val cfg = TargetConfig(type = ExerciseRecordType.CARDIO, durationSec = 1200L, intensity = "高")
        val decoded = TargetConfigJson.decode(TargetConfigJson.encode(cfg))
        assertNotNull(decoded)
        assertEquals(ExerciseRecordType.CARDIO, decoded!!.type)
        assertEquals(1200L, decoded.durationSec)
        assertEquals("高", decoded.intensity)
    }
}
