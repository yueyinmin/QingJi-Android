package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠记录表（Room 13 → 14）
 * ------------------------------------------------------------
 * 规矩沿用健康体系 P0 那套：
 * 1. 全新表，MIGRATION_13_14 只有 CREATE TABLE，一行旧数据都不碰；
 * 2. 每个非主键列都写 @ColumnInfo(defaultValue = ...)，
 *    迁移 SQL 里的 DEFAULT 必须和这里逐字一致，否则 Room 打开时校验不过；
 * 3. 睡眠日期归属 = 起床日期（工单 9.4）：23:30 入睡、07:30 起床 → 归属起床那天。
 *
 * 工单 9.6 设备扩展预留：sourceType 至少支持 MANUAL / HEALTH_CONNECT / DEVICE，
 * 外部记录留 externalId 与原始时间区间（startTime/endTime 就是原始区间，绝不丢）。
 * 没有设备数据时不伪造深睡/浅睡/REM —— 所以没有阶段字段。
 * ============================================================
 */
@Entity(
    tableName = "sleep_record",
    indices = [Index("date"), Index(value = ["sourceType", "externalId"], unique = true)]
)
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 归属日期（起床日期，工单 9.4） */
    val date: LocalDate,
    /** 入睡时间（epoch 毫秒，原始区间起点，跨午夜场景完整保留） */
    val startTime: Long,
    /** 起床时间（epoch 毫秒，原始区间终点） */
    val endTime: Long,
    /** 睡眠时长（分钟）= (endTime - startTime) / 60000，存快照方便统计免重算 */
    @ColumnInfo(defaultValue = "0") val durationMinutes: Int = 0,
    /** 睡眠质量：1 很差 / 2 较差 / 3 一般 / 4 良好 / 5 很好（0 = 没填） */
    @ColumnInfo(defaultValue = "0") val quality: Int = 0,
    /** 起床精神状态：1 疲惫 / 2 一般 / 3 良好 / 4 精力充沛（0 = 没填） */
    @ColumnInfo(defaultValue = "0") val morningFeeling: Int = 0,
    /** 夜间醒来次数（0 = 没填或没醒） */
    @ColumnInfo(defaultValue = "0") val wakeCount: Int = 0,
    /** 午睡时长（分钟，可选，0 = 没午睡/没填） */
    @ColumnInfo(defaultValue = "0") val napMinutes: Int = 0,
    @ColumnInfo(defaultValue = "") val note: String = "",
    /**
     * 数据来源：MANUAL（手动，第一版唯一入口）/
     * HEALTH_CONNECT / DEVICE（设备同步预留）
     */
    @ColumnInfo(defaultValue = "MANUAL") val sourceType: String = "MANUAL",
    /** 外部记录 ID（设备同步用，手动记录为空串；和 sourceType 组成唯一索引防重复导入） */
    @ColumnInfo(defaultValue = "") val externalId: String = "",
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0
) {
    companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_HEALTH_CONNECT = "HEALTH_CONNECT"
        const val SOURCE_DEVICE = "DEVICE"

        /** 睡眠质量文案（1~5）。0 表示没填。 */
        fun qualityLabel(quality: Int): String = when (quality) {
            1 -> "很差"; 2 -> "较差"; 3 -> "一般"; 4 -> "良好"; 5 -> "很好"; else -> "未填写"
        }

        /** 起床精神状态文案（1~4）。0 表示没填。 */
        fun feelingLabel(feeling: Int): String = when (feeling) {
            1 -> "疲惫"; 2 -> "一般"; 3 -> "良好"; 4 -> "精力充沛"; else -> "未填写"
        }
    }
}
