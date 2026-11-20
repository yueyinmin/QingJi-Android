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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.qingji.app.data.local.entity.BrandEntity
import com.qingji.app.data.local.entity.Food
import com.qingji.app.domain.AllergenRules
import com.qingji.app.domain.FoodIcon
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.FoodLibraryViewModel

/**
 * ============================================================
 * 外卖快餐 → 品牌 → 菜品（v1.6 食物库整体重构）
 * ------------------------------------------------------------
 * 布局与「家庭厨房」「标准库」统一为双栏：左侧品牌竖排分类，
 * 右侧顶部菜品分类横排 + LazyColumn 卡片列表。
 * 品牌由 brand 表动态驱动（红线 5 不写死品牌），左右独立滚动。
 *
 * v1.6 已按工单删除「堂食菜品」标签、右侧信息按钮、品牌说明弹窗及
 * 卡片上的堂食标记；列表只展示真实菜品数据，没数据的显示普通空状态。
 * 品牌/菜品溯源数据与外卖独立库结构保留不动；收藏、搜索、记餐链路不变。
 * ============================================================
 */
@Composable
fun BrandMenuScreen(
    vm: FoodLibraryViewModel,
    allergens: List<String>,
    query: String,
    onPick: (Food) -> Unit
) {
    val brands by vm.brands.collectAsState()
    val selectedBrandId by vm.selectedBrandId.collectAsState()
    val menu by vm.brandMenu.collectAsState()

    var selectedCategory by remember { mutableStateOf("") }
    // QJ-FOOD-UI-003：按主人决定删除菜品溯源 UI 入口（ⓘ 图标 + 弹层 + 状态）。
    // 溯源数据与查询能力（Repository.brandDishDetail、相关数据表）全部保留，仅 UI 不再展示。

    // 进入外卖分支：默认选首个品牌（数据库给什么就选什么，不写死）
    LaunchedEffect(brands) {
        if (brands.isNotEmpty() && selectedBrandId !in brands.map { it.brandId }) {
            vm.ensureBrandSelected()
            selectedCategory = ""
        }
    }
    LaunchedEffect(selectedBrandId) { selectedCategory = "" }

    val selectedBrand: BrandEntity? = brands.firstOrNull { it.brandId == selectedBrandId }
    val q = query.trim()

    // 2026-11-18 UI 改造：对齐截图「家庭厨房」的双级布局——
    // 左边竖排大类（这里是品牌），右边顶部横排细类（这里是菜品分类），下面是卡片列表。
    // 数据口径、收藏、溯源、搜索、记餐链路一律不动，只改排布。
    if (brands.isEmpty() || selectedBrand == null) {
        HintBox(text = s("暂无可用品牌"))
    } else {
        // 菜品分类：当前品牌菜单里出现过的 category，全部 + 具体类，横排可滑动
        val allLabel = s("全部")
        val categories = remember(menu, allLabel) {
            listOf(allLabel) + menu.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
        }
        val effectiveCategory = if (selectedCategory in categories) selectedCategory else categories.first()

        val filtered = remember(menu, effectiveCategory, q) {
            menu.filter { food ->
                (effectiveCategory == allLabel || food.category == effectiveCategory) &&
                    (q.isBlank() || food.name.contains(q, ignoreCase = true) ||
                        food.pinyin.startsWith(q, ignoreCase = true) ||
                        food.pinyinFull.startsWith(q, ignoreCase = true) ||
                        food.aliases.split("|").any { it.contains(q, ignoreCase = true) })
            }.sortedWith(
                // 有热量的排前面；同样没数据的按名字排，翻起来有章法。
                compareBy<Food> { it.kcalPer100g <= 0.0 }.thenBy { it.name }
            )
        }

        // v1.6：恢复双栏——左侧品牌竖排（数据库动态驱动，不写死），右侧分类+菜品列表。
        // 已删除「堂食菜品」标签、信息按钮、品牌说明弹窗及卡片堂食标记；
        // 品牌/菜品溯源数据与外卖独立库结构保留不动。
        com.qingji.app.ui.components.FoodTwoPane(
            modifier = Modifier.fillMaxSize(),
            categories = brands.map { it.brandId },
            selected = selectedBrandId,
            labelOf = { id -> "🏪 " + (brands.firstOrNull { it.brandId == id }?.name ?: id) },
            onSelect = { id -> vm.selectBrand(id); selectedCategory = "" }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 右侧顶部：菜品分类横排胶囊
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val selected = cat == effectiveCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.PillCorner))
                                .background(if (selected) QingJiGreen else QingJiGreenLight)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                color = if (selected) CardWhite else QingJiGreenDark,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    if (filtered.isEmpty()) {
                        item { HintBox(text = s("这道分类下暂时没有菜品")) }
                    } else {
                        items(count = filtered.size, key = { i -> filtered[i].id }, contentType = { "brand_food_row" }) { i ->
                            val food = filtered[i]
                            BrandDishRow(
                                food = food,
                                risky = AllergenRules.isRisky(food.name, allergens),
                                onFavorite = { vm.toggleFavorite(food) },
                                onClick = { onPick(food) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

}

/** 一行菜：和家庭厨房 LibraryFoodRow 同款视觉；QJ-FOOD-UI-003 起去掉溯源 ⓘ 按钮。 */
@Composable
private fun BrandDishRow(
    food: Food,
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
            Text(text = FoodIcon.emoji(food.name), fontSize = 19.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = food.name, fontSize = 14.sp, color = TextPrimary)
                if (risky) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = s("含过敏原"), fontSize = 9.sp, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(DangerRed).padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (food.kcalPer100g > 0.0) {
                    "${food.kcalPer100g.toInt()} ${s("千卡")}/100g"
                } else {
                    s("暂无热量数据")
                },
                fontSize = 11.sp, color = TextSecondary
            )
        }
        // QJ-FOOD-UI-003：删除溯源 ⓘ 按钮，菜品列表更干净；过敏原安全提示（上方红色标记）保留。
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).clickable { onFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (food.favorite) "♥" else "♡",
                fontSize = 17.sp,
                color = if (food.favorite) QingJiGreen else TextSecondary
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

// QJ-FOOD-UI-003：溯源弹层组件（TraceDetailSheet / TraceSection）已随 ⓘ 入口一并移除。
// 溯源数据查询能力（Repository.brandDishDetail）与数据表保留，未做数据库迁移。

