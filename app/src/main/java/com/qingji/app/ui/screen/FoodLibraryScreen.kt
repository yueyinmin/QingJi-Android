package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Food
import com.qingji.app.domain.AllergenRules
import com.qingji.app.domain.FoodCategory
import com.qingji.app.domain.NutritionMatch
import com.qingji.app.domain.FoodIcon
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.EatenFood
import com.qingji.app.ui.viewmodel.FoodLibraryViewModel

/** v1.5 阶段二：家庭厨房顶部「全部」分类的哨兵值（不与任何真实分类重名）。 */
private const val ALL_CATEGORY = "__ALL__"

/**
 * ============================================================
 * 食物库（v2.0）
 * ------------------------------------------------------------
 * 三个页签：
 * - 标准：预置食物，再分「家庭厨房 / 外卖快餐」；家庭厨房按分类筛
 * - 自定义：用户自己加的（含从 AI 识别结果存进来的）
 * - 吃过的：近 90 天按名字聚合的历史
 *
 * 每一条都能收藏（心形）；点一下能直接加到今天某一餐。
 * 命中过敏原的会打警告（设置 → 饮食偏好与忌口 里选的）。
 * ============================================================
 */
@Composable
fun FoodLibraryScreen(
    onBack: () -> Unit,
    onAddCustomFood: () -> Unit = {},
    onEditCustomFood: (Long) -> Unit = {},
    // QJ-FOOD-UI-003：品牌新增/管理入口已移除，此回调保留兼容导航签名（当前不触发）。
    onManageBrands: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    vm: FoodLibraryViewModel = viewModel()
) {
    val allFoods by vm.allFoods.collectAsState()
    val catalogFoods by vm.catalogFoods.collectAsState()
    val userFoods by vm.userFoods.collectAsState()
    val eatenFoods by vm.eatenFoods.collectAsState()

    // 读过敏原用来打警告
    // TASK-05：统一从 LocalAppSettings 取，不再单独订阅 SettingsStore。
    val settings = com.qingji.app.ui.LocalAppSettings.current
    val allergens = settings.allergens.split(",").map { it.trim() }.filter { it.isNotBlank() }

    // v1.5 阶段四（工单 5.6）：来源/品牌/分类/收藏筛选改用 rememberSaveable，
    // 旋转屏幕/进程回收后自动恢复；配合「独立搜索页」方案（退出只 pop 搜索页、
    // 本页 Composable 一直在返回栈里没销毁），滚动位置与各筛选状态天然保留。
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var kitchenTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    var category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(ALL_CATEGORY) }
    // 家庭厨房的横排细类（大类右上的那排小胶囊）。空串 = 全部。
    var subCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var pick by remember { mutableStateOf<Food?>(null) }
    // v1.5 阶段四：搜索改为独立全屏页，食物库页内不再有搜索框/搜索词。
    // query 保留空串仅供旧的家常菜"相关食物"逻辑读取（恒不触发），不影响分类。
    val query = ""
    var showFavorites by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 家庭厨房列表滚动位置：用 Saver 存进 savedState，退出搜索/旋转后回到原来的位置
    val homeListState = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.foundation.lazy.LazyListState.Saver
    ) { androidx.compose.foundation.lazy.LazyListState() }

    // 「我的收藏」当成第 4 个 tab：原来三页的内容一行都不用动
    val effectiveTab = if (showFavorites) 3 else tab
    val title = if (showFavorites) s("我的收藏") else when (tab) {
        0 -> s("标准食物库")
        1 -> s("自定义食物")
        else -> s("吃过的")
    }
    // 搜索：名字包含，空串不过滤
    val matches: (String) -> Boolean = { name ->
        val q = query.trim()
        q.isBlank() || name.contains(q, ignoreCase = true)
    }
    // v3.4.0 F8：食物库条目额外支持拼音首字母（sp → 薯片）。
    // v1.0.6：补上全拼和别名——原来只认「名字包含 + 首字母前缀」，
    // 所以搜 mifan 搜不到米饭、搜别名也搜不到，跟「搜索支持中文/别名/首字母/全拼」
    // 的说明对不上（文字识别那个搜索页是四种都支持的，两个入口行为不一致最坑人）。
    // 「吃过的」是聚合数据、没有 pinyin 字段，那里仍按名字匹配。
    val matchesFood: (com.qingji.app.data.local.entity.Food) -> Boolean = { food ->
        val q = query.trim()
        matches(food.name) ||
            (q.isNotEmpty() && (
                food.pinyin.startsWith(q, ignoreCase = true) ||
                    food.pinyinFull.startsWith(q, ignoreCase = true) ||
                    food.aliases.split("|").any { it.isNotBlank() && it.contains(q, ignoreCase = true) }
                ))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题栏：返回 / 居中标题 / 搜索（参考吃有数）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = s("返回"),
                tint = TextPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .pressable { onBack() }
            )
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // QJ-FOOD-UI-003：标题栏右侧图标按页签分流——
            // 标准食物库=搜索；自定义食物=加号（进新增自定义食物）；吃过的=不显示图标。
            // 三个分支都放同尺寸 22.dp 占位：隐藏图标时标题不会左右偏移。
            when (effectiveTab) {
                3 -> Spacer(Modifier.size(22.dp)) // 我的收藏：不显示图标
                1 -> Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = s("新增自定义食物"),
                    tint = QingJiGreen,
                    modifier = Modifier
                        .size(22.dp)
                        .pressable { onAddCustomFood() }
                )
                2 -> Spacer(Modifier.size(22.dp)) // 吃过的：不显示搜索图标
                else -> Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = s("搜索食物"),
                    tint = QingJiGreen,
                    modifier = Modifier
                        .size(22.dp)
                        .pressable { onOpenSearch() }
                )
            }
        }

        // v1.5 阶段四：搜索框已移到独立全屏页（点上面的放大镜进入），
        // 本页不再条件插入输入框，页面结构/分类/底栏不再被搜索破坏。

        // 2026-11-18：去掉标题下那行免责小字（主人嫌它是多余的小黑字备注）

        // 内容区占满剩余高度，底栏才不会被列表挤出屏幕
        Column(modifier = Modifier.weight(1f)) {
        when (effectiveTab) {
            0 -> {
                // QJ-FOOD-UI-003：标题栏与分类分段之间拉开间距（8dp → 16dp），
                // 让「家庭厨房 / 外卖快餐」不再贴着标题；Segmented 自身已是独立分段组件。
                Spacer(Modifier.height(8.dp))
                Segmented(listOf(s("家庭厨房"), s("外卖快餐")), kitchenTab) { kitchenTab = it }
                Spacer(Modifier.height(16.dp))

                if (kitchenTab == 0) {
                    // v1.6 TASK-05 数据隔离：标准库只显示 food_catalog 的标准食物，不掺自定义
                    val homeFoods = catalogFoods.filter {
                        it.category != FoodCategory.FAST_FOOD && matchesFood(it)
                    }
                    val available = FoodCategory.ALL.filter { cat ->
                        homeFoods.any { it.category == cat }
                    }
                    // 选中分类若无数据则回落「全部」（而不是强选第一个）
                    if (category != ALL_CATEGORY && category !in available && available.isNotEmpty()) {
                        category = ALL_CATEGORY
                    }

                    // v1.3.10 搜索升级：搜索时不再被左侧分类卡住——
                    // 命中跨全部分类显示；命中不足 3 条时自动补「相关食物」
                    // （NutritionMatch 相似度 ≥45，如搜姜母鸭带出啤酒鸭/盐水鸭），
                    // 解决「搜一道菜只出一条，看着少一大截」的问题。
                    val searching = query.trim().isNotEmpty()
                    val related = if (searching) {
                        catalogFoods.filter {
                            it.category != FoodCategory.FAST_FOOD &&
                                !matchesFood(it) &&
                                NutritionMatch.score(query.trim(), it.name) >= 45
                        }.take(12)
                    } else {
                        emptyList()
                    }

                    // 2026-11-18：右上横排细类（照外卖快餐同款样式）。
                    // 细类来自数据里的 subcategory 字段，按食物条数多的排前面；
                    // 纯英文的值（staple/dish/fruit 这类录入痕迹）不上横栏，
                    // 那些食物照样能在「全部」里翻到。
                    val subs = homeFoods
                        .filter { (category == ALL_CATEGORY || it.category == category) && it.subcategory.isNotBlank() }
                        .groupingBy { it.subcategory }
                        .eachCount()
                        .entries
                        .filter { (it.key.firstOrNull()?.code ?: 0) > 127 } // 只要中文细类
                        .sortedByDescending { it.value }
                        .map { it.key }
                    val effectiveSub = if (subCategory in subs) subCategory else ""
                    val allSubLabel = s("全部")

                    // v1.6：恢复双栏——左侧主分类竖排，右侧细类横排 + 食物列表。
                    // 左右独立滚动，切分类只换右侧数据、不重建页面。
                    val twoPaneAllLabel = s("全部")
                    com.qingji.app.ui.components.FoodTwoPane(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        categories = listOf(ALL_CATEGORY) + available,
                        selected = category,
                        labelOf = { cat -> if (cat == ALL_CATEGORY) twoPaneAllLabel else catEmoji(cat) + " " + cat },
                        onSelect = { cat -> category = cat; subCategory = "" }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 横排细类胶囊：和外卖快餐右上那排同款（搜索模式下隐藏）
                            if (!searching && subs.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 10.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(allSubLabel).forEach { label ->
                                        val selected = effectiveSub == ""
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            color = if (selected) Color.White else TextSecondary,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (selected) QingJiGreen else CardWhite)
                                                .clickable { subCategory = "" }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                    subs.forEach { sub ->
                                        val selected = sub == effectiveSub
                                        Text(
                                            text = sub,
                                            fontSize = 12.sp,
                                            color = if (selected) Color.White else TextSecondary,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (selected) QingJiGreen else CardWhite)
                                                .clickable { subCategory = sub }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // v1.4 性能专项（工单 11）：Column 全量组合 → LazyColumn 只组合可见行。
                            // 家常菜 190 条、全库 1121 条时，旧写法每次进分类都要把整个列表
                            // 的组件全部建出来，来回切换明显卡顿；LazyColumn 视口外不组合。
                            // 2026-11-18：再叠加细类过滤。
                            val filtered = if (searching) {
                                homeFoods
                            } else {
                                homeFoods.filter {
                                    (category == ALL_CATEGORY || it.category == category) &&
                                        (effectiveSub == "" || it.subcategory == effectiveSub)
                                }
                            }
                            androidx.compose.foundation.lazy.LazyColumn(
                                state = homeListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            ) {
                                item { Spacer(Modifier.height(4.dp)) }
                                if (searching) {
                                    item {
                                        Text(
                                            text = s("匹配结果") + " · " + filtered.size,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (filtered.isEmpty() && related.isEmpty()) {
                                    item {
                                        HintBox(text = s("这道分类下暂时没有菜品"))
                                    }
                                }
                                items(
                                    count = filtered.size,
                                    key = { i -> filtered[i].id },
                                    contentType = { "food_row" }
                                ) { i ->
                                    val food = filtered[i]
                                    LibraryFoodRow(
                                        name = if (searching) food.name + "（" + food.category + "）" else food.name,
                                        kcalPer100g = food.kcalPer100g,
                                        favorite = food.favorite,
                                        risky = AllergenRules.isRisky(food.name, allergens),
                                        onFavorite = { vm.toggleFavorite(food) },
                                        onClick = { pick = food }
                                    )
                                }
                                if (searching && related.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = s("相关食物") + " · " + related.size,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                                items(
                                    count = related.size,
                                    key = { i -> "rel_" + related[i].id },
                                    contentType = { "food_row" }
                                ) { i ->
                                    val food = related[i]
                                    LibraryFoodRow(
                                        name = food.name + "（" + food.category + "）",
                                        kcalPer100g = food.kcalPer100g,
                                        favorite = food.favorite,
                                        risky = AllergenRules.isRisky(food.name, allergens),
                                        onFavorite = { vm.toggleFavorite(food) },
                                        onClick = { pick = food }
                                    )
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    } // FoodTwoPane 结束
                } else {
                    // QJ-FOOD-UI-003：删除「+ 管理我的外卖品牌」入口按钮。
                    // 品牌数据、品牌菜单、菜品营养、溯源关联全部保留，仍由 BrandMenuScreen 正常展示。
                    // 外卖快餐（第六阶段）：品牌选择 → 品牌菜单 → 菜品列表。
                    // 品牌由数据库动态驱动；堂食菜当菜单；外卖商品空态如实提示。
                    // 家庭厨房分支、底栏、收藏、记录链路都不动。
                    BrandMenuScreen(
                        vm = vm,
                        allergens = allergens,
                        query = query,
                        onPick = { food -> pick = food }
                    )
                }
            }

            1 -> Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // v1.6：自定义食物库整体重构（就地搜索 + 食物/食物组双胶囊 + 多对多食物组）
                CustomFoodLibraryScreen(
                    vm = vm,
                    allergens = allergens,
                    onAddCustomFood = onAddCustomFood,
                    onEditCustomFood = onEditCustomFood,
                    onPick = { food -> pick = food }
                )
            }

            2 -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
            ) {
                Spacer(Modifier.height(4.dp))
                if (eatenFoods.isEmpty()) {
                    HintBox(text = "记几笔之后，常吃的东西会自动出现在这里")
                } else {
                    eatenFoods.filter { matches(it.name) }.forEach { item ->
                        EatenRow(item = item, onClick = {
                            // 「吃过的」也能直接加一餐：转成临时 Food 复用同一个弹窗
                            pick = Food(
                                name = item.name,
                                kcalPer100g = item.kcalPer100g,
                                defaultGrams = 100.0
                            )
                        })
                    }
                }
                Spacer(Modifier.height(80.dp))
            }

            // ---------- 我的收藏（右下心形按钮切进来） ----------
            3 -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
            ) {
                val favorites = (allFoods + userFoods)
                    .filter { it.favorite && matchesFood(it) }
                    .distinctBy { it.name }
                if (favorites.isEmpty()) {
                    HintBox(text = "点列表右边的 ♡ 收藏；右下角的心形可以只看收藏")
                } else {
                    favorites.forEach { food ->
                        LibraryFoodRow(
                            name = food.name,
                            kcalPer100g = food.kcalPer100g,
                            favorite = true,
                            risky = AllergenRules.isRisky(food.name, allergens),
                            onFavorite = { vm.toggleFavorite(food) },
                            onClick = { pick = food }
                        )
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        } // 内容区 Column 结束
    } // Column 结束

    // ---------- v1.6 悬浮 Dock：标准 / 自定义 / 吃过的 + 独立收藏按钮 ----------
    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
        com.qingji.app.ui.components.FoodLibraryDock(
            items = listOf(
                com.qingji.app.ui.components.FoodDockItem(s("标准"), Icons.Outlined.Description),
                com.qingji.app.ui.components.FoodDockItem(s("自定义"), Icons.Outlined.EditNote),
                com.qingji.app.ui.components.FoodDockItem(s("吃过的"), Icons.Outlined.History)
            ),
            selectedIndex = tab,
            onSelect = { index -> tab = index; showFavorites = false },
            favoriteActive = showFavorites,
            onFavoriteClick = { showFavorites = !showFavorites }
        )
    }
    } // 外层 Box 结束

    // ---------- 加到某一餐 ----------
    pick?.let { food ->
        AddToMealDialog(
            foodName = food.name,
            defaultGrams = food.defaultGrams,
            onDismiss = { pick = null },
            onConfirm = { meal, grams, dayOffset ->
                vm.addToMeal(
                    food.name,
                    food.kcalPer100g,
                    grams,
                    meal,
                    dayOffset,
                    food.proteinPer100g,
                    food.carbPer100g,
                    food.fatPer100g,
                    food.nutritionBasis,
                    food.defaultGrams
                )
                pick = null
            }
        )
    }
}

@Composable
private fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding)
            .clip(RoundedCornerShape(Dimens.PillCorner))
            .background(CardWhite)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (selected) QingJiGreenDark else TextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.PillCorner))
                    .background(if (selected) QingJiGreenLight else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun LibraryFoodRow(
    name: String,
    kcalPer100g: Double,
    favorite: Boolean,
    risky: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            .background(CardWhite)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(PageBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(text = FoodIcon.emoji(name), fontSize = 19.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, fontSize = 14.sp, color = TextPrimary)
                if (risky) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "含过敏原",
                        fontSize = 9.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DangerRed)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${kcalPer100g.toInt()} 千卡/100g",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (favorite) "♥" else "♡",
                fontSize = 17.sp,
                color = if (favorite) QingJiGreen else TextSecondary
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun EatenRow(item: EatenFood, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            .background(CardWhite)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(PageBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(text = FoodIcon.emoji(item.name), fontSize = 19.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "最近 ${item.lastDate} · 吃过 ${item.count} 次 · " +
                    "${item.kcalPer100g.toInt()} 千卡/100g",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** 选餐次 + 填克数。 */
@Composable
private fun AddToMealDialog(
    foodName: String,
    defaultGrams: Double,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Int) -> Unit
) {
    var meal by remember { mutableStateOf(MealRules.guess()) }
    var grams by remember { mutableStateOf(defaultGrams.toInt().toString()) }
    var dayOffset by remember { mutableStateOf(0L) }

    com.qingji.app.ui.components.AppBottomSheet(
        title = s("加到哪一餐"),
        subtitle = foodName,
        confirmText = s("加入"),
        onConfirm = {
            val g = grams.toDoubleOrNull()?.coerceIn(1.0, 3000.0) ?: defaultGrams
            onConfirm(meal, g, dayOffset.toInt())
        },
        onDismiss = onDismiss
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MealRules.ALL.forEach { type ->
                val selected = type == meal
                Text(
                    text = MealRules.label(type),
                    fontSize = 12.sp,
                    color = if (selected) Color.White else TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(if (selected) QingJiGreen else PageBackground)
                        .clickable { meal = type }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(12.dp))
        Text(text = s("记到哪一天"), fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0L to s("今天"), 1L to s("昨天"), 2L to s("前天")).forEach { (offset, label) ->
                val selected = dayOffset == offset
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (selected) Color.White else TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) QingJiGreen else PageBackground)
                        .clickable { dayOffset = offset }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        com.qingji.app.ui.components.LabeledTextField(
            label = s("份量（克）"),
            value = grams,
            onValueChange = { grams = it },
            hint = "100",
            numberOnly = true
        )
    }
}

/** 分类的小图标，纯装饰。 */
private fun catEmoji(category: String): String = when (category) {
    FoodCategory.STAPLE -> "🍚"
    FoodCategory.NOODLE -> "🍜"
    FoodCategory.HOME_DISH -> "🥘"
    FoodCategory.MEAT -> "🍖"
    FoodCategory.SEAFOOD -> "🐟"
    FoodCategory.VEGETABLE -> "🥬"
    FoodCategory.FRUIT -> "🍎"
    FoodCategory.EGG_DAIRY -> "🥚"
    FoodCategory.SOUP -> "🍲"
    FoodCategory.DRINK -> "☕"
    FoodCategory.SNACK -> "🍪"
    FoodCategory.FAST_FOOD -> "🍔"
    else -> "🍽"
}
