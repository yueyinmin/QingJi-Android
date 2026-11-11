package com.qingji.app.ui.screen.record

import com.qingji.app.domain.AppClock
import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
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
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.TextAiViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * 语音记录（M3.3）
 * ------------------------------------------------------------
 * 用安卓系统自带的 SpeechRecognizer：把话转成文字这一步**完全免费、
 * 不花 API 额度、也不需要联网权限之外的东西**。转出来的文字再走
 * 文字 AI 的拆条流程（M3.1），或直接手动搜索（断网降级）。
 *
 * 系统语音识别在部分国产 ROM 上可能被裁掉，页面会给出中文提示，
 * 并保留「直接手打」的输入框，不会让人卡住。
 * ============================================================
 */
@Composable
fun VoiceRecordScreen(
    onBack: () -> Unit,
    onFallback: () -> Unit,
    onOpenAiConfig: () -> Unit,
    vm: TextAiViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(MealRules.guess()) }
    val items = remember { mutableStateListOf<EditableItem>() }
    // v3.5.0：记到哪一天可选（默认今天），复用日期/时/分三列滚轮
    var recordDate by remember { mutableStateOf(AppClock.today()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 有些手机（尤其没装谷歌/厂商语音服务的）根本起不来 SpeechRecognizer。
    // 先问一句能不能用，不能用就走系统识别界面兜底。
    val canRecognize = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    // 设备本身就不支持语音：进页面就明说，别让人点了按钮才看到红字
    val noVoiceOnDevice = !canRecognize
    val recognizer = remember {
        if (canRecognize) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    // 兜底：调系统的「语音输入」界面，它通常会弹一个对话框让你说话
    val systemVoiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            recognizedText = text
        } else {
            errorText = "没听清，可以直接在下面手打"
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(Unit) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                errorText = ""
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                errorText = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "录音出问题了，检查麦克风"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一遍试试"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音，点一下再讲"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "系统语音服务连不上，可以直接在下面手打"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音服务忙，稍等一下再点"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没给麦克风权限，去系统设置里打开"
                    else -> "这台手机的语音识别不可用，可以直接在下面手打"
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                recognizedText = list?.firstOrNull().orEmpty().ifBlank { recognizedText }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) recognizedText = list[0]
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        errorText = ""

        // 没有可用的语音服务 → 交给系统自带的识别界面
        if (recognizer == null) {
            val fallback = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说说你吃了什么")
            }
            runCatching { systemVoiceLauncher.launch(fallback) }
                .onFailure { errorText = "这台手机连系统的语音输入界面都没有，直接在下面打字就行，效果一样" }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening() else errorText = "需要麦克风权限才能语音输入，也可以直接在下面手打"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "语音记录", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            HintBox(
                text = if (noVoiceOnDevice) {
                    "这台手机不支持语音转文字（系统没装语音服务）。直接在下面打字，一样能记、一样能用 AI 拆条。"
                } else {
                    "说话转文字由手机系统完成，免费、不花 AI 额度；拆条才用 AI。"
                }
            )
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text(text = "说一句吃了什么", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))

                // 一个大大的录音按钮，点一下就开始听
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isListening) QingJiGreenLight else PageBackground)
                        .clickable {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isListening) "● 正在听…" else "🎤 点一下开始说",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isListening) QingJiGreenDark else TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isListening) "说完停顿一下会自动结束" else "例如：中午吃了米饭、番茄炒蛋和一碗汤",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "识别出来的文字（可以手改）",
                    value = recognizedText,
                    onValueChange = { recognizedText = it },
                    hint = "也可以直接在这里打字",
                    singleLine = false,
                    minLines = 2
                )
                Spacer(Modifier.height(12.dp))
                Text(text = "记到哪一餐", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                MealSelector(selected = mealType, onSelect = { mealType = it })
                Spacer(Modifier.height(12.dp))
                RecordDateRow(date = recordDate, onClick = { showDatePicker = true })
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLoading) "拆条中…" else "用 AI 拆成食物条目",
                    enabled = !isLoading && recognizedText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorText = ""
                            vm.recognize(recognizedText.trim(), AppClock.today()).fold(
                                onSuccess = { data ->
                                    items.clear()
                                    items.addAll(data.toEditableItems())
                                    mealType = data.mealType
                                },
                                onFailure = { e -> errorText = e.message ?: "识别失败" }
                            )
                            isLoading = false
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            if (errorText.isNotBlank()) {
                // 「这台手机不支持语音」不是错误，是设备能力问题——用中性提示别吓人
                HintBox(
                    text = errorText,
                    isError = !(noVoiceOnDevice || errorText.startsWith("这台手机连系统的语音输入界面"))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "去设置 AI 服务 →",
                    fontSize = 13.sp,
                    color = QingJiGreen,
                    modifier = Modifier.clickable { onOpenAiConfig() }.padding(4.dp)
                )
            }

            // 断网 / 没配 AI 时的出口：拿着文字去手动搜索
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable { onFallback() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "不用 AI，去手动搜索记这一笔",
                    fontSize = 13.sp,
                    color = QingJiGreenDark
                )
            }

            if (items.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                EditableFoodList(
                    items = items,
                    onUpdate = { index, value -> items[index] = value },
                    onDelete = { index -> items.removeAt(index) },
                    onScale = { index, multiplier -> items[index] = items[index].withMultiplier(multiplier) }
                )
                Spacer(Modifier.height(6.dp))
                ConfirmSaveBar(
                    items = items,
                    onSave = {
                        scope.launch {
                            val logs = items.toFoodLogs(mealType, "voice", date = recordDate)
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
