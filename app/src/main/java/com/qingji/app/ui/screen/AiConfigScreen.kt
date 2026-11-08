package com.qingji.app.ui.screen

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
import androidx.compose.material3.Switch
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
import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
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
import com.qingji.app.ui.viewmodel.AiConfigViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * AI 服务配置（需求文档 5.1 / 1.4）
 * ------------------------------------------------------------
 * 要点：
 * 1. 用的是自己的 Key、自己的服务商，App 不内置任何账号。
 * 2. Key 用手机硬件里的钥匙加密后存，不进数据库、不进备份、不进日志。
 * 3. 输入框不回显已存的 Key：留空 = 继续用旧 Key，填新值才覆盖。
 * 4. 不配 Key 时 App 完全不联网，手动搜索等功能照常用。
 *
 * 下面的服务商地址只是常见默认值，服务商随时可能调整，
 * 一切以各家官方文档为准，这里全部可以手改。
 * ============================================================
 */

private data class Preset(
    val name: String,
    val baseUrl: String,
    val model: String,
    /** 视觉模型（拍照识别用）。留空表示这家没有视觉模型，只能用文字识别。 */
    val visionModel: String = "",
    /** 选中后显示在下面的提示 */
    val hint: String = ""
)

private val presets = listOf(
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", hint = "DeepSeek 目前没有视觉模型，拍照识别用不了"),
    Preset("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "qwen-vl-plus"),
    Preset("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "moonshot-v1-8k-vision-preview"),
    Preset(
        "智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "glm-4v-flash",
        hint = "glm-4-flash 免费但份量估算比较粗；想要更准就填 glm-4-plus 和 glm-4v-plus（收费）"
    ),
    Preset("硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2-VL-7B-Instruct"),
    Preset("豆包", "https://ark.cn-beijing.volces.com/api/v3", ""),
    Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o-mini"),
    Preset("自定义", "", "")
)

@Composable
fun AiConfigScreen(
    onBack: () -> Unit,
    vm: AiConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedConfig by vm.config.collectAsState()
    val hasKey by vm.hasKey.collectAsState()

    var provider by remember { mutableStateOf("custom") }
    var baseUrl by remember { mutableStateOf("") }
    var textModel by remember { mutableStateOf("") }
    var visionModel by remember { mutableStateOf("") }
    var presetHint by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("30") }
    var dailyLimit by remember { mutableStateOf("100") }
    var enabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    // 只在第一次拿到已存配置时把表单填满，之后用户改了什么都不会被覆盖
    LaunchedEffect(savedConfig) {
        if (!loaded && savedConfig != null) {
            val c = savedConfig!!
            provider = c.provider
            baseUrl = c.baseUrl
            textModel = c.textModel
            visionModel = c.visionModel
            timeout = c.timeoutSec.toString()
            dailyLimit = c.dailyLimit.toString()
            enabled = c.enabled
            loaded = true
        }
    }

    fun buildConfig() = AiConfig(
        id = 1,
        provider = provider,
        baseUrl = baseUrl.trim(),
        textModel = textModel.trim(),
        visionModel = visionModel.trim(),
        temperature = 0.2,
        timeoutSec = timeout.toIntOrNull()?.coerceIn(5, 120) ?: 30,
        dailyLimit = dailyLimit.toIntOrNull()?.coerceIn(1, 10000) ?: 100,
        enabled = enabled,
        updatedAt = System.currentTimeMillis()
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "AI 服务配置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            HintBox(
                text = "不配也能用：手动搜索、记体重、看进展都不需要 AI。" +
                    "只有「文字 AI 记录」会联网，而且连的是你自己填的服务商。"
            )
            Spacer(Modifier.height(12.dp))

            // ---------- 服务商预设 ----------
            SectionCard {
                Text(
                    text = "选个服务商（自动填地址和模型名，可再手改）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                presets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { preset ->
                            val selected = provider == preset.key()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) QingJiGreen else PageBackground)
                                    .clickable {
                                        provider = preset.key()
                                        baseUrl = preset.baseUrl
                                        if (preset.model.isNotBlank()) textModel = preset.model
                                        visionModel = preset.visionModel
                                        presetHint = preset.hint
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else TextSecondary
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 必填项 ----------
            SectionCard {
                LabeledTextField(
                    label = "Base URL（接口地址）",
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    hint = "https://api.deepseek.com/v1"
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "文本模型名",
                    value = textModel,
                    onValueChange = { textModel = it },
                    hint = "deepseek-chat"
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = "视觉模型名（拍照识别用，只记文字可以留空）",
                    value = visionModel,
                    onValueChange = { visionModel = it },
                    hint = "例如 glm-4v-flash"
                )
                if (presetHint.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    HintBox(text = presetHint)
                }
                Spacer(Modifier.height(12.dp))
                LabeledTextField(
                    label = if (hasKey) "API Key（已保存，留空表示不改）" else "API Key",
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    hint = if (hasKey) "••••••••••••••••" else "sk-...",
                    isPassword = true
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Key 用系统硬件加密后单独存放，不会写进数据库，也不会被备份导出。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 高级与安全 ----------
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "启用 AI 识别", fontSize = 14.sp, color = TextPrimary)
                        Text(
                            text = "关掉后所有联网识别都会停",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledTextField(
                            label = "超时（秒）",
                            value = timeout,
                            onValueChange = { timeout = it },
                            numberOnly = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledTextField(
                            label = "单日调用上限",
                            value = dailyLimit,
                            onValueChange = { dailyLimit = it },
                            numberOnly = true
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "到上限后自动停止调用，手动搜索和拍照以外的方式仍可用，防止费用失控。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            if (message.isNotBlank()) {
                HintBox(text = message, isError = isError)
                Spacer(Modifier.height(12.dp))
            }

            // ---------- 测试 / 保存 ----------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .clickable(enabled = !isTesting) {
                        scope.launch {
                            isTesting = true
                            message = ""
                            val result = vm.test(buildConfig(), keyInput)
                            isTesting = false
                            result.fold(
                                onSuccess = { message = "✅ $it"; isError = false },
                                onFailure = {
                                    message = "测试失败：${it.message}"
                                    isError = true
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTesting) "正在测试…" else "测试连接（会消耗极少量额度）",
                    fontSize = 14.sp,
                    color = QingJiGreenDark
                )
            }

            Spacer(Modifier.height(10.dp))

            PrimaryButton(
                text = "保存配置",
                onClick = {
                    if (baseUrl.isBlank() || textModel.isBlank()) {
                        message = "Base URL 和模型名不能为空"
                        isError = true
                        return@PrimaryButton
                    }
                    if (!hasKey && keyInput.isBlank()) {
                        message = "还没填 API Key"
                        isError = true
                        return@PrimaryButton
                    }
                    scope.launch {
                        vm.save(buildConfig(), keyInput)
                        Toast.makeText(context, "AI 配置已保存", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 服务商在数据库里存的英文代号，界面上显示的是中文名。 */
private fun Preset.key(): String = when (name) {
    "DeepSeek" -> "deepseek"
    "通义千问" -> "qwen"
    "Kimi" -> "kimi"
    "智谱" -> "glm"
    "硅基流动" -> "siliconflow"
    "豆包" -> "doubao"
    "OpenAI" -> "openai"
    else -> "custom"
}
