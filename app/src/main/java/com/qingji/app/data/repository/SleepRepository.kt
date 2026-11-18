package com.qingji.app.data.repository

import android.content.Context
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.domain.AppClock
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠记录仓库（工单 9）
 * ------------------------------------------------------------
 * 规则（工单原文落到代码的地方）：
 * 1. 日期归属 = 起床日期（工单 9.4）：23:30 入睡、07:30 起床 → date 归属 07:30 那天；
 *    startTime/endTime 原始区间永远完整保留，归属只是统计口径；
 * 2. 跨午夜天然支持：endTime > startTime 才合法（支持跨天的长睡，如 23:30→次日 07:30）；
 * 3. 手动记录 externalId 用 UUID，保证 (sourceType, externalId) 唯一索引不撞行；
 *    设备同步（预留）进来时用外部 ID 去重，重复导入 = 覆盖同一条；
 * 4. 无记录的地方显示「尚未记录」，不伪造 0 小时——这是 UI 层的规矩，
 *    这里只保证「查不到就返回空列表」。
 * ============================================================
 */
class SleepRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.sleepDao()

    /** 新增 / 编辑保存。编辑时传原 id 与原 externalId。 */
    suspend fun save(
        id: Long = 0,
        startTime: Long,
        endTime: Long,
        quality: Int = 0,
        morningFeeling: Int = 0,
        wakeCount: Int = 0,
        napMinutes: Int = 0,
        note: String = "",
        sourceType: String = SleepRecordEntity.SOURCE_MANUAL,
        externalId: String = ""
    ): Result<SleepRecordEntity> {
        // 基础校验：起床必须晚于入睡；同一天内也允许（午睡补录走 napMinutes 字段）
        if (endTime <= startTime) {
            return Result.failure(IllegalArgumentException("起床时间必须晚于入睡时间"))
        }
        val durationMinutes = ((endTime - startTime) / 60_000L).toInt()
        if (durationMinutes > 24 * 60) {
            return Result.failure(IllegalArgumentException("睡眠时长不能超过 24 小时，请检查时间填反了没有"))
        }
        // 归属日期 = 起床那一刻的日历日（工单 9.4）
        val date = AppClock.ofEpochMillisToLocalDate(endTime)
        val now = System.currentTimeMillis()
        val record = SleepRecordEntity(
            id = id,
            date = date,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            quality = quality.coerceIn(0, 5),
            morningFeeling = morningFeeling.coerceIn(0, 4),
            wakeCount = wakeCount.coerceAtLeast(0),
            napMinutes = napMinutes.coerceAtLeast(0),
            note = note,
            sourceType = sourceType,
            externalId = externalId.ifBlank { UUID.randomUUID().toString() },
            createdAt = now,
            updatedAt = now
        )
        return runCatching {
            if (id > 0L) {
                // 编辑：保留原 createdAt，只刷 updatedAt
                val old = dao.findRange(date.toString(), date.toString()).firstOrNull { it.id == id }
                dao.update(record.copy(createdAt = old?.createdAt ?: now))
            } else {
                dao.insert(record)
            }
            record
        }
    }

    /** 删除（历史记录页长按删除用）。 */
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** 某一天的睡眠列表（归属日期 = 起床日期）。 */
    fun observeByDate(date: LocalDate): Flow<List<SleepRecordEntity>> =
        dao.observeByDate(date.toString())

    /** 日期区间（含两端），7 / 30 天趋势用。 */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepRecordEntity>> =
        dao.observeRange(from.toString(), to.toString())

    /** 全部记录，新到旧。 */
    fun observeAll(): Flow<List<SleepRecordEntity>> = dao.observeAll()
}
