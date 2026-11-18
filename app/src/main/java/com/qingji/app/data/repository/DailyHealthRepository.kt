package com.qingji.app.data.repository

import android.content.Context
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
    suspend fun summaryFor(date: LocalDate): DailyHealthSummaryEntity {
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
        val sig = listOf(
            food?.count ?: 0,
            round10(food?.kcalSum ?: 0.0),
            round10(food?.proteinSum ?: 0.0),
            ledger.sumOf { it.count },
            round10(ledger.sumOf { it.kcalSum }),
            session?.count ?: 0,
            workoutSets.size,
            sleep?.count ?: 0,
            sleep?.minutesSum ?: 0
        ).joinToString("|")

        val cached = summaryDao.findByDate(date.toString())
        if (cached != null && cached.signature == sig) return cached

        // ---- 缓存失效或没有：用刚拿到的聚合值构造 + 落表 ----
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
        val rebuilt = DailyHealthAggregator.aggregate(date, raw, System.currentTimeMillis())
        summaryDao.upsert(rebuilt)
        return rebuilt
    }

    /** 区间批量（统计页用；逐日签名校验，失效的重建）。 */
    suspend fun summariesFor(from: LocalDate, to: LocalDate): List<DailyHealthSummaryEntity> {
        val out = mutableListOf<DailyHealthSummaryEntity>()
        var d = from
        while (!d.isAfter(to)) {
            out.add(summaryFor(d))
            d = d.plusDays(1)
        }
        return out
    }

    /** 手动清缓存（备份恢复 / 数据导入后调，下次查询自动重建）。 */
    suspend fun invalidateAll() = summaryDao.clearAll()

    private fun round10(v: Double): Double = kotlin.math.round(v * 10) / 10
}
