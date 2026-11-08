package com.qingji.app.ui.screen

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.backup.BackupManager
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.screen.settings.ConfirmDialog
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

/**
 * ============================================================
 * 数据备份与导出（M9）
 * ------------------------------------------------------------
 * 三条路：
 * 1. 导出全量 JSON —— 换手机用这份，能恢复所有表；
 * 2. 导出饮食 CSV —— 想在 Excel 里自己分析用；
 * 3. 导入 JSON —— 覆盖恢复，二次确认后才执行。
 *
 * 文件写在 App 私有外部目录，用系统分享发出去（微信/网盘/邮箱）。
 * 这样做不需要任何存储权限，安卓 10 之后的沙盒规则也能过。
 * ============================================================
 */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember { BackupManager(context) }

    var busy by remember { mutableStateOf(false) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }

    // 选文件（导入用）。用 */* 是因为有些文件管理器把 .json 报成 octet-stream
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingUri = uri
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "数据备份与导出", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---------- 导出 ----------
            SectionCard {
                Text(
                    text = "导出",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "导出的文件会放在 App 私有目录，然后用系统分享发到微信 / 网盘 / 邮箱。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))

                PrimaryButton(
                    text = if (busy) "正在导出…" else "导出全量 JSON（换手机用这个）",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            backup.exportJson()
                                .onSuccess { file ->
                                    lastFile = file
                                    Toast.makeText(context, "已导出：${file.name}", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                                }
                            busy = false
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = if (busy) "正在导出…" else "导出饮食记录 CSV（Excel 能开）",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            backup.exportCsv()
                                .onSuccess { file ->
                                    lastFile = file
                                    Toast.makeText(context, "已导出：${file.name}", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                                }
                            busy = false
                        }
                    }
                )

                lastFile?.let { file ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontSize = 12.sp, color = QingJiGreenDark)
                            Text(
                                text = backup.sizeText(file.length()),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "分享出去",
                            fontSize = 13.sp,
                            color = QingJiGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Color.White)
                                .clickable { backup.share(file) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "目录：${backup.backupDirPath()}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 导入 ----------
            SectionCard {
                Text(
                    text = "导入（覆盖恢复）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选中之前导出的 JSON 文件。导入会**先清空本机现有记录**再灌入，" +
                        "所以点之前一定要确认这是你要的那份。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "选择 JSON 备份文件",
                    enabled = !busy,
                    onClick = { pickFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
                )

                importResult?.let { text ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .padding(10.dp)
                    ) {
                        Text(text, fontSize = 12.sp, color = QingJiGreenDark)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 说明 ----------
            SectionCard {
                Text(
                    text = "几点说明",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "API Key 不在备份里 —— Key 单独加密存在系统 Keystore，导出时根本读不到，所以新手机要重新填一次。",
                    "照片不跟着 JSON 走 —— 照片是文件路径，需要单独拷贝 files/images 目录。",
                    "CSV 只导饮食记录 —— 方便你在 Excel 里做分析，但它不是备份，不能用来恢复。",
                    "卸载 App 会清掉私有目录 —— 导出后请务必用「分享出去」把文件发到别的地方。"
                ).forEach { line ->
                    Row(modifier = Modifier.padding(bottom = 6.dp)) {
                        Text("· ", fontSize = 12.sp, color = QingJiGreen)
                        Text(line, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 导入确认：选完文件先弹这个，不确认绝不动数据
    pendingUri?.let { uri ->
        ConfirmDialog(
            title = "确认覆盖导入？",
            body = "导入会用备份文件里的内容**替换**本机现有的饮食、体重、饮水、日记和档案。\n" +
                "现有数据会被清掉，不能恢复。\n\n确定继续吗？",
            confirmText = "确认导入",
            onConfirm = {
                pendingUri = null
                busy = true
                scope.launch {
                    backup.importJson(uri)
                        .onSuccess { summary ->
                            importResult = summary
                            Toast.makeText(context, "导入完成", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure {
                            importResult = "导入失败：${it.message ?: "文件格式不对"}"
                        }
                    busy = false
                }
            },
            onDismiss = { pendingUri = null }
        )
    }
}
