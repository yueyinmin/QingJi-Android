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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import com.qingji.app.domain.BrandDishDetail
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
 * 外卖快餐 → 选品牌 → 品牌菜单（第六阶段 UI，工单 2.3/2.4/2.5）
 * ------------------------------------------------------------
 * 品牌胶囊由数据库 brand 表动态驱动（红线 5：不写死老乡鸡按钮），
 * 横向滚动不换行；选中浅绿底 + 绿字，切换只换菜单。
 *
 * 用户拍板：菜单 = 老乡鸡 301 道真实堂食菜（卡片标「堂食·溯源报告2.0」），
 * 每道可点开折叠溯源详情。外卖商品 8 张表全空 → 顶部如实显示空态，
 * 绝不把堂食菜复制成外卖商品（红线 3/15）。
 *
 * 家庭厨房分支、底部导航、收藏、饮食记录链路一律不碰：本组件只替换
 * FoodLibraryScreen 里 kitchenTab==1 的旧「FAST_FOOD 平铺」实现。
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
    var traceDish by remember { mutableStateOf<Food?>(null) }
    var traceDetail by remember { mutableStateOf<BrandDishDetail?>(null) }

    // 进入外卖分支：默认选首个品牌（数据库给什么就选什么，不写死）
    LaunchedEffect(brands) {
        if (brands.isNotEmpty() && selectedBrandId !in brands.map { it.brandId }) {
            vm.ensureBrandSelected()
            selectedCategory = ""
        }
    }
    // 品牌变了重置分类，避免上一个品牌的分类残留（工单 2.5-4：只更新列表，不闪烁）
    LaunchedEffect(selectedBrandId) { selectedCategory = "" }

    val selectedBrand: BrandEntity? = brands.firstOrNull { it.brandId == selectedBrandId }
    val q = query.trim()

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 品牌选择栏：横向滚动胶囊（首期只有老乡鸡）----
        if (brands.isNotEmpty()) {
            Text(
                text = s("选择品牌"),
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                brands.forEach { brand ->
                    val selected = brand.brandId == selectedBrandId
                    Text(
                        text = brand.name,
                        fontSize = 13.sp,
                        color = if (selected) QingJiGreenDark else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .background(if (selected) QingJiGreenLight else CardWhite)
                            .clickable {
                                vm.selectBrand(brand.brandId)
                                selectedCategory = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (selectedBrand == null) {
            HintBox(text = s("暂无可用品牌"))
            return@Column
        }

        // ---- 外卖商品空态（8 张表全空，如实说明，不造假）----
        HintBox(text = s("${selectedBrand.name}外卖商品暂无公开数据；下面展示的是堂食菜品，来自菜品溯源报告 2.0"))
        Spacer(Modifier.height(6.dp))

        // ---- 分类栏：堂食菜按实际分类动态生成，无菜分类不展示 ----
        val allLabel = s("全部")
        val categories = remember(menu, allLabel) {
            listOf(allLabel) + menu.map { it.category }.distinct().filter { it.isNotBlank() }.sorted()
        }
        val effectiveCategory = if (selectedCategory in categories) selectedCategory else categories.first()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val selected = cat == effectiveCategory
                Text(
                    text = cat,
                    fontSize = 12.sp,
                    color = if (selected) QingJiGreenDark else TextSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .background(if (selected) QingJiGreenLight else CardWhite)
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // ---- 菜单列表：按分类 + 搜索词过滤（搜索沿用现有 matchesFood 口径）----
        val filtered = menu.filter { food ->
            (effectiveCategory == s("全部") || food.category == effectiveCategory) &&
                (q.isBlank() || food.name.contains(q, ignoreCase = true) ||
                    food.pinyin.startsWith(q, ignoreCase = true) ||
                    food.pinyinFull.startsWith(q, ignoreCase = true) ||
                    food.aliases.split("|").any { it.contains(q, ignoreCase = true) })
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            if (filtered.isEmpty()) {
                HintBox(text = s("这道分类下暂时没有菜品"))
            } else {
                filtered.forEach { food ->
                    BrandDishRow(
                        food = food,
                        risky = AllergenRules.isRisky(food.name, allergens),
                        onFavorite = { vm.toggleFavorite(food) },
                        onClick = { onPick(food) },
                        onTrace = {
                            traceDish = food
                        }
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // ---- 载入溯源详情（异步），成功后弹折叠面板 ----
    LaunchedEffect(traceDish) {
        traceDetail = traceDish?.let { vm.brandDishDetail(it) }
    }
    traceDish?.let { dish ->
        TraceDetailSheet(
            dishName = dish.name,
            detail = traceDetail,
            onDismiss = { traceDish = null; traceDetail = null }
        )
    }
}

/** 品牌菜单的一行：复用 LibraryFoodRow 的视觉语言，标「堂食·溯源2.0」+ 溯源按钮。 */
@Composable
private fun BrandDishRow(
    food: Food,
    risky: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
    onTrace: () -> Unit
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
                text = "${food.kcalPer100g.toInt()} 千卡/100g · ${s("堂食")}·${s("溯源报告2.0")}",
                fontSize = 11.sp, color = TextSecondary
            )
        }
        // 溯源入口
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onTrace() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = s("菜品溯源"),
                tint = QingJiGreen,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(2.dp))
        // 收藏
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

/**
 * 菜品溯源折叠详情（工单第九节十项内容）。
 * 有数据分组折叠展示；trace 为空显示「暂无公开资料」（不推测供应商/加工方式）。
 */
@Composable
private fun TraceDetailSheet(
    dishName: String,
    detail: BrandDishDetail?,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text(text = s("关闭"), color = QingJiGreenDark,
                modifier = Modifier.clickable { onDismiss() }.padding(8.dp))
        },
        title = { Text(text = "$dishName · ${s("菜品溯源")}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (detail == null) {
                    Text(text = s("暂无公开资料"), fontSize = 13.sp, color = TextSecondary)
                } else {
                    TraceSection(s("加工等级"), detail.processingLabel)
                    val page = detail.trace.reportPage.takeIf { it > 0 }?.toString() ?: detail.trace.pdfPage.toString()
                    TraceSection(s("报告版本与页码"), "${detail.trace.sourceVersion} · ${s("第")}$page ${s("页")}")
                    if (detail.trace.cookingMethod.isNotBlank()) {
                        TraceSection(s("烹饪方式"), detail.trace.cookingMethod)
                    }
                    if (detail.ingredients.isNotEmpty()) {
                        TraceSection(
                            s("原料组成与来源"),
                            detail.ingredients.joinToString("\n") { ing ->
                                "· ${ing.ingredientName}" + (if (ing.supplier.isNotBlank()) "（${ing.supplier}）" else "")
                            }
                        )
                    }
                    if (detail.processing.isNotEmpty()) {
                        TraceSection(
                            s("中央厨房 / 门店处理"),
                            detail.processing.joinToString("\n") { "· ${it.item}：${it.description}" }
                        )
                    }
                    if (detail.trace.deliveryText.isNotBlank()) {
                        TraceSection(s("配送与冷链"), detail.trace.deliveryText)
                    }
                    if (detail.trace.storeProcessText.isNotBlank()) {
                        TraceSection(s("门店制作流程"), detail.trace.storeProcessText)
                    }
                    Text(
                        text = s("数据来源：老乡鸡菜品溯源报告 2.0；未逐条人工核对，仅供参考"),
                        fontSize = 11.sp, color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun TraceSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
        Spacer(Modifier.height(2.dp))
        Text(text = body, fontSize = 12.sp, color = TextPrimary)
    }
}
