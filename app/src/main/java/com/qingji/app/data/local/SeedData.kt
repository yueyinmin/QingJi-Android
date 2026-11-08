package com.qingji.app.data.local

import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.UserProfile
import java.time.LocalDate

/**
 * ============================================================
 * M0 阶段的「假数据」
 * ------------------------------------------------------------
 * 需求文档第 10 节规定 M0 要「项目搭建、主题色、导航、Room 建表、假数据」。
 * 这个文件是预置数据的来源：App 第一次启动时，往数据库里灌
 * ① 常见中国食物（M0 的 20 条 + M3 扩充的常见中餐/主食/蔬果/饮品/外卖）
 * ② 一条默认个人档案（数字和首页、进展页写死的假数据对齐）
 *
 * 注意：这里的营养数值是《中国食物成分表》常见项的近似值，
 * 只用于估算，不能当医学/营养建议用。
 * 需求文档 13.1 的目标是 500-800 条，当前是 100 条出头的高频项；
 * 剩下低频项建议后续用 CSV 批量导入，别再往这个文件里手写（见验收报告）。
 * QingJiApp 会按「名字」增量补齐，老用户升级不会被覆盖、也不会重复插。
 * ============================================================
 */
object SeedData {

    /**
     * 预置食物（100 条出头的常见项）。
     * 每行字段顺序：名字、拼音首字母、每 100g 热量、蛋白、碳水、脂肪、默认一份多少克。
     * 「默认份量」就是点一下食物时自动带出来的克数。
     */
    val presetFoods: List<Food> = listOf(
        // ---- 需求文档 13.2 给的起始 10 条 ----
        presetFood("米饭（熟）", "mf", 116.0, 2.6, 25.9, 0.3, 150.0),
        presetFood("馒头", "mt", 223.0, 7.0, 47.0, 1.1, 100.0),
        presetFood("鸡蛋", "jd", 144.0, 13.3, 2.8, 8.8, 50.0),
        presetFood("全脂牛奶", "qznn", 54.0, 3.0, 5.0, 3.2, 250.0),
        presetFood("无糖豆浆", "wtdj", 31.0, 3.0, 1.2, 1.6, 250.0),
        presetFood("油条", "yt", 388.0, 6.9, 51.0, 17.6, 60.0),
        presetFood("鸡胸肉（煮）", "jxr", 133.0, 29.0, 0.0, 1.9, 100.0),
        presetFood("番茄炒蛋", "fqcd", 110.0, 6.5, 4.0, 7.5, 200.0),
        presetFood("兰州拉面", "lzlm", 130.0, 6.0, 20.0, 2.5, 500.0),
        presetFood("苹果", "pg", 53.0, 0.4, 13.5, 0.2, 200.0),
        // ---- 再补 10 条日常高频的，凑够 M1 搜索能试出效果 ----
        presetFood("香蕉", "xj", 93.0, 1.4, 22.0, 0.2, 120.0),
        presetFood("燕麦片（干）", "ymp", 377.0, 15.0, 61.0, 6.7, 40.0),
        presetFood("红薯（蒸）", "hs", 99.0, 1.6, 23.0, 0.2, 150.0),
        presetFood("玉米（煮）", "ym", 112.0, 4.0, 22.8, 1.2, 200.0),
        presetFood("北豆腐", "bdf", 116.0, 12.2, 3.0, 6.5, 150.0),
        presetFood("西兰花", "xlh", 36.0, 4.1, 4.3, 0.6, 150.0),
        presetFood("猪里脊", "zlj", 155.0, 20.2, 0.0, 7.9, 100.0),
        presetFood("牛肉（瘦）", "nr", 106.0, 20.2, 1.2, 2.3, 100.0),
        presetFood("面条（煮）", "mtiao", 110.0, 4.0, 22.0, 0.5, 200.0),
        presetFood("无糖酸奶", "wtsn", 72.0, 3.5, 5.0, 3.8, 150.0),
        // ---- M3 扩充：常见中餐、主食、肉蛋、蔬菜、水果、饮品 ----
        // 数据为《中国食物成分表》常见项的近似值，只用于估算，不作营养建议。
        // 主食 / 谷薯
        presetFood("白粥", "bz", 46.0, 1.1, 9.9, 0.2, 250.0),
        presetFood("小米粥", "xmz", 46.0, 1.4, 8.4, 0.7, 250.0),
        presetFood("杂粮饭", "zlf", 130.0, 3.5, 27.0, 0.8, 150.0),
        presetFood("糙米饭", "cmf", 112.0, 2.6, 23.0, 0.9, 150.0),
        presetFood("全麦面包", "qmmb", 246.0, 9.0, 45.0, 3.5, 60.0),
        presetFood("吐司面包", "tsmb", 280.0, 8.5, 52.0, 4.5, 60.0),
        presetFood("花卷", "hjuan", 214.0, 6.5, 45.0, 1.0, 100.0),
        presetFood("猪肉包子", "zrbao", 250.0, 8.0, 35.0, 8.0, 100.0),
        presetFood("猪肉饺子", "zrjz", 240.0, 9.0, 30.0, 9.0, 200.0),
        presetFood("馄饨", "huntun", 190.0, 7.0, 26.0, 6.0, 250.0),
        presetFood("烙饼", "lbing", 255.0, 7.5, 45.0, 5.0, 100.0),
        presetFood("方便面（面饼）", "fbm", 470.0, 9.5, 60.0, 20.0, 100.0),
        presetFood("米粉（煮）", "mfen", 110.0, 2.5, 24.0, 0.3, 250.0),
        presetFood("意面（煮）", "ymian", 158.0, 5.8, 31.0, 0.9, 200.0),
        presetFood("荞麦面（煮）", "qmm", 120.0, 4.5, 24.0, 0.6, 200.0),
        presetFood("土豆（蒸）", "tdou", 81.0, 2.0, 17.0, 0.2, 150.0),
        presetFood("山药", "shanya", 57.0, 1.9, 12.4, 0.2, 150.0),
        presetFood("南瓜", "ngua", 23.0, 0.7, 5.3, 0.1, 200.0),
        presetFood("紫薯", "zshu", 106.0, 1.6, 24.0, 0.3, 150.0),
        presetFood("玉米（鲜）", "yumi", 112.0, 4.0, 22.8, 1.2, 200.0),
        // 肉 / 蛋 / 水产
        presetFood("鸡腿（去皮）", "jtui", 181.0, 24.0, 0.0, 9.0, 120.0),
        presetFood("鸡翅", "jchi", 194.0, 17.0, 0.0, 14.0, 100.0),
        presetFood("五花肉", "whr", 508.0, 9.0, 0.0, 53.0, 100.0),
        presetFood("猪排骨", "zpgu", 278.0, 18.0, 0.0, 23.0, 150.0),
        presetFood("火腿肠", "htc", 212.0, 10.0, 15.0, 12.0, 50.0),
        presetFood("牛腩", "nnan", 250.0, 19.0, 0.0, 19.0, 120.0),
        presetFood("牛排", "npai", 250.0, 26.0, 0.0, 15.0, 150.0),
        presetFood("羊肉（瘦）", "yrou", 118.0, 20.5, 0.2, 3.9, 100.0),
        presetFood("三文鱼", "swyu", 208.0, 20.0, 0.0, 13.0, 120.0),
        presetFood("鲈鱼", "luyu", 105.0, 18.6, 0.0, 3.4, 150.0),
        presetFood("虾仁", "xren", 93.0, 18.6, 2.8, 0.8, 100.0),
        presetFood("水煮蛋", "szdan", 144.0, 13.3, 2.8, 8.8, 50.0),
        presetFood("煎蛋", "jdan", 200.0, 13.0, 1.5, 16.0, 55.0),
        presetFood("咸鸭蛋", "xyadan", 190.0, 12.7, 6.0, 12.7, 60.0),
        presetFood("鸡蛋羹", "jigeng", 82.0, 7.0, 2.0, 5.0, 150.0),
        // 豆制品 / 奶
        presetFood("内酯豆腐", "nzdf", 49.0, 5.0, 2.0, 2.5, 200.0),
        presetFood("豆腐干", "dfgan", 140.0, 16.0, 6.0, 7.0, 100.0),
        presetFood("腐竹", "fzhu", 459.0, 44.6, 22.0, 21.7, 50.0),
        presetFood("甜豆浆", "tdj", 45.0, 2.5, 6.0, 1.2, 250.0),
        presetFood("低脂牛奶", "dznn", 43.0, 3.4, 5.0, 1.5, 250.0),
        presetFood("脱脂牛奶", "tznn", 33.0, 3.4, 5.0, 0.3, 250.0),
        presetFood("酸奶（全脂）", "suannai", 86.0, 3.2, 11.0, 3.2, 150.0),
        presetFood("奶酪", "nlao", 328.0, 25.0, 3.5, 24.0, 30.0),
        presetFood("黄豆（煮）", "hdou", 140.0, 14.0, 10.0, 6.0, 100.0),
        presetFood("豆腐脑", "dfnao", 47.0, 4.5, 3.0, 1.8, 250.0),
        // 蔬菜
        presetFood("白菜", "bcai", 17.0, 1.5, 3.2, 0.1, 200.0),
        presetFood("生菜", "scai", 15.0, 1.4, 2.9, 0.2, 150.0),
        presetFood("菠菜", "bc", 24.0, 2.6, 4.5, 0.3, 150.0),
        presetFood("油麦菜", "ymc", 15.0, 1.4, 2.1, 0.4, 150.0),
        presetFood("芹菜", "qcai", 14.0, 0.8, 3.1, 0.1, 150.0),
        presetFood("黄瓜", "hgua", 15.0, 0.8, 2.9, 0.2, 200.0),
        presetFood("番茄", "fqie", 18.0, 0.9, 3.9, 0.2, 150.0),
        presetFood("胡萝卜", "hlb", 39.0, 1.0, 8.8, 0.2, 150.0),
        presetFood("冬瓜", "dgua", 11.0, 0.4, 2.6, 0.2, 200.0),
        presetFood("茄子", "qzie", 21.0, 1.1, 4.9, 0.2, 200.0),
        presetFood("青椒", "qjiao", 22.0, 1.0, 5.4, 0.2, 150.0),
        presetFood("香菇（鲜）", "xgu", 26.0, 2.2, 5.2, 0.3, 100.0),
        presetFood("木耳（水发）", "muer", 21.0, 1.5, 5.5, 0.2, 100.0),
        presetFood("海带（水发）", "hdai", 13.0, 1.2, 2.1, 0.1, 100.0),
        presetFood("豆芽", "dya", 18.0, 2.1, 2.9, 0.1, 150.0),
        presetFood("莲藕", "lou", 70.0, 1.9, 16.0, 0.2, 150.0),
        // 水果
        presetFood("橙子", "cz", 47.0, 0.8, 11.1, 0.2, 200.0),
        presetFood("橘子", "juzi", 44.0, 0.8, 10.2, 0.2, 150.0),
        presetFood("梨", "li", 51.0, 0.3, 13.1, 0.1, 200.0),
        presetFood("葡萄", "ptao", 43.0, 0.5, 10.3, 0.2, 150.0),
        presetFood("西瓜", "xigua", 25.0, 0.5, 6.2, 0.1, 300.0),
        presetFood("草莓", "cm", 30.0, 1.0, 7.1, 0.2, 150.0),
        presetFood("蓝莓", "lmei", 57.0, 0.7, 14.5, 0.3, 100.0),
        presetFood("猕猴桃", "mht", 61.0, 0.8, 14.5, 0.6, 100.0),
        presetFood("桃子", "tzi", 48.0, 0.9, 12.2, 0.1, 200.0),
        presetFood("火龙果", "hlg", 55.0, 1.1, 13.3, 0.2, 200.0),
        presetFood("芒果", "mguo", 60.0, 0.6, 15.0, 0.2, 200.0),
        presetFood("菠萝", "bluo", 44.0, 0.5, 10.8, 0.1, 200.0),
        presetFood("牛油果", "nyg", 160.0, 2.0, 8.5, 14.7, 100.0),
        // 饮品 / 零食
        presetFood("可乐", "kle", 43.0, 0.0, 10.8, 0.0, 330.0),
        presetFood("橙汁", "czhi", 45.0, 0.7, 10.4, 0.2, 250.0),
        presetFood("美式咖啡", "mskf", 2.0, 0.2, 0.0, 0.0, 300.0),
        presetFood("拿铁", "ntie", 55.0, 3.0, 5.0, 2.5, 300.0),
        presetFood("奶茶（全糖）", "ncha", 90.0, 1.0, 15.0, 3.0, 500.0),
        presetFood("花生（炒）", "hsheng", 589.0, 24.0, 21.0, 44.0, 30.0),
        presetFood("核桃（干）", "htao", 646.0, 14.9, 19.1, 58.8, 30.0),
        presetFood("腰果", "yguo", 552.0, 18.0, 30.0, 44.0, 30.0),
        presetFood("黑巧克力", "hqkl", 546.0, 7.8, 45.0, 35.0, 20.0),
        presetFood("薯片", "spian", 536.0, 6.0, 53.0, 34.0, 50.0),
        presetFood("饼干", "bgan", 435.0, 7.0, 65.0, 17.0, 50.0),
        // 常见菜 / 外卖
        presetFood("宫保鸡丁", "gbjd", 190.0, 12.0, 8.0, 12.0, 200.0),
        presetFood("红烧肉", "hsr", 470.0, 12.0, 8.0, 43.0, 150.0),
        presetFood("鱼香肉丝", "yxrs", 200.0, 10.0, 12.0, 12.0, 200.0),
        presetFood("麻婆豆腐", "mpdf", 150.0, 9.0, 6.0, 10.0, 200.0),
        presetFood("青椒肉丝", "qjrs", 160.0, 11.0, 6.0, 10.0, 200.0),
        presetFood("糖醋里脊", "tclj", 280.0, 13.0, 25.0, 14.0, 180.0),
        presetFood("炒青菜", "cqc", 65.0, 2.0, 4.0, 4.5, 200.0),
        presetFood("蛋炒饭", "dcf", 180.0, 6.0, 24.0, 6.5, 250.0),
        presetFood("扬州炒饭", "yzcf", 190.0, 7.0, 25.0, 7.0, 250.0),
        presetFood("牛肉面", "nrmian", 135.0, 7.0, 18.0, 3.5, 500.0),
        presetFood("炸鸡（带皮）", "zhaji", 280.0, 20.0, 12.0, 17.0, 150.0),
        presetFood("汉堡", "hanbao", 250.0, 12.0, 28.0, 10.0, 180.0),
        presetFood("披萨", "psa", 270.0, 11.0, 32.0, 11.0, 150.0),
        presetFood("炸薯条", "zst", 312.0, 4.0, 41.0, 15.0, 100.0),
        presetFood("小笼包", "xlb", 240.0, 9.0, 28.0, 10.0, 150.0),
        presetFood("煎饼果子", "jbgz", 230.0, 8.0, 32.0, 8.0, 200.0),
        presetFood("紫菜蛋花汤", "zcdht", 25.0, 1.5, 2.0, 1.0, 300.0),

        // ---- 外卖快餐（带品牌，食物库里单独归到「外卖快餐」）----
        // 数值是按公开营养表取的近似值，只用于估算；各家配方会变，仅供参考。
        fastFood("香辣鸡腿堡", "xljtb", 250.0, 12.0, 25.0, 11.0, 200.0, "肯德基"),
        fastFood("原味鸡", "ywj", 280.0, 25.0, 8.0, 17.0, 100.0, "肯德基"),
        fastFood("薯条", "st", 312.0, 4.0, 41.0, 15.0, 110.0, "肯德基"),
        fastFood("上校鸡块", "sxjk", 290.0, 15.0, 16.0, 19.0, 100.0, "肯德基"),
        fastFood("葡式蛋挞", "psdt", 375.0, 6.0, 30.0, 26.0, 60.0, "肯德基"),
        fastFood("帕尼尼", "pnn", 255.0, 12.0, 26.0, 11.0, 160.0, "肯德基"),
        fastFood("巨无霸", "jwb", 250.0, 13.0, 20.0, 13.0, 215.0, "麦当劳"),
        fastFood("麦辣鸡腿堡", "mljtb", 245.0, 13.0, 24.0, 11.0, 200.0, "麦当劳"),
        fastFood("薯条", "st", 310.0, 4.0, 41.0, 15.0, 115.0, "麦当劳"),
        fastFood("麦乐鸡", "mlj", 296.0, 16.0, 16.0, 19.0, 100.0, "麦当劳"),
        fastFood("皇堡", "hb", 230.0, 12.0, 18.0, 12.0, 290.0, "汉堡王"),
        fastFood("香辣鸡腿堡", "xljtb", 240.0, 11.0, 25.0, 11.0, 180.0, "华莱士"),
        fastFood("超级至尊披萨", "cjzspz", 250.0, 11.0, 27.0, 10.0, 130.0, "必胜客"),
        fastFood("排骨饭", "pgf", 160.0, 7.0, 22.0, 5.0, 450.0, "真功夫"),
        fastFood("农家小炒肉饭", "njxcxrf", 170.0, 8.0, 20.0, 6.0, 450.0, "老乡鸡"),
        fastFood("花生拌面", "hsbm", 152.0, 5.0, 24.0, 4.0, 250.0, "沙县小吃"),
        fastFood("现磨豆浆", "xmdj", 31.0, 3.0, 1.2, 1.6, 350.0, "永和大王"),
        fastFood("牛肉拉面", "nrlm", 112.0, 5.0, 18.0, 2.0, 500.0, "兰州拉面")
    )

    /**
     * 默认个人档案。
     * 这些数字故意和 M0 界面上写死的假数据对上：
     * 首页 2313 千卡 / 蛋白 115g / 碳水 289g / 脂肪 77g / 饮水 3200ml，
     * 进展页 当前 92kg → 目标 70kg。
     * 这样 M1 把界面改成读数据库时，数字不会突然跳变。
     */
    val defaultProfile: UserProfile = UserProfile(
        id = 1,                          // 档案表固定只有这一行
        gender = "male",
        birthday = LocalDate.of(2000, 1, 1),
        heightCm = 175.0,
        startWeight = 92.0,
        targetWeight = 70.0,
        activityLevel = 2,               // 1 久坐 / 2 轻度 / 3 中度 / 4 高度
        dailyKcalGoal = 2313,
        proteinGoalG = 115,
        carbGoalG = 289,
        fatGoalG = 77,
        waterGoalMl = 3200,
        unit = "kg"                      // 数据库永远存 kg，显示「斤」时只在界面乘 2
    )

    /**
     * 造一条预置食物的小工具。
     * 故意不叫 Food（和类名重名容易让编译器挑错），
     * 用它可以少写重复的参数名，上面每行才这么短。
     */
    /**
     * 造一条外卖快餐：比 presetFood 多一个品牌字段。
     * 有品牌的会被归到「外卖快餐」而不是「家庭厨房」。
     */
    private fun fastFood(
        name: String,
        pinyin: String,
        kcal: Double,
        protein: Double,
        carb: Double,
        fat: Double,
        grams: Double,
        brand: String
    ): Food = Food(
        name = name,
        pinyin = pinyin,
        kcalPer100g = kcal,
        proteinPer100g = protein,
        carbPer100g = carb,
        fatPer100g = fat,
        defaultGrams = grams,
        source = "preset",
        category = "快餐",
        brand = brand
    )

    private fun presetFood(
        name: String,
        pinyin: String,
        kcal: Double,
        protein: Double,
        carb: Double,
        fat: Double,
        grams: Double
    ): Food = Food(
        name = name,
        pinyin = pinyin,
        kcalPer100g = kcal,
        proteinPer100g = protein,
        carbPer100g = carb,
        fatPer100g = fat,
        defaultGrams = grams,
        source = "preset"               // 标明是预置数据，以后和用户自建的区分开
    )


        /**
         * 预置运动库（30 条常见项，MET 值取自《运动代谢当量》常见表）。
         * 记运动时选一项 + 填时长，消耗自动按「MET × 体重 × 时长」算，
         * 不再让用户自己填消耗数字。
         */
        fun presetExercise(name: String, met: Double, category: String) = com.qingji.app.data.local.entity.ExercisePreset(
            name = name, met = met, category = category
        )

        val presetExercises: List<com.qingji.app.data.local.entity.ExercisePreset> = listOf(
            presetExercise("快走 (5 km/h)", 3.5, "有氧"),
            presetExercise("快走 (6 km/h)", 5.0, "有氧"),
            presetExercise("上坡快走", 6.5, "有氧"),
            presetExercise("跑步 (8 km/h)", 8.3, "有氧"),
            presetExercise("跑步 (11 km/h)", 11.5, "有氧"),
            presetExercise("跳绳", 12.0, "有氧"),
            presetExercise("骑行 (19 km/h)", 8.0, "有氧"),
            presetExercise("骑行 (23 km/h)", 10.0, "有氧"),
            presetExercise("骑行 (26 km/h)", 12.0, "有氧"),
            presetExercise("动感单车", 8.5, "有氧"),
            presetExercise("划船机", 7.0, "有氧"),
            presetExercise("仰泳", 7.0, "有氧"),
            presetExercise("自由泳", 8.3, "有氧"),
            presetExercise("蛙泳", 5.8, "有氧"),
            presetExercise("爬楼梯", 8.0, "有氧"),
            presetExercise("椭圆机", 5.0, "有氧"),
            presetExercise("跑步机 (中速)", 7.0, "有氧"),
            presetExercise("HIIT 高强度间歇", 10.0, "有氧"),
            presetExercise("力量训练（轻）", 3.5, "力量"),
            presetExercise("力量训练（重）", 6.0, "力量"),
            presetExercise("俯卧撑", 4.0, "力量"),
            presetExercise("深蹲", 5.0, "力量"),
            presetExercise("平板支撑", 3.0, "力量"),
            presetExercise("瑜伽", 2.5, "柔韧"),
            presetExercise("拉伸", 2.3, "柔韧"),
            presetExercise("普拉提", 3.0, "柔韧"),
            presetExercise("羽毛球", 5.5, "有氧"),
            presetExercise("乒乓球", 4.0, "有氧"),
            presetExercise("篮球", 6.5, "有氧"),
            presetExercise("足球", 7.0, "有氧")
        )

}