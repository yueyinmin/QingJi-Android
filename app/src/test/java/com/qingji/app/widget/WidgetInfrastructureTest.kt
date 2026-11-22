package com.qingji.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Field

/** 阶段 A5：WidgetTarget 映射完整性 + Pin requestCode 唯一性。 */
@RunWith(RobolectricTestRunner::class)
class WidgetInfrastructureTest {

    @Test
    fun `所有WidgetTarget都有widgetOf映射`() {
        // 通过反射读取私有 widgetOf，避免为测试暴露生产 API。
        val f: Field = WidgetRefresher::class.java.getDeclaredField("widgetOf")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = f.get(WidgetRefresher) as Map<WidgetTarget, *>
        WidgetTarget.entries.forEach { target ->
            assertTrue("$target 缺少 widgetOf 映射（会被 mapNotNull 静默丢弃）", map.containsKey(target))
        }
        assertEquals(WidgetTarget.entries.size, map.size)
    }

    @Test
    fun `Pin requestCode真实PendingIntent唯一且保留旧值`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actual = WidgetTarget.entries.associateWith { target ->
            shadowOf(WidgetPinReceiver.pendingIntent(context, target)).requestCode
        }
        assertEquals(1, actual[WidgetTarget.QUICK])
        assertEquals(2, actual[WidgetTarget.OVERVIEW])
        assertEquals(3, actual[WidgetTarget.WEIGHT])
        assertEquals(4, actual[WidgetTarget.BODY_DATA])
        assertEquals(actual.size, actual.values.distinct().size) // 唯一
        assertEquals(0, shadowOf(WidgetPinReceiver.pendingIntent(context, null)).requestCode)
    }

    @Test
    fun `ALL自动包含BODY_DATA`() {
        assertTrue(WidgetRefresher.ALL.contains(WidgetTarget.BODY_DATA))
    }
}
