package com.qingji.app.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qingji.app.diagnostics.AppLogger
import com.qingji.app.diagnostics.DiagnosticExporter
import com.qingji.app.ui.components.AppDialog
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.NormalGreen
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================
 * 导出诊断日志页（任务书 5.3）
 * ------------------------------------------------------------
 * 流程：先展示「导出什么 / 绝不包含什么」→ 用户点确认 → 后台生成
 * ZIP → 显示结果和文件大小 → 系统分享面板，用户自己选发给谁。
 * 绝不自动上传 GitHub，绝不静默发送（任务书红线）。
 * ============================================================
 */
@Composable
fun DiagnosticExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrivacyConfirm by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var resultFile by remember { mutableStateOf<java.io.File?>(null) }
    var failText by remember { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("← 返回", fontSize = TextSizes.Body, color = QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
                Text("导出诊断日志", fontSize = TextSizes.PageTitle,
                    fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 会包含什么 ----------
            SectionCard {
                Text("诊断包里有什么", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· diagnostic_summary.json：错误统计概要\n" +
                        "· app_info.json：轻记版本号、包名\n" +
                        "· device_info.json：手机型号、安卓版本等 6 项\n" +
                        "· error_logs.txt：错误日志全文（已脱敏）",
                    fontSize = TextSizes.Body, color = TextPrimary, lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            // ---------- 绝不包含什么（任务书 5.2 白名单承诺） ----------
            SectionCard {
                Text("绝不会包含（白名单硬保证）", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = NormalGreen)
                Spacer(Modifier.height(6.dp))
                Text(
                    "✗ AI 的 API Key、密码、Cookie\n" +
                        "✗ 数据库文件、用户备份\n" +
                        "✗ 饮食、体重、围度等任何记录\n" +
                        "✗ 照片和头像\n" +
                        "✗ AI 请求 / 响应的完整正文",
                    fontSize = TextSizes.Body, color = TextPrimary, lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { showPrivacyConfirm = true },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (exporting) "正在生成…" else "生成诊断包并分享", fontSize = TextSizes.Body) }

            resultText?.let { txt ->
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text("✓ 生成成功", fontSize = TextSizes.CardTitle,
                        fontWeight = FontWeight.SemiBold, color = QingJiGreen)
                    Spacer(Modifier.height(4.dp))
                    Text(txt, fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("下一步会弹出系统分享面板，你自己选发给谁（微信 / 邮件都行）。",
                        fontSize = TextSizes.Caption, color = TextSecondary)
                }
            }
            failText?.let { txt ->
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Text("⚠ 生成失败：$txt", fontSize = TextSizes.Body, color = DangerRed)
                }
            }
            Spacer(Modifier.height(Dimens.FabSpace + 24.dp))
        }
    }

    // ---------- 导出前隐私确认（任务书 5.3 第 2-3 步） ----------
    if (showPrivacyConfirm) {
        AppDialog(
            title = "确认导出诊断包？",
            confirmText = "生成",
            onConfirm = {
                showPrivacyConfirm = false
                exporting = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        DiagnosticExporter.build(context, AppLogger.readAllFromDisk())
                    }
                    exporting = false
                    r.fold(
                        onSuccess = { out ->
                            resultFile = out.file
                            resultText = "文件：${out.file.name}\n大小：%.1f KB · 日志 ${out.entryCount} 条" +
                                "（严重 ${out.fatalCount + out.errorCount} / 普通 ${out.warnCount}）"
                                .format(out.file.length() / 1024.0)
                            shareDiagnostic(context, out.file)
                        },
                        onFailure = { failText = it.message ?: "未知错误" }
                    )
                }
            },
            dismissText = "取消",
            onDismiss = { showPrivacyConfirm = false }
        ) {
            Text("会生成一个 ZIP 文件（只含上面白名单里的内容，全部已脱敏），生成后由你手动选择分享对象。轻记不会自动上传任何东西。",
                fontSize = 14.sp, color = TextPrimary)
        }
    }
}

/** 系统分享面板（复用备份导出的 FileProvider + ACTION_SEND 模式）。 */
private fun shareDiagnostic(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "发送诊断日志").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    )
}
