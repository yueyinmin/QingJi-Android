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
    fun `全新安装不弹更新提示`() {
        assertTrue(UpdateNoticeStore(context).pendingReleases(110).isEmpty())
        // 同版本再打开也不弹
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
