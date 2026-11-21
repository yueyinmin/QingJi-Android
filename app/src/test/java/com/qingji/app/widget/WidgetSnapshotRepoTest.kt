package com.qingji.app.widget

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * 快照严格校验 / 新鲜度 / 分项可用状态单测（W-01/W-03/W-07）。
 */
class WidgetSnapshotRepoTest {

    private fun sample() = WidgetData(
        goalKcal = 1800, eatenKcal = 700, remainingKcal = 1450,
        protein = 80, proteinGoal = 120, carb = 150, carbGoal = 200,
        fat = 40, fatGoal = 50, water = 1200, waterGoal = 3200,
        latestWeight = 68.5, unit = "kg",
        workoutAvailable = true, sleepAvailable = true,
        workoutMinutes = 45, sleepMinutes = 420,
        hasSnapshot = true,
        dataDate = LocalDate.parse("2026-11-20"),
        zoneId = "Asia/Shanghai", snapshotAt = 1761000000000L
    )

    private fun assertRejected(mutate: (org.json.JSONObject) -> Unit) {
        try {
            val json = WidgetSnapshotRepo.toJson(sample())
            mutate(json) // 非有限数在 put 阶段就会被 JSON 层拒绝
            WidgetSnapshotRepo.fromJson(json)
            fail("损坏快照应被拒绝")
        } catch (e: JSONException) {
            // 预期：整份作废（put 阶段或解析阶段）
        } catch (e: Exception) {
            // IllegalArgumentException（范围校验）也算拒绝
        }
    }

    // ---- W-01 版本与严格字段校验 ----
    @Test
    fun `合法快照往返保留全部字段`() {
        val d = sample()
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertEquals(1800, r.goalKcal)
        assertEquals(1450, r.remainingKcal)
        assertEquals(3200, r.waterGoal)
        assertEquals(68.5, r.latestWeight!!, 0.01)
        assertTrue(r.workoutAvailable)
        assertTrue(r.sleepAvailable)
        assertEquals(45, r.workoutMinutes)
        assertEquals(420, r.sleepMinutes)
        assertEquals(LocalDate.parse("2026-11-20"), r.dataDate)
        assertEquals("Asia/Shanghai", r.zoneId)
    }

    @Test
    fun `缺少schemaVersion被拒绝`() = assertRejected { it.remove("schemaVersion") }

    @Test
    fun `schemaVersion不匹配被拒绝`() = assertRejected {
        it.put("schemaVersion", WidgetData.SCHEMA_VERSION + 1)
    }

    @Test
    fun `关键字段缺失被拒绝`() = assertRejected { it.remove("goalKcal") }

    @Test
    fun `字段类型错误被拒绝`() = assertRejected { it.put("water", "not-a-number") }

    @Test
    fun `负数非法数值被拒绝`() = assertRejected { it.put("water", -1) }

    @Test
    fun `NaN体重被拒绝`() = assertRejected { it.put("latestWeight", Double.NaN) }

    @Test
    fun `Infinity体重被拒绝`() = assertRejected { it.put("latestWeight", Double.POSITIVE_INFINITY) }

    @Test
    fun `空日期被拒绝`() = assertRejected { it.put("dataDate", "") }

    @Test
    fun `损坏日期被拒绝`() = assertRejected { it.put("dataDate", "not-a-date") }

    @Test
    fun `空时区被拒绝`() = assertRejected { it.put("zoneId", "") }

    @Test
    fun `无体重用NULL且往返保留`() {
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(sample().copy(latestWeight = null)))
        assertNull(r.latestWeight)
    }

    // ---- W-03 新鲜度 ----
    @Test
    fun `新鲜快照判定FRESH`() {
        val d = sample().copy(snapshotAt = 1000L)
        assertEquals(WidgetSnapshotRepo.Freshness.FRESH, WidgetSnapshotRepo.freshnessOf(d, 1000L + 60_000L))
    }

    @Test
    fun `阈值边界判定`() {
        val d = sample().copy(snapshotAt = 1000L)
        val atThreshold = 1000L + WidgetSnapshotRepo.FRESH_THRESHOLD_MS
        assertEquals(WidgetSnapshotRepo.Freshness.FRESH, WidgetSnapshotRepo.freshnessOf(d, atThreshold))
        assertEquals(WidgetSnapshotRepo.Freshness.STALE, WidgetSnapshotRepo.freshnessOf(d, atThreshold + 1))
    }

    @Test
    fun `过期快照判定STALE但仍可展示`() {
        val d = sample().copy(snapshotAt = 1000L)
        assertEquals(WidgetSnapshotRepo.Freshness.STALE, WidgetSnapshotRepo.freshnessOf(d, 1000L + 3_600_000L))
    }

    @Test
    fun `无快照判定NO_SNAPSHOT`() {
        assertEquals(
            WidgetSnapshotRepo.Freshness.NO_SNAPSHOT,
            WidgetSnapshotRepo.freshnessOf(WidgetData(stale = true), 1000L)
        )
    }

    // ---- W-07 分项可用状态 ----
    @Test
    fun `训练失败睡眠成功时状态分别保留`() {
        val d = sample().copy(workoutAvailable = false, sleepAvailable = true, sleepMinutes = 480)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertFalse(r.workoutAvailable)
        assertTrue(r.sleepAvailable)
        assertEquals(480, r.sleepMinutes)
    }

    @Test
    fun `两项都失败仍保留失败状态不伪装为0`() {
        val d = sample().copy(workoutAvailable = false, sleepAvailable = false)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertFalse(r.workoutAvailable)
        assertFalse(r.sleepAvailable)
    }
}
