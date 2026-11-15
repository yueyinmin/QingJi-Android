package com.qingji.app.domain

/**
 * ============================================================
 * 时间 / 时长格式化的唯一入口
 * ------------------------------------------------------------
 * 2026-11-15：`"%02d:%02d"` 这类格式串原先手写在 7 个文件里，
 * 而且两种完全不同的语义混用同一个串：
 *   · 「秒数」—— 计时条、断食时钟、训练计时；
 *   · 「当天第几分钟」—— 断食窗口 07:40 这种。
 * 改一处格式得改七遍，漏一个就出现「20:00 和 20:0」并存。
 * 这里按语义拆成五个函数，其余地方一律调这里。
 * ============================================================
 */
object TimeFormat {

    /** 秒数 → 00:12:34。跨日不截断，26:10:05 这样显示。断食时钟用它。 */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    fun clock(totalSeconds: Int): String = clock(totalSeconds.toLong())

    /** 秒数 → 01:23:45；不足 1 小时省略小时段，显示 23:45。训练计时用它。 */
    fun stopwatch(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, sec)
        else "%02d:%02d".format(m, sec)
    }

    fun stopwatch(totalSeconds: Int): String = stopwatch(totalSeconds.toLong())

    /** 小时 + 分钟 → 07:40。提醒时间用它，越界值会夹紧到 00~23 / 00~59。 */
    fun hourMinute(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /** 「当天第几分钟」→ 07:40。跨日会绕回，1440 → 00:00。断食窗口用它。 */
    fun dayMinute(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** 时长分钟数 → 09:32。这是「多久」，不是「几点」，所以不取模。 */
    fun durationMinute(totalMinutes: Int): String {
        val m = totalMinutes.coerceAtLeast(0)
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
