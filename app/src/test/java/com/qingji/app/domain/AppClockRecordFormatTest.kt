package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** QJ-RECORD-INPUT-UNIFIED-V3 / B3·B4：记录弹层日期/时间格式统一。 */
class AppClockRecordFormatTest {

    @Test
    fun shortDateLabelDropsYear() {
        assertEquals("11月26日", AppClock.formatDateLabel(LocalDate.of(2026, 11, 26)))
    }

    @Test
    fun fullDateLabelKeepsYear() {
        assertEquals("2026年11月26日", AppClock.formatDateFull(LocalDate.of(2026, 11, 26)))
    }

    @Test
    fun clockFormatUsesTwoDigits() {
        assertEquals("09:05", AppClock.formatClock(LocalTime.of(9, 5)))
        assertEquals("23:59", AppClock.formatClock(LocalTime.of(23, 59)))
    }

    @Test
    fun epochMillisConvertsThroughAppTimeZone() {
        val time = LocalTime.of(12, 30)
        val epoch = time.atDate(LocalDate.of(2026, 11, 26)).atZone(AppClock.zone).toInstant().toEpochMilli()
        assertEquals(time, AppClock.ofEpochMillisToLocalTime(epoch))
        assertEquals("12:30", AppClock.formatClock(epoch))
    }
}
