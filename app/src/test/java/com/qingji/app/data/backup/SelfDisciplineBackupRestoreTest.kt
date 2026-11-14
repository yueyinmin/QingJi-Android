package com.qingji.app.data.backup

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplineInterventionEntity
import com.qingji.app.data.local.entity.SelfDisciplineMilestoneEntity
import com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity
import com.qingji.app.data.local.entity.SelfDisciplineSettingEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * 自律数据进备份后的两条底线（对齐 TrainingBackupRestoreTest）：
 * 1. 自家 v4 备份 → 自律 7 表整包覆盖（不残留旧数据，id / goalId 关联不断）；
 * 2. v1/v2/v3 老备份（那时还没有自律模块）→ 本机 7 表一行都不能被清掉。
 */
@RunWith(RobolectricTestRunner::class)
class SelfDisciplineBackupRestoreTest {

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

    private fun manager() = BackupManager(ApplicationProvider.getApplicationContext())

    private fun goal(id: Long, title: String) = SelfDisciplineGoalEntity(
        id = id, title = title, goalType = "custom", startDate = LocalDate.of(2026, 10, 1)
    )
    private fun period(id: Long, goalId: Long) = SelfDisciplinePeriodEntity(
        id = id, goalId = goalId, startAt = LocalDate.of(2026, 10, 1), status = "active"
    )
    private fun checkin(id: Long, goalId: Long, date: LocalDate) = SelfDisciplineCheckinEntity(
        id = id, goalId = goalId, localDate = date, status = "on_target"
    )
    private fun urge(id: Long, goalId: Long) = SelfDisciplineUrgeEntity(
        id = id, goalId = goalId, occurredAt = 1L
    )
    private fun setting(key: String, value: String) = SelfDisciplineSettingEntity(key, value)
    private fun milestone(id: Long, goalId: Long) = SelfDisciplineMilestoneEntity(
        id = id, goalId = goalId, milestoneDays = 7
    )
    private fun intervention(id: Long, goalId: Long) = SelfDisciplineInterventionEntity(
        id = id, goalId = goalId, type = "breath", occurredAt = 1L
    )

    /** 本机 7 表各灌 2 行，标题带「本地」便于断言没被覆盖。 */
    private suspend fun seedLocal() {
        db.selfDisciplineGoalDao().insert(goal(1L, "本地目标A"))
        db.selfDisciplineGoalDao().insert(goal(2L, "本地目标B"))
        db.selfDisciplinePeriodDao().insert(period(1L, 1L))
        db.selfDisciplinePeriodDao().insert(period(2L, 2L))
        db.selfDisciplineCheckinDao().upsert(checkin(1L, 1L, LocalDate.of(2026, 10, 2)))
        db.selfDisciplineCheckinDao().upsert(checkin(2L, 2L, LocalDate.of(2026, 10, 3)))
        db.selfDisciplineUrgeDao().insert(urge(1L, 1L))
        db.selfDisciplineUrgeDao().insert(urge(2L, 2L))
        db.selfDisciplineSettingDao().set(setting("k1", "v1"))
        db.selfDisciplineSettingDao().set(setting("k2", "v2"))
        db.selfDisciplineMilestoneDao().insert(milestone(1L, 1L))
        db.selfDisciplineMilestoneDao().insert(milestone(2L, 2L))
        db.selfDisciplineInterventionDao().insert(intervention(1L, 1L))
        db.selfDisciplineInterventionDao().insert(intervention(2L, 2L))
    }

    /** 7 表逐表断言行数不变（各 2 行），并确认本机标题还在。 */
    private suspend fun assertSevenTablesUnchanged() {
        val titles = db.selfDisciplineGoalDao().findAll().map { it.title }.sorted()
        assertEquals(listOf("本地目标A", "本地目标B"), titles)
        assertEquals(2, db.selfDisciplineGoalDao().findAll().size)
        assertEquals(2, db.selfDisciplinePeriodDao().findAll().size)
        assertEquals(2, db.selfDisciplineCheckinDao().findAll().size)
        assertEquals(2, db.selfDisciplineUrgeDao().findAll().size)
        assertEquals(2, db.selfDisciplineSettingDao().getAll().size)
        assertEquals(2, db.selfDisciplineMilestoneDao().findAll().size)
        assertEquals(2, db.selfDisciplineInterventionDao().findAll().size)
    }

    private fun oldBackup(version: Int) =
        """{"app":"qingji","version":$version,"weights":[]}"""

    private fun assertOldBackupKeepsLocal(version: Int) = runBlocking {
        seedLocal()
        val parsed = parseBackup(oldBackup(version)).getOrThrow()
        assertFalse(parsed.hasSelfDiscipline)
        assertNull(parsed.selfDiscipline)
        // 和正式导入保持一致：清表 + 灌入必须在同一个事务里
        db.withTransaction { manager().restoreInto(db, parsed) }
        assertSevenTablesUnchanged()
    }

    @Test
    fun `v1 老备份不碰本机自律数据`() { assertOldBackupKeepsLocal(1) }

    @Test
    fun `v2 老备份不碰本机自律数据`() { assertOldBackupKeepsLocal(2) }

    @Test
    fun `v3 老备份不碰本机自律数据`() { assertOldBackupKeepsLocal(3) }

    @Test
    fun `v4 备份按 selfDiscipline 段整包覆盖`() = runBlocking {
        seedLocal()
        val json = """
            {
              "app":"qingji","version":4,
              "selfDiscipline":{
                "goals":[{"id":99,"title":"新目标","goalType":"custom","startDate":"2026-11-01"}],
                "periods":[{"id":9,"goalId":99,"startAt":"2026-11-01","status":"active"}],
                "checkins":[{"id":9,"goalId":99,"localDate":"2026-11-02","status":"on_target"}],
                "urgeLogs":[{"id":9,"goalId":99,"occurredAt":1}],
                "settings":[{"key":"default_goal","value":"99"}],
                "milestones":[{"id":9,"goalId":99,"milestoneDays":7}],
                "interventions":[{"id":9,"goalId":99,"type":"breath","occurredAt":1}]
              }
            }
        """.trimIndent()
        val parsed = parseBackup(json).getOrThrow()
        assertTrue(parsed.hasSelfDiscipline)
        assertTrue(parsed.selfDiscipline != null)
        db.withTransaction { manager().restoreInto(db, parsed) }

        val goals = db.selfDisciplineGoalDao().findAll()
        assertEquals(1, goals.size)
        assertEquals(99L, goals.first().id)
        assertEquals("新目标", goals.first().title)
        assertEquals(1, db.selfDisciplinePeriodDao().findAll().size)
        assertEquals(1, db.selfDisciplineCheckinDao().findAll().size)
        assertEquals(1, db.selfDisciplineUrgeDao().findAll().size)
        assertEquals(1, db.selfDisciplineSettingDao().getAll().size)
        assertEquals(1, db.selfDisciplineMilestoneDao().findAll().size)
        assertEquals(1, db.selfDisciplineInterventionDao().findAll().size)
        // 子表 goalId 仍指向备份里的 goals.id
        assertEquals(99L, db.selfDisciplinePeriodDao().findAll().first().goalId)
        assertEquals(99L, db.selfDisciplineCheckinDao().findAll().first().goalId)
        assertEquals(99L, db.selfDisciplineUrgeDao().findAll().first().goalId)
        assertEquals(99L, db.selfDisciplineMilestoneDao().findAll().first().goalId)
        assertEquals(99L, db.selfDisciplineInterventionDao().findAll().first().goalId)
    }

    @Test
    fun `selfDiscipline 段读坏时保留本机数据`() = runBlocking {
        seedLocal()
        // 键存在但不是 JSONObject：optJSONObject 返回 null → sd=null，hasSelfDiscipline=true
        val parsed = parseBackup("""{"app":"qingji","version":4,"selfDiscipline":"坏掉的字符串"}""").getOrThrow()
        assertTrue(parsed.hasSelfDiscipline)
        assertNull(parsed.selfDiscipline)
        db.withTransaction { manager().restoreInto(db, parsed) }
        assertSevenTablesUnchanged()
    }

    @Test
    fun `导出再恢复后 goalId 关联不断`() = runBlocking {
        // 本机一组带关联的数据
        db.selfDisciplineGoalDao().insert(goal(42L, "往返目标"))
        db.selfDisciplinePeriodDao().insert(period(7L, 42L))
        db.selfDisciplineCheckinDao().upsert(checkin(7L, 42L, LocalDate.of(2026, 10, 2)))
        db.selfDisciplineUrgeDao().insert(urge(7L, 42L))
        db.selfDisciplineSettingDao().set(setting("default_goal", "42"))
        db.selfDisciplineMilestoneDao().insert(milestone(7L, 42L))
        db.selfDisciplineInterventionDao().insert(intervention(7L, 42L))

        // 模拟 buildJson：读出 7 表构造整段，再塞进一份 v4 备份
        val backup = SelfDisciplineBackup(
            goals = db.selfDisciplineGoalDao().findAll(),
            periods = db.selfDisciplinePeriodDao().findAll(),
            checkins = db.selfDisciplineCheckinDao().findAll(),
            urgeLogs = db.selfDisciplineUrgeDao().findAll(),
            settings = db.selfDisciplineSettingDao().getAll(),
            milestones = db.selfDisciplineMilestoneDao().findAll(),
            interventions = db.selfDisciplineInterventionDao().findAll()
        )
        val root = JSONObject().apply {
            put("app", "qingji")
            put("version", 4)
            put("selfDiscipline", backup.toJson())
        }
        val parsed = parseBackup(root.toString()).getOrThrow()
        assertTrue(parsed.hasSelfDiscipline)
        db.withTransaction { manager().restoreInto(db, parsed) }

        val goals = db.selfDisciplineGoalDao().findAll()
        assertEquals(1, goals.size)
        assertEquals(42L, goals.first().id)
        assertEquals(42L, db.selfDisciplinePeriodDao().findAll().first().goalId)
        assertEquals(42L, db.selfDisciplineCheckinDao().findAll().first().goalId)
        assertEquals(42L, db.selfDisciplineUrgeDao().findAll().first().goalId)
        assertEquals(42L, db.selfDisciplineMilestoneDao().findAll().first().goalId)
        assertEquals(42L, db.selfDisciplineInterventionDao().findAll().first().goalId)
        assertEquals("42", db.selfDisciplineSettingDao().get("default_goal")?.value)
    }
}
