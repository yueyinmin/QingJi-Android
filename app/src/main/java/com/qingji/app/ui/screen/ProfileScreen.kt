package com.qingji.app.ui.screen

import com.qingji.app.domain.AppClock
import android.widget.Toast
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
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.domain.NutritionCalculator
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period

/**
 * ============================================================
 * 个人资料（M1）
 * ------------------------------------------------------------
 * 填完点保存，会自动按 Mifflin-St Jeor 公式算出：
 *   基础代谢 BMR → 每日总消耗 TDEE → 减重/维持/增重的热量预算
 *   → 蛋白 / 碳水 / 脂肪目标
 * 这些数字写回 user_profile，首页仪表盘立刻按新目标显示。
 *
 * 计算结果只是自用估算，不是医疗建议；界面上也写明了这一点。
 * ============================================================
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.profile.collectAsState()

    var gender by remember { mutableStateOf("male") }
    var birthday by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var startWeight by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(2) }
    var waterGoal by remember { mutableStateOf("2000") }
    var mode by remember { mutableStateOf(NutritionCalculator.MODE_LOSE) }
    var errorText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!loaded && saved != null) {
            val p = saved!!
            gender = p.gender
            birthday = p.birthday?.toString().orEmpty()
            height = p.heightCm.toString()
            startWeight = p.startWeight.toString()
            targetWeight = p.targetWeight.toString()
            activityLevel = p.activityLevel
            waterGoal = p.waterGoalMl.toString()
            loaded = true
        }
    }

    // 实时预览：不写库，纯粹算给使用者看，改一个数立刻变
    val age = parseBirthday(birthday)?.let { Period.between(it, AppClock.today()).years } ?: 25
    val preview = NutritionCalculator.compute(
        gender = gender,
        age = age,
        heightCm = height.toDoubleOrNull() ?: 0.0,
        weightKg = startWeight.toDoubleOrNull() ?: 0.0,
        activityLevel = activityLevel,
        mode = mode
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "个人资料", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SectionCard {
                Text(text = "基本信息", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))

                Text(text = "性别", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    options = listOf("男" to "male", "女" to "female"),
                    selected = gender,
                    onSelect = { gender = it }
                )

                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "生日（yyyy-MM-dd）",
                    value = birthday,
                    onValueChange = { birthday = it },
                    hint = "2000-01-01"
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "身高（cm）",
                    value = height,
                    onValueChange = { height = it },
                    hint = "175",
                    decimalOnly = true
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "目标", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "起始体重（kg）",
                    value = startWeight,
                    onValueChange = { startWeight = it },
                    decimalOnly = true
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "目标体重（kg）",
                    value = targetWeight,
                    onValueChange = { targetWeight = it },
                    decimalOnly = true
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "目标模式", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                ChoiceRow(
                    options = listOf(
                        "减重" to NutritionCalculator.MODE_LOSE,
                        "维持" to NutritionCalculator.MODE_KEEP,
                        "增重" to NutritionCalculator.MODE_GAIN
                    ),
                    selected = mode,
                    onSelect = { mode = it }
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "每日饮水目标（ml）",
                    value = waterGoal,
                    onValueChange = { waterGoal = it },
                    numberOnly = true
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "活动量", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                (1..4).forEach { level ->
                    val selected = level == activityLevel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) com.qingji.app.ui.theme.QingJiGreenLight else Color.Transparent)
                            .clickable { activityLevel = level }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = NutritionCalculator.activityLabel(level),
                            fontSize = 13.sp,
                            color = if (selected) com.qingji.app.ui.theme.QingJiGreenDark else TextSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "按公式自动算出的每日目标", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                PreviewRow("基础代谢 BMR", "${preview.bmr} 千卡")
                PreviewRow("每日总消耗 TDEE", "${preview.tdee} 千卡")
                PreviewRow("热量预算", "${preview.kcal} 千卡")
                PreviewRow("蛋白 / 碳水 / 脂肪", "${preview.proteinG} / ${preview.carbG} / ${preview.fatG} g")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "自用估算，不是医疗建议；身体不适请找医生。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
            }

            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = "保存并把目标应用到首页",
                onClick = {
                    val parsedBirthday = parseBirthday(birthday)
                    val h = height.toDoubleOrNull()
                    val sw = startWeight.toDoubleOrNull()
                    val tw = targetWeight.toDoubleOrNull()
                    when {
                        birthday.isNotBlank() && parsedBirthday == null ->
                            errorText = "生日格式不对，要写成 2000-01-01"
                        h == null || h !in 80.0..250.0 ->
                            errorText = "身高请填 80~250 之间的数字"
                        sw == null || sw !in 20.0..350.0 ->
                            errorText = "起始体重请填 20~350 之间的数字"
                        tw == null || tw !in 20.0..350.0 ->
                            errorText = "目标体重请填 20~350 之间的数字"
                        else -> {
                            errorText = ""
                            scope.launch {
                                vm.save(
                                    profile = UserProfile(
                                        id = 1,
                                        gender = gender,
                                        birthday = parsedBirthday,
                                        heightCm = h,
                                        startWeight = sw,
                                        targetWeight = tw,
                                        activityLevel = activityLevel,
                                        dailyKcalGoal = saved?.dailyKcalGoal ?: 0,
                                        proteinGoalG = saved?.proteinGoalG ?: 0,
                                        carbGoalG = saved?.carbGoalG ?: 0,
                                        fatGoalG = saved?.fatGoalG ?: 0,
                                        waterGoalMl = waterGoal.toIntOrNull()?.coerceIn(200, 8000) ?: 2000,
                                        unit = saved?.unit ?: "kg",
                                        // 不传会用「当前时间」当创建时间，导致「加入天数」每次保存都归 1
                                        createdAt = saved?.createdAt ?: System.currentTimeMillis()
                                    ),
                                    mode = mode
                                )
                                Toast.makeText(context, "已保存，首页目标已更新", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 把 "2000-01-01" 解析成日期；填错就返回 null，绝不抛异常崩页面。 */
private fun parseBirthday(text: String): LocalDate? = try {
    if (text.isBlank()) null else LocalDate.parse(text.trim())
} catch (e: Exception) {
    null
}

/** 一行两列/三列的小选择按钮。 */
@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) QingJiGreen else Color(0xFFF4F5F6))
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
