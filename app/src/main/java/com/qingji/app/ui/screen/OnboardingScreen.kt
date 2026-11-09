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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import java.time.LocalDate

/**
 * ============================================================
 * 首次引导（v1.3 重做成「一页一问」向导）
 * ------------------------------------------------------------
 * 之前是一整页塞十几个输入框，看着就累；现在一次只问一件事，
 * 下面给现成的选项，选不到的可以自己加标签（想加多少个都行）。
 *
 * 页序：欢迎 → 性别 → 身体数据 → 活动水平 → 饮水目标
 *      → 过敏原 → 忌口 → 偏好菜系 → 完成
 *
 * 每日预算仍按 Mifflin-St Jeor 现场算（TDEE − 300），不留默认值。
 * 偏好三项写入 DataStore（过敏原 / 忌口 / 菜系），和设置页共用一份数据。
 * ============================================================
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onSave: (UserProfile) -> Unit,
    onSavePreferences: (allergens: String, avoidFoods: String, cuisines: String) -> Unit =
        { _, _, _ -> }
) {
    // ---------- 所有答案 ----------
    var step by rememberSaveable { mutableIntStateOf(0) }
    var gender by rememberSaveable { mutableStateOf("male") }
    var heightText by rememberSaveable { mutableStateOf("170") }
    var weightText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("25") }
    var activity by rememberSaveable { mutableIntStateOf(2) }
    var waterText by rememberSaveable { mutableStateOf("2000") }
    var waterCustom by rememberSaveable { mutableStateOf("") }
    var allergenSet by rememberSaveable { mutableStateOf(setOf<String>()) }
    var allergenAdded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var avoidSet by rememberSaveable { mutableStateOf(setOf<String>()) }
    var avoidAdded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var cuisineSet by rememberSaveable { mutableStateOf(setOf<String>()) }
    var cuisineAdded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var errorText by remember { mutableStateOf("") }

    val totalSteps = 8

    // ---------- 保存：最后一步触发 ----------
    fun finish() {
        val height = heightText.toDoubleOrNull()
        val weight = weightText.toDoubleOrNull()
        val target = targetText.toDoubleOrNull()
        val age = ageText.toIntOrNull()
        val water = waterText.toIntOrNull()
        if (height == null || weight == null || target == null || age == null || age !in 10..100) {
            errorText = "身高、体重、目标、年龄都要是有效数字"
            step = 2
            return
        }
        if (target >= weight) {
            errorText = "目标体重要比现在轻一点，减重才有得减"
            step = 2
            return
        }
        // Mifflin-St Jeor 简化：男性 10w+6.25h-5a+5，女性 -161
        val bmr = if (gender == "male") {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }
        val factor = when (activity) { 1 -> 1.2; 2 -> 1.375; 3 -> 1.55; else -> 1.725 }
        val budget = (bmr * factor - 300).toInt().coerceIn(1200, 5000)

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
        onSavePreferences(
            allergenSet.joinToString(","),
            avoidSet.joinToString(","),
            cuisineSet.joinToString(",")
        )
        onDone()
    }

    // ---------- 每步的校验 ----------
    fun canGoNext(): Boolean = when (step) {
        2 -> heightText.toDoubleOrNull() != null &&
            weightText.toDoubleOrNull() != null &&
            targetText.toDoubleOrNull() != null &&
            ageText.toIntOrNull() != null
        4 -> (waterText.toIntOrNull() ?: 0) > 0
        else -> true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(40.dp))

        // ---------- 进度条 ----------
        LinearProgressIndicator(
            progress = { (step + 1f) / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = QingJiGreen,
            trackColor = QingJiGreenLight
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${step + 1} / $totalSteps",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(24.dp))

        // ---------- 一页一问 ----------
        when (step) {
            0 -> {
                Text(text = "欢迎使用轻记", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "先填几个数字，每天的热量和营养目标都会按你的情况算，不留任何默认值。",
                    fontSize = 14.sp, color = TextSecondary, lineHeight = 22.sp
                )
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreen)
                        .pressable { step = 1 }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "开始", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            1 -> QuestionPage(
                title = "你的性别是？",
                subtitle = "热量公式里会用到这一项"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("male" to "男生", "female" to "女生").forEach { (key, label) ->
                        val selected = gender == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) QingJiGreenLight else Color.White)
                                .clickable { gender = key }
                                .padding(vertical = 26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) QingJiGreenDark else TextPrimary
                            )
                        }
                    }
                }
            }

            2 -> QuestionPage(
                title = "身体数据",
                subtitle = "填真实数字就行，之后随时能在「我的」里改"
            ) {
                SectionCard {
                    Field("年龄", ageText, { ageText = it }, "25")
                    Spacer(Modifier.height(10.dp))
                    Field("身高（cm）", heightText, { heightText = it }, "170")
                    Spacer(Modifier.height(10.dp))
                    Field("当前体重（kg）", weightText, { weightText = it }, "70.0")
                    Spacer(Modifier.height(10.dp))
                    Field("目标体重（kg）", targetText, { targetText = it }, "65.0")
                }
                if (errorText.contains("体重") || errorText.contains("数字")) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = errorText, fontSize = 13.sp, color = Color(0xFFD64545))
                }
            }

            3 -> QuestionPage(
                title = "平时活动量大吗？",
                subtitle = "影响每天能吃多少的热量预算"
            ) {
                SectionCard {
                    listOf(
                        1 to "基本坐着", 2 to "偶尔动动",
                        3 to "经常运动", 4 to "天天高强度"
                    ).forEach { (lv, label) ->
                        val selected = activity == lv
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) QingJiGreenLight else PageBackground)
                                .clickable { activity = lv }
                                .padding(vertical = 14.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) QingJiGreenDark else TextPrimary
                            )
                        }
                    }
                }
            }

            4 -> QuestionPage(
                title = "每天想喝多少水？",
                subtitle = "喝水提醒和进度条都按这个算"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1500", "2000", "2500", "3000").forEach { v ->
                        val selected = waterText == v
                        Text(
                            text = "${v.toInt()}ml",
                            fontSize = 14.sp,
                            color = if (selected) Color.White else TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) QingJiGreen else QingJiGreenLight)
                                .clickable {
                                    waterText = v
                                    waterCustom = ""
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = waterCustom,
                    onValueChange = {
                        waterCustom = it
                        it.toIntOrNull()?.let { num -> if (num > 0) waterText = num.toString() }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("或者自己填一个数", fontSize = 13.sp, color = TextSecondary) }
                )
            }

            5 -> QuestionPage(
                title = "有哪些过敏原？",
                subtitle = "选出来的会提醒 AI 帮你避开，没有就跳过"
            ) {
                TagPicker(
                    preset = listOf("花生", "海鲜", "牛奶", "鸡蛋"),
                    selected = allergenSet,
                    added = allergenAdded,
                    onToggle = { v -> allergenSet = if (v in allergenSet) allergenSet - v else allergenSet + v },
                    onAdd = { v ->
                        allergenAdded = allergenAdded + v
                        allergenSet = allergenSet + v
                    },
                    hint = "还有别的过敏原？填在这里"
                )
            }

            6 -> QuestionPage(
                title = "有什么忌口？",
                subtitle = "不想吃的挑出来，AI 记录时会帮你注意"
            ) {
                TagPicker(
                    preset = listOf("辛辣", "香菜", "油炸", "甜食"),
                    selected = avoidSet,
                    added = avoidAdded,
                    onToggle = { v -> avoidSet = if (v in avoidSet) avoidSet - v else avoidSet + v },
                    onAdd = { v ->
                        avoidAdded = avoidAdded + v
                        avoidSet = avoidSet + v
                    },
                    hint = "其他忌口，一个一个加进来"
                )
            }

            7 -> QuestionPage(
                title = "平时爱吃哪类菜？",
                subtitle = "选几个常吃的，AI 识别会更懂你的口味"
            ) {
                TagPicker(
                    preset = listOf("家常菜", "川菜", "粤菜", "面食", "轻食"),
                    selected = cuisineSet,
                    added = cuisineAdded,
                    onToggle = { v -> cuisineSet = if (v in cuisineSet) cuisineSet - v else cuisineSet + v },
                    onAdd = { v ->
                        cuisineAdded = cuisineAdded + v
                        cuisineSet = cuisineSet + v
                    },
                    hint = "喜欢的菜系不在里面？自己加"
                )
            }
        }

        if (errorText.isNotBlank() && step != 2) {
            Spacer(Modifier.height(8.dp))
            Text(text = errorText, fontSize = 13.sp, color = Color(0xFFD64545))
        }

        Spacer(Modifier.height(24.dp))

        // ---------- 底部按钮 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step in 1..7) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                        .pressable {
                            errorText = ""
                            step -= 1
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "上一步", fontSize = 15.sp, color = QingJiGreenDark)
                }
                Spacer(Modifier.width(12.dp))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (canGoNext() || step == 0) QingJiGreen else QingJiGreen.copy(alpha = 0.45f))
                    .pressable(enabled = canGoNext()) {
                        errorText = ""
                        if (step == totalSteps - 1) finish() else step += 1
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (step == totalSteps - 1) "完成，开始用" else "下一步",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** 一页一问的骨架：问题标题 + 副说明 + 选项区。 */
@Composable
private fun QuestionPage(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    Text(
        text = subtitle,
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 20.sp
    )
    Spacer(Modifier.height(24.dp))
    content()
}

/**
 * 标签选择器：现成选项 + 自定义添加，想加多少个都行。
 * 自定义的标签加进来就是一个普通 chip，同样可以选中 / 取消。
 */
@Composable
private fun TagPicker(
    preset: List<String>,
    selected: Set<String>,
    added: List<String>,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
    hint: String
) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (preset + added).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tag ->
                    val on = tag in selected
                    Text(
                        text = tag,
                        fontSize = 14.sp,
                        color = if (on) Color.White else TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) QingJiGreen else Color.White)
                            .clickable { onToggle(tag) }
                            .padding(vertical = 12.dp)
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            placeholder = { Text(hint, fontSize = 13.sp, color = TextSecondary) }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "添加",
            fontSize = 14.sp,
            color = if (input.isBlank()) TextSecondary else QingJiGreenDark,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(QingJiGreenLight)
                .clickable(enabled = input.isNotBlank()) {
                    val v = input.trim()
                    if (v.isNotEmpty() && v !in preset && v !in added) {
                        onAdd(v)
                    }
                    input = ""
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
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
