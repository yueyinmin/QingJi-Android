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


/**
 * 身体数据仪表盘（工单 P0）：一张大圆角卡整合体重 / 体脂率 / BMI / 减重进度。
 * 数据全部来自外部（已按全量记录计算），本组件只负责展示，不含业务公式。
 */
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
    /** 最近一次有效体脂率（与当前体重可不同日） */
    bodyFat: com.qingji.app.domain.BodyMetricsCalculator.BodyFatRecord? = null,
    /** 目标是否已达成 */
    goalReached: Boolean = false,
    /** 点卡片进记体重页 */
    onClick: () -> Unit = {}
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val animProgress by animateFloatAsState(
        targetValue = if (entered) progress else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "progressGrow"
    )

    SectionCard {
        // 顶部：标题（点进详情） + 健康同步入口（未接入，不伪造同步状态）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s("身体数据"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.clickable { onClick() }
            )
            HealthSyncEntry()
        }

        Spacer(Modifier.height(12.dp))

        // 主体左右双栏：左体重/体脂，右 BMI 仪表盘
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                BodyWeightSummary(currentWeight, targetWeight, unit)
                Spacer(Modifier.height(8.dp))
                BodyFatSummary(bodyFat)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                BodyBmiSummary(bmi, category, categoryColor, profile != null)
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Spacer(Modifier.height(12.dp))

        WeightLossProgressSection(animProgress, remain, unit, recordCount, goalReached)
        if (profile == null) {
            Spacer(Modifier.height(6.dp))
            Text(text = s("个人资料还没填完整，进度可能显示为 0"), fontSize = 11.sp, color = WarnOrange)
        }
    }
}

/** 健康同步入口：当前无 Health Connect SDK，仅占位，不展示可误导的同步成功态。 */
@Composable
private fun HealthSyncEntry() {
    Text(
        text = s("健康同步 · 暂未开放"),
        fontSize = 11.sp,
        color = TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(QingJiGreenLight)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 左栏：当前体重（视觉主体）+ 目标体重 */
@Composable
private fun BodyWeightSummary(currentWeight: Double, targetWeight: Double, unit: String) {
    Text(text = s("体重"), fontSize = 11.sp, color = TextSecondary)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = if (currentWeight > 0) com.qingji.app.domain.WeightUnit.format(currentWeight, unit) else "--",
            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary
        )
        Text(
            text = " ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
            fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp)
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = "目标 ${
            if (targetWeight > 0) com.qingji.app.domain.WeightUnit.format(targetWeight, unit) else "--"
        } ${com.qingji.app.domain.WeightUnit.symbol(unit)}",
        fontSize = 12.sp, color = TextSecondary
    )
}

/** 左栏：最近一次有效体脂率（不推算、不伪装实测） */
@Composable
private fun BodyFatSummary(bodyFat: com.qingji.app.domain.BodyMetricsCalculator.BodyFatRecord?) {
    Text(text = s("体脂率"), fontSize = 11.sp, color = TextSecondary)
    if (bodyFat == null) {
        Text(text = s("未记录"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
    } else {
        Text(
            text = "${String.format("%.1f", bodyFat.value)}%",
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
        )
        Text(
            text = "${bodyFat.date} 记录",
            fontSize = 10.sp, color = TextSecondary
        )
    }
}

/** 右栏：BMI 数值 + 放大仪表盘 + 中国标准分级 */
@Composable
private fun BodyBmiSummary(bmi: Double, category: String, categoryColor: Color, profileReady: Boolean) {
    val hasData = profileReady && bmi > 0
    // BMI 标题与分级胶囊同一行：标准跟着 BMI 走，不再堆在仪表盘下方另占一行；
    // 去掉「中国标准」字样，减少纵向占用。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "BMI", fontSize = 11.sp, color = TextSecondary)
        if (hasData) {
            Text(
                text = when (category) {
                    "正常" -> "健康"
                    else -> category
                },
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = categoryColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(categoryColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    if (!hasData) {
        Text(text = "--", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(text = s("补全身高体重后显示"), fontSize = 10.sp, color = TextSecondary)
        return
    }
    Text(
        text = String.format("%.1f", bmi),
        fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary
    )
    // 仪表盘只画半圆+指针，数值已在上方放大展示，不再重复；刻度映射与指针算法不变
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MiniBmiGauge(
            bmi = bmi, categoryColor = categoryColor,
            width = 120.dp, height = 62.dp, showLabels = false
        )
    }
}

/** 底部：减重进度（百分比 / 进度条 / 还差多少） */
@Composable
private fun WeightLossProgressSection(
    progress: Float, remain: Double, unit: String, recordCount: Int, goalReached: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = s("减重进度"), fontSize = 14.sp, color = TextPrimary)
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = QingJiGreen
        )
    }
    Spacer(Modifier.height(8.dp))
    ThinProgressBar(progress = progress.coerceIn(0f, 1f), barHeight = 8.dp)
    Spacer(Modifier.height(8.dp))
    Text(
        text = when {
            recordCount == 0 -> "还没有体重记录"
            goalReached -> s("已达成目标") + " · 已记录 $recordCount 次"
            else -> "还差 ${
                com.qingji.app.domain.WeightUnit.format(remain, unit)
            } ${com.qingji.app.domain.WeightUnit.symbol(unit)} · 已记录 $recordCount 次"
        },
        fontSize = 12.sp, color = TextSecondary
    )
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
internal fun MiniBmiGauge(
    bmi: Double,
    categoryColor: Color,
    width: androidx.compose.ui.unit.Dp = 88.dp,
    height: androidx.compose.ui.unit.Dp = 48.dp,
    /** 仪表盘卡已在外层放大展示数值，内部不再重复画数字/BMI 字样 */
    showLabels: Boolean = true
) {
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
        Canvas(modifier = Modifier.size(width = width, height = height)) {
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
        if (showLabels) {
            Text(
                text = if (hasData) String.format("%.1f", bmi) else "--",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasData) categoryColor else TextSecondary
            )
            Text(text = "BMI", fontSize = 10.sp, color = TextSecondary)
        }
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
