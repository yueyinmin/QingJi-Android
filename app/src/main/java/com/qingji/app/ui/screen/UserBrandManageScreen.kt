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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.UserBrand
import com.qingji.app.data.local.entity.UserBrandDish
import com.qingji.app.domain.FoodDataSource
import com.qingji.app.domain.FoodNutritionCalculator
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.UserBrandViewModel

/**
 * v1.5 阶段四：用户自建外卖品牌管理页。
 *
 * 落地工单要求：
 *   · 品牌用顶部横滑胶囊切换，不再左侧竖栏
 *   · 新增品牌 / 品牌备注 / 数据来源说明
 *   · 菜品复用统一营养录入（基准 + 三宏量），来源标识
 *   · 菜品可编辑/删除、可加入饮食记录（走统一折算）
 *   · 删品牌时关联菜品一起清（VM/DAO 事务），不动历史 food_log
 */
@Composable
fun UserBrandManageScreen(
    onBack: () -> Unit,
    vm: UserBrandViewModel = viewModel()
) {
    val brands by vm.brands.collectAsState()
    val selectedId by vm.selectedBrandId.collectAsState()
    val dishes by vm.dishes.collectAsState()
    val message by vm.message.collectAsState()

    var showBrandSheet by remember { mutableStateOf(false) }
    var editingBrand by remember { mutableStateOf<UserBrand?>(null) }
    var showDishSheet by remember { mutableStateOf(false) }
    var editingDish by remember { mutableStateOf<UserBrandDish?>(null) }
    var addingDish by remember { mutableStateOf(false) }
    var deleteBrand by remember { mutableStateOf<UserBrand?>(null) }
    var deleteDish by remember { mutableStateOf<UserBrandDish?>(null) }
    var pickDish by remember { mutableStateOf<UserBrandDish?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(brands) { vm.ensureSelected(brands) }
    LaunchedEffect(message) { if (message != null) vm.consumeMessage() }

    val currentBrand = brands.firstOrNull { it.id == selectedId }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = "我的外卖品牌",
            onBack = onBack
            // QJ-FOOD-UI-003：删除右上角「+ 新增品牌」入口；已有品牌仍可查看/编辑。
        )

        if (brands.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("🏪", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("暂无自建品牌", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    // QJ-FOOD-UI-003：删除空态「+ 新增品牌」按钮；
                    // 新增品牌入口已全面收起，已有品牌数据、菜品、溯源关联完整保留。
                }
            }
        } else {
            // 顶部横滑品牌胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                brands.forEach { b ->
                    val active = b.id == selectedId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) QingJiGreen else CardWhite)
                            .clickable { vm.selectBrand(b.id) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(b.icon, fontSize = 15.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            b.name,
                            fontSize = 13.sp,
                            color = if (active) Color.White else TextPrimary,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }

            // 品牌信息 + 操作行
            if (currentBrand != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenPadding)
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(CardWhite)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${currentBrand.name} · ${dishes.size} 道菜", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(FoodDataSource.label(currentBrand.sourceLabel.ifBlank { "USER_BRAND" }), fontSize = 11.sp, color = TextSecondary)
                    }
                    IconButton(onClick = { showInfo = true }) { Icon(Icons.Filled.Info, "品牌说明", tint = TextSecondary) }
                    IconButton(onClick = { editingBrand = currentBrand; showBrandSheet = true }) { Icon(Icons.Filled.Edit, "编辑品牌", tint = TextSecondary) }
                    IconButton(onClick = { deleteBrand = currentBrand }) { Icon(Icons.Filled.Delete, "删除品牌", tint = DangerRed) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(text = "+ 新增菜品", onClick = { editingDish = null; addingDish = true; showDishSheet = true }, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            if (dishes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    HintBox(text = "这个品牌还没有菜品，点上方「新增菜品」添加")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dishes, key = { it.id }) { dish ->
                        BrandDishManageRow(
                            dish = dish,
                            onClick = { pickDish = dish },
                            onEdit = { editingDish = dish; showDishSheet = true },
                            onDelete = { deleteDish = dish }
                        )
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }

    // 新建/编辑品牌
    if (showBrandSheet) {
        val editing = editingBrand
        BrandFormSheet(
            brand = editing,
            onDismiss = { showBrandSheet = false },
            onSave = { name, icon, note, source ->
                if (editing != null) {
                    // 编辑：只更新，绝不新建（保留原 id/排序）
                    vm.updateBrand(editing.copy(name = name.trim(), icon = icon, note = note, sourceLabel = source))
                    showBrandSheet = false
                } else {
                    vm.createBrand(name, icon, note, source, onResult = { showBrandSheet = false })
                }
            }
        )
    }

    // 新建/编辑菜品
    if (showDishSheet && currentBrand != null) {
        DishFormSheet(
            dish = editingDish,
            brandId = currentBrand.id,
            onDismiss = { showDishSheet = false },
            onSave = { d -> vm.saveDish(d, onResult = { showDishSheet = false }) }
        )
    }

    // 加入饮食记录
    pickDish?.let { dish ->
        BrandDishAddToMealSheet(
            dish = dish,
            onDismiss = { pickDish = null },
            onAdd = { grams, meal, offset -> vm.addDishToMeal(dish, grams, meal, offset); pickDish = null }
        )
    }

    // 品牌说明
    if (showInfo && currentBrand != null) {
        AppDialog(title = "${currentBrand.name} · 数据来源", onDismiss = { showInfo = false }) {
            Text("数据来源：${FoodDataSource.label(currentBrand.sourceLabel.ifBlank { "USER_BRAND" })}", fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("适用范围：本品牌下 ${dishes.size} 道自建菜品", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Text("这些营养数据由你手动录入，未收录的项目显示「暂无营养数据」，不代表官方数值。", fontSize = 13.sp, color = TextSecondary)
            if (currentBrand.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("备注：${currentBrand.note}", fontSize = 13.sp, color = TextSecondary)
            }
        }
    }

    // 删除品牌确认
    deleteBrand?.let { b ->
        AppDialog(
            title = "删除品牌「${b.name}」？",
            onDismiss = { deleteBrand = null },
            confirmText = "删除",
            danger = true,
            onConfirm = { vm.deleteBrand(b.id); deleteBrand = null }
        ) {
            Text("这个品牌下的 ${dishes.size} 道菜品会一起删除。已经记过的饮食记录不受影响。", fontSize = 13.sp, color = TextSecondary)
        }
    }

    // 删除菜品确认
    deleteDish?.let { d ->
        AppDialog(
            title = "删除菜品「${d.name}」？",
            onDismiss = { deleteDish = null },
            confirmText = "删除",
            danger = true,
            onConfirm = { vm.deleteDish(d.id); deleteDish = null }
        ) {
            Text("已经记过的饮食记录不受影响。", fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun BrandDishManageRow(
    dish: UserBrandDish,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            .background(CardWhite)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(PageBackground),
            contentAlignment = Alignment.Center
        ) { Text("🍽", fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dish.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            val kcalText = if (dish.kcal > 0) "${ dish.kcal.toInt() } 千卡 · ${FoodNutritionCalculator.basisLabel(dish.nutritionBasis)}" else "暂无营养数据"
            Text(kcalText, fontSize = 12.sp, color = TextSecondary)
            Text(FoodDataSource.label(dish.source), fontSize = 11.sp, color = TextSecondary)
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑", tint = TextSecondary, modifier = Modifier.size(20.dp)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", tint = DangerRed, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun BrandFormSheet(
    brand: UserBrand?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(brand?.name ?: "") }
    var icon by remember { mutableStateOf(brand?.icon ?: "🏪") }
    var note by remember { mutableStateOf(brand?.note ?: "") }
    var source by remember { mutableStateOf(brand?.sourceLabel ?: "") }
    AppBottomSheet(
        title = if (brand == null) "新增品牌" else "编辑品牌",
        confirmText = "保存",
        onConfirm = { if (name.isNotBlank()) onSave(name, icon, note, source) },
        confirmEnabled = name.isNotBlank(),
        skipPartiallyExpanded = true,
        onDismiss = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LabeledTextField(label = "品牌名称 *", value = name, onValueChange = { name = it }, hint = "例如：老乡鸡、肯德基")
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "品牌标识（emoji）", value = icon, onValueChange = { icon = it }, hint = "例如：🏪 🍗 🍜")
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "数据来源说明（选填）", value = source, onValueChange = { source = it }, hint = "例如：菜单公开数据、自己估算")
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "品牌备注（选填）", value = note, onValueChange = { note = it }, hint = "记录这个品牌的来源、备注", singleLine = false, minLines = 2)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DishFormSheet(
    dish: UserBrandDish?,
    brandId: Long,
    onDismiss: () -> Unit,
    onSave: (UserBrandDish) -> Unit
) {
    var name by remember { mutableStateOf(dish?.name ?: "") }
    var category by remember { mutableStateOf(dish?.category ?: "其他") }
    var kcal by remember { mutableStateOf(dish?.kcal?.toString() ?: "") }
    var protein by remember { mutableStateOf(dish?.protein?.toString() ?: "") }
    var fat by remember { mutableStateOf(dish?.fat?.toString() ?: "") }
    var carb by remember { mutableStateOf(dish?.carb?.toString() ?: "") }
    var basis by remember { mutableStateOf(dish?.nutritionBasis ?: FoodNutritionCalculator.PER_100G) }
    var serving by remember { mutableStateOf(dish?.servingGrams?.toString() ?: "100") }
    var source by remember { mutableStateOf(dish?.source ?: FoodDataSource.USER_BRAND) }
    var note by remember { mutableStateOf(dish?.note ?: "") }

    val basisItems = listOf(
        FoodNutritionCalculator.PER_100G to "每100克",
        FoodNutritionCalculator.PER_100ML to "每100毫升",
        FoodNutritionCalculator.PER_SERVING to "每份"
    )
    val sourceItems = listOf(
        FoodDataSource.OFFICIAL to "官方数据",
        FoodDataSource.PUBLIC to "公开数据",
        FoodDataSource.USER_BRAND to "自建品牌数据",
        FoodDataSource.MANUAL to "用户手动录入"
    )

    AppBottomSheet(
        title = if (dish == null) "新增菜品" else "编辑菜品",
        confirmText = "保存",
        onConfirm = {
            if (name.isNotBlank()) {
                onSave(
                    (dish ?: UserBrandDish(brandId = brandId, name = name.trim())).copy(
                        brandId = brandId,
                        name = name.trim(),
                        category = category.trim().ifBlank { "其他" },
                        kcal = kcal.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        protein = protein.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        fat = fat.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        carb = carb.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        nutritionBasis = basis,
                        servingGrams = serving.toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0,
                        source = source,
                        note = note.trim()
                    )
                )
            }
        },
        confirmEnabled = name.isNotBlank(),
        skipPartiallyExpanded = true,
        onDismiss = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LabeledTextField(label = "菜品名称 *", value = name, onValueChange = { name = it }, hint = "例如：肥西老母鸡汤")
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "菜品分类", value = category, onValueChange = { category = it }, hint = "例如：主食 / 凉拌 / 饮品")
            Spacer(Modifier.height(14.dp))
            Text("计量基准", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                basisItems.forEach { (value, label) ->
                    val active = value == basis
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (active) QingJiGreen else CardWhite)
                            .clickable { basis = value }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 12.sp, color = if (active) Color.White else TextPrimary)
                    }
                }
            }
            if (basis == FoodNutritionCalculator.PER_SERVING) {
                Spacer(Modifier.height(10.dp))
                LabeledTextField(label = "每份重量（克）", value = serving, onValueChange = { serving = it }, decimalOnly = true)
            }
            Spacer(Modifier.height(14.dp))
            Text("营养数据 · ${FoodNutritionCalculator.basisLabel(basis)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QingJiGreen)
            Spacer(Modifier.height(8.dp))
            LabeledTextField(label = "热量（千卡）", value = kcal, onValueChange = { kcal = it }, decimalOnly = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { LabeledTextField(label = "蛋白质（克）", value = protein, onValueChange = { protein = it }, decimalOnly = true) }
                Box(Modifier.weight(1f)) { LabeledTextField(label = "脂肪（克）", value = fat, onValueChange = { fat = it }, decimalOnly = true) }
            }
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "碳水（克）", value = carb, onValueChange = { carb = it }, decimalOnly = true)
            Spacer(Modifier.height(14.dp))
            Text("数据来源", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sourceItems.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (source == value) QingJiGreen else CardWhite)
                            .clickable { source = value }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 13.sp, color = if (source == value) Color.White else TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LabeledTextField(label = "备注（选填）", value = note, onValueChange = { note = it }, hint = "没有可靠数据时留空，界面显示「暂无营养数据」")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandDishAddToMealSheet(
    dish: UserBrandDish,
    onDismiss: () -> Unit,
    onAdd: (Double, String, Int) -> Unit
) {
    var grams by remember { mutableStateOf(if (dish.nutritionBasis == FoodNutritionCalculator.PER_SERVING) dish.servingGrams.toString() else "100") }
    var meal by remember { mutableStateOf("lunch") }
    var offset by remember { mutableStateOf(0) }
    val meals = listOf("breakfast" to "早餐", "lunch" to "午餐", "dinner" to "晚餐", "snack" to "加餐")
    val g = grams.toDoubleOrNull() ?: 0.0
    AppBottomSheet(
        title = "加入饮食记录",
        subtitle = dish.name,
        confirmText = "加入",
        onConfirm = { if (g > 0) onAdd(g, meal, offset) },
        confirmEnabled = g > 0,
        skipPartiallyExpanded = true,
        onDismiss = onDismiss
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("吃了多少（${FoodNutritionCalculator.basisLabel(dish.nutritionBasis)}）", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            LabeledTextField(label = "克数 / 毫升 / 份数重量", value = grams, onValueChange = { grams = it }, decimalOnly = true)
            Spacer(Modifier.height(14.dp))
            Text("哪一餐", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { (value, label) ->
                    val active = value == meal
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (active) QingJiGreen else CardWhite)
                            .clickable { meal = value }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 12.sp, color = if (active) Color.White else TextPrimary) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("日期", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "今天", 1 to "昨天").forEach { (value, label) ->
                    val active = value == offset
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (active) QingJiGreen else CardWhite)
                            .clickable { offset = value }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = 12.sp, color = if (active) Color.White else TextPrimary) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
