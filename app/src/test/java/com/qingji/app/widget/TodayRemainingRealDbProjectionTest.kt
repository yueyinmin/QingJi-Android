package com.qingji.app.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.FoodLog
import com.qingji.app.data.local.entity.UserProfile
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
 * 最终补证：TodayRemaining 真实 Room → Repository 写入 → loadTodayRemaining → 投影字段断言（C 层）。
 *
 * 数据口径（来自 FoodLogDao.observeDailySummary 的 SUM 聚合）：
 *   eatenKcal = SUM(kcal).toInt()，protein/carb/fat 同理 SUM 后 toInt；
 *   goalKcal = profile.dailyKcalGoal（>0 才算配置）；
 *   remainingKcal = goal - eaten + bonus（无运动 → bonus=0）；
 *   remainingFoodOnly = goal - eaten；
 *   canShowRemaining = goalConfigured && 三项 state 非 FAILED。
 * 每条测试的期望值都可在注释中手算核对。
 */
@RunWith(RobolectricTestRunner::class)
class TodayRemainingRealDbProjectionTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: QingJiRepository
    private val today = AppClock.today()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = QingJiRepository(context, refreshHook = { _, _ -> }, dbOverride = db)
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun saveGoals(
        kcal: Int = 2000, protein: Int = 100, carb: Int = 200, fat: Int = 50
    ) {
        repo.saveProfile(UserProfile(
            id = 1, dailyKcalGoal = kcal, proteinGoalG = protein,
            carbGoalG = carb, fatGoalG = fat, heightCm = 175.0
        ))
    }

    private suspend fun food(kcal: Double, p: Double, c: Double, f: Double, meal: String = "breakfast") =
        repo.addFoodLog(FoodLog(
            date = today, mealType = meal, foodName = "测试食物", grams = 100.0,
            kcal = kcal, proteinG = p, carbG = c, fatG = f
        ))

    /** 测试 A：正常饮食记录投影（两条不同餐次，数值可手算）。 */
    @Test
    fun `A正常饮食投影_热量营养目标剩余与状态全部正确`() = runBlocking {
        saveGoals(2000, 100, 200, 50)
        food(500.0, 20.0, 60.0, 10.0, "breakfast") // 早餐 500kcal / P20 C60 F10
        food(300.0, 15.0, 30.0, 8.0, "lunch")      // 午餐 300kcal / P15 C30 F8

        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)

        assertEquals(2000, d.goalKcal)              // 目标 = 档案
        assertEquals(800, d.eatenKcal)              // 500+300
        assertEquals(35, d.proteinCurrent)          // 20+15
        assertEquals(90, d.carbCurrent)             // 60+30
        assertEquals(18, d.fatCurrent)              // 10+8
        assertEquals(100, d.proteinGoal)
        assertEquals(200, d.carbGoal)
        assertEquals(50, d.fatGoal)
        assertEquals(1200, d.remainingKcal)         // 2000-800，无运动补偿
        assertEquals(1200, d.remainingFoodOnly)
        assertTrue(d.goalConfigured)
        assertTrue(d.canShowRemaining)
        assertEquals(ItemState.SUCCESS, d.dietState)
        assertEquals(ItemState.SUCCESS, d.profileState)
    }

    /** 测试 B：多餐累计不重不漏（早/午/晚/加餐四餐）。 */
    @Test
    fun `B多餐累计_四餐全部计入且不重复`() = runBlocking {
        saveGoals(2000)
        food(100.0, 1.0, 1.0, 1.0, "breakfast")
        food(200.0, 2.0, 2.0, 2.0, "lunch")
        food(300.0, 3.0, 3.0, 3.0, "dinner")
        food(400.0, 4.0, 4.0, 4.0, "snack")

        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(1000, d.eatenKcal)             // 100+200+300+400
        assertEquals(10, d.proteinCurrent)          // 1+2+3+4
        assertEquals(1000, d.remainingKcal)
    }

    /** 测试 C：未设置营养目标（dailyKcalGoal=0）——摄入仍可读，goalConfigured=false，不造进度。 */
    @Test
    fun `C未设置目标_摄入仍真实读取且目标语义为空`() = runBlocking {
        repo.saveProfile(UserProfile(id = 1, dailyKcalGoal = 0, proteinGoalG = 0))
        food(450.0, 10.0, 20.0, 5.0)

        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(450, d.eatenKcal)              // 摄入是真值
        assertEquals(0, d.goalKcal)
        assertEquals(false, d.goalConfigured)
        assertEquals(0, d.remainingKcal)            // 不伪造 2000 剩余
        assertTrue(!d.canShowRemaining)             // UI 显示 --
        assertEquals(0, d.proteinGoal)
    }

    /** 测试 D：有目标无饮食记录——EMPTY 真 0，不是 FAILED。 */
    @Test
    fun `D有目标无记录_零摄入且状态非失败`() = runBlocking {
        saveGoals(2000, 100, 200, 50)
        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(0, d.eatenKcal)
        assertEquals(0, d.proteinCurrent)
        assertEquals(2000, d.remainingKcal)         // 剩余 = 目标
        assertEquals(2000, d.remainingFoodOnly)
        // 真实语义：Room SUM 空表返回 DailySummary(null 字段) 这一非 null 对象，
        // Outcome.of(非null)=SUCCESS，0 是「确实没记」的真值；FAILED 才是查询失败。
        assertEquals(ItemState.SUCCESS, d.dietState)
        assertTrue(d.canShowRemaining)
    }

    /** 测试 E：跨日期隔离——昨天记录不进今天。 */
    @Test
    fun `E跨日期隔离_昨天饮食不进入今天摄入`() = runBlocking {
        saveGoals(2000)
        repo.addFoodLog(FoodLog(
            date = today.minusDays(1), mealType = "dinner", foodName = "昨天",
            grams = 100.0, kcal = 999.0, proteinG = 99.0, carbG = 99.0, fatG = 99.0
        ))
        food(200.0, 5.0, 10.0, 2.0)

        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(200, d.eatenKcal)              // 只算今天，不含 999
        assertEquals(5, d.proteinCurrent)
        assertEquals(today, d.date)
        assertEquals(AppClock.zone.id, d.zoneId)
    }

    /** 测试 F：超目标——摄入保留真值，进度按 WidgetProgress.fraction 钳制到 1，剩余为负。 */
    @Test
    fun `F超目标_摄入真实剩余为负且进度钳制`() = runBlocking {
        saveGoals(1000)
        food(1500.0, 50.0, 100.0, 20.0)

        val d = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(1500, d.eatenKcal)             // 不截断
        assertEquals(-500, d.remainingKcal)         // 负剩余由 UI 显红
        assertEquals(-500, d.remainingFoodOnly)
        val progress = WidgetProgress.fraction(d.eatenKcal, d.goalKcal)
        assertEquals(1f, progress, 0.001f)          // 进度不超 100%
        assertTrue(d.canShowRemaining)
    }

    /** 测试 G：空数据与查询失败区分——真实空表 EMPTY；注入异常时 FAILED 不伪 0。 */
    @Test
    fun `G空数据与失败严格区分`() = runBlocking {
        saveGoals(2000)
        val empty = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
        assertEquals(ItemState.SUCCESS, empty.dietState) // 空表聚合对象非 null → SUCCESS + 真 0
        assertEquals(0, empty.eatenKcal)

        // 注入业务异常到 summary 查询：必须映射 FAILED，不能吞成 0
        WidgetSnapshotRepo.queryHookForTest = { label ->
            if (label == "today.summary") throw IllegalStateException("db error")
        }
        try {
            val failed = WidgetSnapshotRepo.loadTodayRemaining(context, dbOverride = db)
            assertEquals(ItemState.FAILED, failed.dietState)
            assertTrue(!failed.canShowRemaining)
            assertEquals(0, failed.eatenKcal)       // 字段是占位，UI 靠 state 显示 --
        } finally {
            WidgetSnapshotRepo.queryHookForTest = null
        }
    }
}
