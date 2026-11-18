package com.qingji.app.domain

import com.qingji.app.data.local.entity.SleepRecordEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ============================================================
 * 健康体系 V1.1 · 睡眠评分计算器（工单 · 睡眠页重设计 P0）
 * ------------------------------------------------------------
 * 纯函数：输入一条睡眠记录 + 近 7 天记录，输出 0~100 的分数、评级档、
 * 逐项明细（UI 长按「分数怎么来的」用）。不落库、不依赖 Android / Compose，
 * 单测直接跑在 JVM 上。
 *
 * 评分口径（工单原文，逐条对应下方代码）：
 *   1. 基础 60 分；
 *   2. 时长达成率 = min(时长 / 目标时长, 1) × 25 分（目标默认 8h，设置页可调，暂用常量）；
 *   3. 质量档：很好 +10 / 良好 +6 / 一般 +3 / 较差 −5 / 很差 −10；
 *   4. 起床精神：精力充沛 +5 / 良好 +3 / 一般 0 / 疲惫 −5；
 *   5. 夜醒：没醒 +0，每醒 1 次 −3（上限 −9）；
 *   6. 规律性（P1 加分项）：入睡时间相对本人近 7 天中位数偏移
 *      ≤30min +0，31–60min −2，>60min −4；数据不足 3 晚不计分（显示 —）；
 *   7. clamp 到 0–100；评级：<60 较差，60–74 一般，75–89 良好，≥90 优秀。
 *
 * 红线：这是「根据你的记录估算」的自评合成分，不是设备监测分。
 * 界面必须标注这句，且不能据此推断深睡 / REM / 鼾声（无数据来源）。
 *
 * 关于入睡时刻的「睡眠轴」：
 * 入睡 23:30 和 00:27 在钟面上分属两天，直接按 0~1439 分钟比大小会把
 * 跨午夜的规律性算错（23:30 离 00:27 明明只差 57 分钟，却被当成 23 小时）。
 * 这里把早于 12:00 的入睡时刻 +1440 分钟映射到「睡眠轴」，
 * 再按环形距离算偏移，跨午夜就对了。中位数同样在睡眠轴上取。
 * ============================================================
 */
object SleepScoreCalculator {

    /** 目标睡眠时长（分钟）。工单：默认 8h，设置页可调，暂用常量。 */
    const val DEFAULT_TARGET_MINUTES: Int = 8 * 60

    /** 规律性计分所需的最少晚数（工单：不足 3 晚不计分）。 */
    private const val MIN_NIGHTS_FOR_REGULARITY = 3

    /** 逐项明细的一行。delta 是该分项对总分的影响（可为负）。 */
    data class Part(
        val label: String,
        val delta: Int,
        /** 数据不足等无法计分时显示的一句说明；正常分项为空。 */
        val note: String = ""
    )

    /** 规律性结果：median/deviation 均为「睡眠轴」分钟数；null = 数据不足不计分。 */
    data class Regularity(
        val medianBedMinute: Int,
        val deviationMinutes: Int,
        val delta: Int
    )

    /** 计算结果的全部内容，UI 只负责画，不再二次算分。 */
    data class Result(
        val score: Int,
        val rating: String,
        val parts: List<Part>,
        val regularity: Regularity?
    )

    /**
     * 纯数字入口（单测友好）。调用方负责把实体字段拆成这里的入参。
     * recentBedMinutes 传近 7 天（含当晚）每天的入睡时刻（0~1439 分钟），
     * 只用于规律性；不足 3 条时规律性返回 null。
     */
    fun evaluate(
        durationMinutes: Int,
        quality: Int,
        morningFeeling: Int,
        wakeCount: Int,
        bedMinute: Int?,
        recentBedMinutes: List<Int>,
        targetMinutes: Int = DEFAULT_TARGET_MINUTES
    ): Result {
        val parts = mutableListOf<Part>()
        var total = 60.0
        parts += Part("基础分", 60)

        // 2. 时长达成率（最多拿满 25 分，睡过头不加分）
        val durationDelta = ((durationMinutes.coerceAtLeast(0).toDouble() /
                targetMinutes.coerceAtLeast(1)).coerceAtMost(1.0) * 25).roundToInt()
        total += durationDelta
        parts += Part("睡眠时长 ${formatMinutes(durationMinutes)}", durationDelta)

        // 3. 质量档（quality：1 很差 / 2 较差 / 3 一般 / 4 良好 / 5 很好）
        val qualityDelta = when (quality) {
            5 -> 10; 4 -> 6; 3 -> 3; 2 -> -5; 1 -> -10
            else -> 0
        }
        if (quality in 1..5) {
            total += qualityDelta
            parts += Part("睡眠质量 ${SleepRecordEntity.qualityLabel(quality)}", qualityDelta)
        }

        // 4. 起床精神（1 疲惫 / 2 一般 / 3 良好 / 4 精力充沛）
        val feelingDelta = when (morningFeeling) {
            4 -> 5; 3 -> 3; 2 -> 0; 1 -> -5
            else -> 0
        }
        if (morningFeeling in 1..4) {
            total += feelingDelta
            parts += Part("起床精神 ${SleepRecordEntity.feelingLabel(morningFeeling)}", feelingDelta)
        }

        // 5. 夜醒（上限 −9）
        val wakeTimes = wakeCount.coerceAtLeast(0)
        val wakeDelta = -(minOf(wakeTimes, 3) * 3)
        parts += Part(
            if (wakeTimes == 0) "夜间没醒" else "夜醒 $wakeTimes 次",
            wakeDelta
        )
        total += wakeDelta

        // 6. 规律性（数据不足 3 晚：不计分，UI 显示 —）
        val regularity = regularityOf(bedMinute, recentBedMinutes)
        if (regularity == null) {
            parts += Part("规律性", 0, "近 7 天不足 $MIN_NIGHTS_FOR_REGULARITY 晚，不计分")
        } else {
            total += regularity.delta
            parts += Part(
                "入睡规律性（偏移 ${regularity.deviationMinutes} 分钟）",
                regularity.delta
            )
        }

        val score = total.roundToInt().coerceIn(0, 100)
        return Result(score, ratingOf(score), parts, regularity)
    }

    /**
     * 实体入口：从 SleepRecordEntity 提取字段后再走 [evaluate]。
     * zone 用于把 startTime 换算成入睡钟点，默认 AppClock.zone（记录时区）。
     */
    fun score(
        record: SleepRecordEntity,
        recentRecords: List<SleepRecordEntity>,
        targetMinutes: Int = DEFAULT_TARGET_MINUTES,
        zone: ZoneId = AppClock.zone
    ): Result {
        val bedMinute = record.bedMinuteOf(zone)
        val recent = recentRecords.mapNotNull { it.bedMinuteOf(zone) }
        return evaluate(
            durationMinutes = record.durationMinutes,
            quality = record.quality,
            morningFeeling = record.morningFeeling,
            wakeCount = record.wakeCount,
            bedMinute = bedMinute,
            recentBedMinutes = recent,
            targetMinutes = targetMinutes
        )
    }

    /** 评级档：<60 较差，60–74 一般，75–89 良好，≥90 优秀。 */
    fun ratingOf(score: Int): String = when {
        score < 60 -> "较差"
        score < 75 -> "一般"
        score < 90 -> "良好"
        else -> "优秀"
    }

    /**
     * 规律性：入睡时刻相对本人近 7 天中位数的偏移。
     * 数据不足 3 晚 → null（不计分）；否则 ≤30min 0 分、31–60min −2、>60min −4。
     */
    private fun regularityOf(bedMinute: Int?, recentBedMinutes: List<Int>): Regularity? {
        if (bedMinute == null || recentBedMinutes.size < MIN_NIGHTS_FOR_REGULARITY) return null
        val axis = recentBedMinutes.map { toSleepAxis(it) }.sorted()
        val median = axis[axis.size / 2] // 偶数取上中位数，口径固定、可复现
        val deviation = circularDistance(toSleepAxis(bedMinute), median)
        val delta = when {
            deviation <= 30 -> 0
            deviation <= 60 -> -2
            else -> -4
        }
        return Regularity(median % (24 * 60), deviation, delta)
    }

    /** 钟面分钟（0~1439）→ 睡眠轴：早于中午的视为次日，+1440。 */
    private fun toSleepAxis(minuteOfDay: Int): Int {
        val m = ((minuteOfDay % (24 * 60)) + 24 * 60) % (24 * 60)
        return if (m < 12 * 60) m + 24 * 60 else m
    }

    /** 两个睡眠轴时刻的环形距离（分钟）。 */
    private fun circularDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 24 * 60 - d)
    }

    /** 入睡时刻的钟面分钟数；startTime 不存在（0）时返回 null。 */
    private fun SleepRecordEntity.bedMinuteOf(zone: ZoneId): Int? {
        if (startTime <= 0L) return null
        val t: LocalTime = Instant.ofEpochMilli(startTime).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** 「1小时5分」这类展示串，仅用于明细行，不参与计分。 */
    private fun formatMinutes(minutes: Int): String {
        val m = minutes.coerceAtLeast(0)
        return "${m / 60}小时${m % 60}分"
    }
}
