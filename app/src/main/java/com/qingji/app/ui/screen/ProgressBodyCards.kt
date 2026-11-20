package com.qingji.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.data.local.entity.UserProfile
import com.qingji.app.domain.AppClock
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.components.ThinProgressBar
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.WarnOrange
import com.qingji.app.ui.theme.pressable


/** 身体数据卡：当前体重、目标、BMI 分级、减重进度。 */
@Composable
internal fun BodyDataCard(
    profile: UserProfile?,
    currentWeight: Double,
    targetWeight: Double,
    bmi: Double,
    category: String,
    categoryColor: Color,
    progress: Float,
    remain: Double,
    recordCount: Int,
    unit: String,
    /** 点卡片进记体重页 */
    onClick: () -> Unit = {}
) {
    // 进场动画：进度条从 0 涨到真实百分比，右上角的数字跟着一起跑。
    // 为什么要有：减肥进度是这个页面最该「有感觉」的地方，
    // 静态一条进度条看不出「我又往前走了一点」。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animProgress by animateFloatAsState(
        targetValue = if (entered) progress else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progressGrow"
    )

    SectionCard(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = s("身体数据"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(text = s("记体重 ›"), fontSize = 11.sp, color = QingJiGreenDark)
        }

        Spacer(Modifier.height(12.dp))

        // 体重区：只放大字 + 目标，右上角不再放 BMI 数字
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (currentWeight > 0) {
                    com.qingji.app.domain.WeightUnit.format(currentWeight, unit)
                } else {
                    "--"
                },
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = " ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Text(
            text = "目标: ${
                if (targetWeight > 0) {
                    com.qingji.app.domain.WeightUnit.format(targetWeight, unit)
                } else {
                    "--"
                }
            } ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
            fontSize = 12.sp,
            color = TextSecondary
        )

        // v3.2.4：BMI 小仪表盘放这块左下角，灰色细竖线隔开，右下角显示分级
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                MiniBmiGauge(bmi = bmi, categoryColor = categoryColor)
            }
            // 灰色细线（竖）：把仪表盘和分级结论分开
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .background(DividerColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = s("BMI 分级"), fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (bmi > 0) when (category) {
                        "正常" -> "健康"
                        "偏瘦" -> "偏瘦"
                        "超重" -> "超重"
                        "肥胖" -> "肥胖"
                        else -> category
                    } else "待记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(text = s("中国标准"), fontSize = 10.sp, color = TextSecondary)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = s("减脂进展"), fontSize = 14.sp, color = TextPrimary)
            Text(
                text = "${(animProgress * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = QingJiGreen
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinProgressBar(progress = animProgress, barHeight = 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (recordCount == 0) {
                "还没有体重记录"
            } else {
                "还差 ${
                    com.qingji.app.domain.WeightUnit.format(remain, unit)
                } ${com.qingji.app.domain.WeightUnit.symbol(unit)} · 已记录 $recordCount 次"
            },
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (profile == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = s("个人资料还没填完整，进度可能显示为 0"),
                fontSize = 11.sp,
                color = WarnOrange
            )
        }
    }
}


/**
 * v3.2.4：缩小版 BMI 仪表盘，收进「身体数据」卡片左下角。
 * 半圆弧按中国标准分四段（偏瘦 / 健康 / 超重 / 肥胖），
 * 一根短指针指到当前 BMI，底下写数值和「BMI」标签。
 *
 * 注意：Canvas 的绘制 lambda 不是 @Composable，
 * 所有会随主题变化的颜色必须先在 Composable 作用域取成局部变量再传进去。
 */
@Composable
internal fun MiniBmiGauge(bmi: Double, categoryColor: Color) {
    val hasData = bmi > 0
    val sweep = if (hasData) {
        ((bmi.coerceIn(15.0, 35.0) - 15.0) / 20.0).toFloat()
    } else 0f

    val thin = QingJiGreen.copy(alpha = 0.30f)
    val normal = QingJiGreen
    val warn = WarnOrange
    val danger = DangerRed
    val needle = TextPrimary
    val track = DividerColor

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(width = 88.dp, height = 48.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(
                width = size.width - stroke,
                height = (size.height - inset) * 2 - stroke
            )
            val topLeft = Offset(inset, inset)
            // 四段：15~18.5 / 18.5~24 / 24~28 / 28~35
            val parts = listOf(
                3.5f / 20f to thin,
                5.5f / 20f to normal,
                4f / 20f to warn,
                7f / 20f to danger
            )
            var start = 180f
            parts.forEach { (portion, color) ->
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = 180f * portion,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                start += 180f * portion
            }
            // 没数据时盖一层灰：空表盘不至于看起来像画坏了
            if (!hasData) {
                drawArc(
                    color = track,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
            }
            val cx = size.width / 2
            val cy = size.height - inset
            val radius = arcSize.width / 2 - stroke / 2
            if (hasData) {
                val angle = Math.toRadians((180f + 180f * sweep).toDouble())
                drawLine(
                    color = needle,
                    start = Offset(cx, cy),
                    end = Offset(
                        cx + (radius * kotlin.math.cos(angle)).toFloat(),
                        cy + (radius * kotlin.math.sin(angle)).toFloat()
                    ),
                    strokeWidth = 2.dp.toPx()
                )
            }
            drawCircle(color = needle, radius = 3.dp.toPx(), center = Offset(cx, cy))
        }
        Text(
            text = if (hasData) String.format("%.1f", bmi) else "--",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (hasData) categoryColor else TextSecondary
        )
        Text(text = "BMI", fontSize = 10.sp, color = TextSecondary)
    }
}


/** 身体维度：最新一次量的五个围度。 */
@Composable
internal fun BodyMeasureCard(
    latest: com.qingji.app.data.local.entity.BodyMeasure?,
    onAdd: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = s("身体维度"),
                    fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (latest == null) "还没量过围度" else "最近一次：${latest.date}",
                    fontSize = TextSizes.Caption,
                    color = TextSecondary
                )
            }
            Text(
                text = if (latest == null) "+ 记录" else "更新",
                fontSize = 12.sp,
                color = QingJiGreenDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.TinyCorner))
                    .background(QingJiGreenLight)
                    .pressable { onAdd() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        if (latest == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = s("量一次腰围、胸围、臀围，变化比体重更早看得出来"),
                fontSize = 12.sp,
                color = TextSecondary
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MeasureCell("腰围", latest.waistCm)
                MeasureCell("胸围", latest.chestCm)
                MeasureCell("臀围", latest.hipCm)
                MeasureCell("大腿", latest.thighCm)
                MeasureCell("上臂", latest.armCm)
            }
        }
    }
}


@Composable
internal fun MeasureCell(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (value == null) "—" else "${value.toInt()}",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(text = "$label cm", fontSize = 10.sp, color = TextSecondary)
    }
}


/** 围度录入弹窗。留空的项不会存。 */
@Composable
internal fun BodyMeasureDialog(
    initial: com.qingji.app.data.local.entity.BodyMeasure?,
    onDismiss: () -> Unit,
    onConfirm: (com.qingji.app.data.local.entity.BodyMeasure) -> Unit
) {
    var waist by remember { mutableStateOf(initial?.waistCm?.toInt()?.toString().orEmpty()) }
    var chest by remember { mutableStateOf(initial?.chestCm?.toInt()?.toString().orEmpty()) }
    var hip by remember { mutableStateOf(initial?.hipCm?.toInt()?.toString().orEmpty()) }
    var thigh by remember { mutableStateOf(initial?.thighCm?.toInt()?.toString().orEmpty()) }
    var arm by remember { mutableStateOf(initial?.armCm?.toInt()?.toString().orEmpty()) }

    com.qingji.app.ui.components.AppBottomSheet(
        title = "记一组围度",
        subtitle = "只填量过的就行，单位厘米",
        confirmText = "保存",
        onConfirm = {
            onConfirm(
                com.qingji.app.data.local.entity.BodyMeasure(
                    date = AppClock.today(),
                    waistCm = waist.toDoubleOrNull(),
                    chestCm = chest.toDoubleOrNull(),
                    hipCm = hip.toDoubleOrNull(),
                    thighCm = thigh.toDoubleOrNull(),
                    armCm = arm.toDoubleOrNull()
                )
            )
        },
        onDismiss = onDismiss
    ) {
        Column {
            MeasureField("腰围", waist) { waist = it }
            MeasureField("胸围", chest) { chest = it }
            MeasureField("臀围", hip) { hip = it }
            MeasureField("大腿围", thigh) { thigh = it }
            MeasureField("上臂围", arm) { arm = it }
        }
    }
}


@Composable
internal fun MeasureField(label: String, value: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            modifier = Modifier.width(110.dp)
        )
    }
}
