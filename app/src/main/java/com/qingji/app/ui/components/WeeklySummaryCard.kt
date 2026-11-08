package com.qingji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.WeeklySummaryCalculator
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * 首页底部「本周小结」卡片（v3.4.0 · R2）。
 * 数据全部来自 WeeklySummaryCalculator（纯本地算、不调 AI），
 * 记录不足 3 天时只显示「再记几天」，不给用 0 拼出来的假结论。
 */
@Composable
fun WeeklySummaryCard(
    summary: WeeklySummaryCalculator.Summary,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周小结",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "近 7 天", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))

        if (!summary.ready) {
            Text(text = summary.conclusion, fontSize = 13.sp, color = TextSecondary)
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryStat("平均摄入", summary.avgKcal.toString(), "千卡")
            SummaryStat("平均缺口", summary.avgGap.toString(), "千卡")
            SummaryStat("饮水达标", summary.waterDays.toString(), "天")
            SummaryStat("运动", summary.exerciseDays.toString(), "天")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = summary.conclusion,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(text = summary.advice, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun SummaryStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = QingJiGreen
        )
        Text(text = "$label($unit)", fontSize = 10.sp, color = TextSecondary)
    }
}
