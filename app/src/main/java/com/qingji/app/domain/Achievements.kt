package com.qingji.app.domain

/**
 * ============================================================
 * 我的成就（M6）
 * ------------------------------------------------------------
 * 规则写在这里而不是界面里，好处有两个：
 * 1. 能写单元测试，改规则不怕算错；
 * 2. 以后想加一枚徽章，只在这个文件加一行。
 *
 * 每条成就都是「当前值 / 目标值」，当前值够了就是已解锁。
 * 全部数字来自本机数据库，不联网、不瞎编。
 * ============================================================
 */

/** 算成就需要的原始数字，由仓库层从数据库统计后传进来。 */
data class AchievementStats(
    val foodLogCount: Int = 0,      // 饮食记录条数
    val weightLogCount: Int = 0,    // 体重记录次数
    val diaryCount: Int = 0,        // 日记篇数
    val waterCount: Int = 0,        // 喝水次数
    val aiCount: Int = 0,           // AI 调用次数
    val streakDays: Int = 0,        // 连续记录天数（饮食或体重任一有记）
    val weightLostKg: Double = 0.0, // 已减掉的公斤数（起始 − 最新）
    val distinctFoodCount: Int = 0  // 吃过的不同食物种类数
)

/** 一枚成就。 */
data class Achievement(
    val id: String,
    val title: String,
    val desc: String,
    val current: Int,
    val target: Int
) {
    val unlocked: Boolean get() = current >= target

    /** 0 到 1 的进度，界面画进度条用。 */
    val progress: Float
        get() = if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)

    /** 「3 / 50」这种文案。 */
    val progressText: String get() = "$current / $target"
}

object Achievements {

    /**
     * 全部成就清单（固定顺序：先易后难，解锁的也按这个顺序排）。
     * 数量少而明确，别为了凑数造一堆「点过一次就算」的假成就。
     */
    fun list(stats: AchievementStats): List<Achievement> = listOf(
        Achievement(
            id = "first_bite",
            title = "开张啦",
            desc = "记下第一条饮食",
            current = stats.foodLogCount,
            target = 1
        ),
        Achievement(
            id = "first_weigh",
            title = "上秤了",
            desc = "记下第一次体重",
            current = stats.weightLogCount,
            target = 1
        ),
        Achievement(
            id = "first_diary",
            title = "第一篇",
            desc = "写第一篇日记",
            current = stats.diaryCount,
            target = 1
        ),
        Achievement(
            id = "water_50",
            title = "水做的",
            desc = "累计喝水 50 次",
            current = stats.waterCount,
            target = 50
        ),
        Achievement(
            id = "weigh_10",
            title = "称重习惯",
            desc = "累计记录体重 10 次",
            current = stats.weightLogCount,
            target = 10
        ),
        Achievement(
            id = "diary_30",
            title = "玻璃罐满了",
            desc = "写满 30 篇日记",
            current = stats.diaryCount,
            target = 30
        ),
        Achievement(
            id = "food_100",
            title = "百条俱乐部",
            desc = "累计记录 100 条饮食",
            current = stats.foodLogCount,
            target = 100
        ),
        Achievement(
            id = "streak_7",
            title = "连记七天",
            desc = "连续 7 天都有记录",
            current = stats.streakDays,
            target = 7
        ),
        Achievement(
            id = "variety_20",
            title = "什么都吃",
            desc = "吃过 20 种不同食物",
            current = stats.distinctFoodCount,
            target = 20
        ),
        Achievement(
            id = "lose_1kg",
            title = "开了个头",
            desc = "累计减掉 1 公斤",
            current = stats.weightLostKg.toInt(),
            target = 1
        ),
        Achievement(
            id = "ai_20",
            title = "AI 助手",
            desc = "累计使用 AI 识别 20 次",
            current = stats.aiCount,
            target = 20
        )
    )

    /**
     * 连续记录天数：从「今天」往前数，只要这天有饮食或体重记录就算一天，
     * 一旦断掉立刻停。今天还没记不算断 —— 从昨天开始数，
     * 否则用户早上打开 App 会看到连续天数清零，体验很差。
     */
    fun streakDays(activeDates: Set<String>, today: String, yesterday: String): Int {
        var cursor = when {
            activeDates.contains(today) -> today
            activeDates.contains(yesterday) -> yesterday
            else -> return 0
        }
        var count = 0
        while (activeDates.contains(cursor)) {
            count++
            cursor = java.time.LocalDate.parse(cursor).minusDays(1).toString()
        }
        return count
    }
}
