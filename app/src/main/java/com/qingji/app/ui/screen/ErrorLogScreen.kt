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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.diagnostics.AppLogger
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import com.qingji.app.BuildConfig
import com.qingji.app.diagnostics.DiagnosticInfoProvider
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 错误日志页状态：内存快照 + 磁盘全量合并展示（新的在前）。
 */
class ErrorLogViewModel : ViewModel() {

    private val _entries = MutableStateFlow<List<AppLogger.Entry>>(emptyList())
    val entries: StateFlow<List<AppLogger.Entry>> = _entries

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val disk = AppLogger.readAllFromDisk()
            val memory = AppLogger.recent()
            // 磁盘是完整档案（新的在前）；内存里比磁盘新的部分补在前面
            val diskNewestTs = disk.firstOrNull()?.timestamp ?: Long.MAX_VALUE
            val newerInMemory = memory.filter { it.timestamp > diskNewestTs }
            _entries.value = newerInMemory + disk
        }
    }

    fun clear() {
        AppLogger.clearAll()
        _entries.value = emptyList()
    }
}

/**
 * ============================================================
 * 错误日志页（任务书 4.4）
 * ------------------------------------------------------------
 * 展示：错误总数 / 严重错误数 / 普通错误数 + 按时间倒序的列表。
 * 每条：模块、摘要、时间、等级；点开看详情（事件代码 / 异常类型 /
 * 时间 / 脱敏堆栈）。清空必须二次确认（AppDialog danger）。
 * ============================================================
 */
@Composable
fun ErrorLogScreen(
    onBack: () -> Unit,
    onOpenDiagnosticExport: () -> Unit,
    vm: ErrorLogViewModel = viewModel()
) {
    val entries by vm.entries.collectAsState()
    var detail by remember { mutableStateOf<AppLogger.Entry?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    /** 正在反馈的错误条目（P2 一键问题反馈）。 */
    var feedbackTarget by remember { mutableStateOf<AppLogger.Entry?>(null) }

    val fatalCount = entries.count { it.level == AppLogger.Level.FATAL || it.level == AppLogger.Level.ERROR }
    val normalCount = entries.count { it.level == AppLogger.Level.WARN || it.level == AppLogger.Level.DEBUG }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        // 与个人资料页同款顶栏；右上角动作位放「清空」（有日志时才显示）
        PageTopBar(
            title = "错误日志",
            onBack = onBack,
            actionText = if (entries.isNotEmpty()) "清空" else null,
            onAction = { showClearConfirm = true }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            // ---------- 统计三栏 ----------
            SectionCard {
                Text("错误概览", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryCol("${entries.size}", "日志总数", Modifier.weight(1f))
                    SummaryCol("$fatalCount", "严重（错误/崩溃）", Modifier.weight(1f))
                    SummaryCol("$normalCount", "普通（警告/调试）", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 列表（倒序） ----------
            if (entries.isEmpty()) {
                SectionCard {
                    Text("还没有任何日志记录。出了问题的时候，错误会自动留在这里。",
                        fontSize = TextSizes.Body, color = TextSecondary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(entries, key = { "${it.timestamp}_${it.eventCode}_${it.module}" }) { e ->
                        SectionCard(
                            contentPadding = Dimens.CardPaddingCompact,
                            modifier = Modifier.clickable { detail = e }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${e.module} · ${e.eventCode}",
                                        fontSize = TextSizes.CardTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(e.message, fontSize = TextSizes.Tiny,
                                        color = TextSecondary, maxLines = 2)
                                }
                                Spacer(Modifier.padding(4.dp))
                                val severe = e.level == AppLogger.Level.ERROR || e.level == AppLogger.Level.FATAL
                                Text(
                                    e.level.name,
                                    fontSize = TextSizes.Caption,
                                    color = if (severe) DangerRed else TextSecondary
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(timeFmt.format(Date(e.timestamp)), fontSize = TextSizes.Tiny, color = TextSecondary)
                        }
                    }
                    item { Spacer(Modifier.height(Dimens.FabSpace)) }
                }
            }
        }
    }

    // ---------- 详情弹层 ----------
    detail?.let { e ->
        AppBottomSheet(
            title = "${e.module} · ${e.eventCode}",
            subtitle = timeFmt.format(Date(e.timestamp)),
            skipPartiallyExpanded = true,
            onDismiss = { detail = null }
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("等级：${e.level.name}", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("事件代码：${e.eventCode}", fontSize = TextSizes.Body, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text("说明：${e.message}", fontSize = TextSizes.Body, color = TextPrimary, lineHeight = 20.sp)
                e.throwableType?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("异常类型：$it", fontSize = TextSizes.Body, color = TextPrimary)
                }
                e.stackTrace?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("堆栈（已脱敏）", fontSize = TextSizes.SectionTitle,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(it, fontSize = TextSizes.Tiny, color = TextSecondary, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                // 任务书第七节：一键问题反馈入口（用户确认后复制 + 打开 Issues 网页）
                OutlinedButton(
                    onClick = { feedbackTarget = e },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("反馈此问题", fontSize = TextSizes.Body, color = QingJiGreen) }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    // ---------- 清空二次确认（任务书 4.4） ----------
    if (showClearConfirm) {
        AppDialog(
            title = "清空全部日志？",
            confirmText = "全部删除",
            onConfirm = { vm.clear(); showClearConfirm = false },
            dismissText = "取消",
            danger = true,
            onDismiss = { showClearConfirm = false }
        ) {
            Text("日志清了就找不回来。如果要反馈问题给开发者，先去「导出诊断日志」。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }

    // ---------- P2 一键问题反馈弹窗（任务书第七节） ----------
    // 流程：自动整理版本 / 设备 / 事件代码 / 脱敏摘要，用户补一句描述；
    // 点「复制并打开 Issues」= 复制到剪贴板 + 用浏览器打开 Issues 页，
    // 由用户自己粘贴提交，App 绝不自动创建 Issue、绝不自动上传。
    feedbackTarget?.let { entry ->
        val context = androidx.compose.ui.platform.LocalContext.current
        var userNote by remember(entry) { mutableStateOf("") }
        val timeFmtLong = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

        fun buildFeedbackText(): String {
            val dev = DiagnosticInfoProvider.deviceInfo()
            val stack = entry.stackTrace?.take(600) ?: "（无堆栈）"
            return buildString {
                append("### 问题描述\n")
                append(userNote.ifBlank { "（在这里补一句：什么操作之后出的这个错）\n" })
                append("\n### 环境\n")
                append("- 应用版本：v${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）\n")
                append("- 设备：${dev.optString("manufacturer")} ${dev.optString("model")}\n")
                append("- 系统：Android ${dev.optString("android_version")}（API ${dev.optString("api_level")}）\n")
                append("- 模块：${entry.module}\n")
                append("- 事件代码：${entry.eventCode}\n")
                append("- 发生时间：${timeFmtLong.format(Date(entry.timestamp))}\n")
                append("\n### 异常摘要（已脱敏）\n")
                append("```\n")
                append(entry.throwableType?.let { "$it\n" } ?: "")
                append(stack)
                append("\n```\n")
            }
        }

        AppDialog(
            title = "反馈此问题",
            confirmText = "复制并打开 Issues",
            onConfirm = {
                val text = buildFeedbackText()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QingJi 反馈", text))
                Toast.makeText(context, "反馈内容已复制，粘贴提交即可", Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yueyinmin/QingJi-Android/issues"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                feedbackTarget = null
            },
            dismissText = "取消",
            onDismiss = { feedbackTarget = null }
        ) {
            Column {
                Text("下面会自动带上版本、设备、错误代码（全部已脱敏）。你可以补一句当时在做什么：",
                    fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                BasicTextField(
                    value = userNote,
                    onValueChange = { userNote = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = TextSizes.Body, color = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.TinyCorner))
                        .background(DividerColor.copy(alpha = 0.5f))
                        .padding(10.dp)
                        .height(72.dp),
                    decorationBox = { inner ->
                        if (userNote.isEmpty()) {
                            Text("例：点了「导出备份」之后弹出来的",
                                fontSize = TextSizes.Caption, color = TextSecondary)
                        }
                        inner()
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text("点确认后：反馈文本复制到剪贴板 → 打开 GitHub Issues 网页 → 你自己粘贴、检查、提交。不会自动发布任何内容。",
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun SummaryCol(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}
