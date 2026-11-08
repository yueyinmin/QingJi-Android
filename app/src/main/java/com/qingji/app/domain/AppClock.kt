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
}
