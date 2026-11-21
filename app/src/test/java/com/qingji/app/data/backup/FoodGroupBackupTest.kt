package com.qingji.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.UserFoodGroup
import com.qingji.app.data.local.entity.UserFoodGroupItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.6：自定义食物组备份测试。
 * 覆盖两件最关键的事：
 * 1. v6 备份能把食物组 + 关联完整读出来；
 * 2. **老备份（version < 6）导入时绝不清本机已建的食物组**——这是最高风险区，
 *    一旦清错，用户导一份旧备份就把刚建的组全弄没了。
 */
class FoodGroupBackupTest {

    @Test
    fun `v6备份能读出食物组与关联`() {
        val json = """
            {"app":"qingji","version":6,"exportedAt":"2026-11-19",
             "userFoodGroups":[{"id":1,"name":"早餐组","coverUri":"","sortOrder":0,"createdAt":100,"updatedAt":100}],
             "userFoodGroupItems":[{"id":1,"groupId":1,"foodId":7,"createdAt":100}]}
        """.trimIndent()
        val parsed = parseBackup(json).getOrThrow()
        assertEquals(6, parsed.version)
        assertEquals(true, parsed.hasUserFoodGroups)
        assertEquals(1, parsed.userFoodGroups.size)
        assertEquals("早餐组", parsed.userFoodGroups[0].name)
        assertEquals(1L, parsed.userFoodGroupItems[0].groupId)
        assertEquals(7L, parsed.userFoodGroupItems[0].foodId)
        assertFalse("有组数据就不算空备份", parsed.isEmpty)
    }

    @Test
    fun `v5老备份缺食物组段时当空不报错`() {
        val json = """{"app":"qingji","version":5,"exportedAt":"","foods":[]}""".trimIndent()
        val parsed = parseBackup(json).getOrThrow()
        assertEquals(false, parsed.hasUserFoodGroups)
        assertEquals(0, parsed.userFoodGroups.size)
        assertEquals(0, parsed.userFoodGroupItems.size)
    }

    /** 清表守护需要真 Room（内存库），单独一个 Robolectric 测试类。 */
    @RunWith(RobolectricTestRunner::class)
    class RestoreGuardTest {
        private lateinit var db: AppDatabase

        @Before
        fun setUp() {
            db = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java
            ).allowMainThreadQueries().build()
        }

        @After
        fun tearDown() { db.close() }

        @Test
        fun `导入v5老备份不清本机食物组`() = runBlocking {
            db.userFoodGroupDao().insertGroup(UserFoodGroup(id = 1, name = "本机建的组"))
            db.userFoodGroupDao().insertItem(UserFoodGroupItem(id = 1, groupId = 1, foodId = 3))
            val manager = BackupManager(ApplicationProvider.getApplicationContext())
            // v5 老备份：hasUserFoodGroups=false（缺段）
            manager.restoreInto(db, ParsedBackup(version = 5, hasUserFoodGroups = false))
            assertEquals("老备份导入绝不能清掉本机刚建的食物组", 1, db.userFoodGroupDao().allGroups().size)
            assertEquals(1, db.userFoodGroupDao().foodIdsInGroup(1).size)
        }

        @Test
        fun `导入v6备份整段覆盖食物组`() = runBlocking {
            db.userFoodGroupDao().insertGroup(UserFoodGroup(id = 99, name = "旧的本地组"))
            val manager = BackupManager(ApplicationProvider.getApplicationContext())
            manager.restoreInto(
                db,
                ParsedBackup(
                    version = 6,
                    hasUserFoodGroups = true,
                    userFoodGroups = listOf(UserFoodGroup(id = 1, name = "备份里的组")),
                    userFoodGroupItems = listOf(UserFoodGroupItem(id = 1, groupId = 1, foodId = 7))
                )
            )
            val groups = db.userFoodGroupDao().allGroups()
            assertEquals(1, groups.size)
            assertEquals("备份里的组", groups[0].name)
            assertEquals(listOf(7L), db.userFoodGroupDao().foodIdsInGroup(1))
        }
    }
}
