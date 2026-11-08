package com.qingji.app.domain

/**
 * ============================================================
 * 食物分类（食物库左侧导航用）
 * ------------------------------------------------------------
 * 为什么不手工给 124 条预置食物贴标签：一条条改容易漏、容易错，
 * 而且以后加食物还得再标一次。改成按名字自动归类，
 * 新加的食物直接落到合适的类里。
 *
 * 规则顺序敏感：「西红柿鸡蛋面」要算面食不算蔬菜。
 * ============================================================
 */
object FoodCategory {

    const val STAPLE = "主食"
    const val NOODLE = "面食"
    const val HOME_DISH = "家常菜"
    const val MEAT = "肉类"
    const val SEAFOOD = "水产海鲜"
    const val VEGETABLE = "蔬菜"
    const val FRUIT = "水果"
    const val EGG_DAIRY = "蛋豆乳"
    const val SOUP = "汤羹"
    const val DRINK = "饮品甜点"
    const val SNACK = "零食"
    const val FAST_FOOD = "快餐"
    const val OTHER = "其他"

    /** 左侧导航的展示顺序。 */
    val ALL = listOf(
        STAPLE, NOODLE, HOME_DISH, MEAT, SEAFOOD, VEGETABLE,
        FRUIT, EGG_DAIRY, SOUP, DRINK, SNACK, FAST_FOOD, OTHER
    )

    private val rules = listOf(
        // 面食优先：带「面/粉/饺/包」的多半是主食类而不是菜
        NOODLE to listOf("面", "粉", "米线", "线面", "馄饨", "云吞", "饺", "包", "馒头", "花卷", "烧麦", "饼", "油条", "馍"),
        FAST_FOOD to listOf("肯德基", "麦当劳", "汉堡王", "华莱士", "德克士", "必胜客", "达美乐", "老乡鸡", "真功夫", "永和大王", "南城香", "乡村基", "沙县", "兰州拉面", "和府捞面", "遇见小面", "大米先生", "汉堡", "薯条", "鸡块", "披萨", "炸鸡"),
        SOUP to listOf("汤", "羹", "粥", "稀饭", "煲"),
        DRINK to listOf("茶", "咖啡", "拿铁", "美式", "果汁", "饮料", "奶茶", "可乐", "汽水", "豆浆", "牛奶", "酸奶", "酒", "啤酒", "豆花", "冰"),
        FRUIT to listOf("苹果", "梨", "桃", "橙", "橘", "柚", "莓", "葡萄", "樱桃", "芒果", "火龙果", "猕猴桃", "香蕉", "西瓜", "哈密瓜", "柠檬", "枣", "柿"),
        VEGETABLE to listOf("菜", "菠菜", "白菜", "生菜", "西兰花", "番茄", "西红柿", "黄瓜", "茄子", "萝卜", "蘑菇", "菌", "笋", "藕", "豆角", "青椒", "蒜苔", "花菜", "土豆"),
        SEAFOOD to listOf("鱼", "虾", "蟹", "贝", "蛤", "鱿鱼", "海参", "紫菜", "海带"),
        MEAT to listOf("牛", "猪", "羊", "鸡", "鸭", "鹅", "肉", "排骨", "火腿", "培根", "香肠"),
        // 主食放在蛋豆乳前面：「蛋炒饭」要算主食，不能因为有个「蛋」就归到蛋类
        STAPLE to listOf("米", "饭", "粥", "玉米", "红薯", "紫薯", "山药", "燕麦", "寿司", "薯"),
        EGG_DAIRY to listOf("蛋", "豆腐", "豆干", "腐竹", "豆皮"),
        SNACK to listOf("薯片", "饼干", "巧克力", "糖", "蛋糕", "冰淇淋", "雪糕", "布丁", "坚果", "花生", "核桃", "杏仁", "腰果", "瓜子"),
        HOME_DISH to listOf("炒", "烧", "焖", "拌", "卤", "煎", "炸", "蒸", "炖")
    )

    /**
     * 按名字猜分类。
     * @param brand 外卖品牌名，有值时优先归到「快餐」
     */
    fun guess(name: String, brand: String = ""): String {
        if (brand.isNotBlank()) return FAST_FOOD
        val text = name.trim()
        if (text.isEmpty()) return OTHER
        rules.forEach { (category, keywords) ->
            if (keywords.any { text.contains(it) }) return category
        }
        return OTHER
    }
}
