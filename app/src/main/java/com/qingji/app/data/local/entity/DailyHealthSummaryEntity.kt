package com.qingji.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * ============================================================
 * 健康体系 V1.1 · 每日健康汇总缓存表（Room 14 → 15，工单 10.2）
 * ------------------------------------------------------------
 * 只是「可重建的缓存」：聚合口径全部来自原始表（FoodLog / 消耗账本 /
 * workout_session / sleep_record），删掉这行也能立刻算回来（工单 10.2）。
 * signature 存各原始源当日轻量聚合的拼接值；查询时签名对不上就整行重建，
 * 所以原始数据一变，缓存自动失效，不存在「改了记录缓存还是旧的」。
 * 三种业务原始数据不混存（工单 10.1），这张表只放算好的结果。
 * ============================================================
 */
@Entity(
    tableName = "daily_health_summary",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyHealthSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    // ---------- 饮食（FoodLog 当日求和） ----------
    @ColumnInfo(defaultValue = "0.0") val calorieIntake: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val proteinG: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val carbohydratesG: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val fatG: Double = 0.0,
    // ---------- 运动消耗（统一账本按来源分类；exerciseCalories = 全部来源总和） ----------
    @ColumnInfo(defaultValue = "0.0") val exerciseCalories: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val strengthCalories: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val cardioCalories: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val walkingCalories: Double = 0.0,
    // ---------- 力量训练（workout_session 当日；容量 kg 与消耗 kcal 必须分开，工单 10.2） ----------
    @ColumnInfo(defaultValue = "0") val workoutDurationMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val workoutCount: Int = 0,
    @ColumnInfo(defaultValue = "0.0") val workoutVolumeKg: Double = 0.0,
    // ---------- 睡眠（sleep_record 按归属日期 = 起床日期） ----------
    @ColumnInfo(defaultValue = "0") val sleepDurationMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0") val sleepRecordCount: Int = 0,
    /** 原始源签名（重建判据），空串 = 从未计算 */
    @ColumnInfo(defaultValue = "") val signature: String = "",
    @ColumnInfo(defaultValue = "0") val lastCalculatedAt: Long = 0
)
