package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Food
import com.qingji.app.data.local.entity.UserBrandDish
import com.qingji.app.domain.FoodDataSource
import com.qingji.app.domain.FoodNutritionCalculator
import com.qingji.app.domain.FoodSearchResult
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.FoodLibrarySearchViewModel

/**
 * v1.5 阶段四：食物库独立全局搜索页。
 *
 * 工单要点：
 *   · 独立全屏页，不再往食物库页面插搜索框，原页布局/状态不受破坏
 *   · 顶部搜索框 + 取消返回，范围选择（全部/家庭厨房/外卖快餐/自定义）
 *   · 最近搜索 + 空状态
 *   · 一次覆盖：标准食物/自定义/自建品牌/品牌菜品，单列统一列表
 *   · 无结果：创建自定义食物 / 创建外卖品牌 / 重新搜索
 *   · imePadding + 键盘搜索动作，键盘不遮挡结果
 *   · 退出即 pop，底下食物库 Composable 一直在栈里，状态天然恢复
 */
@Composable
fun FoodLibrarySearchScreen(
    onBack: () -> Unit,
    onAddCustomFood: () -> Unit,
    onManageBrands: () -> Unit,
    onOpenFoodDetail: (Food) -> Unit = {},
    vm: FoodLibrarySearchViewModel = viewModel()
) {
    val keyword by vm.keyword.collectAsState()
    val scope by vm.scope.collectAsState()
    val results by vm.results.collectAsState()
    val history by vm.searchHistory.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var pickFood by remember { mutableStateOf<Food?>(null) }
    var pickDish by remember { mutableStateOf<UserBrandDish?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .imePadding()
    ) {
        // 顶部：搜索框 + 取消
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = vm::setKeyword,
                singleLine = true,
                placeholder = { Text("搜食物、品牌、菜品…", fontSize = 13.sp, color = TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = QingJiGreen, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, "清空",
                            modifier = Modifier.size(18.dp).clickable { vm.clearKeyword() },
                            tint = TextSecondary
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "取消", fontSize = 14.sp, color = QingJiGreen,
                modifier = Modifier.clickable { keyboard?.hide(); onBack() }
            )
        }

        // 范围选择：横排胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FoodSearchResult.Scope.entries.forEach { s ->
                val active = s == scope
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) QingJiGreen else PageBackground)
                        .clickable { vm.setScope(s) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.label, fontSize = 13.sp, color = if (active) Color.White else TextPrimary)
                }
            }
        }

        when {
            keyword.isBlank() -> {
                // 初始：最近搜索 + 空态提示
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.ScreenPadding)
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text("最近搜索", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    if (history.isEmpty()) {
                        HintBox(text = "输入食物名、品牌名或菜品名开始搜索")
                    } else {
                        HistoryChips(history = history, onUse = vm::useHistory)
                    }
                }
            }
            results.isEmpty() -> {
                // 无结果：创建引导
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("没有找到相关食物", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("换个词试试，或者自己建一个", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Button(onClick = onAddCustomFood) {
                        Text("创建自定义食物")
                    }
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedButton(onClick = onManageBrands) {
                        Text("创建外卖品牌")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("重新搜索", fontSize = 13.sp, color = QingJiGreen,
                        modifier = Modifier.clickable { vm.clearKeyword() })
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(results, key = {
                        when (it) {
                            is FoodSearchResult.FoodItem -> "f${it.food.id}_${it.food.name}"
                            is FoodSearchResult.BrandItem -> "b${it.brand.id}"
                            is FoodSearchResult.DishItem -> "d${it.dish.id}"
                        }
                    }) { r ->
                        when (r) {
                            is FoodSearchResult.FoodItem -> SearchFoodRow(
                                food = r.food,
                                onClick = { vm.rememberCurrent(); pickFood = r.food },
                                onDetail = { vm.rememberCurrent(); onOpenFoodDetail(r.food) }
                            )
                            is FoodSearchResult.BrandItem -> SearchBrandRow(
                                name = r.brand.name, icon = r.brand.icon, onClick = onManageBrands
                            )
                            is FoodSearchResult.DishItem -> SearchDishRow(
                                dish = r.dish, brandName = r.brandName,
                                onClick = { vm.rememberCurrent(); pickDish = r.dish }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    pickFood?.let { food ->
        SearchAddToMealSheet(
            title = food.name,
            basis = FoodNutritionCalculator.normalizeBasis(food.nutritionBasis),
            defaultGrams = food.defaultGrams,
            onDismiss = { pickFood = null },
            onAdd = { grams, meal, offset -> vm.addFoodToMeal(food, grams, meal, offset); pickFood = null }
        )
    }
    pickDish?.let { dish ->
        SearchAddToMealSheet(
            title = dish.name,
            basis = FoodNutritionCalculator.normalizeBasis(dish.nutritionBasis),
            defaultGrams = dish.servingGrams,
            onDismiss = { pickDish = null },
            onAdd = { grams, meal, offset -> vm.addDishToMeal(dish, grams, meal, offset); pickDish = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryChips(history: List<String>, onUse: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        history.forEach { h ->
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(CardWhite)
                    .clickable { onUse(h) }.padding(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(h, fontSize = 12.sp, color = TextSecondary) }
        }
    }
}

@Composable
private fun SearchFoodRow(food: Food, onClick: () -> Unit, onDetail: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.ItemCorner)).background(CardWhite)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground),
            contentAlignment = Alignment.Center) { Text("🍽", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(food.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            val kcal = if (food.kcalPer100g > 0) "${food.kcalPer100g.toInt()} 千卡 · ${FoodNutritionCalculator.basisLabel(food.nutritionBasis)}" else "暂无营养数据"
            Text(kcal, fontSize = 12.sp, color = TextSecondary)
            Text(FoodDataSource.label(food.source), fontSize = 11.sp, color = TextSecondary)
        }
        Text("详情", fontSize = 12.sp, color = QingJiGreen, modifier = Modifier.clickable(onClick = onDetail))
    }
}

@Composable
private fun SearchBrandRow(name: String, icon: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.ItemCorner)).background(CardWhite)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground),
            contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text("外卖品牌", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SearchDishRow(dish: UserBrandDish, brandName: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.ItemCorner)).background(CardWhite)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground),
            contentAlignment = Alignment.Center) { Text("🍲", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dish.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            val kcal = if (dish.kcal > 0) "${dish.kcal.toInt()} 千卡 · ${FoodNutritionCalculator.basisLabel(dish.nutritionBasis)}" else "暂无营养数据"
            Text(kcal, fontSize = 12.sp, color = TextSecondary)
            Text("${brandName} · ${FoodDataSource.label(dish.source)}", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SearchAddToMealSheet(
    title: String,
    basis: String,
    defaultGrams: Double,
    onDismiss: () -> Unit,
    onAdd: (Double, String, Int) -> Unit
) {
    var grams by remember { mutableStateOf(defaultGrams.toString()) }
    var meal by remember { mutableStateOf("lunch") }
    var offset by remember { mutableStateOf(0) }
    val meals = listOf("breakfast" to "早餐", "lunch" to "午餐", "dinner" to "晚餐", "snack" to "加餐")
    val g = grams.toDoubleOrNull() ?: 0.0
    AppBottomSheet(
        title = "加入饮食记录", subtitle = title,
        confirmText = "加入", onConfirm = { if (g > 0) onAdd(g, meal, offset) },
        confirmEnabled = g > 0, skipPartiallyExpanded = true, onDismiss = onDismiss
    ) {
        Column {
            Text("吃了多少（${FoodNutritionCalculator.basisLabel(basis)}）", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = grams, onValueChange = { grams = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Text("哪一餐", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { (value, label) ->
                    val active = value == meal
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (active) QingJiGreen else CardWhite)
                        .clickable { meal = value }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 12.sp, color = if (active) Color.White else TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "今天", 1 to "昨天").forEach { (value, label) ->
                    val active = value == offset
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (active) QingJiGreen else CardWhite)
                        .clickable { offset = value }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 12.sp, color = if (active) Color.White else TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
