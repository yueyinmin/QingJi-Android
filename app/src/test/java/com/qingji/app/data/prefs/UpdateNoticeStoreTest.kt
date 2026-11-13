package com.qingji.app.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 「升级弹一次、看过就不再弹」的记忆逻辑。 */
@RunWith(RobolectricTestRunner::class)
class UpdateNoticeStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个用例从「干净安装」状态开始
        context.getSharedPreferences("app_version_notice", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `全新安装或从没弹过提示的老版本升级 都弹当前版本`() {
        // 1.0.9 及以前根本没有 last_seen 键，覆盖安装后必须能看到 1.0.10 的更新内容
        val list = UpdateNoticeStore(context).pendingReleases(110)
        assertEquals(1, list.size)
        assertEquals("1.0.10", list.first().versionName)
        // 没点「我知道了」之前，重开还会弹，不会静默变成已读
        assertEquals(1, UpdateNoticeStore(context).pendingReleases(110).size)
        UpdateNoticeStore(context).markSeen(110)
        assertTrue(UpdateNoticeStore(context).pendingReleases(110).isEmpty())
    }

    @Test
    fun `升级后弹一次 点过我知道了就不再弹`() {
        val store = UpdateNoticeStore(context)
        store.markSeen(109)

        assertEquals(1, store.pendingReleases(110).size)
        // 还没点「我知道了」：中途杀进程回来仍然会弹
        assertEquals(1, UpdateNoticeStore(context).pendingReleases(110).size)

        store.markSeen(110)
        assertTrue(UpdateNoticeStore(context).pendingReleases(110).isEmpty())
    }

    @Test
    fun `连跳几个版本时一次列全`() {
        val store = UpdateNoticeStore(context)
        store.markSeen(108)
        assertEquals(2, store.pendingReleases(110).size)
    }
}
