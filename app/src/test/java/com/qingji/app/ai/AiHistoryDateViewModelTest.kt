package com.qingji.app.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import com.qingji.app.data.repository.QingJiRepository
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.viewmodel.AiAnalysisViewModel
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
 * QJ-AI-HISTORY-DATE-V1：AiAnalysisViewModel.daily(date) 显式日期测试。
 * 注入仓库 + 捕获 facts，验证 daily 不会把目标日期替换成 AppClock.today()。
 */
@RunWith(RobolectricTestRunner::class)
class AiHistoryDateViewModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var vm: AiAnalysisViewModel
    private var capturedFacts: String? = null
    private lateinit var today: java.time.LocalDate
    private lateinit var yesterday: java.time.LocalDate
    private lateinit var specified: java.time.LocalDate

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        today = AppClock.today()
        yesterday = today.minusDays(1)
        specified = today.minusDays(4)

        db.aiConfigDao().save(AiConfig(id = 1, enabled = true, dailyLimit = 100,
            baseUrl = "https://example.invalid/v1", textModel = "fake-model"))
        db.userProfileDao().save(UserProfile(id = 1, dailyKcalGoal = 2000, heightCm = 170.0, unit = "kg"))

        // 每天的食物名带唯一标记，便于断言查询落在哪一天
        listOf(today, yesterday, specified).forEachIndexed { index, date ->
            db.foodLogDao().insert(FoodLog(date = date, mealType = "lunch",
                foodName = "标记日$index", grams = 100.0, kcal = (100 + index * 100).toDouble()))
        }
        db.weightLogDao().save(WeightLog(date = specified, weightKg = 68.0))
        db.weightLogDao().save(WeightLog(date = today, weightKg = 70.0))

        val repo = QingJiRepository(context, dbOverride = db,
            analyzeExecutor = { _, _, facts -> capturedFacts = facts; Result.success("ok") })
        vm = AiAnalysisViewModel(context.applicationContext as android.app.Application, repo)
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun factsOf(date: java.time.LocalDate): String {
        capturedFacts = null
        assertTrue(vm.daily(date).isSuccess)
        return capturedFacts ?: error("未捕获 facts")
    }

    @Test
    fun `daily today 查询今天的数据`() = runBlocking {
        val facts = factsOf(today)
        assertTrue(facts.contains("lunch:100千卡/1项"))
        assertTrue(facts.contains("今日复盘"))
    }

    @Test
    fun `daily yesterday 查询昨天的数据`() = runBlocking {
        val facts = factsOf(yesterday)
        assertTrue(facts.contains("lunch:200千卡/1项"))
        assertTrue(facts.contains("$yesterday"))
        assertFalse("不应混入今天数据", facts.contains("lunch:100千卡"))
    }

    @Test
    fun `daily 指定历史日期查询该日数据`() = runBlocking {
        val facts = factsOf(specified)
        assertTrue(facts.contains("lunch:300千卡/1项"))
        assertTrue(facts.contains("$specified"))
        assertFalse(facts.contains("lunch:100千卡"))
        assertFalse(facts.contains("lunch:200千卡"))
    }

    @Test
    fun `daily 内部不会把目标日期替换为今天`() = runBlocking {
        val facts = factsOf(specified)
        assertTrue("AI 提示词必须明确目标日期", facts.contains("分析日期：$specified"))
        assertFalse("历史分析不得自称今日复盘", facts.contains("今日复盘"))
        assertTrue(facts.contains("历史复盘"))
    }

    @Test
    fun `历史日期无数据时返回正确结果且不拿今天补`() = runBlocking {
        val emptyDate = today.minusDays(10)
        val facts = factsOf(emptyDate)
        assertTrue(facts.contains("${emptyDate}没有饮食记录"))
        assertTrue(facts.contains("饮水 0ml"))
        assertFalse(facts.contains("lunch:100千卡"))
        assertTrue(vm.daily(emptyDate).isSuccess)
    }

    @Test
    fun `周报不受历史日期参数影响仍统计近七天`() = runBlocking {
        capturedFacts = null
        assertTrue(vm.weekly().isSuccess)
        val weeklyFacts = capturedFacts ?: error("未捕获周报 facts")
        assertTrue(weeklyFacts.contains("【周报】"))
        assertTrue(weeklyFacts.contains("$today"))
        assertTrue(weeklyFacts.contains("${today.minusDays(6)}"))
        assertFalse(weeklyFacts.contains("分析日期：$specified"))
    }

    @Test
    fun `每日额度按实际调用日统计不受分析日影响`() = runBlocking {
        factsOf(specified)
        assertEquals(1, db.usageStatDao().countByType(today, "ai"))
        assertEquals(0, db.usageStatDao().countByType(specified, "ai") ?: 0)
    }
}
