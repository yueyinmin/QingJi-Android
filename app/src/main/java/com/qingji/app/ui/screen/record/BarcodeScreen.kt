package com.qingji.app.ui.screen.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.qingji.app.data.remote.ImageRecorder
import com.qingji.app.domain.BarcodeRules
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.MealSelector
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.BarcodeViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * ============================================================
 * 扫码记录（M3.4）
 * ------------------------------------------------------------
 * 顺序严格按需求：本地 food 表 → OpenFoodFacts 免费接口 → 都没有就手动录入。
 * 断网时本地库和手动录入照常可用，只是联网查询那一步会失败。
 *
 * 没引 CameraX 预览，也没引 ML Kit：用系统相机拍一张，交给 ZXing 纯 Java
 * 对**照片**做静态解码。实测 ML Kit 会把 APK 从 18MB 撑到 40MB（超 PRD 的
 * 25MB 红线），ZXing core 只有约 0.5MB，EAN-13 一样能扫。
 * ============================================================
 */
@Composable
fun BarcodeScreen(
    onBack: () -> Unit,
    vm: BarcodeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var codeInput by remember { mutableStateOf("") }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var infoText by remember { mutableStateOf("") }
    var food by remember { mutableStateOf<Food?>(null) }
    var gramsText by remember { mutableStateOf("100") }
    var mealType by remember { mutableStateOf(MealRules.guess()) }
    // v3.5.0：记到哪一天可选（默认今天），复用日期/时/分三列滚轮
    var recordDate by remember { mutableStateOf(AppClock.today()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 手动录入兜底表单
    var showManual by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualKcal by remember { mutableStateOf("") }

    fun resolve(code: String) {
        scope.launch {
            isLoading = true
            errorText = ""
            infoText = ""
            food = null
            vm.resolve(code).fold(
                onSuccess = {
                    food = it
                    gramsText = it.defaultGrams.toInt().toString()
                    infoText = if (it.source == "openfoodfacts") {
                        "从开放食品库查到，已缓存到本机，下次断网也能用"
                    } else {
                        "命中本机食物库，没有联网"
                    }
                },
                onFailure = {
                    errorText = it.message ?: "没查到这个条码"
                    showManual = true
                }
            )
            isLoading = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) return@rememberLauncherForActivityResult
        cameraFile?.let { file ->
            val uri = ImageRecorder.uriFor(context, file)
            scope.launch {
                isLoading = true
                // ZXing 在后台线程解照片，解不出来就让人手输
                val raw = ImageRecorder.decodeBarcode(context, uri)
                isLoading = false
                if (raw.isNullOrBlank()) {
                    errorText = "照片里没认出条码，对准一点再拍，或者直接手动输入数字"
                } else {
                    codeInput = BarcodeRules.normalize(raw)
                    resolve(codeInput)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "扫码记录", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            HintBox(text = "先查本机食物库，查不到才联网；断网时本地记录和手动录入照样能用。")
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "商品条码", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                LabeledTextField(
                    label = "条码数字（EAN-13 / EAN-8）",
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    hint = "例如 6901234567892",
                    numberOnly = true
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreen)
                            .clickable {
                                val file = ImageRecorder.createTempFile(context)
                                cameraFile = file
                                takePicture.launch(ImageRecorder.uriFor(context, file))
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("拍照扫", color = Color.White, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .clickable(enabled = !isLoading) { resolve(codeInput) }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLoading) "查询中…" else "查询",
                            color = QingJiGreenDark,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (infoText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = infoText)
            }
            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
            }

            // ---------- 查到了：填份量保存 ----------
            food?.let { item ->
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text(text = item.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "每 100g：${item.kcalPer100g.toInt()} 千卡 · 蛋白 ${item.proteinPer100g.toInt()}g" +
                            " · 碳水 ${item.carbPer100g.toInt()}g · 脂肪 ${item.fatPer100g.toInt()}g",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledTextField(
                        label = "吃了多少克",
                        value = gramsText,
                        onValueChange = { gramsText = it },
                        decimalOnly = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    MealSelector(selected = mealType, onSelect = { mealType = it })
                    Spacer(Modifier.height(12.dp))
                    RecordDateRow(date = recordDate, onClick = { showDatePicker = true })
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "保存这一笔",
                        onClick = {
                            val grams = gramsText.toDoubleOrNull()
                            if (grams == null || grams <= 0) {
                                errorText = "请填一个大于 0 的克数"
                                return@PrimaryButton
                            }
                            scope.launch {
                                vm.add(item, grams, mealType, recordDate)
                                onBack()
                            }
                        }
                    )
                }
            }

            // ---------- 没查到：手动录入并缓存 ----------
            if (showManual) {
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text(text = "手动录入并保存到食物库", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(
                        label = "食物名称",
                        value = manualName,
                        onValueChange = { manualName = it },
                        hint = "看包装上的名字填"
                    )
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(
                        label = "每 100 克热量（千卡）",
                        value = manualKcal,
                        onValueChange = { manualKcal = it },
                        hint = "看包装上的营养成分表",
                        decimalOnly = true
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "保存并继续记一笔",
                        onClick = {
                            val kcal = manualKcal.toDoubleOrNull()
                            when {
                                manualName.isBlank() -> errorText = "填一下食物名称"
                                kcal == null || kcal <= 0 -> errorText = "热量要填大于 0 的数字"
                                else -> scope.launch {
                                    val created = vm.saveManual(manualName, kcal, codeInput)
                                    food = created
                                    showManual = false
                                    infoText = "已存进本机食物库，下次扫这个条码直接命中"
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // v3.5.0：日期/时/分三列滚轮（复用运动模块组件，最多回看最近 7 天）
    if (showDatePicker) {
        com.qingji.app.ui.components.ExerciseDateTimeWheelSheet(
            initial = recordDate.atTime(12, 0),
            onConfirm = { recordDate = it.toLocalDate() },
            onDismiss = { showDatePicker = false }
        )
    }
}
