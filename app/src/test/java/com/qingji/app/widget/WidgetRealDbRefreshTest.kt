package com.qingji.app.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * V2.1 阶段 A4：真实 Repository + 独立 Room 数据库写入 → 刷新 Target 验证。
 * 每个测试创建独立内存 DB，结束关闭，规避进程单例多用例连接指针错乱。
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRealDbRefreshTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: QingJiRepository
    private lateinit var calls: MutableList<Set<WidgetTarget>>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        calls = mutableListOf()
        repo = QingJiRepository(
            context,
            refreshHook = { _, targets -> calls.add(targets) },
            dbOverride = db
        )
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `新增当天运动刷新OVERVIEW`() = runBlocking {
        repo.addExercise(ExerciseLog(date = AppClock.today(), type = "跑步", minutes = 30, kcal = 200.0))
        assertEquals(setOf(WidgetTarget.OVERVIEW), calls.singleOrNull())
        assertTrue(db.exerciseLogDao().findRange(AppClock.today(), AppClock.today()).isNotEmpty())
    }

    @Test
    fun `修改今天运动改成历史日期仍刷新OVERVIEW`() = runBlocking {
        repo.addExercise(ExerciseLog(date = AppClock.today(), type = "跑步", minutes = 30, kcal = 200.0))
        calls.clear()
        val log = db.exerciseLogDao().findRange(AppClock.today(), AppClock.today()).first()
        repo.updateExercise(log.copy(date = AppClock.today().minusDays(3)))
        // V2.1修复：原日期是今天也要刷（旧实现只判新日期会漏）
        assertEquals(setOf(WidgetTarget.OVERVIEW), calls.singleOrNull())
    }

    @Test
    fun `新增体重刷新WEIGHT与BODY_DATA`() = runBlocking {
        repo.saveWeight(WeightLog(date = AppClock.today(), weightKg = 70.0))
        assertEquals(setOf(WidgetTarget.WEIGHT, WidgetTarget.BODY_DATA), calls.singleOrNull())
        assertTrue(db.weightLogDao().latest()?.weightKg == 70.0)
    }

    @Test
    fun `删除体重刷新两个目标且删0行不刷新`() = runBlocking {
        repo.saveWeight(WeightLog(date = AppClock.today(), weightKg = 70.0))
        calls.clear()
        assertTrue(repo.deleteWeight(AppClock.today()))
        assertEquals(setOf(WidgetTarget.WEIGHT, WidgetTarget.BODY_DATA), calls.singleOrNull())
        calls.clear()
        assertFalse(repo.deleteWeight(AppClock.today())) // 已删，0 行
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `热量目标修改只刷新OVERVIEW`() = runBlocking {
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2200, heightCm = 175.0, unit = "kg"))
        calls.clear()
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2300, heightCm = 175.0, unit = "kg"))
        assertEquals(setOf(WidgetTarget.OVERVIEW), calls.singleOrNull())
    }

    @Test
    fun `身高修改只刷新BODY_DATA`() = runBlocking {
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2200, heightCm = 175.0, unit = "kg"))
        calls.clear()
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2200, heightCm = 180.0, unit = "kg"))
        assertEquals(setOf(WidgetTarget.BODY_DATA), calls.singleOrNull())
    }

    @Test
    fun `单位修改刷新BODY_DATA与WEIGHT`() = runBlocking {
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2200, heightCm = 175.0, unit = "kg"))
        calls.clear()
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 2200, heightCm = 175.0, unit = "jin"))
        assertEquals(setOf(WidgetTarget.BODY_DATA, WidgetTarget.WEIGHT), calls.singleOrNull())
    }
}
