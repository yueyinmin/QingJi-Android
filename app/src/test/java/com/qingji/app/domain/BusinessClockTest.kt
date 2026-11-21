package com.qingji.app.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.prefs.SettingsKeys
import com.qingji.app.data.prefs.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/**
 * P1-05：业务时区在后台/独立入口的恢复。
 * 验证「系统时区 ≠ 用户业务时区」时，日历与 today() 都按业务时区。
 */
@RunWith(RobolectricTestRunner::class)
class BusinessClockTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 模拟用户在海外、系统时区与记录时区不同的场景
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        runBlocking { SettingsStore.set(context, SettingsKeys.TIME_ZONE, "Asia/Shanghai") }
    }

    @Test
    fun `挂起恢复后AppClock使用业务时区`() = runBlocking {
        AppClock.setZone("UTC") // 模拟尚未恢复
        BusinessClock.restore(context)
        assertEquals("Asia/Shanghai", AppClock.zone.id)
    }

    @Test
    fun `阻塞恢复适用于后台同步入口`() {
        AppClock.setZone("UTC")
        BusinessClock.restoreBlocking(context)
        assertEquals("Asia/Shanghai", AppClock.zone.id)
    }

    @Test
    fun `闹钟Calendar按业务时区而非系统时区`() {
        AppClock.setZone("UTC")
        val cal = BusinessClock.calendarForBusinessZone(context, 9, 30)
        assertEquals(9, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(java.util.Calendar.MINUTE))
        assertEquals("Asia/Shanghai", cal.timeZone.id)
    }

    @Test
    fun `非法时区配置不崩溃且保持现状`() = runBlocking {
        SettingsStore.set(context, SettingsKeys.TIME_ZONE, "Not/AZone")
        AppClock.setZone("UTC")
        BusinessClock.restore(context)
        assertEquals("UTC", AppClock.zone.id)
    }
}
