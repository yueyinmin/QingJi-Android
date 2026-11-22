package com.qingji.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 最终验收补证：UI 展示依赖矩阵（P0 §1）。
 *
 * 三条 UI 规则与依赖矩阵逐项对应：
 * - remainingText：剩余热量依赖 profile + diet + exercise，任一 FAILED → "--"；
 * - macroProgress：营养条依赖 diet + profile（目标来自 profile），任一 FAILED → null（"--"）；
 * - itemText：单项展示只依赖自身 state，FAILED → "--"，SUCCESS/EMPTY → 真值（含真 0）。
 *
 * 目标是证明：单项失败不影响无依赖关系的其他分项；多源同时失败时各自独立判定。
 */
class WidgetUiStateMatrixTest {

    private fun ok() = WidgetData(
        goalKcal = 2000, eatenKcal = 500, remainingKcal = 1500,
        protein = 80, proteinGoal = 120, carb = 150, carbGoal = 200,
        fat = 40, fatGoal = 50, water = 1200, waterGoal = 3200,
        latestWeight = 68.0,
        goalConfigured = true,
        profileState = ItemState.SUCCESS, dietState = ItemState.SUCCESS,
        exerciseState = ItemState.SUCCESS, waterState = ItemState.SUCCESS,
        weightState = ItemState.SUCCESS, workoutState = ItemState.SUCCESS,
        sleepState = ItemState.SUCCESS, workoutMinutes = 30, sleepMinutes = 420
    )

    // ---- 收尾 P0：profile EMPTY / SUCCESS 但目标无效 ----
    @Test
    fun `profile为EMPTY无档案_不得用默认2000展示伪造剩余热量`() {
        // observeProfile 查成功但无档案 → EMPTY；dailyKcalGoal 默认 0（还没算过）→ goalConfigured=false。
        val d = ok().copy(
            profileState = ItemState.EMPTY, goalConfigured = false,
            goalKcal = 2000, remainingKcal = 1500 // 内部默认占位值仍在，但必须被门控
        )
        assertFalse(d.canShowRemaining)
        assertEquals("--", remainingText(d))
    }

    @Test
    fun `profile为SUCCESS但目标无效_剩余热量显示--`() {
        val d = ok().copy(profileState = ItemState.SUCCESS, goalConfigured = false)
        assertFalse(d.canShowRemaining)
        assertEquals("--", remainingText(d))
    }

    @Test
    fun `目标不可用时_营养摄入有值也不绘制营养空轨道`() {
        val noGoal = ok().copy(goalConfigured = false, proteinGoal = 0, carbGoal = 0, fatGoal = 0)
        assertNull(macroProgress(noGoal.dietState, noGoal.profileState, noGoal.protein, noGoal.proteinGoal))
        assertNull(macroProgress(noGoal.dietState, noGoal.profileState, noGoal.carb, noGoal.carbGoal))
        assertNull(macroProgress(noGoal.dietState, noGoal.profileState, noGoal.fat, noGoal.fatGoal))
    }

    @Test
    fun `goalKcal为0的旧值也不得作为分母绘制进度`() {
        val d = ok().copy(proteinGoal = 0)
        assertNull(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal))
    }

    // ---- 依赖矩阵：profile ----
    @Test
    fun `profile失败_目标热量不可用_剩余热量显示--`() {
        val d = ok().copy(profileState = ItemState.FAILED)
        assertFalse(d.canShowRemaining)
        assertEquals("--", remainingText(d))
        // 营养条目标来自 profile，profile 失败必须一并不可用
        assertNull(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal))
    }

    // ---- 依赖矩阵：diet ----
    @Test
    fun `diet失败_营养条不可用_剩余热量显示--`() {
        val d = ok().copy(dietState = ItemState.FAILED)
        assertFalse(d.canShowRemaining)
        assertEquals("--", remainingText(d))
        assertNull(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal))
    }

    @Test
    fun `diet为EMPTY_营养条可用且真0不被吞`() {
        val d = ok().copy(dietState = ItemState.EMPTY, eatenKcal = 0, protein = 0, carb = 0, fat = 0)
        assertTrue(d.canShowRemaining)
        assertEquals(0f, macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal) ?: -1f, 0f)
    }

    // ---- 依赖矩阵：exercise ----
    @Test
    fun `exercise失败_剩余热量显示--但营养条与饮水不受影响`() {
        val d = ok().copy(exerciseState = ItemState.FAILED)
        assertFalse(d.canShowRemaining)
        assertEquals("--", remainingText(d))
        // 无依赖关系的分项照常可用
        assertTrue(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal)!! > 0f)
        assertEquals("1200 / 3200 ml", if (d.waterState == ItemState.FAILED) "--" else "${d.water} / ${d.waterGoal} ml")
    }

    // ---- 依赖矩阵：water / weight / workout / sleep（均不依赖剩余热量） ----
    @Test
    fun `water失败只影响饮水_不影响剩余热量与其他分项`() {
        val d = ok().copy(waterState = ItemState.FAILED)
        assertTrue(d.canShowRemaining)
        assertEquals("${d.remainingKcal}", remainingText(d))
        assertEquals("--", itemText(d.waterState, "${d.water} ml"))
        assertEquals("30 分", itemText(d.workoutState, "${d.workoutMinutes} 分"))
    }

    @Test
    fun `weight失败只影响体重_不影响训练睡眠剩余`() {
        val d = ok().copy(weightState = ItemState.FAILED)
        assertTrue(d.canShowRemaining)
        assertEquals("--", itemText(d.weightState, "68.0 kg"))
        assertEquals("30 分", itemText(d.workoutState, "${d.workoutMinutes} 分"))
        assertEquals("420 分", itemText(d.sleepState, "${d.sleepMinutes} 分"))
    }

    @Test
    fun `workout与sleep各自失败互不影响`() {
        val wFail = ok().copy(workoutState = ItemState.FAILED)
        assertEquals("--", itemText(wFail.workoutState, "30 分"))
        assertEquals("420 分", itemText(wFail.sleepState, "420 分"))
        val sFail = ok().copy(sleepState = ItemState.FAILED)
        assertEquals("30 分", itemText(sFail.workoutState, "30 分"))
        assertEquals("--", itemText(sFail.sleepState, "420 分"))
    }

    @Test
    fun `训练睡眠EMPTY时显示真实0`() {
        val d = ok().copy(
            workoutState = ItemState.EMPTY, workoutMinutes = 0,
            sleepState = ItemState.EMPTY, sleepMinutes = 0
        )
        assertEquals("0 分", itemText(d.workoutState, "${d.workoutMinutes} 分"))
        assertEquals("0 分", itemText(d.sleepState, "${d.sleepMinutes} 分"))
    }

    // ---- 多源同时失败 + 互不影响的组合测试 ----
    @Test
    fun `三剩余依赖同时失败_剩余热量--但四个独立分项仍为真值`() {
        val d = ok().copy(
            profileState = ItemState.FAILED,
            dietState = ItemState.FAILED,
            exerciseState = ItemState.FAILED
        )
        assertEquals("--", remainingText(d))
        assertNull(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal))
        // 不依赖剩余热量的分项不受影响
        assertEquals("1200 / 3200 ml", if (d.waterState == ItemState.FAILED) "--" else "${d.water} / ${d.waterGoal} ml")
        assertEquals("68.0 kg", itemText(d.weightState, "68.0 kg"))
        assertEquals("30 分", itemText(d.workoutState, "30 分"))
        assertEquals("420 分", itemText(d.sleepState, "420 分"))
    }

    @Test
    fun `全部分项失败_每项各自显示--_无一伪造默认0`() {
        val d = ok().copy(
            profileState = ItemState.FAILED, dietState = ItemState.FAILED,
            exerciseState = ItemState.FAILED, waterState = ItemState.FAILED,
            weightState = ItemState.FAILED, workoutState = ItemState.FAILED,
            sleepState = ItemState.FAILED
        )
        assertEquals("--", remainingText(d))
        assertNull(macroProgress(d.dietState, d.profileState, d.protein, d.proteinGoal))
        assertEquals("--", itemText(d.waterState, "${d.water} ml"))
        assertEquals("--", itemText(d.weightState, "68.0 kg"))
        assertEquals("--", itemText(d.workoutState, "30 分"))
        assertEquals("--", itemText(d.sleepState, "420 分"))
    }

    @Test
    fun `stale无快照时剩余热量显示--即使内部有默认值`() {
        val d = ok().copy(stale = true, hasSnapshot = false)
        assertEquals("--", remainingText(d))
    }
}
