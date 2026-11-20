package com.qingji.app.ui.screen.training

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
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.DailyHealthSummaryEntity
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * 健康体系 V1.1（工单 10.4）：日历选日期 → 每日健康汇总卡。
 * 五个模块一行一项：饮食摄入 / 运动消耗（分类细分）/ 力量训练（次数·时长·容量kg）/
 * 睡眠时长·条数。口径来自 DailyHealthRepository（可缓存可重建）。
 * 全天没有任何数据时显示空态，不补造数字（工单 10.5 同款铁律）。
 */
@Composable
internal fun DailyHealthSummaryCard(
    summary: DailyHealthSummaryEntity?,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier, contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每日健康汇总", fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            // 汇总是缓存值，可随时从原始记录重建（工单 10.2），角标注明口径
            Text("按原始记录自动汇总", fontSize = 9.sp, color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DividerColor.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(8.dp))

        if (summary == null || (!hasAnyData(summary))) {
            Text("这一天还没有健康数据。月历里选个日期看看。",
                fontSize = TextSizes.Body, color = TextSecondary)
            return@SectionCard
        }

        // 饮食
        SummaryRow("饮食", "${summary.calorieIntake.toInt()} 千卡",
            detail = "蛋白 %.0fg · 碳水 %.0fg · 脂肪 %.0fg".format(
                summary.proteinG, summary.carbohydratesG, summary.fatG),
            show = summary.calorieIntake > 0.0)
        // 运动消耗（分类）
        SummaryRow("运动消耗", "${summary.exerciseCalories.toInt()} 千卡",
            detail = listOfNotNull(
                if (summary.strengthCalories > 0) "力量 ${summary.strengthCalories.toInt()}" else null,
                if (summary.cardioCalories > 0) "有氧 ${summary.cardioCalories.toInt()}" else null,
                if (summary.walkingCalories > 0) "步行 ${summary.walkingCalories.toInt()}" else null
            ).joinToString(" · ").ifBlank { "暂无分类数据" },
            show = summary.exerciseCalories > 0.0)
        // 力量训练（容量 kg 与消耗 kcal 是两回事，分开显示）
        SummaryRow("力量训练", "${summary.workoutCount} 次 · ${summary.workoutDurationMinutes} 分钟",
            detail = "训练容量 %.1f kg（=重量×次数，跟消耗分开算）".format(summary.workoutVolumeKg),
            show = summary.workoutCount > 0)
        // 睡眠（归属日期 = 起床日期）
        SummaryRow("睡眠", "${summary.sleepDurationMinutes / 60}小时${summary.sleepDurationMinutes % 60}分",
            detail = "${summary.sleepRecordCount} 条记录（按起床日期归属）",
            show = summary.sleepRecordCount > 0)
    }
}

private fun hasAnyData(s: DailyHealthSummaryEntity): Boolean =
    s.calorieIntake > 0.0 || s.exerciseCalories > 0.0 ||
        s.workoutCount > 0 || s.sleepRecordCount > 0

@Composable
private fun SummaryRow(title: String, value: String, detail: String, show: Boolean) {
    if (!show) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(QingJiGreenLight)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(title, fontSize = 10.sp, color = QingJiGreen)
            }
            Text(value, fontSize = TextSizes.Body, fontWeight = FontWeight.SemiBold,
                color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(detail, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}
