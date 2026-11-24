package com.qingji.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ============================================================
 * 主题色（v2.0）
 * ------------------------------------------------------------
 * 为什么这么设计：全 App 有几百处引用 QingJiGreen / QingJiGreenDark /
 * QingJiGreenLight。如果改成 CompositionLocal，所有页面都要动一遍，
 * 风险太高。所以还是保留这三个入口，只在内部按「当前主题」取值。
 *
 * 每套主题给三档：
 * - main  主色（按钮、进度条、选中态）
 * - dark  深一档（浮层按钮、强调文字）
 * - soft  浅色底（标签背景、选中底色）
 * 深色模式另给一套更亮的，避免深底上发灰。
 *
 * 三大营养素（蛋白/碳水/脂肪）也跟主题走：
 * 碳水用主题主色，蛋白往冷色偏，脂肪保持暖色 —— 整体协调，
 * 但三条之间仍能一眼分开。
 * ============================================================
 */
object AccentThemes {

    data class Spec(
        val key: String,
        val label: String,
        /** 浅色模式：主色 / 深色 / 浅底 */
        val lightMain: Long,
        val lightDark: Long,
        val lightSoft: Long,
        /** 深色模式：主色 / 深色 / 浅底 */
        val darkMain: Long,
        val darkDark: Long,
        val darkSoft: Long,
        /** 三大营养素：蛋白 / 碳水 / 脂肪 */
        val macroProtein: Long,
        val macroCarb: Long,
        val macroFat: Long,
        /**
         * 悬浮底栏选中胶囊的专用配色（可空 = 跟随本主题的浅底 / 主色）。
         * 松绿这套是主人反复指定的精确值，只有它单独给值；
         * 其余四套留空，自动用各自的 lightSoft / lightMain，换肤时底栏一起变。
         */
        val dockSoft: Long? = null,
        val dockContent: Long? = null
    )

    val ALL = listOf(
        Spec(
            key = "green", label = "松绿",
            lightMain = 0xFF299D76, lightDark = 0xFF1F7E62, lightSoft = 0xFFEAF7F1,
            darkMain = 0xFF4DBB9B, darkDark = 0xFF8FE0C4, darkSoft = 0xFF1D3A33,
            macroProtein = 0xFF5B8DEF, macroCarb = 0xFF299D76, macroFat = 0xFFF5A623,
            dockSoft = 0xFFE5F3EF, dockContent = 0xFF23866B
        ),
        Spec(
            key = "blue", label = "海蓝",
            lightMain = 0xFF2F6FED, lightDark = 0xFF2559C4, lightSoft = 0xFFE9EFFD,
            darkMain = 0xFF6FA0FF, darkDark = 0xFFA9C4FF, darkSoft = 0xFF1B2A44,
            macroProtein = 0xFF7B61FF, macroCarb = 0xFF2F6FED, macroFat = 0xFFE0A03A
        ),
        Spec(
            key = "purple", label = "黛紫",
            lightMain = 0xFF7B5CD6, lightDark = 0xFF6247B8, lightSoft = 0xFFF0EBFC,
            darkMain = 0xFFA98BEF, darkDark = 0xFFC9B8F7, darkSoft = 0xFF2A2340,
            macroProtein = 0xFF4FA3D1, macroCarb = 0xFF7B5CD6, macroFat = 0xFFE0935B
        ),
        Spec(
            key = "orange", label = "暖橙",
            lightMain = 0xFFE0803A, lightDark = 0xFFBF6526, lightSoft = 0xFFFDF0E5,
            darkMain = 0xFFF0A566, darkDark = 0xFFF7C79A, darkSoft = 0xFF3A2A1B,
            macroProtein = 0xFF5B8DEF, macroCarb = 0xFFE0803A, macroFat = 0xFFC9A227
        ),
        Spec(
            key = "pink", label = "樱粉",
            lightMain = 0xFFD9548A, lightDark = 0xFFB33F70, lightSoft = 0xFFFCEAF1,
            darkMain = 0xFFEC7FA6, darkDark = 0xFFF5B3C9, darkSoft = 0xFF3A1F29,
            macroProtein = 0xFF6C8AE4, macroCarb = 0xFFD9548A, macroFat = 0xFFE0A03A
        ),
        Spec(
            key = "coral", label = "珊瑚红",
            lightMain = 0xffe8665b, lightDark = 0xffc44a40, lightSoft = 0xfffdecea,
            darkMain = 0xffff8f82, darkDark = 0xffffb6ad, darkSoft = 0xff3a211e,
            macroProtein = 0xff5b8def, macroCarb = 0xffe8665b, macroFat = 0xffe0a03a
        ),
        Spec(
            key = "indigo", label = "靛蓝",
            lightMain = 0xff4a5bd6, lightDark = 0xff3746b0, lightSoft = 0xffeaecfd,
            darkMain = 0xff8a97ff, darkDark = 0xffb6beff, darkSoft = 0xff1e2440,
            macroProtein = 0xff38a0c4, macroCarb = 0xff4a5bd6, macroFat = 0xffe0935b
        ),
        Spec(
            key = "teal", label = "青碧",
            lightMain = 0xff1b9aa8, lightDark = 0xff137783, lightSoft = 0xffe6f7f8,
            darkMain = 0xff5fd2de, darkDark = 0xff9ae8ef, darkSoft = 0xff173439,
            macroProtein = 0xff5b8def, macroCarb = 0xff1b9aa8, macroFat = 0xffe0a03a
        ),
        Spec(
            key = "amber", label = "琥珀",
            lightMain = 0xffd99a20, lightDark = 0xffb47a14, lightSoft = 0xfffdf4e0,
            darkMain = 0xfff5c25c, darkDark = 0xffffde9a, darkSoft = 0xff3a2e15,
            macroProtein = 0xff5b8def, macroCarb = 0xffd99a20, macroFat = 0xffb56b2a
        ),
        Spec(
            key = "rose", label = "玫红",
            lightMain = 0xffd4559e, lightDark = 0xffae3d7d, lightSoft = 0xfffceaf4,
            darkMain = 0xfff398cc, darkDark = 0xffffc2e2, darkSoft = 0xff3a1f2f,
            macroProtein = 0xff6c8ae4, macroCarb = 0xffd4559e, macroFat = 0xffe0a03a
        ),
        Spec(
            key = "charcoal", label = "墨黑",
            lightMain = 0xff3e4a52, lightDark = 0xff2a343b, lightSoft = 0xffeceff1,
            darkMain = 0xffaab7c0, darkDark = 0xffd2dbe1, darkSoft = 0xff232a2f,
            macroProtein = 0xff5b8def, macroCarb = 0xff3e4a52, macroFat = 0xffe0803a
        ),
        Spec(
            key = "sky", label = "天青",
            lightMain = 0xff2b96d6, lightDark = 0xff1f74aa, lightSoft = 0xffe8f5fc,
            darkMain = 0xff6fc4ef, darkDark = 0xffa8ddf7, darkSoft = 0xff17333f,
            macroProtein = 0xff7b61ff, macroCarb = 0xff2b96d6, macroFat = 0xffe0a03a
        ),
        Spec(
            key = "grass", label = "草绿",
            lightMain = 0xff5aa832, lightDark = 0xff438321, lightSoft = 0xffedf7e7,
            darkMain = 0xff94d36f, darkDark = 0xffc3eba9, darkSoft = 0xff26361c,
            macroProtein = 0xff4fa3d1, macroCarb = 0xff5aa832, macroFat = 0xffe08a3a
        ),
        Spec(
            key = "lilac", label = "丁香",
            lightMain = 0xff9a72c9, lightDark = 0xff7c55ad, lightSoft = 0xfff3edfa,
            darkMain = 0xffc4a6ea, darkDark = 0xffdcc9f4, darkSoft = 0xff2e2440,
            macroProtein = 0xff4fa3d1, macroCarb = 0xff9a72c9, macroFat = 0xffe0935b
        ),
        Spec(
            key = "bronze", label = "青铜",
            lightMain = 0xffa9762e, lightDark = 0xff875b20, lightSoft = 0xfff8f0e3,
            darkMain = 0xffd9a862, darkDark = 0xffefcca0, darkSoft = 0xff35291a,
            macroProtein = 0xff5b8def, macroCarb = 0xffa9762e, macroFat = 0xffc95a3c
        ),
        Spec(
            key = "peach", label = "蜜桃",
            lightMain = 0xffe88468, lightDark = 0xffc2634a, lightSoft = 0xfffdf0ea,
            darkMain = 0xffffaf96, darkDark = 0xffffcfbc, darkSoft = 0xff3a231c,
            macroProtein = 0xff6c8ae4, macroCarb = 0xffe88468, macroFat = 0xffd9a020
        ),
        Spec(
            key = "steel", label = "钢蓝",
            lightMain = 0xff4e6b8a, lightDark = 0xff3a5270, lightSoft = 0xffeaf0f5,
            darkMain = 0xff9ab6d0, darkDark = 0xffc4d7e8, darkSoft = 0xff1f2b37,
            macroProtein = 0xff7b61ff, macroCarb = 0xff4e6b8a, macroFat = 0xffe0803a
        ),
        Spec(
            key = "olive", label = "橄榄",
            lightMain = 0xff7c8f3a, lightDark = 0xff5e7028, lightSoft = 0xfff2f5e5,
            darkMain = 0xffb2c46e, darkDark = 0xffd6e3a4, darkSoft = 0xff2a321a,
            macroProtein = 0xff4fa3d1, macroCarb = 0xff7c8f3a, macroFat = 0xffe0803a
        ),
        Spec(
            key = "rouge", label = "胭脂",
            lightMain = 0xffc43b5a, lightDark = 0xff9e2943, lightSoft = 0xfffceaee,
            darkMain = 0xfff07c96, darkDark = 0xffffb2c3, darkSoft = 0xff3a1c24,
            macroProtein = 0xff5b8def, macroCarb = 0xffc43b5a, macroFat = 0xffe0a03a
        ),
        Spec(
            key = "slate", label = "青灰",
            lightMain = 0xff5c7a80, lightDark = 0xff425c61, lightSoft = 0xffedf2f3,
            darkMain = 0xffa3c0c5, darkDark = 0xffccdde0, darkSoft = 0xff202e31,
            macroProtein = 0xff7b61ff, macroCarb = 0xff5c7a80, macroFat = 0xffe0803a
        ),
    )

    /** 按 key 找主题，找不到回落到第一套（松绿）。 */
    fun byKey(key: String?): Spec = ALL.firstOrNull { it.key == key } ?: ALL.first()

    /** 取主色。 */
    fun main(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkMain else spec.lightMain)
    }

    fun darkOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkDark else spec.lightDark)
    }

    fun softOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return Color(if (dark) spec.darkSoft else spec.lightSoft)
    }

    /**
     * 悬浮底栏选中胶囊的底色。
     *
     * 深色模式一律用本主题的深色浅底（深绿 / 深蓝…），否则浅色胶囊在暗底上会刺眼；
     * 浅色模式优先用 Spec.dockSoft（目前只有松绿给值 #E5F3EF），没给值就回落 lightSoft。
     */
    fun dockSoftOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return when {
            dark -> Color(spec.darkSoft)
            spec.dockSoft != null -> Color(spec.dockSoft)
            else -> Color(spec.lightSoft)
        }
    }

    /**
     * 悬浮底栏选中胶囊里的图标与文字颜色。
     *
     * 同理：深色模式用本主题深色主色（在深底上够亮），
     * 浅色模式优先 dockContent（松绿 #23866B），没给值回落 lightMain。
     */
    fun dockContentOf(key: String?, dark: Boolean): Color {
        val spec = byKey(key)
        return when {
            dark -> Color(spec.darkMain)
            spec.dockContent != null -> Color(spec.dockContent)
            else -> Color(spec.lightMain)
        }
    }
}
