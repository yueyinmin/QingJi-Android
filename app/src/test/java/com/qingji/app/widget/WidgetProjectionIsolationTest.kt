package com.qingji.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 投影独立性验证（不是真实 DB C 层测试）。
 * 验证两个 Key/schema/快照/invalidate 互不污染，且失效只影响目标 Key。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetProjectionIsolationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("qingji_widget_snapshot", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `两个投影schema独立`() {
        assertEquals(2, TodayRemainingWidgetData.SCHEMA_VERSION)
        assertEquals(1, BodyDataWidgetData.SCHEMA_VERSION)
        assertNotEquals(TodayRemainingWidgetData.SCHEMA_VERSION, WidgetData.SCHEMA_VERSION)
    }

    @Test
    fun `invalidate只失效目标Key`() {
        val prefs = context.getSharedPreferences("qingji_widget_snapshot", Context.MODE_PRIVATE)
        prefs.edit().putString("today_remaining_v1", "T").putString("body_data_v1", "B").commit()

        WidgetSnapshotRepo.invalidateProjections(context, setOf(WidgetTarget.OVERVIEW))

        assertTrue(!prefs.contains("today_remaining_v1")) // 今日被清
        assertEquals("B", prefs.getString("body_data_v1", null)) // 身体不受影响

        prefs.edit().putString("today_remaining_v1", "T2").commit()
        WidgetSnapshotRepo.invalidateProjections(context, setOf(WidgetTarget.BODY_DATA))
        assertEquals("T2", prefs.getString("today_remaining_v1", null))
        assertTrue(!prefs.contains("body_data_v1"))
    }

    @Test
    fun `无关target不触发任何投影失效`() {
        val prefs = context.getSharedPreferences("qingji_widget_snapshot", Context.MODE_PRIVATE)
        prefs.edit().putString("today_remaining_v1", "T").putString("body_data_v1", "B").commit()

        WidgetSnapshotRepo.invalidateProjections(context, setOf(WidgetTarget.QUICK, WidgetTarget.WEIGHT))

        assertEquals("T", prefs.getString("today_remaining_v1", null))
        assertEquals("B", prefs.getString("body_data_v1", null))
    }

    @Test
    fun `两个投影snapshotAt与stale互相独立`() {
        val t1 = TodayRemainingWidgetData(snapshotAt = 100L, stale = true, hasSnapshot = true)
        val b1 = BodyDataWidgetData(snapshotAt = 200L, stale = false, hasSnapshot = true)
        assertEquals(100L, t1.snapshotAt)
        assertTrue(t1.stale)
        assertEquals(200L, b1.snapshotAt)
        assertTrue(!b1.stale)
        // 改一个不影响另一个
        val t2 = t1.copy(stale = false, snapshotAt = 300L)
        assertTrue(!t2.stale)
        assertTrue(b1.stale == false && b1.snapshotAt == 200L)
    }

    @Test
    fun `一个投影FAILED不影响另一个可用性`() {
        val t = TodayRemainingWidgetData(
            profileState = ItemState.FAILED, hasSnapshot = true, goalConfigured = true
        )
        val b = BodyDataWidgetData(bodyState = ItemState.SUCCESS, hasSnapshot = true)
        assertTrue(!t.canShowRemaining)
        assertTrue(b.canShowBody)
    }
}
