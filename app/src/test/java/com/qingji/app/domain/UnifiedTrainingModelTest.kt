package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一运动体系 B1：等长 JSON 兼容（工单二-5 五点）+ WorkoutItem 五类模型。 */
class UnifiedTrainingModelTest {

    // ---------- 等长 WorkoutSet ds key 兼容 ----------

    @Test
    fun `旧版WorkoutSet JSON能读取且durationSec为null`() {
        // 旧结构：只有 w/r/rpe/rest/done/tr/tw，无 ds
        val old = """[{"w":40.5,"r":10,"rpe":7,"rest":60,"done":true,"tr":12,"tw":45.0}]"""
        val sets = TrainingMath.decodeSets(old)
        assertEquals(1, sets.size)
        assertEquals(40.5, sets[0].weightKg, 0.001)
        assertEquals(10, sets[0].reps)
        assertEquals(12, sets[0].targetReps)
        assertNull(sets[0].durationSec) // 旧数据不补造 0
    }

    @Test
    fun `新版durationSec能独立保存并读回`() {
        val sets = listOf(WorkoutSet(reps = 0, durationSec = 45))
        val json = TrainingMath.encodeSets(sets)
        assertTrue(json.contains("\"ds\":45"))
        val back = TrainingMath.decodeSets(json)
        assertEquals(45, back[0].durationSec)
        assertEquals(0, back[0].reps)
    }

    @Test
    fun `旧数据解码后重新保存不丢字段`() {
        val old = """[{"w":20.0,"r":15,"rpe":6,"rest":30,"done":false}]"""
        val sets = TrainingMath.decodeSets(old)
        val resaved = TrainingMath.decodeSets(TrainingMath.encodeSets(sets))
        assertEquals(20.0, resaved[0].weightKg, 0.001)
        assertEquals(15, resaved[0].reps)
        assertEquals(6, resaved[0].rpe)
        assertEquals(30, resaved[0].restSec)
        assertEquals(false, resaved[0].completed)
        assertNull(resaved[0].durationSec)
    }

    @Test
    fun `坏ds值降级null且普通组次不受影响`() {
        val bad = """[{"w":10,"r":5,"ds":-1},{"w":12,"r":8}]"""
        val sets = TrainingMath.decodeSets(bad)
        assertNull(sets[0].durationSec)
        assertNull(sets[1].durationSec)
        assertEquals(5, sets[0].reps)
        assertEquals(8, sets[1].reps)
    }

    // ---------- WorkoutItem 五类 ----------

    @Test
    fun `五类均含完整身份字段且往返一致`() {
        val items = listOf(
            WorkoutItem.Strength("i1", "ex-chest-02", "杠铃卧推", 0, sets = listOf(WorkoutSet(40.0, 10))),
            WorkoutItem.Cardio("i2", "wg:running", "跑步", 1, durationSec = 1200, distanceMeters = 5000.0, calculatedKcal = 150.0),
            WorkoutItem.Hiit("i3", "seed-hiit", "HIIT", 2, workSec = 30, restSec = 30, rounds = 8, calculatedKcal = 120.0),
            WorkoutItem.Flexibility("i4", "wg:cobra", "眼镜蛇式", 3, durationSec = 60, content = "拉伸腰部"),
            WorkoutItem.Other("i5", "custom-1", "自定义", 4, durationSec = 300, reps = null)
        )
        items.forEach { item ->
            val back = WorkoutItem.fromJson(WorkoutItem.toJson(item))
            assertTrue("往返失败: ${item.name}", back != null)
            assertEquals(item.itemId, back!!.itemId)
            assertEquals(item.exerciseUuid, back.exerciseUuid)
            assertEquals(item.name, back.name)
            assertEquals(item.recordType, back.recordType)
            assertEquals(item.order, back.order)
            assertEquals(item.mode, back.mode)
            assertEquals(item.sessionUuid, back.sessionUuid)
        }
    }

    @Test
    fun `同动作重复以itemId区分不覆盖`() {
        val a = WorkoutItem.Strength("id-a", "ex-chest-02", "杠铃卧推", 0)
        val b = WorkoutItem.Strength("id-b", "ex-chest-02", "杠铃卧推", 1)
        assertEquals("同一 UUID", a.exerciseUuid, b.exerciseUuid)
        assertTrue("itemId 必须不同", a.itemId != b.itemId)
        assertTrue("顺序必须不同", a.order != b.order)
    }

    @Test
    fun `未知类型与损坏JSON降级安全`() {
        val unknown = """{"v":1,"id":"x","eu":"e","n":"怪动作","t":"UNKNOWN_TYPE","o":0}"""
        val parsed = WorkoutItem.fromJson(unknown)
        assertEquals(ExerciseRecordType.OTHER, parsed!!.recordType)
        assertEquals(WorkoutMode.NORMAL, parsed.mode)
        assertNull(WorkoutItem.fromJson("not-json"))
        assertNull(WorkoutItem.fromJson(null))
    }

    @Test
    fun `旧版无schemaVersion的JSON仍可读取`() {
        val legacy = """{"id":"old","eu":"wg:running","n":"跑步","t":"CARDIO","o":0,"d":600}"""
        val parsed = WorkoutItem.fromJson(legacy)
        assertTrue(parsed is WorkoutItem.Cardio)
        assertEquals(600L, (parsed as WorkoutItem.Cardio).durationSec)
    }
}
