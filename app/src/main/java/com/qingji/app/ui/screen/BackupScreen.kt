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
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.ExportFilter
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.components.AppBottomSheet
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
import java.time.YearMonth

/**
 * ============================================================
 * 数据备份与导出（M9）
 * ------------------------------------------------------------
 * 三条路：
 * 1. 导出记录类 JSON —— 换手机用这份，覆盖档案 / 饮食 / 体重 / 饮水 / 日记 / 围度 /
 *    运动 / 断食 / 训练 / 自律等记录类数据；不含 68 个设置项、食物搜索历史、照片文件本身、AI Key；
 * 2. 导出饮食 CSV —— 想在 Excel 里自己分析用；
 * 3. 导入 JSON —— 覆盖恢复（记录类数据），二次确认后才执行。
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

    // v3.4.0：CSV 导出范围。null / 空集合都表示「不限」，老用户默认行为不变。
    var exportMonth by remember { mutableStateOf<YearMonth?>(null) }
    var exportMeals by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMonthPicker by remember { mutableStateOf(false) }

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
                    text = if (busy) "正在导出…" else "导出记录类 JSON（换手机用这个）",
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

                Spacer(Modifier.height(16.dp))

                // ---------- CSV 导出范围（v3.4.0） ----------
                Text(
                    text = "CSV 导出范围",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只影响下面的饮食 CSV；记录类 JSON 始终导出全部记录，不受这里筛选影响。",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = exportMonth?.let { "时间：${it.year} 年 ${it.monthValue} 月" }
                        ?: "时间：全部（点这里选月份）",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(QingJiGreenLight)
                        .clickable { showMonthPicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(text = "餐次（不选 = 全部）", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealRules.ALL.forEach { meal ->
                        val selected = meal in exportMeals
                        Text(
                            text = MealRules.label(meal),
                            fontSize = 12.sp,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) QingJiGreen else QingJiGreenLight)
                                .clickable {
                                    exportMeals = if (selected) exportMeals - meal else exportMeals + meal
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本次导出：${ExportFilter.describe(exportMonth, exportMeals)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(12.dp))

                PrimaryButton(
                    text = if (busy) "正在导出…" else "导出饮食记录 CSV（Excel 能开）",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            backup.exportCsv(exportMonth, exportMeals)
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
                            Text(
                                text = "未包含：68 个设置项、食物搜索历史、照片文件本身、AI Key",
                                fontSize = 10.sp,
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
                    // v1.0.6：原文案里的 ** 是 markdown 语法，Compose 的 Text 不认，
                    // 会原样显示成两个星号；顺手去掉，并把「先自己导一份保底」写进去。
                    text = "选中之前导出的 JSON 文件。导入会先清空本机现有记录再灌入，" +
                        "所以点之前一定要确认这是你要的那份。\n" +
                        "拿不准就先在上面导一份现在的 JSON 保底 —— 导回来就回到今天的样子。",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
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
                    "JSON 只导记录类数据 —— 68 个设置项、食物搜索历史、照片文件本身、AI Key 都不包含，换机后需要重新设置 / 重新配置。",
                    "API Key 不在备份里 —— Key 单独加密存在系统 Keystore，导出时根本读不到，所以新手机要重新填一次。",
                    "照片不跟着 JSON 走 —— 照片是文件路径，需要单独拷贝 files/images 和 files/progress 目录。",
                    "CSV 只导饮食记录 —— 可以按月份、按餐次筛选，方便在 Excel 里做分析，但它不是备份，不能用来恢复。",
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

    // 月份选择：年份左右翻 + 12 个月格子，外加「不限月份」
    if (showMonthPicker) {
        AppBottomSheet(
            title = "选择导出月份",
            subtitle = "只影响饮食记录 CSV，JSON 备份不受影响",
            onDismiss = { showMonthPicker = false }
        ) {
            var viewYear by remember { mutableStateOf(exportMonth?.year ?: AppClock.today().year) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "上一年",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(QingJiGreenLight)
                        .clickable { viewYear -= 1 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(text = "$viewYear 年", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = "下一年",
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(QingJiGreenLight)
                        .clickable { viewYear += 1 }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            (1..12).chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { m ->
                        val selected = exportMonth == YearMonth.of(viewYear, m)
                        Text(
                            text = "${m}月",
                            fontSize = 13.sp,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) QingJiGreen else QingJiGreenLight)
                                .clickable {
                                    exportMonth = YearMonth.of(viewYear, m)
                                    showMonthPicker = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = "不限月份（导出全部时间）",
                fontSize = 13.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        exportMonth = null
                        showMonthPicker = false
                    }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    // 导入确认：选完文件先弹这个，不确认绝不动数据
    pendingUri?.let { uri ->
        ConfirmDialog(
            title = "确认覆盖导入？",
            // 别在文案里写 ** 这种 markdown 语法：Compose 的 Text 不认，会原样显示星号
            body = "导入会用备份里的记录类数据覆盖本机现有记录：饮食、体重、饮水、日记、围度、" +
                "运动、断食、训练、自律、照片条目等；个人档案与 AI 服务配置也按备份覆盖。\n" +
                "68 个设置项、食物搜索历史、照片文件本身、AI Key 不在备份里，不会被改动。\n\n确定继续吗？",
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
