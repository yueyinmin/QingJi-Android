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
import com.qingji.app.domain.AllergenRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.viewmodel.SettingsViewModel

/**
 * ============================================================
 * 饮食偏好与忌口（v2.0）
 * ------------------------------------------------------------
 * 这一页的每一项都不是摆设，直接喂给 AI：
 * - 过敏原 / 忌口 → 拼进每次识别的规则（含用餐详情里的「重新识别」）
 * - 烹饪时长 / 菜系 → 进偏好，用于推荐时加权
 *
 * 用的是独立页面而不是弹窗：选项多，弹窗里塞不下。
 * ============================================================
 */
@Composable
fun FoodPreferenceScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val settings by vm.settings.collectAsState()

    val selectedAllergens = remember(settings.allergens) {
        settings.allergens.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
    }
    var allergens by remember(settings.allergens) { mutableStateOf(selectedAllergens.toList()) }
    var avoidFoods by remember(settings.avoidFoods) {
        mutableStateOf(settings.avoidFoods.split(",").map { it.trim() }.filter { it.isNotBlank() })
    }
    var maxCook by remember(settings.maxCookMinutes) { mutableStateOf(settings.maxCookMinutes) }
    var cuisines by remember(settings.cuisines) {
        mutableStateOf(settings.cuisines.split(",").map { it.trim() }.filter { it.isNotBlank() })
    }
    var newAvoid by remember { mutableStateOf("") }

    fun save() {
        vm.setAllergens(allergens.joinToString(","))
        vm.setAvoidFoods(avoidFoods.joinToString(","))
        vm.setMaxCookMinutes(maxCook)
        vm.setCuisines(cuisines.joinToString(","))
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(
            title = "饮食偏好与忌口",
            onBack = onBack,
            actionText = "保存",
            onAction = { save() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))

            HintBox(
                text = "这些设置只影响 AI 识别和推荐：命中过敏原或忌口的食物会被标出来，" +
                    "含过敏原的推荐会被直接过滤。"
            )

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 过敏原 ----------
            SectionCard {
                Text(
                    text = "过敏原",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选中的过敏原，含它的食物会打警告，推荐里不出现",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                AllergenRules.ALL.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val selected = item in allergens
                            Text(
                                text = item,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.PillCorner))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable {
                                        allergens = if (selected) allergens - item else allergens + item
                                    }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 忌口食物 ----------
            SectionCard {
                Text(
                    text = "忌口食物",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "不喜欢吃的东西，比如香菜、内脏、苦瓜",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                if (avoidFoods.isEmpty()) {
                    Text(text = "还没有添加忌口食物", fontSize = 12.sp, color = TextSecondary)
                } else {
                    avoidFoods.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Dimens.PillCorner))
                                        .background(QingJiGreenLight)
                                        .clickable { avoidFoods = avoidFoods - item }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item, fontSize = 12.sp, color = QingJiGreenDark)
                                    Spacer(Modifier.height(0.dp))
                                    Text(
                                        text = " ×",
                                        fontSize = 12.sp,
                                        color = QingJiGreenDark
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledTextField(
                            label = "添加一个",
                            value = newAvoid,
                            onValueChange = { newAvoid = it },
                            hint = "例如 香菜"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "+ 添加忌口食物",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(QingJiGreenLight)
                        .clickable {
                            val v = newAvoid.trim()
                            if (v.isNotBlank() && v !in avoidFoods) {
                                avoidFoods = avoidFoods + v
                                newAvoid = ""
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 最长烹饪时长 ----------
            SectionCard {
                Text(
                    text = "最长烹饪时长",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "超过这个时长的做法不推荐，适合工作日只想快手做饭",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                val options = listOf(0 to "不限", 15 to "15 分钟内", 30 to "30 分钟内", 45 to "45 分钟内", 60 to "60 分钟内")
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (minutes, label) ->
                            val selected = maxCook == minutes
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.PillCorner))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable { maxCook = minutes }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(Dimens.Gap))

            // ---------- 偏好菜系 ----------
            SectionCard {
                Text(
                    text = "偏好菜系",
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选中的菜系在推荐里会靠前",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                val options = listOf("家常菜", "川味", "粤味", "清淡", "北方菜", "汤羹")
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            val selected = item in cuisines
                            Text(
                                text = item,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Dimens.PillCorner))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable {
                                        cuisines = if (selected) cuisines - item else cuisines + item
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}
