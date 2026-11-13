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
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val fatalCount = entries.count { it.level == AppLogger.Level.FATAL || it.level == AppLogger.Level.ERROR }
    val normalCount = entries.count { it.level == AppLogger.Level.WARN || it.level == AppLogger.Level.DEBUG }
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("← 返回", fontSize = TextSizes.Body, color = QingJiGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
                Text("错误日志", fontSize = TextSizes.PageTitle,
                    fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Text("清空", fontSize = 13.sp, color = DangerRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.PillCorner))
                            .clickable { showClearConfirm = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
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
}

@Composable
private fun SummaryCol(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}
