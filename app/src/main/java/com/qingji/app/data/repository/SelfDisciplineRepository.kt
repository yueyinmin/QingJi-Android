package com.qingji.app.data.repository

import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.SelfDisciplineCheckinEntity
import com.qingji.app.data.local.entity.SelfDisciplineGoalEntity
import com.qingji.app.data.local.entity.SelfDisciplinePeriodEntity
import com.qingji.app.data.local.entity.SelfDisciplineSettingEntity
import com.qingji.app.data.local.entity.SelfDisciplineUrgeEntity
import com.qingji.app.domain.SelfDisciplineContracts
import com.qingji.app.domain.SelfDisciplineEngine
import java.time.LocalDate

/**
 * ============================================================
 * 自律管理仓库（SD-013 / SD-003 / SD-004）
 * ------------------------------------------------------------
 * 和训练中心一样：构造拿 Context，直接 AppDatabase.get(context) 取 DAO，不引 Hilt。
 * 复杂状态变更（删目标级联、偏离结束周期、重新开始）一律包在事务里，
 * 不让「有周期没目标」「结束了周期却没写偏离打卡」这类半成品出现。
 * ============================================================
 */

/** 仪表盘统计快照（首页 / 历史页共用）。 */
data class SelfDisciplineStats(
    val hasGoal: Boolean,
    val currentDays: Int,
    val bestDays: Int,
    val onTargetDays: Int,
    val deviationCount: Int,
    val streak: Int,
    val urgeCount: Int,
    val totalCheckins: Int
)

class SelfDisciplineRepository(
    context: android.content.Context,
    /**
     * 默认取全局单例；单测可以传内存库进来，避免测试之间互相污染。
     * 不改调用方写法：所有页面仍然 new SelfDisciplineRepository(context)。
     */
    private val db: AppDatabase = AppDatabase.get(context)
) {

    private val goalDao = db.selfDisciplineGoalDao()
    private val periodDao = db.selfDisciplinePeriodDao()
    private val checkinDao = db.selfDisciplineCheckinDao()
    private val urgeDao = db.selfDisciplineUrgeDao()
    private val settingDao = db.selfDisciplineSettingDao()
    private val milestoneDao = db.selfDisciplineMilestoneDao()
    private val interventionDao = db.selfDisciplineInterventionDao()

    // ---------------- 目标管理（SD-003） ----------------

    /** 新建目标：插入目标后立刻开一个进行中周期（startAt = 目标开始日）。 */
    suspend fun createGoal(goal: SelfDisciplineGoalEntity): Long = db.withTransaction {
        val id = goalDao.insert(goal.copy(id = 0))
        periodDao.insert(
            SelfDisciplinePeriodEntity(goalId = id, startAt = goal.startDate, status = SelfDisciplineContracts.PERIOD_ACTIVE)
        )
        id
    }

    /** 编辑目标（P0 不改开始日，避免误伤历史周期）。 */
    suspend fun updateGoal(goal: SelfDisciplineGoalEntity) = goalDao.update(goal)

    suspend fun setGoalEnabled(id: Long, enabled: Boolean) = goalDao.setEnabled(id, enabled)

    /** 删除目标：级联清掉它名下的周期 / 打卡 / 冲动 / 里程碑 / 应对记录，再删目标本身。 */
    suspend fun deleteGoal(id: Long) = db.withTransaction {
        checkinDao.deleteByGoal(id)
        urgeDao.deleteByGoal(id)
        milestoneDao.deleteByGoal(id)
        interventionDao.deleteByGoal(id)
        periodDao.deleteByGoal(id)
        goalDao.deleteById(id)
        // 顺手清掉「主要目标」指向，避免悬空
        val cur = settingDao.get(SelfDisciplineContracts.SETTING_DEFAULT_GOAL)?.value
        if (cur == id.toString()) settingDao.set(
            SelfDisciplineSettingEntity(SelfDisciplineContracts.SETTING_DEFAULT_GOAL, "")
        )
    }

    fun observeGoals() = goalDao.observeAll()
    suspend fun getGoal(id: Long) = goalDao.getById(id)

    /** 主要目标：优先取设置里指定的；没有或已失效则退回第一个启用中的。 */
    suspend fun getPrimaryGoal(): SelfDisciplineGoalEntity? {
        val preferred = settingDao.get(SelfDisciplineContracts.SETTING_DEFAULT_GOAL)?.value
        if (!preferred.isNullOrEmpty()) {
            val g = goalDao.getById(preferred.toLongOrNull() ?: -1)
            if (g != null && g.isEnabled) return g
        }
        return goalDao.getDefaultEnabled()
    }

    // ---------------- 周期 / 计时（SD-004） ----------------

    fun observePeriods(goalId: Long) = periodDao.observeByGoal(goalId)
    suspend fun getActivePeriod(goalId: Long) = periodDao.getActive(goalId)

    /** 当前进行中周期已坚持天数。 */
    suspend fun currentDays(goalId: Long): Int {
        val active = periodDao.getActive(goalId) ?: return 0
        return SelfDisciplineEngine.currentPeriodDays(active.startAt)
    }

    /** 历史最佳天数。 */
    suspend fun bestDays(goalId: Long): Int {
        val periods = periodDao.findAllByGoal(goalId)
        return SelfDisciplineEngine.bestPeriodDays(
            periods.map { SelfDisciplineEngine.PeriodInput(it.startAt, it.endAt) }
        )
    }

    /** 结束进行中周期（偏离 / 达成 / 手动）。 */
    suspend fun endActivePeriod(goalId: Long, endDate: LocalDate, reason: String) {
        val active = periodDao.getActive(goalId) ?: return
        periodDao.endPeriod(active.id, endDate, reason)
    }

    /** 重新开始：先结束进行中周期（手动），再开一个新的进行中周期（从 startDate 起）。 */
    suspend fun restartPeriod(goalId: Long, startDate: LocalDate) = db.withTransaction {
        periodDao.getActive(goalId)?.let { periodDao.endPeriod(it.id, startDate.minusDays(1), SelfDisciplineContracts.REASON_MANUAL) }
        periodDao.insert(
            SelfDisciplinePeriodEntity(goalId = goalId, startAt = startDate, status = SelfDisciplineContracts.PERIOD_ACTIVE)
        )
    }

    // ---------------- 每日打卡（SD-005） ----------------

    suspend fun saveCheckin(checkin: SelfDisciplineCheckinEntity) = checkinDao.upsert(checkin)

    suspend fun getCheckin(goalId: Long, date: LocalDate) = checkinDao.getByDate(goalId, date)

    fun observeCheckins(goalId: Long) = checkinDao.observeByGoal(goalId)

    fun observeCheckinRange(goalId: Long, from: LocalDate, to: LocalDate) =
        checkinDao.observeRange(goalId, from, to)

    suspend fun deleteCheckin(id: Long) = checkinDao.deleteById(id)

    // ---------------- 冲动记录（SD-006） ----------------

    suspend fun addUrge(urge: SelfDisciplineUrgeEntity) = urgeDao.insert(urge)
    fun observeUrges(goalId: Long) = urgeDao.observeByGoal(goalId)
    suspend fun deleteUrge(id: Long) = urgeDao.deleteById(id)

    // ---------------- 目标偏离（SD-007） ----------------

    /**
     * 记录目标偏离：结束进行中周期（endAt = 偏离日），并写一条 off_target 打卡标记那天。
     * 不自动重开周期，用户之后可手动「重新开始」。
     * @return 被结束的周期 id（用于撤销）。
     */
    suspend fun recordDeviation(
        goalId: Long,
        date: LocalDate,
        triggers: String,
        mood: String,
        note: String
    ): Long = db.withTransaction {
        val active = periodDao.getActive(goalId)
        val periodId = active?.id ?: -1L
        // 偏离日不能早于周期开始，也不能晚于今天
        val safeDate = when {
            active != null && date.isBefore(active.startAt) -> active.startAt
            date.isAfter(LocalDate.now()) -> LocalDate.now()
            else -> date
        }
        if (active != null) {
            periodDao.endPeriod(active.id, safeDate, SelfDisciplineContracts.REASON_DEVIATION)
        }
        // 写一条偏离打卡，让日历能显示那天是「目标偏离」。
        // 但这天如果已经有用户自己的打卡（比如上午记了「符合目标」），绝不覆盖 ——
        // 工单明确要求「不得删除此前的正常记录」，撤销时也就不会把原记录一起删掉。
        // 日历的偏离色另从「周期结束原因」里取（见 HistoryScreen），不依赖这条打卡。
        if (checkinDao.getByDate(goalId, safeDate) == null) {
            checkinDao.upsert(
                SelfDisciplineCheckinEntity(
                    goalId = goalId,
                    localDate = safeDate,
                    status = SelfDisciplineContracts.STATUS_OFF_TARGET,
                    mood = mood,
                    triggers = triggers,
                    note = note
                )
            )
        }
        periodId
    }

    /** 撤销偏离：重开被结束的周期（清 endAt / status=active），并删掉那条偏离打卡。 */
    suspend fun undoDeviation(periodId: Long, deviationDate: LocalDate, goalId: Long) = db.withTransaction {
        periodDao.reopen(periodId)
        checkinDao.getByDate(goalId, deviationDate)?.let {
            if (it.status == SelfDisciplineContracts.STATUS_OFF_TARGET) checkinDao.deleteById(it.id)
        }
    }

    // ---------------- 统计（SD-009 基础，P0 先给核心几项） ----------------

    suspend fun computeStats(goalId: Long): SelfDisciplineStats {
        val goal = goalDao.getById(goalId) ?: return SelfDisciplineStats(
            hasGoal = false, 0, 0, 0, 0, 0, 0, 0
        )
        val periods = periodDao.findAllByGoal(goalId)
        val checkins = checkinDao.findAll().filter { it.goalId == goalId }
        val urges = urgeDao.findAllByGoal(goalId)
        val active = periods.firstOrNull { it.status == SelfDisciplineContracts.PERIOD_ACTIVE }
        val recordedDates = checkins
            .filter { it.status != SelfDisciplineContracts.STATUS_SKIP }
            .map { it.localDate }.toSet()
        return SelfDisciplineStats(
            hasGoal = true,
            currentDays = SelfDisciplineEngine.currentPeriodDays(active?.startAt),
            bestDays = SelfDisciplineEngine.bestPeriodDays(
                periods.map { SelfDisciplineEngine.PeriodInput(it.startAt, it.endAt) }
            ),
            onTargetDays = SelfDisciplineEngine.onTargetDays(checkins.map { it.status }),
            // 偏离次数按「周期结束原因」算，而不是数 off_target 打卡：
            // 当天已有正常打卡时我们不会覆盖它，数打卡会漏掉这次偏离。
            deviationCount = periods.count { it.endReason == SelfDisciplineContracts.REASON_DEVIATION },
            streak = SelfDisciplineEngine.currentStreak(recordedDates),
            urgeCount = urges.size,
            totalCheckins = checkins.size
        )
    }

    // ---------------- 设置 ----------------

    suspend fun getSetting(key: String): String? = settingDao.get(key)?.value
    suspend fun setSetting(key: String, value: String) = settingDao.set(SelfDisciplineSettingEntity(key, value))
}
