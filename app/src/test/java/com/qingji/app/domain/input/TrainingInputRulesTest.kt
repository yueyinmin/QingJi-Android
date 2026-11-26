package com.qingji.app.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QJ-RECORD-INPUT-UNIFIED-V3 / B2：单位回填回归测试。
 * 重点锁死「秒当分钟 / 米当公里」这两个会静默损坏历史数据的缺陷。
 */
class TrainingInputRulesTest {

    @Test
    fun thirtyMinutesIsStoredAs1800SecondsAndShownAs30() {
        assertEquals(30.0, TrainingInputRules.secondsToMinutes(1800L), 0.001)
        assertEquals("30", TrainingInputRules.secondsToMinutesText(1800L))
        assertEquals(1800L, TrainingInputRules.minutesToSeconds(30.0))
    }

    @Test
    fun nonRoundSecondsDoNotBecomeTenHours() {
        // 旧实现把 1850 秒直接塞进分钟框，被 600 上限截成 600 分钟 = 36000 秒
        assertEquals("31", TrainingInputRules.secondsToMinutesText(1850L))
        assertEquals(1800L, TrainingInputRules.minutesToSeconds(30.9))
    }

    @Test
    fun minutesAreClampedByExistingBusinessLimit() {
        assertEquals(36000L, TrainingInputRules.minutesToSeconds(9999.0))
        assertEquals(0L, TrainingInputRules.minutesToSeconds(-5.0))
    }

    @Test
    fun fiveThousandMetersIsFiveKilometersNotFiveThousandKilometers() {
        assertEquals(5.0, TrainingInputRules.metersToKm(5000.0), 0.001)
        assertEquals("5", TrainingInputRules.metersToKmText(5000.0))
        assertEquals(5000.0, TrainingInputRules.kmToMeters(5.0) ?: 0.0, 0.001)
    }

    @Test
    fun kilometerTextKeepsAtMostThreeDecimals() {
        assertEquals("5.123", TrainingInputRules.metersToKmText(5123.0))
    }

    @Test
    fun invalidKilometerValuesAreDroppedAsBefore() {
        assertNull(TrainingInputRules.kmToMeters(null))
        assertNull(TrainingInputRules.kmToMeters(0.0))
        assertNull(TrainingInputRules.kmToMeters(-1.0))
        assertNull(TrainingInputRules.kmToMeters(501.0))
    }
}
