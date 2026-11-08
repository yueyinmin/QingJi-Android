package com.qingji.app.ui.screen

import com.qingji.app.domain.AppClock
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.qingji.app.data.local.entity.Diary
import com.qingji.app.data.remote.ImageRecorder
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.DiaryEditViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * ============================================================
 * 日记编辑（M5）
 * ------------------------------------------------------------
 * 需求：日期、1-5 星、文字、最多 9 张图、关联当日体重。
 * 照片会复制进 App 私有目录再存路径——系统相册给的临时地址
 * 重启后会失效，直接存 Uri 会让日记里的图变空白。
 * ============================================================
 */
@Composable
fun DiaryEditScreen(
    onBack: () -> Unit,
    diaryId: Long = 0L,
    vm: DiaryEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dateText by remember { mutableStateOf(AppClock.today().toString()) }
    var rating by remember { mutableStateOf(3) }
    var content by remember { mutableStateOf("") }
    var imagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var includeWeight by remember { mutableStateOf(false) }
    var weightValue by remember { mutableStateOf<Double?>(null) }
    var errorText by remember { mutableStateOf("") }

    LaunchedEffect(diaryId) {
        weightValue = vm.latestWeight()
        if (diaryId > 0) {
            vm.load(diaryId)?.let { old ->
                dateText = old.date.toString()
                rating = old.rating.coerceIn(0, 5)
                content = old.content
                imagePaths = old.imagePaths.split(",").filter { it.isNotBlank() }
                old.weightKg?.let { includeWeight = true; weightValue = it }
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val remain = 9 - imagePaths.size
        if (remain <= 0) {
            errorText = "最多 9 张图，先删掉几张再加"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val copied = uris.take(remain).mapNotNull { ImageRecorder.copyToPrivate(context, it) }
            imagePaths = imagePaths + copied
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = if (diaryId > 0) "编辑日记" else "写一篇日记", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SectionCard {
                LabeledTextField(
                    label = "日期（yyyy-MM-dd）",
                    value = dateText,
                    onValueChange = { dateText = it },
                    hint = "2026-11-07"
                )
                Spacer(Modifier.height(14.dp))
                Text(text = "今天给自己打几分", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { star ->
                        val selected = star <= rating
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) QingJiGreenLight else PageBackground)
                                .clickable { rating = star }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$star★",
                                fontSize = 14.sp,
                                color = if (selected) QingJiGreenDark else TextSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                LabeledTextField(
                    label = "写点什么",
                    value = content,
                    onValueChange = { content = it },
                    hint = "今天吃了什么、感觉怎么样…",
                    singleLine = false,
                    minLines = 5
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "照片（最多 9 张）", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            text = "照片只存在这个 App 里，不会进系统相册",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = "+ 添加",
                        fontSize = 13.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(QingJiGreenLight)
                            .clickable { pickImages.launch("image/*") }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (imagePaths.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    // 3 列一行，点一下删掉
                    imagePaths.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { path ->
                                Box(modifier = Modifier.weight(1f)) {
                                    AsyncImage(
                                        model = File(path),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(QingJiGreenLight)
                                    )
                                    Text(
                                        text = "×",
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x99000000))
                                            .clickable { imagePaths = imagePaths - path }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "关联当日体重", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            text = weightValue?.let { "最近记录 ${it}kg" } ?: "还没有体重记录",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = includeWeight,
                        onCheckedChange = { includeWeight = it },
                        enabled = weightValue != null
                    )
                }
            }

            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
            }

            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = "保存日记",
                onClick = {
                    val date = try {
                        LocalDate.parse(dateText.trim())
                    } catch (e: Exception) {
                        errorText = "日期格式不对，要写成 2026-11-07"
                        return@PrimaryButton
                    }
                    errorText = ""
                    scope.launch {
                        vm.save(
                            Diary(
                                id = diaryId,           // 0 = 新增；非 0 = 覆盖同一行
                                date = date,
                                rating = rating,
                                content = content.trim(),
                                imagePaths = imagePaths.joinToString(","),
                                weightKg = if (includeWeight) weightValue else null,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        onBack()
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
