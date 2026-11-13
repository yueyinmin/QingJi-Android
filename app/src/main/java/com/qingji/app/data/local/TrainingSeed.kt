package com.qingji.app.data.local

import com.qingji.app.data.local.entity.ExerciseEntity

/**
 * ============================================================
 * P0 的「最小动作库」（内置，随 APK 走）
 * ------------------------------------------------------------
 * 为什么是这份清单、而不是《健身 Excel 超级套表》：
 * 需求手册 P0 的验收要求「添加动作：从动作库选，支持搜索」，
 * 但完整动作库排在 P1、且 Excel 素材还没做完难度/安全/授权审核（红线 4）。
 * 所以 P0 先给一份「名称 + 肌群 + 器械 + 一句话要点」的通用清单，
 * 让「选动作 → 记组 → 上次成绩带出」这条主链路能跑通；
 * 图文详情、7 类计划模板留到 P1 审核完再补。
 *
 * 这些只是动作名称与常识性提示，不是训练处方，不给重量/组数建议。
 * uuid 写死，保证换手机、重装后仍然是同一个动作（上次成绩能对上）。
 * ============================================================
 */
object TrainingSeed {

    const val CHEST = "chest"
    const val BACK = "back"
    const val SHOULDER = "shoulder"
    const val ARM = "arm"
    const val LEG = "leg"
    const val CORE = "core"
    const val STRETCH = "stretch"

    /** 肌群 → 界面上的中文名（胶囊筛选与动作库分类都用它）。 */
    val CATEGORY_LABELS = listOf(
        CHEST to "胸",
        BACK to "背",
        SHOULDER to "肩",
        ARM to "手臂",
        LEG to "腿",
        CORE to "核心",
        STRETCH to "拉伸"
    )

    private data class Seed(
        val uuid: String,
        val name: String,
        val category: String,
        val primary: String,
        val equipment: String,
        val tip: String
    )

    private val SEEDS = listOf(
        Seed("ex-chest-01", "哑铃卧推", CHEST, "胸大肌", "哑铃", "肩胛收紧，下放至胸口两侧再推起"),
        Seed("ex-chest-02", "杠铃卧推", CHEST, "胸大肌", "杠铃", "手腕保持中立，杠铃落到乳线附近"),
        Seed("ex-chest-03", "俯卧撑", CHEST, "胸大肌", "徒手", "身体一条直线，肘部约 45 度"),
        Seed("ex-chest-04", "上斜哑铃卧推", CHEST, "上胸", "哑铃", "靠背约 30 度，别抬腰"),
        Seed("ex-chest-05", "器械推胸", CHEST, "胸大肌", "器械", "手柄与胸齐平，推到末端别锁死手肘"),
        Seed("ex-chest-06", "哑铃飞鸟", CHEST, "胸大肌", "哑铃", "手肘微屈固定，做开合不做成推"),

        Seed("ex-back-01", "引体向上", BACK, "背阔肌", "徒手", "先沉肩再拉，别用脖子够杠"),
        Seed("ex-back-02", "高位下拉", BACK, "背阔肌", "器械", "身体别后仰借力，拉到锁骨附近"),
        Seed("ex-back-03", "坐姿划船", BACK, "背中部", "器械", "挺胸，肩胛后收再拉"),
        Seed("ex-back-04", "单臂哑铃划船", BACK, "背阔肌", "哑铃", "背部保持平，别转身甩"),
        Seed("ex-back-05", "杠铃划船", BACK, "背中部", "杠铃", "髋部折叠，杠铃贴腿走"),
        Seed("ex-back-06", "硬拉", BACK, "下背/臀腿", "杠铃", "杠贴身，腰背保持中立，量力而行"),

        Seed("ex-shoulder-01", "哑铃肩推", SHOULDER, "三角肌前/中束", "哑铃", "核心收紧别塌腰"),
        Seed("ex-shoulder-02", "哑铃侧平举", SHOULDER, "三角肌中束", "哑铃", "手肘微屈，抬到肩高即可"),
        Seed("ex-shoulder-03", "面拉", SHOULDER, "三角肌后束", "绳索", "往额头方向拉，肩别前扣"),
        Seed("ex-shoulder-04", "阿诺德推举", SHOULDER, "三角肌", "哑铃", "推起时匀速旋转手腕"),
        Seed("ex-shoulder-05", "俯身反向飞鸟", SHOULDER, "三角肌后束", "哑铃", "背挺直，别用斜方肌耸肩"),

        Seed("ex-arm-01", "杠铃弯举", ARM, "肱二头肌", "杠铃", "大臂夹紧，别靠身体晃"),
        Seed("ex-arm-02", "哑铃交替弯举", ARM, "肱二头肌", "哑铃", "手腕不内扣"),
        Seed("ex-arm-03", "绳索下压", ARM, "肱三头肌", "绳索", "手肘贴身，只动小臂"),
        Seed("ex-arm-04", "窄距俯卧撑", ARM, "肱三头肌", "徒手", "手肘往后贴，不向外张"),
        Seed("ex-arm-05", "锤式弯举", ARM, "肱桡肌", "哑铃", "掌心相对，全程不转腕"),

        Seed("ex-leg-01", "杠铃深蹲", LEG, "股四头肌/臀", "杠铃", "膝盖朝脚尖方向，腰背中立"),
        Seed("ex-leg-02", "哑铃深蹲", LEG, "股四头肌/臀", "哑铃", "下蹲时臀部往后坐"),
        Seed("ex-leg-03", "罗马尼亚硬拉", LEG, "腘绳肌/臀", "哑铃", "微屈膝，髋部往后推"),
        Seed("ex-leg-04", "腿举", LEG, "股四头肌", "器械", "膝盖别锁死，别让腰离开靠垫"),
        Seed("ex-leg-05", "箭步蹲", LEG, "臀腿", "徒手", "前脚掌踩稳，后膝轻触地"),
        Seed("ex-leg-06", "提踵", LEG, "小腿", "徒手", "顶端停一下再慢放"),

        Seed("ex-core-01", "平板支撑", CORE, "腹横肌", "徒手", "腰别塌，臀部与肩同高"),
        Seed("ex-core-02", "卷腹", CORE, "腹直肌", "徒手", "下背贴地，别用手拽脖子"),
        Seed("ex-core-03", "死虫式", CORE, "核心稳定", "徒手", "腰部始终贴地"),
        Seed("ex-core-04", "俄罗斯转体", CORE, "腹斜肌", "徒手", "转体别靠甩手"),
        Seed("ex-core-05", "悬垂举腿", CORE, "下腹", "徒手", "先收骨盆再抬腿"),

        Seed("ex-stretch-01", "胸部拉伸", STRETCH, "胸大肌", "徒手", "门框辅助，肩膀别耸"),
        Seed("ex-stretch-02", "背阔肌拉伸", STRETCH, "背阔肌", "徒手", "侧腰有拉伸感即停"),
        Seed("ex-stretch-03", "股四头肌拉伸", STRETCH, "股四头肌", "徒手", "骨盆微微后倾"),
        Seed("ex-stretch-04", "腘绳肌拉伸", STRETCH, "腘绳肌", "徒手", "背挺直从髋部折叠"),
        Seed("ex-stretch-05", "髋屈肌拉伸", STRETCH, "髋屈肌", "徒手", "后侧臀收紧，别压腰")
    )

    /** 首次进入训练中心时灌库；已灌过就跳过，用户删掉的自建动作也不会被强行补回。 */
    fun builtIns(now: Long = System.currentTimeMillis()): List<ExerciseEntity> =
        SEEDS.map {
            ExerciseEntity(
                uuid = it.uuid,
                name = it.name,
                category = it.category,
                primaryMuscles = it.primary,
                equipment = it.equipment,
                steps = it.tip,
                commonMistakes = "",
                isBuiltIn = true,
                isFavorite = false,
                createdAt = now,
                updatedAt = now
            )
        }
}
