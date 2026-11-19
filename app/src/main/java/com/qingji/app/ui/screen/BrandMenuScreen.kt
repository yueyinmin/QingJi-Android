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
 * 外卖快餐 → 品牌 → 菜品（v1.3.5 UI 对齐家庭厨房）
 * ------------------------------------------------------------
 * 布局与「家庭厨房」统一为双栏：左侧竖排分类，右侧 LazyColumn 卡片列表。
 * 差别只在顶部多一行品牌胶囊（数据由 brand 表动态驱动，红线 5 不写死品牌）。
 *
 * 数据口径：菜单仍是老乡鸡 301 道堂食菜（外卖 8 张表为空，如实说明）。
 * 40 道原来显示「0 千卡」的菜已从本地可信库匹配（同名/同类近似，来源写进
 * source_note）；剩下没数据的显示「暂无热量数据」并排到分类末尾——
 * 绝不把「没数据」说成「0 千卡」（红线 3/15）。
 *
 * 家庭厨房分支、底部导航、收藏、记餐链路一律不动。
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

        Column(modifier = Modifier.fillMaxSize()) {
            // v1.5 阶段三：品牌从左侧竖栏改到顶部横滑选择器（单列布局），
            // 左栏不再挤压内容区。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                brands.forEach { brand ->
                    val selected = brand.brandId == selectedBrandId
                    Text(
                        text = "🏪 " + brand.name,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .background(if (selected) QingJiGreen else CardWhite)
                            .clickable {
                                vm.selectBrand(brand.brandId)
                                selectedCategory = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            // v1.5 阶段三：品牌说明从常驻大段文字收进轻量 Info 入口，
            // 只留一行简短状态提示，不再长期占据主页面。
            var showBrandInfo by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s("堂食菜品"),
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.IconButton(onClick = { showBrandInfo = true }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Info,
                        contentDescription = "品牌说明",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (showBrandInfo) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showBrandInfo = false },
                    title = { Text(selectedBrand.name) },
                    text = {
                        Text(
                            "数据来源：菜品溯源报告 2.0（公开数据）。\n" +
                                "适用范围：本品牌堂食菜品；外卖商品暂无公开数据。\n" +
                                "没有可靠营养数据的菜品显示「暂无营养数据」，不作估算。"
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showBrandInfo = false }) { Text("知道了") }
                    }
                )
            }

            // ---------- 横排细类 + 菜列表（单列，占满宽度） ----------
            Column(modifier = Modifier.weight(1f)) {
                // 横排细类胶囊（可横向滑动），跟家庭厨房「面条/包点/馄饨」那排一个样式
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
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
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    if (filtered.isEmpty()) {
                        item { HintBox(text = s("这道分类下暂时没有菜品")) }
                    } else {
                        items(
                            count = filtered.size,
                            key = { i -> filtered[i].id },
                            contentType = { "brand_food_row" }
                        ) { i ->
                            val food = filtered[i]
                            BrandDishRow(
                                food = food,
                                risky = AllergenRules.isRisky(food.name, allergens),
                                onFavorite = { vm.toggleFavorite(food) },
                                onClick = { onPick(food) },
                                onTrace = { traceDish = food }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ---- 溯源详情异步加载，成功后弹折叠面板 ----
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

/** 一行菜：和家庭厨房 LibraryFoodRow 同款视觉，多一个溯源按钮。 */
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
                text = if (food.kcalPer100g > 0.0) {
                    "${food.kcalPer100g.toInt()} ${s("千卡")}/100g · ${s("堂食")}"
                } else {
                    s("暂无热量数据")
                },
                fontSize = 11.sp, color = TextSecondary
            )
        }
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

/** 菜品溯源详情：有数据分组展示，没有就如实说「暂无公开资料」。 */
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

