package com.qingji.app.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WaterLog
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
 * QJ-AI-HISTORY-DATE-V1：Repository 层历史日期数据聚合 + AI 调用统计日期隔离。
 * 真实内存 Room 库；AI 文本执行器注入为捕获 facts 的假实现，不联网。
 *
 * 场景基准：当前调用日 = AppClock.today()（测试内记为 11-24），
 * 分析日 = today.minusDays(4)（记为 11-20）。
 */
@RunWith(RobolectricTestRunner::class)
class AiHistoryDateRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: QingJiRepository
    private var capturedFacts: String? = null

    /** 当前调用日（usageDate）。 */
    private lateinit var usageDate: java.time.LocalDate
    /** 历史分析日（dataDate）。 */
    private lateinit var dataDate: java.time.LocalDate

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        capturedFacts = null
        repo = QingJiRepository(
            context,
            dbOverride = db,
            analyzeExecutor = { _, _, facts ->
                capturedFacts = facts
                Result.success("fake-analysis")
            }
        )
        usageDate = AppClock.today()
        dataDate = usageDate.minusDays(4)

        // 配置一个启用的 AI 服务，额度 100（不会触发熔断）
        db.aiConfigDao().save(
            AiConfig(id = 1, enabled = true, dailyLimit = 100, baseUrl = "https://example.invalid/v1",
                textModel = "fake-model")
        )
        repo.setActiveAiConfig(1)

        // 用户当前目标配置（非历史快照）
        db.userProfileDao().save(
            UserProfile(id = 1, dailyKcalGoal = 2000, proteinGoalG = 100,
                carbGoalG = 250, fatGoalG = 60, heightCm = 170.0, unit = "kg")
        )

        // 历史日：早 300 千卡 + 饮水 500；今天：早 900 千卡 + 饮水 1500
        db.foodLogDao().insert(FoodLog(date = dataDate, mealType = "breakfast",
            foodName = "历史燕麦", grams = 100.0, kcal = 300.0, proteinG = 12.0, carbG = 50.0, fatG = 5.0))
        db.foodLogDao().insert(FoodLog(date = usageDate, mealType = "breakfast",
            foodName = "今天米饭", grams = 300.0, kcal = 900.0, proteinG = 30.0, carbG = 150.0, fatG = 15.0))
        db.waterLogDao().insert(WaterLog(date = dataDate, ml = 500))
        db.waterLogDao().insert(WaterLog(date = usageDate, ml = 1500))
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun aiCount(date: java.time.LocalDate) =
        db.usageStatDao().countByType(date, "ai") ?: 0

    @Test
    fun `指定日期饮食数据正确且不混入今天`() = runBlocking {
        val facts = repo.buildDailyFacts(dataDate)
        assertTrue(facts.contains("$dataDate"))
        assertTrue(facts.contains("breakfast:300千卡/1项"))
        assertFalse("历史 facts 混入今天热量", facts.contains("900千卡"))
        assertFalse("历史 facts 混入今天热量", facts.contains("breakfast:900千卡"))
        assertTrue(facts.contains("历史复盘"))
        assertFalse("历史 facts 不应写「今日复盘」", facts.contains("今日复盘"))
    }

    @Test
    fun `指定日期餐次数据正确`() = runBlocking {
        val facts = repo.buildDailyFacts(dataDate)
        assertTrue(facts.contains("breakfast:300千卡/1项"))
        assertFalse(facts.contains("breakfast:900千卡"))
    }

    @Test
    fun `指定日期饮水数据正确`() = runBlocking {
        val facts = repo.buildDailyFacts(dataDate)
        assertTrue(facts.contains("饮水 500ml"))
        assertFalse(facts.contains("饮水 1500ml"))
    }

    @Test
    fun `今日复盘仍使用今天文案与今天数据`() = runBlocking {
        val facts = repo.buildDailyFacts(usageDate)
        assertTrue(facts.contains("今日复盘"))
        assertTrue(facts.contains("今天摄入 900千卡"))
        assertTrue(facts.contains("饮水 1500ml"))
        assertFalse(facts.contains("breakfast:300千卡"))
    }

    @Test
    fun `空记录日期不会使用今天的数据填充`() = runBlocking {
        val emptyDate = usageDate.minusDays(10)
        val facts = repo.buildDailyFacts(emptyDate)
        assertTrue(facts.contains("${emptyDate}没有饮食记录"))
        assertTrue(facts.contains("饮水 0ml"))
        assertFalse(facts.contains("今天米饭"))
        assertFalse(facts.contains("900千卡"))
    }

    @Test
    fun `目标配置明确标注为当前配置而非历史目标`() = runBlocking {
        val facts = repo.buildDailyFacts(dataDate)
        assertTrue(facts.contains("当前配置的目标，非该日历史目标"))
        assertTrue(facts.contains("目标均为当前配置"))
    }

    @Test
    fun `分析历史日时 facts 用历史数据而调用统计记在调用当天`() = runBlocking {
        // 分析 11-20 的数据，但调用发生在今天（11-24）
        val result = repo.analyze(repo.buildDailyFacts(dataDate), usageDate)
        assertTrue(result.isSuccess)

        val sentFacts = capturedFacts ?: error("AI 执行器未捕获 facts")
        assertTrue(sentFacts.contains("$dataDate"))
        assertTrue(sentFacts.contains("breakfast:300千卡/1项"))
        assertFalse(sentFacts.contains("breakfast:900千卡"))

        // 额度读取与次数写入都在 usageDate
        assertEquals(1, aiCount(usageDate))
        assertEquals(0, aiCount(dataDate))
    }

    @Test
    fun `历史日已有调用次数不影响今天的额度计算`() = runBlocking {
        // 让历史日已有 5 次调用（模拟有人误把调用记到历史日期的旧数据）
        repeat(5) { db.usageStatDao().increment(dataDate, "ai") }
        assertEquals(5, aiCount(dataDate))
        assertEquals(0, aiCount(usageDate))

        val result = repo.analyze(repo.buildDailyFacts(dataDate), usageDate)
        assertTrue("历史日调用数不应消耗今天额度", result.isSuccess)
        assertEquals(1, aiCount(usageDate))
        assertEquals(5, aiCount(dataDate))
    }

    @Test
    fun `今天额度不足时历史分析同样被拦截`() = runBlocking {
        db.usageStatDao().increment(usageDate, "ai") // 今天 1 次
        // 换一个额度=1 的配置
        db.aiConfigDao().save(AiConfig(id = 2, enabled = true, dailyLimit = 1, baseUrl = "https://example.invalid/v1",
            textModel = "fake-model"))
        repo.setActiveAiConfig(2)

        val result = repo.analyze(repo.buildDailyFacts(dataDate), usageDate)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("上限") == true)
        assertEquals("被拦截时不应写入调用统计", 0, aiCount(dataDate))
        assertEquals(1, aiCount(usageDate))
    }

    @Test
    fun `AI 调用失败时两个日期都不写统计`() = runBlocking {
        repo = QingJiRepository(context, dbOverride = db,
            analyzeExecutor = { _, _, _ -> Result.failure(RuntimeException("network down")) })
        val result = repo.analyze(repo.buildDailyFacts(dataDate), usageDate)
        assertTrue(result.isFailure)
        assertEquals(0, aiCount(usageDate))
        assertEquals(0, aiCount(dataDate))
    }
}
