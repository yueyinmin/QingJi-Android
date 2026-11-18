package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.SleepRecordEntity
import com.qingji.app.domain.SleepScoreCalculator
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * 健康体系 V1.1（工单 10.3）：首页睡眠摘要卡。
 * 显示昨晚（归属今天）的睡眠：时长、入睡/起床、质量；无记录显示「尚未记录」，
 * 绝不显示 0 小时（工单 9.5）。点击进睡眠记录页。
 */
@Composable
internal fun SleepSummaryCard(
    lastNight: SleepRecordEntity?,
    onClick: () -> Unit,
    /** 近 7 晚记录，供评分的规律性项用；不传则该项不计分（不会显示错分） */
    recentNights: List<SleepRecordEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 和睡眠页用同一个 Calculator，两处分数口径完全一致（工单：评分回溯不落库）。
    val score = lastNight?.let { night ->
        val date = night.date
        SleepScoreCalculator.score(
            record = night,
            recentRecords = recentNights.filter {
                !it.date.isBefore(date.minusDays(6)) && !it.date.isAfter(date)
            }
        )
    }
    SectionCard(
        contentPadding = 14.dp,
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MultiColor.FastingText.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MultiColor.FastingText,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("昨晚睡眠", fontSize = TextSizes.Caption, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    if (lastNight == null) {
                        Text(
                            "尚未记录",
                            fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    } else {
                        Text(
                            text = "%d小时%d分".format(
                                lastNight.durationMinutes / 60,
                                lastNight.durationMinutes % 60
                            ),
                            fontSize = TextSizes.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }
            if (lastNight != null) {
                Column(horizontalAlignment = Alignment.End) {
                    if (score != null) {
                        Text(
                            "${score.score} · ${score.rating}",
                            fontSize = TextSizes.Caption,
                            color = MultiColor.FastingText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "点开看详情",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Transparent)
                    )
                }
            }
        }
    }
}
