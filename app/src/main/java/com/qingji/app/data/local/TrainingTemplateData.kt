package com.qingji.app.data.local

/**
 * ============================================================
 * P1 的 7 类训练计划模板（周安排示例）
 * ------------------------------------------------------------
 * ⚠️ 合规说明（需求手册红线 4）：
 * 这些是**周安排骨架**，不是训练处方：
 *   · 动作只从 TrainingSeed 的最小动作库里挑，不引用《健身 Excel 超级套表》原文；
 *   · 目标一律 3 组 × 10 次、重量留空（由用户按自身情况自己定），
 *     不给重量建议、不给进阶幅度、不做周期化设计；
 *   · 界面上带「示例安排 · 非训练处方 · 请按自身情况调整」角标；
 *   · 等主人把 Excel 素材做完难度/训练量/安全性审核后，再整份替换这里的数据。
 *
 * weekday：1=周一 … 7=周日。没列出来的日子自动是休息日。
 * ============================================================
 */
object TrainingTemplateData {

    const val NOTICE = "示例安排 · 非训练处方 · 请按自身情况调整"

    data class TemplateDay(
        val weekday: Int,
        val title: String,
        val exerciseNames: List<String>
    )

    data class Template(
        val id: String,
        val name: String,
        val summary: String,
        val days: List<TemplateDay>
    ) {
        /** 7 天完整安排（含休息日），给「我的计划」页展示。 */
        fun fullWeek(): List<Pair<Int, TemplateDay?>> =
            (1..7).map { day -> day to days.firstOrNull { it.weekday == day } }
    }

    val templates: List<Template> = listOf(
        Template(
            id = "beginner", name = "新手入门", summary = "每周 3 次全身，动作少、好上手",
            days = listOf(
                TemplateDay(1, "全身 A", listOf("哑铃深蹲", "俯卧撑", "坐姿划船", "平板支撑")),
                TemplateDay(3, "全身 B", listOf("罗马尼亚硬拉", "哑铃卧推", "高位下拉", "卷腹")),
                TemplateDay(5, "全身 A", listOf("哑铃深蹲", "俯卧撑", "坐姿划船", "平板支撑"))
            )
        ),
        Template(
            id = "home_bodyweight", name = "居家徒手", summary = "不用器械，上肢 / 下肢 / 核心轮流练",
            days = listOf(
                TemplateDay(1, "上肢", listOf("俯卧撑", "窄距俯卧撑", "平板支撑")),
                TemplateDay(3, "下肢", listOf("哑铃深蹲", "箭步蹲", "提踵")),
                TemplateDay(5, "核心", listOf("卷腹", "死虫式", "俄罗斯转体", "平板支撑"))
            )
        ),
        Template(
            id = "home_dumbbell", name = "居家哑铃", summary = "一对哑铃，上肢 / 下肢 / 全身",
            days = listOf(
                TemplateDay(1, "上肢", listOf("哑铃卧推", "单臂哑铃划船", "哑铃肩推", "杠铃弯举")),
                TemplateDay(3, "下肢", listOf("哑铃深蹲", "罗马尼亚硬拉", "箭步蹲")),
                TemplateDay(5, "全身", listOf("哑铃深蹲", "哑铃卧推", "坐姿划船", "卷腹"))
            )
        ),
        Template(
            id = "gym_basic", name = "健身房基础", summary = "胸三头 / 背二头 / 腿肩，每周 3 次",
            days = listOf(
                TemplateDay(1, "胸 · 三头", listOf("杠铃卧推", "上斜哑铃卧推", "器械推胸", "绳索下压")),
                TemplateDay(3, "背 · 二头", listOf("高位下拉", "坐姿划船", "单臂哑铃划船", "杠铃弯举")),
                TemplateDay(5, "腿 · 肩", listOf("杠铃深蹲", "腿举", "哑铃肩推", "哑铃侧平举"))
            )
        ),
        Template(
            id = "upper_lower", name = "上下肢分化", summary = "上 / 下 / 上 / 下，一周四练",
            days = listOf(
                TemplateDay(1, "上肢", listOf("哑铃卧推", "坐姿划船", "哑铃肩推", "杠铃弯举")),
                TemplateDay(2, "下肢", listOf("杠铃深蹲", "罗马尼亚硬拉", "提踵", "卷腹")),
                TemplateDay(4, "上肢", listOf("高位下拉", "上斜哑铃卧推", "哑铃侧平举", "绳索下压")),
                TemplateDay(5, "下肢", listOf("腿举", "箭步蹲", "提踵", "平板支撑"))
            )
        ),
        Template(
            id = "push_pull_legs", name = "推拉腿三分化", summary = "推 / 拉 / 腿循环，一周六练",
            days = listOf(
                TemplateDay(1, "推 · 胸肩三头", listOf("杠铃卧推", "哑铃肩推", "上斜哑铃卧推", "绳索下压")),
                TemplateDay(2, "拉 · 背二头", listOf("引体向上", "杠铃划船", "面拉", "杠铃弯举")),
                TemplateDay(3, "腿", listOf("杠铃深蹲", "罗马尼亚硬拉", "腿举", "提踵")),
                TemplateDay(4, "推 · 胸肩三头", listOf("哑铃卧推", "哑铃肩推", "器械推胸", "窄距俯卧撑")),
                TemplateDay(5, "拉 · 背二头", listOf("高位下拉", "坐姿划船", "面拉", "锤式弯举")),
                TemplateDay(6, "腿", listOf("哑铃深蹲", "硬拉", "箭步蹲", "卷腹"))
            )
        ),
        Template(
            id = "four_split", name = "四分化进阶", summary = "胸 / 背 / 肩 / 腿各一天，一周四练",
            days = listOf(
                TemplateDay(1, "胸", listOf("杠铃卧推", "上斜哑铃卧推", "哑铃飞鸟", "器械推胸")),
                TemplateDay(2, "背", listOf("引体向上", "杠铃划船", "高位下拉", "单臂哑铃划船")),
                TemplateDay(4, "肩", listOf("哑铃肩推", "哑铃侧平举", "面拉", "俯身反向飞鸟")),
                TemplateDay(5, "腿", listOf("杠铃深蹲", "罗马尼亚硬拉", "腿举", "提踵"))
            )
        )
    )

    fun byId(id: String): Template? = templates.firstOrNull { it.id == id }
}
