package com.qingji.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.domain.SelfDisciplineContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * ============================================================
 * 自律管理仓库：P0 业务规则测试（SD-014）
 * ------------------------------------------------------------
 * 直接灌内存 Room，验证工单里最容易被写错、写错了最难发现的几条：
 * 1. 建目标会自动开周期；
 * 2. 同一天只留一条打卡（编辑即覆盖）；
 * 3. 偏离会结束周期并写偏离打卡；
 * 4. 撤销偏离要两边都还原；
 * 5. 删目标要级联清干净；
 * 6. 统计只数真实记录，不拿默认值凑数。
 * ============================================================
 */
@RunWith(RobolectricTestRunner::class)
class SelfDisciplineRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SelfDisciplineRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = SelfDisciplineRepository(ApplicationProvider.getApplicationContext(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun goal(
        start: LocalDate = LocalDate.now(),
        title: String = "测试目标"
    ) = SelfDisciplineGoalEntity(
        title = title,
        goalType = SelfDisciplineContracts.GOAL_CUSTOM,
        startDate = start,
        targetDays = 30
    )

    @Test
    fun `新建目标会自动开一个进行中周期且天数从1开始`() = runBlocking {
        val id = repo.createGoal(goal())
        val active = repo.getActivePeriod(id)
        assertNotNull("建目标必须同时开周期", active)
        assertEquals(LocalDate.now(), active!!.startAt)
        assertEquals(SelfDisciplineContracts.PERIOD_ACTIVE, active.status)
        assertEquals(1, repo.currentDays(id))
    }

    @Test
    fun `同一天重复打卡只保留一条并覆盖内容`() = runBlocking {
        val id = repo.createGoal(goal())
        repo.saveCheckin(
            com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity(
                goalId = id,
                localDate = LocalDate.now(),
                status = SelfDisciplineContracts.STATUS_ON_TARGET,
                mood = "平静"
            )
        )
        repo.saveCheckin(
            com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity(
                goalId = id,
                localDate = LocalDate.now(),
                status = SelfDisciplineContracts.STATUS_OFF_TARGET,
                mood = "焦虑"
            )
        )
        val all = repo.observeCheckins(id).first()
        assertEquals("同一天不该出现两条", 1, all.size)
        assertEquals(SelfDisciplineContracts.STATUS_OFF_TARGET, all.first().status)
        assertEquals("焦虑", all.first().mood)
    }

    @Test
    fun `记录偏离会结束当前周期并写一条偏离打卡`() = runBlocking {
        val id = repo.createGoal(goal(start = LocalDate.now().minusDays(2)))
        assertEquals(3, repo.currentDays(id))

        val periodId = repo.recordDeviation(
            goalId = id,
            date = LocalDate.now(),
            triggers = "压力",
            mood = "低落",
            note = "加班太晚"
        )

        assertTrue("周期 id 要回传给撤销用", periodId > 0)
        assertNull("偏离后不该还有进行中周期", repo.getActivePeriod(id))

        val checkin = repo.getCheckin(id, LocalDate.now())
        assertNotNull(checkin)
        assertEquals(SelfDisciplineContracts.STATUS_OFF_TARGET, checkin!!.status)
        assertEquals("压力", checkin.triggers)

        val stats = repo.computeStats(id)
        assertEquals(1, stats.deviationCount)
        assertEquals(0, stats.currentDays)
        // 历史最佳仍然保留这几天，不能因为一次偏离就清空
        assertEquals(3, stats.bestDays)
    }

    @Test
    fun `撤销偏离会重开周期并删掉那条偏离打卡`() = runBlocking {
        val id = repo.createGoal(goal(start = LocalDate.now().minusDays(2)))
        val today = LocalDate.now()
        val periodId = repo.recordDeviation(id, today, "", "", "")

        repo.undoDeviation(periodId, today, id)

        val active = repo.getActivePeriod(id)
        assertNotNull("撤销后周期要恢复进行中", active)
        assertEquals(SelfDisciplineContracts.PERIOD_ACTIVE, active!!.status)
        assertNull("撤销后不该再留着偏离打卡", repo.getCheckin(id, today))
        assertEquals(3, repo.currentDays(id))
    }

    @Test
    fun `删除目标会级联清掉周期打卡和冲动记录`() = runBlocking {
        val id = repo.createGoal(goal())
        repo.saveCheckin(
            com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity(
                goalId = id,
                localDate = LocalDate.now(),
                status = SelfDisciplineContracts.STATUS_ON_TARGET
            )
        )
        repo.addUrge(SelfDisciplineUrgeEntity(goalId = id, occurredAt = 1L, intensity = 3))

        repo.deleteGoal(id)

        assertNull(repo.getGoal(id))
        assertTrue(repo.observeCheckins(id).first().isEmpty())
        assertTrue(repo.observeUrges(id).first().isEmpty())
        assertTrue(repo.observePeriods(id).first().isEmpty())
    }

    @Test
    fun `重新开始会新开周期并保留旧周期历史`() = runBlocking {
        val id = repo.createGoal(goal(start = LocalDate.now().minusDays(5)))
        repo.restartPeriod(id, LocalDate.now())

        val periods = repo.observePeriods(id).first()
        assertEquals("旧周期 + 新周期", 2, periods.size)
        assertEquals(1, periods.count { it.status == SelfDisciplineContracts.PERIOD_ACTIVE })
        // 旧周期的 5 天还在，历史最佳不能掉成 1
        assertEquals(5, repo.bestDays(id))
    }

    @Test
    fun `没有记录时统计是0而不是默认算成功`() = runBlocking {
        val id = repo.createGoal(goal())
        val stats = repo.computeStats(id)
        assertEquals(0, stats.onTargetDays)
        assertEquals(0, stats.deviationCount)
        assertEquals(0, stats.urgeCount)
        assertEquals(0, stats.streak)
        assertEquals(1, stats.currentDays)
    }

    @Test
    fun `主要目标优先取设置里指定的那个`() = runBlocking {
        val first = repo.createGoal(goal(title = "第一个"))
        val second = repo.createGoal(goal(title = "第二个"))

        assertEquals("没有设置时退回第一个启用目标", first, repo.getPrimaryGoal()?.id)

        repo.setSetting(SelfDisciplineContracts.SETTING_DEFAULT_GOAL, second.toString())
        assertEquals(second, repo.getPrimaryGoal()?.id)

        // 指定的目标被暂停后，不能继续当主要目标
        repo.setGoalEnabled(second, false)
        assertEquals(first, repo.getPrimaryGoal()?.id)
    }

    @Test
    fun `当天已有自己打卡时记录偏离不覆盖原记录撤销也不丢`() = runBlocking {
        val id = repo.createGoal(goal(start = LocalDate.now().minusDays(2)))
        val today = LocalDate.now()

        // 上午先记了一条「符合目标」
        repo.saveCheckin(
            com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity(
                goalId = id,
                localDate = today,
                status = SelfDisciplineContracts.STATUS_ON_TARGET,
                mood = "平静"
            )
        )

        val periodId = repo.recordDeviation(id, today, "压力", "低落", "")
        // 偏离事件本身要算进统计
        assertEquals(1, repo.computeStats(id).deviationCount)
        // 但不能把用户自己的打卡改掉
        val afterDeviation = repo.getCheckin(id, today)
        assertNotNull(afterDeviation)
        assertEquals(SelfDisciplineContracts.STATUS_ON_TARGET, afterDeviation!!.status)
        assertEquals("平静", afterDeviation.mood)

        // 撤销后原打卡仍然在，周期也恢复
        repo.undoDeviation(periodId, today, id)
        val afterUndo = repo.getCheckin(id, today)
        assertNotNull("撤销偏离不能把正常打卡一起删掉", afterUndo)
        assertEquals(SelfDisciplineContracts.STATUS_ON_TARGET, afterUndo!!.status)
        assertNotNull(repo.getActivePeriod(id))
        assertEquals(0, repo.computeStats(id).deviationCount)
    }
}
