package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 运动记录四种方式的换算口径，改公式前先看这里。 */
class ExerciseCalculatorTest {

    @Test
    fun minutesModeUsesMetTimesWeightTimesHours() {
        // MET 6 × 60kg × 0.5h = 180 千卡
        val r = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 30.0)
        assertEquals(30, r.minutes)
        assertEquals(180.0, r.kcal, 0.001)
    }

    @Test
    fun intensityScalesTheResult() {
        val light = ExerciseCalculator.estimate(
            6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 30.0, ExerciseCalculator.INTENSITY_LIGHT
        )
        val medium = ExerciseCalculator.estimate(
            6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 30.0, ExerciseCalculator.INTENSITY_MEDIUM
        )
        val high = ExerciseCalculator.estimate(
            6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 30.0, ExerciseCalculator.INTENSITY_HIGH
        )
        assertEquals(153.0, light.kcal, 0.001)
        assertEquals(180.0, medium.kcal, 0.001)
        assertEquals(207.0, high.kcal, 0.001)
        assertEquals(180.0, ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 30.0, null).kcal, 0.001)
    }

    @Test
    fun stepsModeConvertsToMinutesByStepRate() {
        // 5000 步 / 100 步每分钟 = 50 分钟 → 6 × 60 × 50/60 = 300 千卡
        val r = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_STEPS, 5000.0)
        assertEquals(50, r.minutes)
        assertEquals(300.0, r.kcal, 0.001)
    }

    @Test
    fun kmModeConvertsToMinutesByPace() {
        // 3 公里 × 10 分钟/公里 = 30 分钟 → 180 千卡
        val r = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_KM, 3.0)
        assertEquals(30, r.minutes)
        assertEquals(180.0, r.kcal, 0.001)
    }

    @Test
    fun kcalModeKeepsUserValueAndBackComputesMinutes() {
        // 每分钟 6 千卡，填 300 千卡 → 反推 50 分钟
        val r = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_KCAL, 300.0)
        assertEquals(300.0, r.kcal, 0.001)
        assertEquals(50, r.minutes)
    }

    @Test
    fun badInputsAreClampedNotCrashed() {
        val negative = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_MINUTES, -10.0)
        assertEquals(0, negative.minutes)
        assertEquals(0.0, negative.kcal, 0.001)

        val tiny = ExerciseCalculator.estimate(0.0, 0.0, ExerciseCalculator.MODE_MINUTES, 30.0)
        assertTrue(tiny.kcal > 0.0)

        val huge = ExerciseCalculator.estimate(6.0, 60.0, ExerciseCalculator.MODE_MINUTES, 99999.0)
        assertEquals(600, huge.minutes)
    }

    @Test
    fun presetsAndUnitsMatchEachMode() {
        assertEquals(listOf(10, 20, 30, 45, 60), ExerciseCalculator.presets(ExerciseCalculator.MODE_MINUTES))
        assertEquals(listOf(2000, 5000, 8000, 10000), ExerciseCalculator.presets(ExerciseCalculator.MODE_STEPS))
        assertEquals("分钟", ExerciseCalculator.unit(ExerciseCalculator.MODE_MINUTES))
        assertEquals("公里", ExerciseCalculator.unit(ExerciseCalculator.MODE_KM))
        assertEquals(4, ExerciseCalculator.modes().size)
    }
}
