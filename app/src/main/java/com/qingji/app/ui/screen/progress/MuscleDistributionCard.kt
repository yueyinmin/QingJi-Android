package com.qingji.app.ui.screen.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qingji.app.domain.MuscleDistributionCalculator
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * 肌群分布卡（工单 4.2 第 6 项，P2）。
 *
 * 只画「七类 + 未分类」的横向条：条长按组数占比，右边写组数与训练量。
 * 没有训练数据时给一行引导，不画一排 0% 的空条误导人。
 * 「未分类」是自建/已删动作，如实展示，不硬塞进某个肌群。
 */
@Composable
fun MuscleDistributionCard(
    buckets: List<MuscleDistributionCalculator.Bucket>
) {
    val totalSets = buckets.sumOf { it.sets }
    SectionCard {
        Text(
            text = s("肌群分布"),
            fontSize = TextSizes.CardTitle,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = s("按动作库主分类统计近 30 天训练，估算值仅供参考"),
            fontSize = TextSizes.Tiny,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))

        if (totalSets == 0) {
            Text(
                text = s("还没有力量训练组记录。开始一次训练，这里就会显示各肌群的占比。"),
                fontSize = TextSizes.Caption,
                color = TextSecondary
            )
            return@SectionCard
        }

        val maxSets = buckets.maxOf { it.sets }.coerceAtLeast(1)
        buckets.filter { it.sets > 0 || it.key == "other" }.forEach { b ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = b.label,
                    fontSize = TextSizes.Caption,
                    color = TextPrimary,
                    modifier = Modifier.weight(0.75f)
                )
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(QingJiGreenLight)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(b.sets.toFloat() / maxSets)
                            .height(10.dp)
                            .clip(RoundedCornerShape(Dimens.ItemCorner))
                            .background(QingJiGreen)
                    )
                }
                Text(
                    text = "${b.sets} 组${if (b.volumeKg > 0) " · %.0f kg".format(b.volumeKg) else ""}",
                    fontSize = TextSizes.Tiny,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = s("训练量 = 重量 × 次数；一个动作只算主分类，不重复计数。"),
            fontSize = TextSizes.Tiny,
            color = TextSecondary
        )
    }
}
