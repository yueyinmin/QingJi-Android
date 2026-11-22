package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 C：统一运动入口列表项与五类分流测试。 */
class UnifiedExerciseOptionTest {

    @Test
    fun `预设按分类解析类型不按MET`() {
        assertEquals(ExerciseRecordType.HIIT,
            UnifiedExerciseOptionFactory.fromPreset(1, "HIIT 高强度间歇", 10.0, "有氧", false).recordType)
        assertEquals(ExerciseRecordType.FLEXIBILITY,
            UnifiedExerciseOptionFactory.fromPreset(2, "瑜伽", 2.5, "柔韧", false).recordType)
        assertEquals(ExerciseRecordType.CARDIO,
            UnifiedExerciseOptionFactory.fromPreset(3, "跑步 (8 km/h)", 8.3, "有氧", false).recordType)
        // MET 高不代表力量
        val hiMet = UnifiedExerciseOptionFactory.fromPreset(4, "未知跳绳", 12.0, "有氧", false)
        assertEquals(ExerciseRecordType.CARDIO, hiMet.recordType) // MET=12 不判 HIIT；非白名单名按有氧预设兜底
    }

    @Test
    fun `动作库按UUID精确映射`() {
        val o = UnifiedExerciseOptionFactory.fromAction(
            "ex-chest-02", "杠铃卧推", "Bench Press", "weight_reps", "chest"
        )
        assertEquals(ExerciseRecordType.STRENGTH, o.recordType)
        assertTrue(o.isStrengthAction)
        assertNull(o.met) // 力量动作不显示 MET

        val plank = UnifiedExerciseOptionFactory.fromAction(
            "ex-core-01", "平板支撑", "Plank", "duration", "core"
        )
        assertEquals(ExerciseRecordType.OTHER, plank.recordType) // B1.1 映射
    }

    @Test
    fun `用户修正类型优先`() {
        val o = UnifiedExerciseOptionFactory.fromAction(
            "ex-core-01", "平板支撑", "Plank", "duration", "core", recordTypeStored = "CARDIO"
        )
        assertEquals(ExerciseRecordType.CARDIO, o.recordType)
    }

    @Test
    fun `合并时同名动作库优先且收藏最近排前`() {
        val preset = UnifiedExerciseOptionFactory.fromPreset(1, "俯卧撑", 4.0, "力量", false)
        val action = UnifiedExerciseOptionFactory.fromAction("ex-arm-01", "俯卧撑", "Push-up", "bodyweight_reps", "arms", favorite = true)
        val merged = UnifiedExerciseOptionFactory.merge(listOf(preset), listOf(action))
        assertEquals(1, merged.size) // 去重
        assertEquals("action:ex-arm-01", merged[0].key) // 动作库优先
    }
}
