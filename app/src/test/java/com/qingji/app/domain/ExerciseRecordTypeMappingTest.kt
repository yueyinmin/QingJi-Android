package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一运动体系 B1：324 条映射完整性 + resolver 优先级测试（工单 §12.1）。 */
class ExerciseRecordTypeMappingTest {

    @Test
    fun `324条映射完整且UUID唯一`() {
        val map = ExerciseRecordTypeMap.SEED_TYPES
        assertEquals(ExerciseRecordTypeMap.SEED_COUNT, map.size)
        assertEquals(324, map.size)
        // key 全部非空白、无重复（Map 天然去重，size 相等即证明）
        assertTrue(map.keys.all { it.isNotBlank() })
        assertTrue(map.keys.all { it.startsWith("ex-") || it.startsWith("wg:") })
    }

    @Test
    fun `五类数量与B1_1正式表一致`() {
        val counts = ExerciseRecordTypeMap.SEED_TYPES.values.groupingBy { it }.eachCount()
        assertEquals(252, counts[ExerciseRecordType.STRENGTH])
        assertEquals(44, counts[ExerciseRecordType.CARDIO])
        assertEquals(1, counts[ExerciseRecordType.HIIT])
        assertEquals(15, counts[ExerciseRecordType.FLEXIBILITY])
        assertEquals(12, counts[ExerciseRecordType.OTHER])
    }

    @Test
    fun `精确UUID查询命中且不按名称模糊`() {
        assertEquals(ExerciseRecordType.STRENGTH, ExerciseRecordTypeMap.find("ex-chest-02")) // 杠铃卧推
        assertEquals(ExerciseRecordType.OTHER, ExerciseRecordTypeMap.find("ex-core-01"))    // 平板支撑
        assertEquals(ExerciseRecordType.CARDIO, ExerciseRecordTypeMap.find("wg:running"))    // 跑步
        assertNull(ExerciseRecordTypeMap.find("杠铃卧推")) // 名称不可查
        assertNull(ExerciseRecordTypeMap.find("unknown-uuid"))
    }

    @Test
    fun `resolver持久化值优先于一切推断`() {
        val r = TrainingTypeResolver.resolve(
            persistedRecordType = "FLEXIBILITY",
            name = "HIIT 高强度间歇", exerciseType = "weight_reps", isStretch = false
        )
        assertEquals(ExerciseRecordType.FLEXIBILITY, r.type)
        assertEquals("persisted", r.reason)
    }

    @Test
    fun `resolver HIIT白名单优先于exerciseType`() {
        val r = TrainingTypeResolver.resolve(null, "波比跳", "Burpee", "bodyweight_reps")
        assertEquals(ExerciseRecordType.HIIT, r.type)
    }

    @Test
    fun `resolver 拉伸标记与mobility`() {
        assertEquals(ExerciseRecordType.FLEXIBILITY,
            TrainingTypeResolver.resolve(null, "猫牛式", "Cat-Cow", "duration", "mobility", true).type)
        assertEquals(ExerciseRecordType.FLEXIBILITY,
            TrainingTypeResolver.resolve(null, "瑜伽", "Yoga", "duration").type)
    }

    @Test
    fun `resolver 动作输入形式映射`() {
        assertEquals(ExerciseRecordType.STRENGTH,
            TrainingTypeResolver.resolve(null, "深蹲", "Squat", "bodyweight_reps").type)
        assertEquals(ExerciseRecordType.CARDIO,
            TrainingTypeResolver.resolve(null, "跑步", "Running", "distance_duration").type)
    }

    @Test
    fun `resolver 未知值降级OTHER并保留诊断`() {
        val r = TrainingTypeResolver.resolve(null, "神秘动作", "Mystery", "duration", "core", false)
        assertEquals(ExerciseRecordType.OTHER, r.type)
        assertEquals("fallback", r.reason)
        assertTrue(r.diagnostic!!.contains("神秘动作"))
    }

    @Test
    fun `UUID精确映射优先于名称白名单`() {
        // 平板支撑名称含"平板"，但 UUID 映射为 OTHER 且优先于任何名称推断
        val r = TrainingTypeResolver.resolve(
            null, "平板支撑", "Plank", "duration", "core", false, "ex-core-01"
        )
        assertEquals(ExerciseRecordType.OTHER, r.type)
        assertEquals("uuid-map", r.reason)
        // 杠铃卧推 UUID 映射 STRENGTH
        val s = TrainingTypeResolver.resolve(
            null, "杠铃卧推", "Bench Press", "weight_reps", "chest", false, "ex-chest-02"
        )
        assertEquals(ExerciseRecordType.STRENGTH, s.type)
        assertEquals("uuid-map", s.reason)
    }

    @Test
    fun `WorkoutMode 缺省NORMAL且未知降级`() {
        assertEquals(WorkoutMode.NORMAL, WorkoutMode.fromKey(null))
        assertEquals(WorkoutMode.NORMAL, WorkoutMode.fromKey(""))
        assertEquals(WorkoutMode.NORMAL, WorkoutMode.fromKey("UNKNOWN"))
        assertEquals(WorkoutMode.INTERVAL, WorkoutMode.fromKey("INTERVAL"))
    }
    @Test
    fun `空串持久化值加已知UUID返回有效类型而非OTHER`() {
        // 数据库空串=未人工修正，不是业务类型 OTHER
        val r = TrainingTypeResolver.resolve("", "杠铃卧推", "Bench Press", "weight_reps", "chest", false, "ex-chest-02")
        assertEquals(ExerciseRecordType.STRENGTH, r.type)
        assertEquals("uuid-map", r.reason)
    }

    @Test
    fun `空串加未知UUID降级OTHER并保留诊断`() {
        val r = TrainingTypeResolver.resolve("", "自建怪动作", "Custom", "", "", false, "custom-999")
        assertEquals(ExerciseRecordType.OTHER, r.type)
        assertNotNull(r.diagnostic)
    }

    @Test
    fun `用户修正优先于UUID映射且不被覆盖`() {
        // 用户把平板支撑(默认OTHER)改为CARDIO
        val r = TrainingTypeResolver.resolve("CARDIO", "平板支撑", "Plank", "duration", "core", false, "ex-core-01")
        assertEquals(ExerciseRecordType.CARDIO, r.type)
        assertEquals("persisted", r.reason)
    }

    @Test
    fun `错误持久化值安全降级到UUID映射`() {
        val r = TrainingTypeResolver.resolve("BOGUS_TYPE", "跑步", "Running", "distance_duration", "legs", false, "wg:running")
        assertEquals(ExerciseRecordType.CARDIO, r.type) // 不崩溃，回退映射
        assertEquals("uuid-map", r.reason)
    }

    @Test
    fun `映射版本标识与正式SHA固定`() {
        assertEquals("B1.1-2026-11-21", ExerciseRecordTypeMap.MAPPING_VERSION)
        assertEquals(
            "425893552b937fefd5b86d7ddd8c7299c735b02c8450adb10f288c6345878d8e",
            ExerciseRecordTypeMap.MAPPING_DOC_SHA256
        )
    }

    @Test
    fun `未映射与重复ID检查`() {
        val map = ExerciseRecordTypeMap.SEED_TYPES
        assertEquals(324, map.size)
        assertEquals(324, map.keys.distinct().size) // 无重复 UUID
        assertTrue(map.values.all { it in ExerciseRecordType.entries }) // 无未知类型
    }

}
