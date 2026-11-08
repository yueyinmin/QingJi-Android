package com.qingji.app.ui.screen.record

import android.widget.Toast
import com.qingji.app.domain.AppClock
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.Food
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.MealSelector
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.SearchFoodViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 手动搜索食物（M1 最稳的一条记录路径，不用联网、不用配 AI）
 * ------------------------------------------------------------
 * 流程：输关键词（支持中文名 / 拼音首字母，比如 mf → 米饭）
 *      → 点结果 → 填克数、选餐次 → 确认
 *      → 按「每 100 克」的营养值换算后写进 food_log → 回首页
 *
 * 保存成功直接返回首页，让使用者立刻看到仪表盘数字变了。
 * ============================================================
 */
@Composable
fun SearchFoodScreen(
    onBack: () -> Unit,
    vm: SearchFoodViewModel = viewModel()
) {
    val keyword by vm.keyword.collectAsState()
    val results by vm.results.collectAsState()
    val foodCount by vm.foodCount.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 点中的那一条食物；非空就弹出「填份量」对话框
    var picked by remember { mutableStateOf<Food?>(null) }
    var gramsText by remember { mutableStateOf("") }
    // 记到哪一天：默认今天，可以改成昨天/前天，或者手填日期
    var dateText by remember { mutableStateOf(AppClock.today().toString()) }
    var mealType by remember { mutableStateOf(MealRules.guess()) }
    var errorText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "手动搜索食物", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            LabeledTextField(
                label = "搜索食物",
                value = keyword,
                onValueChange = vm::onKeywordChange,
                hint = "输入名称或拼音首字母，如：米饭 / mf"
            )

            Spacer(Modifier.height(10.dp))
            // 三种状态要分开说清楚，别让用户看到空列表就以为 App 坏了：
            // 还在载入 / 一条食物都没有 / 有食物库但没搜到这个词
            when {
                foodCount < 0 -> HintBox(text = "食物库正在载入，稍等一下下…")
                foodCount == 0 -> HintBox(
                    text = "食物库现在是空的（预置食物还没写进去）。点下面的按钮补回来就能用了。",
                    isError = true
                )
                results.isEmpty() -> HintBox(
                    text = "食物库有 ${foodCount} 条，但没找到「$keyword」。换个说法试试，比如「米饭」「鸡蛋」。"
                )
                else -> HintBox(text = "共 ${results.size} 条 · 点一条填份量")
            }

            // 食物库为空时的兜底出口：不用重装 App，也不用连电脑
            if (foodCount == 0) {
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "恢复预置食物库",
                    onClick = { scope.launch { vm.restorePresetFoods() } }
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard(contentPadding = 0.dp) {
                results.forEachIndexed { index, food ->
                    FoodRow(food = food, onClick = {
                        picked = food
                        gramsText = food.defaultGrams.toInt().toString()
                        mealType = MealRules.guess()
                        errorText = ""
                    })
                    if (index != results.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(1.dp)
                                .background(DividerColor)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- 填份量 + 选餐次的对话框 ----------
    val food = picked
    if (food != null) {
        com.qingji.app.ui.components.AppBottomSheet(
            title = food.name,
            subtitle = "每 100 克约 ${food.kcalPer100g.toInt()} 千卡 · 蛋白 ${food.proteinPer100g.toInt()}g",
            confirmText = "保存",
            closeOnConfirm = false,
            onConfirm = {
                val grams = gramsText.toDoubleOrNull()
                if (grams == null || grams <= 0) {
                    errorText = "请填一个大于 0 的克数"
                    return@AppBottomSheet
                }
                val targetDate = runCatching { java.time.LocalDate.parse(dateText.trim()) }.getOrNull()
                if (targetDate == null) {
                    errorText = "日期格式不对，要写成 2026-11-08 这样"
                    return@AppBottomSheet
                }
                scope.launch {
                    try {
                        vm.addFood(food, grams, mealType, targetDate)
                        picked = null
                        Toast.makeText(context, "已记入${MealRules.label(mealType)}", Toast.LENGTH_SHORT).show()
                        onBack()
                    } catch (e: Exception) {
                        errorText = "保存失败：${e.message ?: "未知原因"}，请再点一次"
                    }
                }
            },
            onDismiss = { picked = null }
        ) {
            Column {
                    Spacer(Modifier.height(4.dp))
                    LabeledTextField(
                        label = "吃了多少克",
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        hint = "例如 150",
                        decimalOnly = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    MealSelector(selected = mealType, onSelect = { mealType = it })

                    Spacer(Modifier.height(12.dp))
                    Text(text = "记到哪一天", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0L to "今天", 1L to "昨天", 2L to "前天").forEach { (offset, label) ->
                            val value = AppClock.today().minusDays(offset).toString()
                            val selected = dateText == value
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable { dateText = value }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LabeledTextField(
                        label = "或者自己填（2026-11-08）",
                        value = dateText,
                        onValueChange = { dateText = it },
                        hint = "2026-11-08"
                    )

                    if (errorText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HintBox(text = errorText, isError = true)
                    }
                }
            }
    }
    }

/** 一条搜索结果：左边名称与拼音，右边每 100 克热量。 */
@Composable
private fun FoodRow(food: Food, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = food.name, fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append("每 100g · 蛋白 ${food.proteinPer100g.toInt()}g")
                    append(" · 碳水 ${food.carbPer100g.toInt()}g")
                    append(" · 脂肪 ${food.fatPer100g.toInt()}g")
                },
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${food.kcalPer100g.toInt()}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = QingJiGreenDark
            )
            Text(text = "千卡/100g", fontSize = 10.sp, color = TextSecondary)
        }
    }
}
