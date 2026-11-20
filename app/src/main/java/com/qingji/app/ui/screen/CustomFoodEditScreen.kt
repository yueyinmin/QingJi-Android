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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.FoodCategory
import com.qingji.app.domain.FoodNutritionCalculator
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.CustomFoodEditViewModel

/**
 * v1.5 阶段二：新增 / 编辑自定义食物表单。
 *
 * 工单要点落地：
 *   · 食物名称必填；品牌/条码/备注选填
 *   · 热量 + 蛋白/脂肪/碳水各自独立输入，支持小数，禁负数（VM 校验）
 *   · 计量基准三选一：每100g / 每100ml / 每份；每份克数仅在「每份」时有意义
 *   · 4-9-4 一致性只提示、不覆盖用户填写
 *   · 保存后可继续编辑、删除（删除在食物库列表左滑完成）
 *
 * @param editId 0 = 新增；>0 = 编辑该 id
 */
@Composable
fun CustomFoodEditScreen(
    editId: Long = 0L,
    onBack: () -> Unit,
    vm: CustomFoodEditViewModel = viewModel()
) {
    val loaded by vm.loaded.collectAsState()
    val saved by vm.saved.collectAsState()

    val name by vm.name.collectAsState()
    val brand by vm.brand.collectAsState()
    val kcal by vm.kcal.collectAsState()
    val protein by vm.protein.collectAsState()
    val carb by vm.carb.collectAsState()
    val fat by vm.fat.collectAsState()
    val fiber by vm.fiber.collectAsState()
    val sodium by vm.sodium.collectAsState()
    val barcode by vm.barcode.collectAsState()
    val category by vm.category.collectAsState()
    val note by vm.note.collectAsState()
    val basis by vm.basis.collectAsState()
    val servingGrams by vm.servingGrams.collectAsState()

    var attempted by remember { mutableStateOf(false) }

    // 进入页面时回填（新建或编辑）
    LaunchedEffect(editId) { vm.startEdit(editId) }

    // 保存成功后返回上一页（食物库会自动刷新 Flow）
    LaunchedEffect(saved) { if (saved != null) onBack() }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = if (editId > 0) "编辑食物" else "新增自定义食物",
            onBack = onBack
        )

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = TextSecondary, fontSize = 14.sp)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- 基本信息 ----
            SectionTitle("基本信息")
            LabeledTextField(
                label = "食物名称 *",
                value = name,
                onValueChange = vm::setName,
                hint = "例如：乳清蛋白粉"
            )
            if (attempted && vm.nameError != null) {
                FieldError(vm.nameError!!)
            }
            Spacer(Modifier.height(10.dp))
            LabeledTextField(
                label = "品牌名称（选填）",
                value = brand,
                onValueChange = vm::setBrand,
                hint = "例如：Optimum Nutrition"
            )
            Spacer(Modifier.height(10.dp))
            CategoryPicker(selected = category, onSelect = vm::setCategory)
            Spacer(Modifier.height(10.dp))
            LabeledTextField(
                label = "商品条码（选填）",
                value = barcode,
                onValueChange = vm::setBarcode,
                hint = "填了之后扫码会优先命中这个食物"
            )

            Spacer(Modifier.height(18.dp))

            // ---- 计量基准 ----
            SectionTitle("营养数据按什么计量")
            HintBox(text = "先选计量基准，再填下面的营养数值。蛋白粉等按包装上的每100g填写即可。")
            Spacer(Modifier.height(8.dp))
            BasisPicker(selected = basis, onSelect = vm::setBasis)
            if (basis == FoodNutritionCalculator.PER_SERVING) {
                Spacer(Modifier.height(10.dp))
                LabeledTextField(
                    label = "每份重量（克）",
                    value = servingGrams,
                    onValueChange = vm::setServingGrams,
                    decimalOnly = true
                )
                if (attempted && vm.numberError("serving") != null) {
                    FieldError(vm.numberError("serving")!!)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- 营养数据 ----
            SectionTitle("营养数据 · ${FoodNutritionCalculator.basisLabel(basis)}")
            LabeledTextField(
                label = "热量（千卡）*",
                value = kcal,
                onValueChange = vm::setKcal,
                hint = "每${FoodNutritionCalculator.basisLabel(basis).removePrefix("每")}的热量",
                decimalOnly = true
            )
            if (attempted && vm.numberError("kcal") != null) FieldError(vm.numberError("kcal")!!)

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    LabeledTextField(
                        label = "蛋白质（克）", value = protein, onValueChange = vm::setProtein,
                        hint = "选填", decimalOnly = true
                    )
                }
                Box(Modifier.weight(1f)) {
                    LabeledTextField(
                        label = "脂肪（克）", value = fat, onValueChange = vm::setFat,
                        hint = "选填", decimalOnly = true
                    )
                }
            }
            if (attempted && (vm.numberError("protein") != null || vm.numberError("fat") != null)) {
                FieldError(listOfNotNull(vm.numberError("protein"), vm.numberError("fat")).joinToString("；"))
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    LabeledTextField(
                        label = "碳水（克）", value = carb, onValueChange = vm::setCarb,
                        hint = "选填", decimalOnly = true
                    )
                }
                Box(Modifier.weight(1f)) {
                    LabeledTextField(
                        label = "膳食纤维（克）", value = fiber, onValueChange = vm::setFiber,
                        hint = "选填", decimalOnly = true
                    )
                }
            }
            if (attempted && (vm.numberError("carb") != null || vm.numberError("fiber") != null)) {
                FieldError(listOfNotNull(vm.numberError("carb"), vm.numberError("fiber")).joinToString("；"))
            }

            Spacer(Modifier.height(10.dp))
            LabeledTextField(
                label = "钠（毫克）",
                value = sodium,
                onValueChange = vm::setSodium,
                hint = "选填，包装上若以克计请 ×1000",
                decimalOnly = true
            )
            if (attempted && vm.numberError("sodium") != null) FieldError(vm.numberError("sodium")!!)

            // 4-9-4 一致性：只提示，不覆盖
            vm.consistencyNote()?.let { note ->
                Spacer(Modifier.height(10.dp))
                HintBox(text = note, isError = true)
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("备注（选填）")
            LabeledTextField(
                label = "",
                value = note,
                onValueChange = vm::setNote,
                hint = "记录来源、口味、包装信息等",
                singleLine = false,
                minLines = 3
            )

            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (editId > 0) "保存修改" else "保存到我的食物库",
                onClick = {
                    attempted = true
                    if (vm.nameError == null &&
                        listOf("kcal", "protein", "carb", "fat", "fiber", "sodium", "serving")
                            .none { vm.numberError(it) != null }
                    ) {
                        vm.save()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = QingJiGreen,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun FieldError(text: String) {
    Text(text = text, fontSize = 12.sp, color = DangerRed, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun BasisPicker(selected: String, onSelect: (String) -> Unit) {
    val items = listOf(
        FoodNutritionCalculator.PER_100G to "每100克",
        FoodNutritionCalculator.PER_100ML to "每100毫升",
        FoodNutritionCalculator.PER_SERVING to "每份"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) QingJiGreen else CardWhite)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (active) androidx.compose.ui.graphics.Color.White else TextSecondary,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Text("食物分类", fontSize = 13.sp, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
    val rows = FoodCategory.ALL.chunked(4)
    Column {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { cat ->
                    val active = cat == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) QingJiGreen else CardWhite)
                            .clickable { onSelect(cat) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            color = if (active) androidx.compose.ui.graphics.Color.White else TextPrimary
                        )
                    }
                }
            }
        }
    }
}
