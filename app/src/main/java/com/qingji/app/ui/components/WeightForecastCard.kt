package com.qingji.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.WeightForecastCalculator
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * 进展页「体重趋势预测」卡片（v3.4.0 · R3）。
 * 样本不足 / 方向相反 / 太久 / 没目标时，直接显示计算器给的说明文案，
 * 不硬凑一个日期出来。
 */
@Composable
fun WeightForecastCard(
    forecast: WeightForecastCalculator.Forecast,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "目标达成预估",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "近 14 天趋势", fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = forecast.message,
            fontSize = if (forecast.show) 14.sp else 13.sp,
            fontWeight = if (forecast.show) FontWeight.Medium else FontWeight.Normal,
            color = if (forecast.show) QingJiGreen else TextSecondary
        )
    }
}
