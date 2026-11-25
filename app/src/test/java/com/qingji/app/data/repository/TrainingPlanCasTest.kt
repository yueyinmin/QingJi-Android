package com.qingji.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.TrainingPlan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 阶段 E：训练计划版本源与 CAS 行为。 */
@RunWith(RobolectricTestRunner::class)
class TrainingPlanCasTest {

    @Test
    fun `CAS版本不匹配不写入匹配才递增`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trainingPlanDao()
            dao.insertAll(
                listOf(
                    TrainingPlan(
                        uuid = "plan-a",
                        name = "A",
                        source = "custom",
                        isActive = true,
                        createdAt = 1000L,
                        version = 3L,
                        updatedAt = 1000L
                    )
                )
            )

            assertEquals(0, dao.casSetActive("plan-a", false, 2L, 2000L))
            assertEquals(1, dao.casSetActive("plan-a", false, 3L, 2000L))
            val after = dao.findByUuid("plan-a")!!
            assertFalse(after.isActive)
            assertEquals(4L, after.version)
            assertEquals(2000L, after.updatedAt)

            assertEquals(0, dao.deleteByVersion("plan-a", 3L))
            assertEquals(1, dao.deleteByVersion("plan-a", 4L))
            assertEquals(null, dao.findByUuid("plan-a"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `deactivateAllWithVersion只递增当前启用计划`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trainingPlanDao()
            dao.insertAll(
                listOf(
                    TrainingPlan("active", "启用的", isActive = true, version = 1L),
                    TrainingPlan("inactive", "未启用的", isActive = false, version = 5L)
                )
            )

            dao.deactivateAllWithVersion(3000L)

            assertEquals(2L, dao.versionOf("active"))
            assertEquals(5L, dao.versionOf("inactive"))
            assertTrue(dao.findAll().none { it.isActive })
        } finally {
            db.close()
        }
    }
}
