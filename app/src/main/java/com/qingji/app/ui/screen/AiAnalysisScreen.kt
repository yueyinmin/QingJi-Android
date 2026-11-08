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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.PrimaryButton
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.AiAnalysisViewModel
import kotlinx.coroutines.launch

/**
 * ============================================================
 * AI 分析（M3.8）：今日复盘 / 周报
 * ------------------------------------------------------------
 * 数字全部由仓库层从数据库聚合好，再拼成一段「事实」发给模型，
 * 模型只负责把数字说成人话，不许自己编数据。
 * prompt 里已经禁掉诊断/治疗/用药/疾病/处方一类表述（合规红线）。
 * ============================================================
 */
@Composable
fun AiAnalysisScreen(
    onBack: () -> Unit,
    onOpenAiConfig: () -> Unit,
    vm: AiAnalysisViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    var isWeekly by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    fun generate() {
        scope.launch {
            isLoading = true
            errorText = ""
            resultText = ""
            val outcome = if (isWeekly) vm.weekly() else vm.daily()
            outcome.fold(
                onSuccess = { resultText = it },
                onFailure = { errorText = it.message ?: "分析失败" }
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = "AI 分析", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("今日复盘" to false, "本周周报" to true).forEach { (label, weekly) ->
                    val selected = isWeekly == weekly
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = if (selected) QingJiGreenDark else TextSecondary,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) QingJiGreenLight else Color.Transparent)
                            .clickable { isWeekly = weekly; resultText = ""; errorText = "" }
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HintBox(
                text = if (isWeekly) {
                    "周报会统计近 7 天的平均摄入、体重变化和最常吃的食物，给下周一条建议。"
                } else {
                    "复盘会对照今天的热量预算和三大营养素目标，给 1-2 条具体建议。"
                }
            )

            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (isLoading) "生成中…" else "生成分析",
                enabled = !isLoading,
                onClick = { generate() }
            )

            if (errorText.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                HintBox(text = errorText, isError = true)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "去设置 AI 服务 →",
                    fontSize = 13.sp,
                    color = QingJiGreen,
                    modifier = Modifier.clickable { onOpenAiConfig() }.padding(4.dp)
                )
            }

            if (resultText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                SectionCard {
                    Text(
                        text = if (isWeekly) "本周周报" else "今日复盘",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(text = resultText, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "以上仅为饮食记录的描述与建议，不构成任何医疗意见",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            } else if (!isLoading) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "点上面按钮生成，没配 AI 也能看首页的数字",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
