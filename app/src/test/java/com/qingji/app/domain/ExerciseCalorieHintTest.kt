package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** B1.1.1 P1：运动消耗三态提示，杜绝「没数据」与「算不出」都显示 0 kcal。 */
class ExerciseCalorieHintTest {

    private fun result(kcal: Double, skipped: Int) = WorkoutCalorieCalculator.Result(
        totalKcal = kcal, strengthKcal = 0.0, cardioKcal = 0.0,
        cardioItems = 0, notComputableItems = skipped,
        method = WorkoutCalorieCalculator.METHOD_CARDIO_ONLY
    )

    @Test
    fun `未录入实际数据时显示未录入而非0千卡`() {
        val msg = ExerciseCalorieHint.message(false, true, result(0.0, 0))
        assertEquals(ExerciseCalorieHint.State.NOT_RECORDED, ExerciseCalorieHint.state(false, true, result(0.0, 0)))
        assertEquals("；未录入可用于计算的实际运动数据，暂未计算消耗", msg)
    }

    @Test
    fun `已录入但缺体重时显示体重缺失`() {
        assertEquals("；未记录结算体重，本次未估算消耗",
            ExerciseCalorieHint.message(true, false, result(0.0, 0)))
    }

    @Test
    fun `已录入但缺参数时显示参数不足`() {
        val msg = ExerciseCalorieHint.message(true, true, result(0.0, 2))
        assertEquals(ExerciseCalorieHint.State.MISSING_PARAMS, ExerciseCalorieHint.state(true, true, result(0.0, 2)))
        assertEquals("；已录入 2 项但缺少可靠时长/参数，暂未计算消耗", msg)
    }

    @Test
    fun `已计算时显示估算热量`() {
        assertEquals(ExerciseCalorieHint.State.CALCULATED, ExerciseCalorieHint.state(true, true, result(320.0, 0)))
        val msg = ExerciseCalorieHint.message(true, true, result(320.0, 0))
        assertEquals(true, msg.contains("320"))
        assertEquals(true, msg.contains("估算"))
    }

    @Test
    fun `只有目标无实际录入仍判为未录入`() {
        // 只有 targetConfigJson、没有 actualJson 的草稿项：hasActualData=false
        assertEquals(ExerciseCalorieHint.State.NOT_RECORDED,
            ExerciseCalorieHint.state(false, true, result(0.0, 0)))
    }
}
