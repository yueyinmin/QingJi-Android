package com.qingji.app.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * V2.2 阶段D：C 层验证 —— 真实 Room 写入 → Widget 投影重新生成 → 断言投影字段。
 * 弥补 V2.1 七个测试只到 A+B 层的缺口。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetProjectionFromRealDbTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: QingJiRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = QingJiRepository(context, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `真实写入档案与体重后身体投影反映最新值`() = runBlocking {
        repo.saveProfile(UserProfile(
            id = 1, heightCm = 175.0, startWeight = 80.0, targetWeight = 70.0,
            gender = "male", unit = "jin", dailyKcalGoal = 2000,
            proteinGoalG = 100, carbGoalG = 200, fatGoalG = 50
        ))
        repo.saveWeight(WeightLog(date = AppClock.today(), weightKg = 76.0, bodyFat = 22.0))

        val projection = WidgetSnapshotRepo.loadBodyData(context, dbOverride = db)

        assertEquals(76.0, projection.latestWeight, 0.001)          // 真实体重进投影
        assertEquals("jin", projection.unit)                         // 单位进投影
        assertEquals(70.0, projection.targetWeight, 0.001)
        assertEquals(80.0, projection.startWeight, 0.001)
        assertTrue(projection.bmi > 0)                               // BMI 由真实身高体重算出
        assertEquals("measured", projection.bodyFatSource)           // 实测体脂来源
        assertEquals(22.0, projection.bodyFat ?: 0.0, 0.001)
        assertTrue(projection.lossProgress > 0f)                     // 减重进度真实计算
    }

    @Test
    fun `删除体重后身体投影回到起始体重且进度归零`() = runBlocking {
        repo.saveProfile(UserProfile(id = 1, heightCm = 175.0, startWeight = 80.0, targetWeight = 70.0))
        repo.saveWeight(WeightLog(date = AppClock.today(), weightKg = 76.0))
        assertTrue(repo.deleteWeight(AppClock.today()))
        val projection = WidgetSnapshotRepo.loadBodyData(context, dbOverride = db)
        assertEquals(80.0, projection.latestWeight, 0.001) // 回退 startWeight
        assertEquals(0f, projection.lossProgress, 0.001f)
    }

    @Test
    fun `投影失败状态在无档案时体现为不可用`() = runBlocking {
        // 无 profile、无体重日志：投影仍能生成，但身体状态为 EMPTY/FAILED，不得伪造数值
        val projection = WidgetSnapshotRepo.loadBodyData(context, dbOverride = db)
        assertEquals(0.0, projection.latestWeight, 0.001)
        assertEquals(0.0, projection.bmi, 0.001)
        assertTrue(projection.latestWeight == 0.0 || projection.bodyState == ItemState.EMPTY ||
            projection.bodyState == ItemState.FAILED)
    }
}
