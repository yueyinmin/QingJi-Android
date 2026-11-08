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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * 首次启动引导（v2.8）。
 *
 * 默认档案已经清除——App 里不该出现「不是用户自己填的数字」。
 * 这一页让用户一次填完：性别、身高、体重、目标体重、活动水平、饮水目标。
 * 每日预算按 Mifflin-St Jeor 公式现场算，不用用户自己查表。
 *
 * v3.4.0：补了一段选填的饮食偏好（忌口 / 过敏 / 偏好菜系）。
 * 这些值存 DataStore，和设置页「饮食偏好与忌口」是同一份数据，
 * 也会自动拼进以后每一次 AI 识别的规则里；不填不影响开始使用。
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onSavePreferences: (allergens: String, avoidFoods: String, cuisines: String) -> Unit =
        { _, _, _ -> }
) {
    var gender by remember { mutableStateOf("male") }
    var heightText by remember { mutableStateOf("170") }
    var weightText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("25") }
    var activity by remember { mutableStateOf(2) }
    var waterText by remember { mutableStateOf("2000") }
    var errorText by remember { mutableStateOf("") }

    // 饮食偏好：选填，点一下选中 / 再点取消
    var allergenSet by remember { mutableStateOf(setOf<String>()) }
    var avoidSet by remember { mutableStateOf(setOf<String>()) }
    var cuisineSet by remember { mutableStateOf(setOf<String>()) }
    var avoidText by remember { mutableStateOf("") }

    fun save() {
        val height = heightText.toDoubleOrNull()
        val weight = weightText.toDoubleOrNull()
        val target = targetText.toDoubleOrNull()
        val age = ageText.toIntOrNull()
        val water = waterText.toIntOrNull()
        if (height == null || weight == null || target == null || age == null || age !in 10..100) {
            errorText = "身高/体重/目标/年龄都要填有效数字"
            return
        }
        if (target >= weight) {
            errorText = "目标体重应该小于当前体重（这是减重 App 呀）"
            return
        }
        // Mifflin-St Jeor 简化：男性 10w+6.25h-5a+5，女性 -161
        val bmr = if (gender == "male") {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }
        val factor = when (activity) { 1 -> 1.2; 2 -> 1.375; 3 -> 1.55; else -> 1.725 }
        val budget = (bmr * factor - 300).toInt().coerceIn(1200, 5000)  // 减重：TDEE - 300

        onSave(
            UserProfile(
                id = 1,
                gender = gender,
                birthday = LocalDate.now().minusYears(age.toLong()),
                heightCm = height,
                startWeight = weight,
                targetWeight = target,
                activityLevel = activity,
                dailyKcalGoal = budget,
                proteinGoalG = (weight * 1.5).toInt(),
                carbGoalG = ((budget * 0.4) / 4).toInt(),
                fatGoalG = ((budget * 0.25) / 9).toInt(),
                waterGoalMl = water ?: 2000
            )
        )
        // 逗号分隔存进已有的设置键；「其他忌口」是自由文本，按中英文逗号都拆一下
        val extra = avoidText
            .split(',', '，', '、')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        onSavePreferences(
            allergenSet.joinToString(","),
            (avoidSet.toList() + extra).distinct().joinToString(","),
            cuisineSet.joinToString(",")
        )
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        Text(text = "欢迎使用轻记", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先花一分钟填一下自己的数据，App 里的所有目标都会按这些数字算——不会有任何「预设的假数据」。",
            fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp
        )
        Spacer(Modifier.height(24.dp))

        SectionCard {
            Text(text = "性别", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("male" to "男", "female" to "女").forEach { (key, label) ->
                    val selected = gender == key
                    Text(
                        text = label, fontSize = 13.sp,
                        color = if (selected) Color.White else TextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) QingJiGreen else PageBackground)
                            .clickable { gender = key }
                            .padding(vertical = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Field("年龄", ageText, { ageText = it }, "25")
            Spacer(Modifier.height(10.dp))
            Field("身高（cm）", heightText, { heightText = it }, "170")
            Spacer(Modifier.height(10.dp))
            Field("当前体重（kg）", weightText, { weightText = it }, "70.0")
            Spacer(Modifier.height(10.dp))
            Field("目标体重（kg）", targetText, { targetText = it }, "65.0")
            Spacer(Modifier.height(14.dp))
            Text(text = "日常活动水平", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "久坐", 2 to "轻度", 3 to "中度", 4 to "高强度").forEach { (lv, label) ->
                    val selected = activity == lv
                    Text(
                        text = label, fontSize = 12.sp,
                        color = if (selected) Color.White else TextSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) QingJiGreen else PageBackground)
                            .clickable { activity = lv }
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Field("每日饮水目标（ml）", waterText, { waterText = it }, "2000")
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 饮食偏好（选填） ----------
        SectionCard {
            Text(text = "饮食偏好（选填）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "以后 AI 识别会自动避开这些；不填也能直接开始，设置页里随时能改。",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(text = "过敏原", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            PreferenceChips(
                labels = listOf("花生", "海鲜", "牛奶", "鸡蛋"),
                selected = allergenSet
            ) { allergenSet = it }
            Spacer(Modifier.height(12.dp))
            Text(text = "忌口", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            PreferenceChips(
                labels = listOf("辛辣", "香菜", "油炸", "甜食"),
                selected = avoidSet
            ) { avoidSet = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = avoidText,
                onValueChange = { avoidText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("其他忌口，用逗号隔开", fontSize = 12.sp, color = TextSecondary) }
            )
            Spacer(Modifier.height(12.dp))
            Text(text = "偏好菜系", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            PreferenceChips(
                labels = listOf("家常菜", "川菜", "粤菜", "面食", "轻食"),
                selected = cuisineSet
            ) { cuisineSet = it }
        }

        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = errorText, fontSize = 13.sp, color = Color(0xFFD64545))
        }

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable { save() }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "开始使用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** 引导页里的多选标签：点一下选中、再点取消。 */
@Composable
private fun PreferenceChips(
    labels: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    labels.chunked(4).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            row.forEach { label ->
                val on = label in selected
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (on) Color.White else TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) QingJiGreen else PageBackground)
                        .clickable { onChange(if (on) selected - label else selected + label) }
                        .padding(vertical = 7.dp)
                )
            }
            repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    Column {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, fontSize = 13.sp, color = TextSecondary) }
        )
    }
}
