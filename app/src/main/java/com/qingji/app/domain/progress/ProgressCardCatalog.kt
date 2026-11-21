package com.qingji.app.domain.progress

/**
 * ============================================================
 * 进展页卡片目录与顺序解析（TASK-07-A）
 * ------------------------------------------------------------
 * 以前这些全塞在 ProgressScreen.kt 的 Composable 里：
 *
 *     val defaultCardIds = listOf("exercise", "forecast", ...)
 *     val cardOrder = settings.progressCardOrder.split(",").filter { it in defaultCardIds }
 *         .let { parsed -> parsed + defaultCardIds.filter { it !in parsed } }
 *     val cardHidden = settings.progressCardHidden.split(",")...
 *
 * 三个问题：
 *   1. 纯字符串计算却写在 UI 里，Context 一掺进来就测不了；
 *   2. 卡片 ID 字符串散落在「默认顺序」「渲染分派」「名称表」三处，改一个漏两个；
 *   3. 「身体数据」卡固定最上、不进管理列表这条规则只存在于注释里。
 *
 * 现在把它们收进 domain：不依赖 Android、不依赖 Compose，纯函数，
 * 有单元测试兜着（ProgressCardCatalogTest）。
 *
 * 语义与改造前完全一致：
 *   - 空串 / 未设置 → 默认顺序、全部显示；
 *   - 存过的顺序里有未知 ID → 直接丢掉，不报错；
 *   - 存过的顺序里缺了新卡（后来加的）→ 按默认顺序补到末尾，用户不会看不见新卡；
 *   - 重复 ID 不去重（保持原样，避免改变既有数据的行为）。
 * ============================================================
 */
object ProgressCardCatalog {

    /** 已退役：v13 起「本周运动消耗」卡搬到首页，不再进入进展页默认顺序 / 管理列表。保留常量仅为兼容历史配置 ID。 */
    const val EXERCISE = "exercise"
    const val FORECAST = "forecast"
    const val CHART = "chart"
    const val WATER = "water"
    const val NUTRITION = "nutrition"
    const val MEASURE = "measure"
    const val PHOTOS = "photos"
    // 2026-11-18：三种日记卡（工单 P1a/P1b），日记页形式按日期分组的滚动流。
    const val SLEEP_DIARY = "sleep_diary"
    const val EXERCISE_DIARY = "exercise_diary"
    const val FOOD_DIARY = "food_diary"

    /**
     * 默认可管理顺序（也是「重置」恢复的顺序）。
     * 「身体数据」卡不在这里 —— 它固定在页面最上，不进管理列表。
     * 新卡一律追加到末尾：老用户存过的顺序里没有它们，parseOrder 会自动补上，
     * 不会因为旧设置而看不见新卡（见下方 parseOrder 注释）。
     */
    val DEFAULT_ORDER: List<String> = listOf(
        FORECAST, CHART, WATER, NUTRITION, MEASURE, PHOTOS,
        SLEEP_DIARY, EXERCISE_DIARY, FOOD_DIARY
    )

    /** 卡片 ID 是否属于可管理卡片。 */
    fun isKnown(id: String): Boolean = id in DEFAULT_ORDER

    /**
     * 把 DataStore 里存的逗号串解析成真实渲染顺序。
     *
     * 过滤掉未知 ID → 再把「默认顺序里有、但存的值里没有」的卡补到末尾。
     * 这样新版本新增的卡片对老用户也会显示，不会因为旧设置而被永久藏起来。
     */
    fun parseOrder(raw: String?): List<String> {
        val parsed = raw.orEmpty()
            .split(",")
            .filter { it in DEFAULT_ORDER }
        return parsed + DEFAULT_ORDER.filter { it !in parsed }
    }

    /** 把 DataStore 里存的隐藏列表解析成集合；空白项忽略。 */
    fun parseHidden(raw: String?): Set<String> =
        raw.orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    /**
     * 拖拽排序的纯换位函数（QJ-UI-PROGRESS-DRAG-001）。
     * 按【卡片 ID】把某张卡移到目标索引，不依赖列表索引身份 —— 列表重排后
     * 用 ID 反查当前位置，避免旧实现用 dragFrom 索引导致的"拖谁错位"。
     *
     * 返回值是新列表（不改入参）。任何非法入参（ID 不存在、索引越界）原样返回，
     * 保证 UI 在拖拽边界不会把列表搞丢或重复。
     */
    fun moveToIndex(order: List<String>, cardId: String, targetIndex: Int): List<String> {
        val from = order.indexOf(cardId)
        if (from < 0 || targetIndex !in order.indices || from == targetIndex) return order
        return order.toMutableList().apply { add(targetIndex, removeAt(from)) }
    }

    /** 真正要渲染的卡片：排好序 + 去掉被隐藏的。 */
    fun visibleCards(rawOrder: String?, rawHidden: String?): List<String> {
        val hidden = parseHidden(rawHidden)
        return parseOrder(rawOrder).filter { it !in hidden }
    }
}
