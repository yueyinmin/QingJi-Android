package com.qingji.app.ui.screen.record

import com.qingji.app.domain.AppClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.MealSelector
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.TextAiViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ============================================================
 * 文字 AI 记录（M3.1）
 * ------------------------------------------------------------
 * 识别结果的编辑区抽到了 RecordConfirm.kt，和图片、语音共用一套。
 * 这里只负责：收输入 → 发请求 → 把结果交给公共列表 → 保存。
 * ============================================================
 */
@Composable
fun TextAiScreen(
    onBack: () -> Unit,
    onOpenAiConfig: () -> Unit,
    vm: TextAiViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealRules.guess()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf("") }
    var needClarify by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<EditableItem>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "文字 AI 记录", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            SectionCard {
                Text(
                    text = "说说你吃了什么",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    label = "",
                    value = input,
                    onValueChange = { input = it },
                    hint = "例如：早上一碗白粥、一个水煮蛋、一杯牛奶",
                    singleLine = false,
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                MealSelector(selected = mealType, onSelect = { mealType = it })
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLoading) "识别中…" else "开始识别",
                    enabled = !isLoading && input.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorText = ""
                            val result = vm.recognize(input.trim(), AppClock.today())
                            isLoading = false
                            result.fold(
                                onSuccess = { data ->
                                    items.clear()
                                    items.addAll(data.toEditableItems())
                                    mealType = data.mealType
                                    advice = data.advice
                                    needClarify = data.needClarify
                                },
                                onFailure = { e -> errorText = e.message ?: "识别失败，请重试" }
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (errorText.isNotBlank()) {
                HintBox(text = errorText, isError = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "去设置 AI 服务 →",
                    fontSize = 13.sp,
                    color = QingJiGreen,
                    modifier = Modifier
                        .clickable { onOpenAiConfig() }
                        .padding(4.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (advice.isNotBlank()) {
                HintBox(
                    text = (if (needClarify) "⚠ 信息不足：" else "AI 建议：") + advice,
                    isError = needClarify
                )
                Spacer(Modifier.height(12.dp))
            }

            if (items.isNotEmpty()) {
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
                            val logs = items.toFoodLogs(mealType, "text")
                            if (logs.isEmpty()) {
                                errorText = "每条都要有名称和大于 0 的克数"
                            } else {
                                vm.save(logs)
                                onBack()
                            }
                        }
                    }
                )
            } else {
                HintBox(text = "识别出来的每一条都能改名称、改克数、删掉，确认无误再保存。")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
