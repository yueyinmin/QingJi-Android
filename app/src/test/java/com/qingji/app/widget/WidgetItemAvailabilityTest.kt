package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验收补充工单 P0 §6：分项读取失败测试 / 正常无记录与读取失败区分 /
 * 热量计算依赖缺失时不显示伪剩余值。
 *
 * loadFresh 内部每项用 Outcome 三态映射（异常→FAILED、null→EMPTY、值→SUCCESS），
 * 这里对承载这些状态的 WidgetData 契约做穷举验证；Outcome 的三态语义由
 * WidgetSnapshotRepo.outcomeOf 在集成路径使用，toJson/fromJson 保证状态不丢失。
 */
class WidgetItemAvailabilityTest {

    private fun base() = WidgetData(
        profileState = ItemState.SUCCESS,
        dietState = ItemState.SUCCESS,
        exerciseState = ItemState.SUCCESS,
        waterState = ItemState.SUCCESS,
        weightState = ItemState.SUCCESS,
        remainingKcal = 1234
    )

    @Test
    fun `全部可用时剩余热量可展示`() {
        assertTrue(base().canShowRemaining)
    }

    @Test
    fun `资料读取失败时剩余热量不可展示`() {
        assertFalse(base().copy(profileState = ItemState.FAILED).canShowRemaining)
    }

    @Test
    fun `饮食读取失败时剩余热量不可展示`() {
        assertFalse(base().copy(dietState = ItemState.FAILED).canShowRemaining)
    }

    @Test
    fun `运动消耗读取失败时剩余热量不可展示`() {
        assertFalse(base().copy(exerciseState = ItemState.FAILED).canShowRemaining)
    }

    @Test
    fun `饮水或体重失败不影响剩余热量展示`() {
        // 剩余热量只依赖 资料/饮食/运动；饮水/体重失败独立，不拖垮主数字。
        val d = base().copy(waterState = ItemState.FAILED, weightState = ItemState.FAILED)
        assertTrue(d.canShowRemaining)
    }

    @Test
    fun `正常无记录EMPTY仍视为可展示`() {
        val d = base().copy(
            dietState = ItemState.EMPTY, eatenKcal = 0,
            waterState = ItemState.EMPTY, water = 0,
            exerciseState = ItemState.EMPTY
        )
        assertTrue(d.canShowRemaining)
        assertEquals(0, d.eatenKcal)
        assertEquals(0, d.water)
    }

    @Test
    fun `剩余热量的伪值即便存在也因canShowRemaining为false被UI拒绝`() {
        // loadFresh 在依赖缺失时把 remainingKcal 置 0；旧快照即使在，canShowRemaining 也为 false。
        val d = base().copy(exerciseState = ItemState.FAILED, remainingKcal = 2000)
        assertFalse(d.canShowRemaining)
        // UI 的 remainingText() 在 canShowRemaining=false 时返回 "--"，不会显示 2000。
    }

    @Test
    fun `训练睡眠状态互不影响`() {
        val d = base().copy(workoutState = ItemState.FAILED, sleepState = ItemState.SUCCESS)
        assertFalse(d.workoutAvailable)
        assertTrue(d.sleepAvailable)
        val d2 = base().copy(workoutState = ItemState.EMPTY, sleepState = ItemState.FAILED)
        assertTrue(d2.workoutAvailable) // EMPTY 是真 0，可用
        assertFalse(d2.sleepAvailable)
    }
}
