package com.qingji.app.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 阶段 A5：WidgetSnapshotRepo 取消传播真实测试（经 queryHookForTest 注入）。 */
@RunWith(RobolectricTestRunner::class)
class WidgetCancellationTest {

    @Test
    fun `查询抛CancellationException时load向上抛出而非降级`() = runBlocking {
        WidgetSnapshotRepo.queryHookForTest = { throw CancellationException("cancelled") }
        try {
            WidgetSnapshotRepo.loadFresh(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            org.junit.Assert.fail("取消应向上传播")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        } finally {
            WidgetSnapshotRepo.queryHookForTest = null
        }
    }

    @Test
    fun `普通业务异常不中断整体降级为FAILED`() = runBlocking {
        WidgetSnapshotRepo.queryHookForTest = { throw IllegalStateException("db error") }
        val d = WidgetSnapshotRepo.loadFresh(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        // 单项业务失败不应导致整份数据不可用，通过 state 表达 -- 而非崩溃
        assertTrue(d.profileState == ItemState.FAILED || d.hasSnapshot)
        WidgetSnapshotRepo.queryHookForTest = null
    }
}
