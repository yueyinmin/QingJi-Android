package com.qingji.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ============================================================
 * App 的「现在几点 / 今天是哪天」唯一入口（M7 记录时区）
 * ------------------------------------------------------------
 * 为什么不能到处直接写 LocalDate.now()：
 * 那个永远按手机系统时区算，用户在设置里选了「记录时区」也不会生效。
 * 全部改成走这里之后，只要在启动时把时区设进来，全 App 的
 * 「今天」就跟着一起变，不用去改几十个页面。
 *
 * 默认值就是手机系统时区 —— 没改设置时，行为跟以前完全一样。
 * ============================================================
 */
object AppClock {

    /** 当前使用的时区，默认跟随手机。 */
    @Volatile
    var zone: ZoneId = ZoneId.systemDefault()

    /** 按设置里的时区取「今天」。记录、统计、日期条都用它。 */
    fun today(): LocalDate = LocalDate.now(zone)

    /** 需要具体时刻时用（比如通知文案里的小时分钟）。 */
    fun now(): LocalDateTime = LocalDateTime.now(zone)

    /** 设置页选了时区就调这里。传错值不抛异常，保持原样。 */
    fun setZone(zoneId: String) {
        zone = try {
            ZoneId.of(zoneId)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }
    }

    /**
     * 健康体系 V1.1：把 epoch 毫秒按当前时区换算成日历日。
     * 睡眠归属日期（工单 9.4：按起床日期归属）用它——
     * 必须走统一时区，不能用系统默认，否则统计口径不一致。
     */
    fun ofEpochMillisToLocalDate(epochMillis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** epoch 毫秒 → 本地时刻（按当前记录时区，别用系统默认）。 */
    fun ofEpochMillisToLocalTime(epochMillis: Long): java.time.LocalTime =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()

    // ---------- 统一的时间展示格式（QJ-RECORD-INPUT-UNIFIED-V3 / 8.3） ----------
    // 以前饮食和饮水各写一份 DateTimeFormatter.ofPattern("HH:mm")，
    // 时区/格式一旦不一致，同一个 createdAt 在两处会显示成不同时间。
    // 所有「几点几分」都从这里取。

    private val clockFormatter: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    /** 时刻 → "HH:mm"。 */
    fun formatClock(time: java.time.LocalTime): String = clockFormatter.format(time)

    /** epoch 毫秒 → "HH:mm"（按 AppClock.zone）。 */
    fun formatClock(epochMillis: Long): String = clockFormatter.format(ofEpochMillisToLocalTime(epochMillis))

    /** 日期 → "11月26日"（弹层日期入口的短标签）。 */
    fun formatDateLabel(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    /** 日期 → "2026年11月26日"（详情页标题）。 */
    fun formatDateFull(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}
