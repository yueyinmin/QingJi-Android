package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.AllergenRules
import com.qingji.app.domain.FoodCategory
import com.qingji.app.domain.FoodIcon
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.EatenFood
import com.qingji.app.ui.viewmodel.FoodLibraryViewModel

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
    vm: FoodLibraryViewModel = viewModel()
) {
    val allFoods by vm.allFoods.collectAsState()
    val userFoods by vm.userFoods.collectAsState()
    val eatenFoods by vm.eatenFoods.collectAsState()

    // 读过敏原用来打警告
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsFlow = remember(context) {
        com.qingji.app.data.prefs.SettingsStore.observe(context)
    }
    val settings by settingsFlow.collectAsState(
        initial = com.qingji.app.data.prefs.QingJiSettings()
    )
    val allergens = settings.allergens.split(",").map { it.trim() }.filter { it.isNotBlank() }

    var tab by remember { mutableStateOf(0) }
    var kitchenTab by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf(FoodCategory.STAPLE) }
    var pick by remember { mutableStateOf<Food?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "食物库", onBack = onBack)

        // ---------- 一级页签 ----------
        Segmented(listOf("标准", "自定义", "吃过的"), tab) { tab = it }
        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> {
                Segmented(listOf("家庭厨房", "外卖快餐"), kitchenTab) { kitchenTab = it }
                Spacer(Modifier.height(8.dp))

                if (kitchenTab == 0) {
                    // 家庭厨房：左边分类，右边列表
                    val homeFoods = allFoods.filter { it.brand.isBlank() }
                    val available = FoodCategory.ALL.filter { cat ->
                        homeFoods.any { it.category == cat }
                    }
                    if (category !in available && available.isNotEmpty()) {
                        category = available.first()
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .width(88.dp)
                                .background(CardWhite)
                                .verticalScroll(rememberScrollState())
                        ) {
                            available.forEach { cat ->
                                val selected = cat == category
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (selected) QingJiGreenLight else Color.Transparent)
                                        .clickable { category = cat }
                                        .padding(vertical = 12.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = catEmoji(cat),
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        color = if (selected) QingJiGreenDark else TextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                            Spacer(Modifier.height(60.dp))
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp)
                        ) {
                            Spacer(Modifier.height(4.dp))
                            homeFoods.filter { it.category == category }.forEach { food ->
                                FoodRow(
                                    name = food.name,
                                    kcalPer100g = food.kcalPer100g,
                                    favorite = food.favorite,
                                    risky = AllergenRules.isRisky(food.name, allergens),
                                    onFavorite = { vm.toggleFavorite(food) },
                                    onClick = { pick = food }
                                )
                            }
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                } else {
                    // 外卖快餐：按品牌分组
                    val fastFoods = allFoods.filter { it.brand.isNotBlank() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Dimens.ScreenPadding)
                    ) {
                        if (fastFoods.isEmpty()) {
                            HintBox(text = "还没有外卖数据。后续可以用 CSV 批量导入各品牌菜品。")
                        } else {
                            fastFoods.groupBy { it.brand }.forEach { (brand, list) ->
                                Text(
                                    text = brand,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                list.forEach { food ->
                                    FoodRow(
                                        name = food.name,
                                        kcalPer100g = food.kcalPer100g,
                                        favorite = food.favorite,
                                        risky = AllergenRules.isRisky(food.name, allergens),
                                        onFavorite = { vm.toggleFavorite(food) },
                                        onClick = { pick = food }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }

            1 -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
            ) {
                Spacer(Modifier.height(4.dp))
                HintBox(text = "这里是你自己加的食物。AI 识别出的食物可以在「用餐详情 → 更多」里存进这里。")
                Spacer(Modifier.height(8.dp))
                if (userFoods.isEmpty()) {
                    Text(text = "还没有自定义食物，左滑可以删除", fontSize = 13.sp, color = TextSecondary)
                } else {
                    userFoods.forEach { food ->
                        com.qingji.app.ui.components.SwipeToRevealDelete(
                            onDelete = { vm.deleteCustomFood(food) }
                        ) {
                            FoodRow(
                                name = food.name,
                                kcalPer100g = food.kcalPer100g,
                                favorite = food.favorite,
                                risky = AllergenRules.isRisky(food.name, allergens),
                                onFavorite = { vm.toggleFavorite(food) },
                                onClick = { pick = food }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding)
            ) {
                Spacer(Modifier.height(4.dp))
                if (eatenFoods.isEmpty()) {
                    HintBox(text = "还没有吃过记录。记几笔饮食之后，这里会按食物名聚合出你常吃的东西。")
                } else {
                    eatenFoods.forEach { item ->
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
        }
    }

    // ---------- 加到某一餐 ----------
    pick?.let { food ->
        AddToMealDialog(
            foodName = food.name,
            defaultGrams = food.defaultGrams,
            onDismiss = { pick = null },
            onConfirm = { meal, grams, dayOffset ->
                vm.addToMeal(food.name, food.kcalPer100g, grams, meal, dayOffset)
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
private fun FoodRow(
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
                            .background(Color(0xFFE05A5A))
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
                color = if (favorite) Color(0xFFE0567A) else TextSecondary
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
        title = "加到哪一餐",
        subtitle = foodName,
        confirmText = "加入",
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
        Text(text = "记到哪一天", fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0L to "今天", 1L to "昨天", 2L to "前天").forEach { (offset, label) ->
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
            label = "份量（克）",
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
