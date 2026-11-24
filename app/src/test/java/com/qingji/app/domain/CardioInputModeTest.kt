package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 步行类有氧录入方式与步数持久化的单测。 */
class CardioInputModeTest {

    @Test
    fun walkingSupportsAllThreeModes() {
        listOf("wg:walking", "wg:running", "wg:cycling", "wg:hiking", "wg:stair-climber", "wg:treadmill-incline-walk")
            .forEach { assertTrue(it, CardioInputMode.supportsDistanceModes(it)) }
    }

    @Test
    fun machineCardioOnlySupportsMinutes() {
        listOf("wg:rowing", "wg:elliptical", "wg:assault-bike", "wg:jump-rope", "wg:skierg")
            .forEach { assertFalse(it, CardioInputMode.supportsDistanceModes(it)) }
    }

    @Test
    fun convertsStepsAndKmToMinutes() {
        assertEquals(30.0, CardioInputMode.toMinutes(CardioInputMode.MODE_STEPS, 3000.0), 0.001)
        assertEquals(30.0, CardioInputMode.toMinutes(CardioInputMode.MODE_KM, 3.0), 0.001)
        assertEquals(12.5, CardioInputMode.toMinutes(CardioInputMode.MODE_MINUTES, 12.5), 0.001)
    }

    @Test
    fun stepsSurviveJsonRoundTrip() {
        val original = WorkoutItem.Cardio(
            itemId = "k1", exerciseUuid = "wg:walking", name = "步行", order = 0,
            durationSec = 1800L, steps = 3000, distanceMeters = null
        )
        val json = WorkoutItem.toJson(original)
        val back = WorkoutItem.fromJson(json) as WorkoutItem.Cardio
        assertEquals(3000, back.steps)
        assertEquals(1800L, back.durationSec)
    }

    @Test
    fun oldJsonWithoutStepsStillLoadsWithZero() {
        // 旧版 JSON（无 st 键）必须兼容，步数读 0
        val legacy = "{\"v\":1,\"id\":\"k1\",\"eu\":\"wg:walking\",\"n\":\"步行\",\"t\":\"CARDIO\",\"o\":0," +
            "\"m\":\"NORMAL\",\"nt\":\"\",\"s\":\"\",\"d\":1800,\"di\":2000.0,\"k\":0.0,\"km\":\"\",\"kv\":\"\"}"
        val parsed = WorkoutItem.fromJson(legacy) as WorkoutItem.Cardio
        assertEquals(0, parsed.steps)
        assertEquals(2000.0, parsed.distanceMeters ?: 0.0, 0.001)
    }
}
