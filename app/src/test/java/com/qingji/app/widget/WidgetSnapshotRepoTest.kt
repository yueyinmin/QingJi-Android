package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 小组件快照序列化测试（阶段 1）：
 * 快照带业务日期/时区/时间，且「剩余热量含补偿」「饮水目标设置值」等口径字段能完整往返。
 */
class WidgetSnapshotRepoTest {

    private fun sample() = WidgetData(
        goalKcal = 1800,
        eatenKcal = 700,
        remainingKcal = 1450,   // 1800-700+350(运动补偿)
        protein = 80, proteinGoal = 120,
        carb = 150, carbGoal = 200,
        fat = 40, fatGoal = 50,
        water = 1200, waterGoal = 3200, // 设置目标，非档案 2000
        latestWeight = 68.5,
        unit = "kg",
        workoutMinutes = 45,
        sleepMinutes = 420,
        hasSnapshot = true,
        dataDate = LocalDate.parse("2026-11-20"),
        zoneId = "Asia/Shanghai",
        snapshotAt = 1761000000000L
    )

    @Test
    fun `快照往返保留全部口径字段`() {
        val d = sample()
        val restored = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertEquals(1800, restored.goalKcal)
        assertEquals(700, restored.eatenKcal)
        assertEquals(1450, restored.remainingKcal)
        assertEquals(1200, restored.water)
        assertEquals(3200, restored.waterGoal)
        assertEquals(45, restored.workoutMinutes)
        assertEquals(420, restored.sleepMinutes)
        assertEquals(68.5, restored.latestWeight!!, 0.01)
        assertEquals(LocalDate.parse("2026-11-20"), restored.dataDate)
        assertEquals("Asia/Shanghai", restored.zoneId)
        assertEquals(1761000000000L, restored.snapshotAt)
        assertTrue(restored.hasSnapshot)
    }

    @Test
    fun `无体重快照用NULL而非伪造数值`() {
        val d = sample().copy(latestWeight = null)
        val restored = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertNull(restored.latestWeight)
    }

    @Test
    fun `空日期解析为null以便跨日校验丢弃`() {
        val json = WidgetSnapshotRepo.toJson(sample())
        json.put("dataDate", "")
        assertNull(WidgetSnapshotRepo.fromJson(json).dataDate)
    }

    @Test
    fun `损坏日期解析不抛异常返回null`() {
        val json = WidgetSnapshotRepo.toJson(sample())
        json.put("dataDate", "not-a-date")
        assertNull(WidgetSnapshotRepo.fromJson(json).dataDate)
    }
}
