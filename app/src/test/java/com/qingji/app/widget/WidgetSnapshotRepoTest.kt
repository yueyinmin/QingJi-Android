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
 * 快照严格校验 / 新鲜度 / 分项可用状态单测（W-01/03/07 + 验收补充工单 P0/P1）。
 */
class WidgetSnapshotRepoTest {

    private fun sample() = WidgetData(
        goalKcal = 1800, eatenKcal = 700, remainingKcal = 1450,
        protein = 80, proteinGoal = 120, carb = 150, carbGoal = 200,
        fat = 40, fatGoal = 50, water = 1200, waterGoal = 3200,
        latestWeight = 68.5, unit = "kg",
        profileState = ItemState.SUCCESS,
        dietState = ItemState.SUCCESS,
        waterState = ItemState.SUCCESS,
        exerciseState = ItemState.SUCCESS,
        weightState = ItemState.SUCCESS,
        workoutState = ItemState.SUCCESS,
        sleepState = ItemState.SUCCESS,
        workoutMinutes = 45, sleepMinutes = 420,
        hasSnapshot = true,
        dataDate = LocalDate.parse("2026-11-20"),
        zoneId = "Asia/Shanghai", snapshotAt = 1761000000000L
    )

    private fun assertRejected(mutate: (org.json.JSONObject) -> Unit) {
        try {
            val json = WidgetSnapshotRepo.toJson(sample())
            mutate(json)
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
        assertTrue(r.canShowRemaining)
        assertEquals(ItemState.SUCCESS, r.workoutState)
        assertEquals(ItemState.SUCCESS, r.sleepState)
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

    /** P0/工单第 9 条：旧 schema 1 快照必须安全作废，不静默迁移。 */
    @Test
    fun `旧schema1快照被安全作废`() = assertRejected { it.put("schemaVersion", 1) }

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

    /** P0/工单第 8 条：分项状态字段缺失必须作废（不能回落默认状态）。 */
    @Test
    fun `分项状态字段缺失被拒绝`() = assertRejected { it.remove("dietState") }

    @Test
    fun `分项状态非法值被拒绝`() = assertRejected { it.put("waterState", "MAYBE") }

    @Test
    fun `无体重用NULL且往返保留`() {
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(sample().copy(latestWeight = null)))
        assertNull(r.latestWeight)
    }

    // ---- P0：分项三态往返（SUCCESS / EMPTY / FAILED） ----
    @Test
    fun `正常无记录EMPTY往返保留且真0不被吞`() {
        val d = sample().copy(
            dietState = ItemState.EMPTY, eatenKcal = 0, protein = 0,
            waterState = ItemState.EMPTY, water = 0
        )
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertEquals(ItemState.EMPTY, r.dietState)
        assertEquals(ItemState.EMPTY, r.waterState)
        assertEquals(0, r.eatenKcal)
        assertEquals(0, r.water)
        assertTrue(r.canShowRemaining) // EMPTY 是可用状态，剩余热量可展示
    }

    @Test
    fun `读取失败FAILED往返保留且剩余热量不可展示`() {
        val d = sample().copy(exerciseState = ItemState.FAILED)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertEquals(ItemState.FAILED, r.exerciseState)
        assertFalse(r.canShowRemaining)
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

    // ---- P1：训练/睡眠独立可用状态 ----
    @Test
    fun `训练失败睡眠成功时状态分别保留`() {
        val d = sample().copy(workoutState = ItemState.FAILED, sleepState = ItemState.SUCCESS, sleepMinutes = 480)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertEquals(ItemState.FAILED, r.workoutState)
        assertEquals(ItemState.SUCCESS, r.sleepState)
        assertFalse(r.workoutAvailable)
        assertTrue(r.sleepAvailable)
        assertEquals(480, r.sleepMinutes)
    }

    @Test
    fun `睡眠失败训练成功时状态分别保留`() {
        val d = sample().copy(workoutState = ItemState.SUCCESS, sleepState = ItemState.FAILED)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertTrue(r.workoutAvailable)
        assertFalse(r.sleepAvailable)
    }

    @Test
    fun `两项都失败仍保留失败状态不伪装为0`() {
        val d = sample().copy(workoutState = ItemState.FAILED, sleepState = ItemState.FAILED)
        val r = WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(d))
        assertFalse(r.workoutAvailable)
        assertFalse(r.sleepAvailable)
    }

    @Test
    fun `正常无记录EMPTY与读取失败FAILED区分`() {
        val empty = sample().copy(workoutState = ItemState.EMPTY, workoutMinutes = 0)
        val failed = sample().copy(workoutState = ItemState.FAILED, workoutMinutes = 0)
        assertTrue(empty.workoutAvailable)   // 真 0 分，可展示
        assertFalse(failed.workoutAvailable) // 失败，UI 必须 --
        assertEquals(ItemState.EMPTY, WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(empty)).workoutState)
        assertEquals(ItemState.FAILED, WidgetSnapshotRepo.fromJson(WidgetSnapshotRepo.toJson(failed)).workoutState)
    }
}
