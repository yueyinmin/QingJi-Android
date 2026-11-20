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
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.LabeledTextField
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.AiConfigViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * AI 服务配置（多服务管理 · AI 多服务工单）
 * ------------------------------------------------------------
 * 1. 顶部：当前使用服务（名称/服务商/模型/Key 状态/启用态）。
 * 2. 中部：已保存服务列表（切换/编辑/测试/删除 + 最近测试 + 当前标识）。
 * 3. 编辑表单：新增走新 ID、编辑保留 ID；Key 留空不改、绝不回显。
 * 4. 切换只改激活 ID；删除当前服务自动改选；测试不改激活服务、不扣额度。
 * Key 永远不进数据库/备份/日志。
 * ============================================================
 */

private data class Preset(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String = "",
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
    val configs by vm.configs.collectAsState()
    val active by vm.activeConfig.collectAsState()
    val activeId by vm.activeId.collectAsState()
    val keyStatus by vm.keyStatus.collectAsState()
    val testResults by vm.testResults.collectAsState()
    val vmMessage by vm.message.collectAsState()

    var editing by remember { mutableStateOf<AiConfig?>(null) } // null=不显示表单
    var isNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AiConfig?>(null) }

    // 表单状态
    var displayName by remember { mutableStateOf("") }
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
    var hasKeyInForm by remember { mutableStateOf(false) }

    fun openEditor(config: AiConfig?, new: Boolean) {
        editing = config
        isNew = new
        displayName = config?.displayName.orEmpty()
        provider = config?.provider ?: "custom"
        baseUrl = config?.baseUrl.orEmpty()
        textModel = config?.textModel.orEmpty()
        visionModel = config?.visionModel.orEmpty()
        timeout = (config?.timeoutSec ?: 30).toString()
        dailyLimit = (config?.dailyLimit ?: 100).toString()
        enabled = config?.enabled ?: true
        keyInput = ""
        presetHint = ""
        hasKeyInForm = config?.let { keyStatus[it.id] == true } ?: false
        message = ""
    }

    fun buildConfig(): AiConfig = AiConfig(
        id = editing?.id ?: 0,
        displayName = displayName.trim(),
        provider = provider,
        baseUrl = baseUrl.trim(),
        textModel = textModel.trim(),
        visionModel = visionModel.trim(),
        temperature = editing?.temperature ?: 0.2,
        timeoutSec = timeout.toIntOrNull()?.coerceIn(5, 120) ?: 30,
        dailyLimit = dailyLimit.toIntOrNull()?.coerceIn(1, 10000) ?: 100,
        enabled = enabled,
        updatedAt = System.currentTimeMillis()
    )

    LaunchedEffect(vmMessage) {
        if (vmMessage != null) { message = vmMessage!!; isError = false; vm.consumeMessage() }
    }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "AI 服务配置", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            HintBox(
                text = "可保存多个服务（如公益站 A/B）。切换只改当前使用的服务，" +
                    "每个服务各自记住地址、模型和加密 Key；Key 不进数据库和备份。"
            )
            Spacer(Modifier.height(12.dp))

            // ---------- 当前使用服务 ----------
            if (active != null) {
                SectionCard {
                    Text("当前使用", fontSize = 12.sp, color = QingJiGreen, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(active!!.displayName.ifBlank { providerName(active!!.provider) },
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("服务商 ${providerName(active!!.provider)} · 文本 ${active!!.textModel}",
                        fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "视觉模型${if (active!!.visionModel.isNotBlank()) "已配置" else "未配置"} · " +
                            "Key ${if (keyStatus[active!!.id] == true) "已保存" else "未保存"} · " +
                            "${if (active!!.enabled) "已启用" else "未启用"}",
                        fontSize = 12.sp, color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---------- 已保存服务列表 ----------
            SectionCard {
                Text("已保存服务（${configs.size}）", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                if (configs.isEmpty()) {
                    Text("还没有保存任何服务，点下面的「添加 AI 服务」开始。",
                        fontSize = 12.sp, color = TextSecondary)
                } else {
                    configs.forEach { c ->
                        val isCurrent = c.id == activeId
                        val test = testResults[c.id]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCurrent) QingJiGreen.copy(alpha = 0.10f) else PageBackground)
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        c.displayName.ifBlank { providerName(c.provider) },
                                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary, modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Text("当前使用", fontSize = 11.sp, color = QingJiGreen,
                                            modifier = Modifier.clip(RoundedCornerShape(999.dp))
                                                .background(QingJiGreen.copy(alpha = 0.14f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp))
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text("${providerName(c.provider)} · ${c.textModel}" +
                                    " · Key ${if (keyStatus[c.id] == true) "已保存" else "未保存"}" +
                                    " · ${if (c.enabled) "启用" else "停用"}",
                                    fontSize = 11.sp, color = TextSecondary)
                                if (test != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("最近测试：$test", fontSize = 11.sp, color = TextSecondary)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (!isCurrent) {
                                        Text("切换", fontSize = 12.sp, color = QingJiGreen,
                                            modifier = Modifier.clickable { vm.switchTo(c) })
                                    }
                                    Text("编辑", fontSize = 12.sp, color = QingJiGreenDark,
                                        modifier = Modifier.clickable { openEditor(c, false) })
                                    Text("删除", fontSize = 12.sp, color = TextSecondary,
                                        modifier = Modifier.clickable { pendingDelete = c })
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 添加按钮 ----------
            PrimaryButton(text = "＋ 添加 AI 服务", onClick = { openEditor(null, true) })
            Spacer(Modifier.height(12.dp))

            // ---------- 编辑表单 ----------
            if (editing != null) {
                SectionCard {
                    Text(if (isNew) "新增服务" else "编辑服务", fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(
                        label = "服务名称（如：公益站 A / DeepSeek 官方）",
                        value = displayName, onValueChange = { displayName = it },
                        hint = "给自己看的备注名"
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("选个服务商（自动填地址和模型名，可再手改）", fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    presets.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                val selected = provider == p.key()
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) QingJiGreen else PageBackground)
                                        .clickable {
                                            provider = p.key(); baseUrl = p.baseUrl
                                            if (p.model.isNotBlank()) textModel = p.model
                                            visionModel = p.visionModel; presetHint = p.hint
                                        }.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(p.name, fontSize = 12.sp,
                                        color = if (selected) Color.White else TextSecondary)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (presetHint.isNotBlank()) { Spacer(Modifier.height(6.dp)); HintBox(text = presetHint) }
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(label = "Base URL（接口地址）", value = baseUrl,
                        onValueChange = { baseUrl = it }, hint = "https://api.deepseek.com/v1")
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(label = "文本模型名", value = textModel,
                        onValueChange = { textModel = it }, hint = "deepseek-chat")
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(label = "视觉模型名（拍照识别用，可留空）", value = visionModel,
                        onValueChange = { visionModel = it }, hint = "例如 glm-4v-flash")
                    Spacer(Modifier.height(10.dp))
                    LabeledTextField(
                        label = if (hasKeyInForm) "API Key（已保存，留空表示不改）" else "API Key",
                        value = keyInput, onValueChange = { keyInput = it },
                        hint = if (hasKeyInForm) "••••••••••••••••" else "sk-...",
                        isPassword = true
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Key 加密后单独存放，不进数据库和备份，也不会明文回显。",
                        fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用此服务", fontSize = 14.sp, color = TextPrimary)
                            Text("关掉后不会用它识别", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            LabeledTextField(label = "超时（秒）", value = timeout,
                                onValueChange = { timeout = it }, numberOnly = true)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            LabeledTextField(label = "单日上限", value = dailyLimit,
                                onValueChange = { dailyLimit = it }, numberOnly = true)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(CardWhite).clickable(enabled = !isTesting) {
                                    scope.launch {
                                        isTesting = true; message = ""
                                        val result = vm.test(buildConfig(), keyInput)
                                        isTesting = false
                                        result.fold(
                                            onSuccess = { message = "✅ $it"; isError = false },
                                            onFailure = { message = "测试失败：${it.message}"; isError = true }
                                        )
                                    }
                                }, contentAlignment = Alignment.Center
                        ) {
                            Text(if (isTesting) "测试中…" else "测试连接", fontSize = 13.sp, color = QingJiGreenDark)
                        }
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(QingJiGreen).clickable {
                                    if (baseUrl.isBlank() || textModel.isBlank()) {
                                        message = "Base URL 和模型名不能为空"; isError = true
                                    } else if (!hasKeyInForm && keyInput.isBlank()) {
                                        message = "还没填 API Key"; isError = true
                                    } else {
                                        scope.launch {
                                            vm.save(buildConfig(), keyInput)
                                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                            editing = null
                                        }
                                    }
                                }, contentAlignment = Alignment.Center
                        ) {
                            Text("保存", fontSize = 13.sp, color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("取消", fontSize = 12.sp, color = TextSecondary,
                        modifier = Modifier.clickable { editing = null })
                }
                Spacer(Modifier.height(12.dp))
            }

            if (message.isNotBlank()) {
                HintBox(text = message, isError = isError)
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingDelete?.let { target ->
        AppDialog(
            title = "删除服务",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch { vm.delete(target.id) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        ) {
            Text(
                "确定删除「${target.displayName.ifBlank { providerName(target.provider) }}」？" +
                    "只删这个服务，其它配置和 Key 不受影响。",
                fontSize = 13.sp, color = TextSecondary
            )
        }
    }
}

/** 服务商英文代号 → 中文显示名。 */
private fun providerName(provider: String): String = when (provider) {
    "deepseek" -> "DeepSeek"
    "qwen" -> "通义千问"
    "kimi" -> "Kimi"
    "glm" -> "智谱"
    "siliconflow" -> "硅基流动"
    "doubao" -> "豆包"
    "openai" -> "OpenAI"
    else -> "自定义"
}

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
