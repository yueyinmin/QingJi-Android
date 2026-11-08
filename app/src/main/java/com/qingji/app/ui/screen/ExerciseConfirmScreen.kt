package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingji.app.data.local.entity.ExerciseLog
import com.qingji.app.domain.ExerciseCalculator
import com.qingji.app.ui.components.ExerciseDateTimeWheelSheet
import com.qingji.app.ui.components.ExerciseIcon
import com.qingji.app.ui.components.ExerciseNumberWheelSheet
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.ExerciseViewModel
import java.time.LocalDateTime

/**
 * ============================================================
 * 确认记录（v3.3.0 三级菜单）
 * ------------------------------------------------------------
 * 从「记录运动」选中一条进来；点「更换」就是返回二级页重新选，
 * 不做二级弹层，跟用户要求一致。
 *
 * 四种记录方式（时长 / 千卡 / 步数 / 公里）最终都换算成
 * ExerciseLog 的 minutes + kcal，换算口径全在 domain/ExerciseCalculator。
 * 底部日期时间行和「保存」按钮固定，只有中间内容随页面滚动。
 * ============================================================
 */
@Composable
fun ExerciseConfirmScreen(
    presetId: Long,
    /** 「更换」和顶栏返回：回到二级菜单重新选运动 */
    onBack: () -> Unit,
    /** 保存成功后回首页 */
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ExerciseViewModel = viewModel()
) {
    val presets by vm.presets.collectAsState()
    val weightKg by vm.weightKg.collectAsState()
    val preset = presets.firstOrNull { it.id == presetId }

    var mode by remember { mutableStateOf(ExerciseCalculator.MODE_MINUTES) }
    var amount by remember { mutableStateOf(ExerciseCalculator.defaultAmount(ExerciseCalculator.MODE_MINUTES)) }
    var intensity by remember { mutableStateOf<String?>(null) }
    var whenAt by remember { mutableStateOf(LocalDateTime.now()) }
    var showCustomWheel by remember { mutableStateOf(false) }
    var showDateTimeWheel by remember { mutableStateOf(false) }

    val estimate = ExerciseCalculator.estimate(
        met = preset?.met ?: 5.0,
        weightKg = weightKg,
        mode = mode,
        amount = amount.toDouble(),
        intensity = intensity
    )
    val unit = ExerciseCalculator.unit(mode)
    val step = stepOf(mode)
    val minAmount = minOf(mode)

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(title = "确认记录", onBack = onBack)

        // ---------- 中间滚动区 ----------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 当前运动 + 更换
            SectionCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = ExerciseIcon.of(preset?.name.orEmpty()),
                            contentDescription = null,
                            tint = QingJiGreen,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Text(
                        text = preset?.name ?: "加载中…",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f).padding(start = 10.dp)
                    )
                    Text(
                        text = "更换",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QingJiGreenDark,
                        modifier = Modifier.pressable { onBack() }.padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(text = "记录方式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseCalculator.modes().forEach { (key, label) ->
                    OptionChip(
                        text = label,
                        selected = mode == key,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            mode = key
                            amount = ExerciseCalculator.defaultAmount(key)
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = amount.toString(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = " $unit",
                    fontSize = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (mode == ExerciseCalculator.MODE_KCAL) {
                    "约等于 ${estimate.minutes} 分钟运动"
                } else {
                    "约 ${estimate.kcal.toInt()} 千卡"
                },
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth().padding(start = 0.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))
            // 固定默认值 + 自定义（两行自动折行）
            val presetsForMode = ExerciseCalculator.presets(mode)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presetsForMode.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            OptionChip(
                                text = "$value ${shortUnit(mode)}",
                                selected = amount == value,
                                modifier = Modifier.weight(1f),
                                onClick = { amount = value }
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip(
                        text = "自定义",
                        selected = amount !in presetsForMode,
                        modifier = Modifier.weight(1f),
                        onClick = { showCustomWheel = true }
                    )
                    repeat(3) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("−") { amount = (amount - step).coerceAtLeast(minAmount) }
                Text(
                    text = "$amount $unit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.width(130.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                StepButton("+") { amount = amount + step }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "强度（可选）",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ExerciseCalculator.INTENSITY_LIGHT to "轻",
                    ExerciseCalculator.INTENSITY_MEDIUM to "中",
                    ExerciseCalculator.INTENSITY_HIGH to "高"
                ).forEach { (key, label) ->
                    OptionChip(
                        text = label,
                        selected = intensity == key,
                        modifier = Modifier.width(64.dp),
                        onClick = { intensity = if (intensity == key) null else key }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(text = "日记", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "保存运动后可写日记",
                fontSize = 12.sp,
                color = TextSecondary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---------- 底部固定区：日期时间 + 保存 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable { showDateTimeWheel = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${whenAt.toLocalDate()} ${"%02d".format(whenAt.hour)}:${"%02d".format(whenAt.minute)}",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "›", fontSize = 18.sp, color = DividerColor)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(QingJiGreen)
                    .pressable(enabled = preset != null) {
                        val p = preset ?: return@pressable
                        vm.save(
                            ExerciseLog(
                                date = whenAt.toLocalDate(),
                                type = p.name,
                                minutes = estimate.minutes,
                                kcal = estimate.kcal
                            )
                        ) { onSaved() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }

    if (showCustomWheel) {
        ExerciseNumberWheelSheet(
            values = customValues(mode),
            initialValue = amount,
            unitLabel = unit,
            onConfirm = { amount = it },
            onDismiss = { showCustomWheel = false }
        )
    }

    if (showDateTimeWheel) {
        ExerciseDateTimeWheelSheet(
            initial = whenAt,
            onConfirm = { whenAt = it },
            onDismiss = { showDateTimeWheel = false }
        )
    }
}

/** 各记录方式在「自定义」滚轮里能选的范围。步数按 100 一档，别生成 5 万个格子。 */
private fun customValues(mode: String): List<Int> = when (mode) {
    ExerciseCalculator.MODE_KCAL -> (10..2000).toList()
    ExerciseCalculator.MODE_STEPS -> (1..300).map { it * 100 }
    ExerciseCalculator.MODE_KM -> (1..100).toList()
    else -> (1..180).toList()
}

private fun stepOf(mode: String): Int = when (mode) {
    ExerciseCalculator.MODE_KCAL -> 50
    ExerciseCalculator.MODE_STEPS -> 500
    ExerciseCalculator.MODE_KM -> 1
    else -> 5
}

private fun minOf(mode: String): Int = when (mode) {
    ExerciseCalculator.MODE_KCAL -> 10
    ExerciseCalculator.MODE_STEPS -> 100
    else -> 1
}

/** 快捷档位上的短单位：10 分 / 300 千卡 / 5000 步 / 3 公里。 */
private fun shortUnit(mode: String): String = when (mode) {
    ExerciseCalculator.MODE_KCAL -> "千卡"
    ExerciseCalculator.MODE_STEPS -> "步"
    ExerciseCalculator.MODE_KM -> "公里"
    else -> "分"
}

@Composable
private fun OptionChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) QingJiGreen else CardWhite)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else DividerColor,
                shape = RoundedCornerShape(12.dp)
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, fontSize = 22.sp, color = TextPrimary)
    }
}
