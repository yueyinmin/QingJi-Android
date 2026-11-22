package com.qingji.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.DailyHealthSummaryEntity
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.DailyHealthAggregator
import com.qingji.app.domain.TrainingMath
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.1 · 每日健康汇总仓库（工单 10.1：统一 Repository 生成）
 * ------------------------------------------------------------
 * 「缓存 + 签名失效」机制（工单 10.2：可缓存、可重建）：
 * 1. 查询时对五个原始源各跑一条 SUM 聚合 SQL（轻量），构造签名；
 * 2. 签名与缓存行一致 → 直接返回缓存（日历快速翻页不重算）；
 * 3. 签名不一致 / 没缓存行 → 用刚拿到的聚合值直接构造汇总行落表（可重建）；
 * 4. 原始数据任何增删改都会让签名变化 → 写路径零侵入，不存在脏缓存。
 * 聚合口径在 domain/DailyHealthAggregator（纯逻辑，有单测）；
 * 容量 kg 与消耗 kcal 是两个独立字段，绝不混算（工单 10.2 红字）。
 * ============================================================
 */
class DailyHealthRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val summaryDao = db.dailyHealthSummaryDao()
    private val foodLogDao = db.foodLogDao()
    private val ledgerDao = db.exerciseSessionCalorieDao()
    private val sessionDao = db.workoutSessionDao()
    private val workoutExerciseDao = db.workoutExerciseDao()
    private val sleepDao = db.sleepDao()

    /** 取某天汇总（缓存优先、签名失效自动重建）。 */
    suspend fun summaryFor(date: LocalDate): DailyHealthSummaryEntity = summaryFor(date, null)

    /**
     * _widget 专属_：只取当日训练时长（分钟），口径与 [summaryFor] 里的
     * `session?.durationSum ?: 0` 完全一致。DAO 聚合对空表返回 count=0/sum=0，
     * 即「真 0」；只有读库异常才由 WidgetSnapshotRepo 映射为 FAILED。
     * 单独暴露是为了让 Widget 的训练/睡眠可用状态彼此独立（验收补充工单 P1），
     * 一个数据源异常不会拖垮另一个；不改动 summaryFor 的任何既有行为。
     */
    suspend fun workoutMinutesFor(date: LocalDate): Int? =
        sessionDao.dayAggregate(date.toString())?.durationSum

    /** 同 [workoutMinutesFor]，只取睡眠分钟，口径 `sleep?.minutesSum ?: 0`。 */
    suspend fun sleepMinutesFor(date: LocalDate): Int? =
        sleepDao.dayAggregate(date.toString())?.minutesSum

    /**
     * 内部单日实现。@param prefetched 区间批量时预取的缓存行（按日期），
     * 命中则免掉逐日 findByDate 的数据库往返；口径与 summaryFor 完全一致。
     */
    private suspend fun summaryFor(
        date: LocalDate,
        prefetched: Map<String, DailyHealthSummaryEntity>?
    ): DailyHealthSummaryEntity {
        // ---- 五源轻量聚合（每源一条 SUM SQL，不拉明细行） ----
        val food = foodLogDao.dayAggregate(date)
        val ledger = ledgerDao.dayAggregate(date.toString())
        val session = sessionDao.dayAggregate(date.toString())
        val setsJsonList = workoutExerciseDao.findSetsBySessionDate(date.toString())
        val sleep = sleepDao.dayAggregate(date.toString())

        val workoutSets = setsJsonList.flatMap { json ->
            TrainingMath.decodeSets(json).map { it.weightKg to it.reps }
        }
        val ledgerRows = ledger.map { DailyHealthAggregator.LedgerRow(it.sourceType, it.kcalSum) }

        // 唯一原始输入：先构造 raw，再用【同一个】signatureOf 做比对与落表。
        // QJ-GOV-2026-01/P1-01：删掉原先 Repository 手写的第二套签名（口径与落表不一致）。
        val raw = DailyHealthAggregator.RawDay(
            foodLogCount = food?.count ?: 0,
            foodKcal = food?.kcalSum ?: 0.0,
            foodProteinG = food?.proteinSum ?: 0.0,
            foodCarbG = food?.carbSum ?: 0.0,
            foodFatG = food?.fatSum ?: 0.0,
            ledger = ledgerRows,
            workoutCount = session?.count ?: 0,
            workoutDurationMinutes = session?.durationSum ?: 0,
            workoutSets = workoutSets,
            sleepRecordCount = sleep?.count ?: 0,
            sleepDurationMinutes = sleep?.minutesSum ?: 0
        )
        val sig = DailyHealthAggregator.signatureOf(raw)

        val dateKey = date.toString()
        val cached = prefetched?.get(dateKey) ?: summaryDao.findByDate(dateKey)
        if (cached != null && cached.signature == sig) return cached

        // ---- 缓存失效或没有：用刚拿到的原始值构造 + 落表 ----
        val rebuilt = DailyHealthAggregator.aggregate(date, raw, System.currentTimeMillis())
        summaryDao.upsert(rebuilt)
        return rebuilt
    }

    /**
     * 区间批量（QJ-GOV-2026-03 / P2-01）。
     *
     * 采用「一次 findRange 预取缓存 + 单事务覆盖区间」：
     * - 缓存读取从 N 次 findByDate 降为 1 次 findRange；
     * - N 次 upsert 收进一个 withTransaction，避免部分日期成功部分失败的半状态；
     * - 单日聚合仍逐日走同一 summaryFor 逻辑（含统一 signatureOf），
     *   保证与 summaryFor(date) 逐日结果完全一致，不复制任何业务口径。
     *
     * 未新增源表区间聚合 SQL：summariesFor 当前无生产调用方（工单要求不得为
     * 优化未使用代码扩大改造），批量源聚合留待有真实调用与性能实测后再评估。
     */
    suspend fun summariesFor(from: LocalDate, to: LocalDate): List<DailyHealthSummaryEntity> {
        if (from.isAfter(to)) return emptyList()
        return db.withTransaction {
            val cached = summaryDao.findRange(from.toString(), to.toString())
                .associateBy { it.date.toString() }
            val out = mutableListOf<DailyHealthSummaryEntity>()
            var d = from
            while (!d.isAfter(to)) {
                out.add(summaryFor(d, cached))
                d = d.plusDays(1)
            }
            out
        }
    }

    /**
     * 手动清缓存（备份恢复 / 数据导入 / 清空记录 / 恢复出厂后调，下次查询自动重建）。
     * P1-01：补上生产调用点，避免这些路径残留旧汇总。
     */
    suspend fun invalidateAll() = summaryDao.clearAll()

    /** 只失效指定日期（跨日期记录按既有归属规则只影响相关日期）。 */
    suspend fun invalidateDate(date: LocalDate) = summaryDao.deleteByDate(date.toString())
}
