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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qingji.app.data.remote.ImageRecorder
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.MealSelector
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.ImageRecordViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * ============================================================
 * 拍照识别（M3.2）
 * ------------------------------------------------------------
 * 流程：拍照 / 相册选图 → 压缩到长边 1024 → 视觉模型 → 可编辑列表 → 保存。
 * 三条硬规矩（需求 M3.2 / 1.4）：
 * 1. 页面常驻「AI 估算，仅供参考」提示条；
 * 2. 结果必须能一条条改份量；
 * 3. 识别失败自动给「改用手动搜索」的降级出口，绝不把人卡死。
 * ============================================================
 */
@Composable
fun ImageRecordScreen(
    onBack: () -> Unit,
    onFallback: () -> Unit,
    onOpenAiConfig: () -> Unit,
    vm: ImageRecordViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cameraFile by remember { mutableStateOf<File?>(null) }
    var previewUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageInfo by remember { mutableStateOf<ImageRecorder.ImageResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealRules.guess()) }
    val items = remember { mutableStateListOf<EditableItem>() }
    // v3.5.0：记到哪一天可选（默认今天），复用日期/时/分三列滚轮
    var recordDate by remember { mutableStateOf(AppClock.today()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 图片选好后统一走这里。
    // v2.3.2 修：以前压缩和预览各自读一遍 uri，相册给的 uri 有时只能读一次
    // 或者干脆读不了，就报「打不开这张图片」。
    // 现在只读 uri 一次，先抢成本地文件；压缩、预览、留档全部走文件。
    fun handleUri(uri: android.net.Uri) {
        previewUri = uri
        errorText = ""
        scope.launch {
            isLoading = true
            ImageRecorder.copyToStable(context, uri).fold(
                onSuccess = { source ->
                    ImageRecorder.compressFileToBase64(context, source).fold(
                        onSuccess = { imageInfo = it },
                        onFailure = { e -> errorText = e.message ?: "处理图片失败" }
                    )
                },
                onFailure = { e -> errorText = e.message ?: "打不开这张图片" }
            )
            isLoading = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraFile?.let { handleUri(ImageRecorder.uriFor(context, it)) }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) handleUri(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "拍照识别", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            HintBox(text = "AI 估算，仅供参考。识别完请逐条核对份量，不准就手改。")
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "选一张饭菜照片", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))

                // 预览优先用压缩后落盘的文件（File），最稳；
                // 压缩还没完成时退回原始 uri。以前直接喂 uri，
                // 相册给的 content:// 有时读不到，就会显示「打不开该图片」。
                val previewModel: Any? = imageInfo?.path?.let { java.io.File(it) } ?: previewUri
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "待识别的食物照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(QingJiGreenLight)
                    )
                    Spacer(Modifier.height(8.dp))
                    imageInfo?.let {
                        Text(
                            text = "已压缩到 ${it.width}×${it.height}，约 ${it.bytes / 1024} KB（省 token）",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PageBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "还没选照片", fontSize = 13.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                }

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
                        Text("拍照", color = Color.White, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .clickable { pickImage.launch("image/*") }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("从相册选", color = QingJiGreenDark, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                MealSelector(selected = mealType, onSelect = { mealType = it })
                Spacer(Modifier.height(12.dp))
                RecordDateRow(date = recordDate, onClick = { showDatePicker = true })
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLoading) "处理中…" else "开始识别",
                    enabled = !isLoading && imageInfo != null,
                    onClick = {
                        val info = imageInfo ?: return@PrimaryButton
                        scope.launch {
                            isLoading = true
                            errorText = ""
                            vm.recognize(info.base64).fold(
                                onSuccess = { data ->
                                    items.clear()
                                    items.addAll(data.toEditableItems())
                                    mealType = data.mealType
                                    advice = data.advice
                                },
                                onFailure = { e -> errorText = e.message ?: "识别失败" }
                            )
                            isLoading = false
                        }
                    }
                )
            }

            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "改用手动搜索 →",
                        fontSize = 13.sp,
                        color = QingJiGreen,
                        modifier = Modifier.clickable { onFallback() }.padding(4.dp)
                    )
                    Text(
                        text = "去设置 AI 服务 →",
                        fontSize = 13.sp,
                        color = QingJiGreen,
                        modifier = Modifier.clickable { onOpenAiConfig() }.padding(4.dp)
                    )
                }
            }

            if (advice.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = "AI 说明：$advice")
            }

            if (items.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))

                // 识别结果上方留一枚缩略图：滚下来看结果时，
                // 也能一眼确认「刚才估的是这张照片」，不会跟上一张搞混。
                val thumbModel: Any? = imageInfo?.path?.let { java.io.File(it) } ?: previewUri
                if (thumbModel != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PageBackground)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = "识别依据的照片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                text = "识别依据：这张照片",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "不满意可以点上面的「重新拍」换一张",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                EditableFoodList(
                    items = items,
                    onUpdate = { index, value -> items[index] = value },
                    onDelete = { index -> items.removeAt(index) },
                    onScale = { index, multiplier -> items[index] = items[index].withMultiplier(multiplier) }
                )
                Spacer(Modifier.height(6.dp))
                ConfirmSaveBar(
                    count = items.size,
                    onSave = {
                        scope.launch {
                            val logs = items.toFoodLogs(
                                mealType = mealType,
                                source = "image",
                                date = recordDate,
                                imagePath = imageInfo?.path
                            )
                            if (logs.isEmpty()) {
                                errorText = "每条都要有名字，克数要大于 0"
                            } else {
                                vm.save(logs, items.nameCorrections())
                                onBack()
                            }
                        }
                    }
                )
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
