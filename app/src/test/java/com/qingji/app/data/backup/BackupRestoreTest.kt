package com.qingji.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.FastingLog
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.data.local.entity.WeightLog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * v1.1.0：备份「覆盖恢复」语义测试（任务书 P0 风险 A）。
 * 直接灌内存 Room，验证 restoreInto 的清空 / 覆盖 / 保留 / 不重复规则。
 */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(gender: String = "male") = UserProfile(
        id = 1,
        gender = gender,
        birthday = null,
        heightCm = 175.0,
        startWeight = 90.0,
        targetWeight = 75.0,
        activityLevel = 2,
        dailyKcalGoal = 1800,
        proteinGoalG = 112,
        carbGoalG = 231,
        fatGoalG = 50,
        waterGoalMl = 2000,
        unit = "kg",
        createdAt = 1L
    )

    @Test
    fun `备份明确没有档案时清空本机档案`() = runBlocking {
        db.userProfileDao().save(profile())
        val manager = BackupManager(ApplicationProvider.getApplicationContext())
        manager.restoreInto(db, ParsedBackup(hasProfile = true, profile = null))
        assertNull("备份里明确写了 null，本机旧档案应被清掉", db.userProfileDao().get())
    }

    @Test
    fun `备份里有档案时覆盖本机档案`() = runBlocking {
        db.userProfileDao().save(profile("male"))
        val manager = BackupManager(ApplicationProvider.getApplicationContext())
        manager.restoreInto(db, ParsedBackup(hasProfile = true, profile = profile("female")))
        assertEquals("female", db.userProfileDao().get()?.gender)
    }

    @Test
    fun `备份键缺失时保留本机档案`() = runBlocking {
        db.userProfileDao().save(profile("male"))
        val manager = BackupManager(ApplicationProvider.getApplicationContext())
        manager.restoreInto(db, ParsedBackup(hasProfile = false, profile = null))
        assertEquals("键缺失（第三方工具生成的文件）不应猜测，保留本机档案", "male", db.userProfileDao().get()?.gender)
    }

    @Test
    fun `重复导入同一份备份不会产生重复数据`() = runBlocking {
        val manager = BackupManager(ApplicationProvider.getApplicationContext())
        val backup = ParsedBackup(
            weights = listOf(WeightLog(date = LocalDate.of(2026, 1, 1), weightKg = 90.0))
        )
        manager.restoreInto(db, backup)
        manager.restoreInto(db, backup)
        assertEquals("每次导入都先清再灌，两次不会翻倍", 1, db.weightLogDao().findAll().size)
    }

    @Test
    fun `自由断食字段恢复落库后保持`() = runBlocking {
        val manager = BackupManager(ApplicationProvider.getApplicationContext())
        val fasting = FastingLog(
            date = LocalDate.of(2026, 11, 9),
            planHours = 18,
            freeMode = true,
            actualMinutes = 965
        )
        manager.restoreInto(db, ParsedBackup(fastings = listOf(fasting)))
        val restored = db.fastingLogDao().findAll().single()
        assertEquals(true, restored.freeMode)
        assertEquals(965, restored.actualMinutes)
    }
}
