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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.FastingRules
import com.qingji.app.domain.TimeFormat
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import com.qingji.app.ui.theme.WarnOrange
import kotlinx.coroutines.delay


/**
 * 断食常驻通知开关（2026-11-11 专项，第一轮只做这一项）。
 *
 * 通知内容完全由现有 [FastingRules.status] 推出来：断食中就显示计时和剩余时间，
 * 进食窗口内就显示窗口时间。所以它不改断食规则、不新增数据库字段，
 * 关掉开关或杀掉服务都不会影响计算结果。
 */
@Composable
internal fun FastingNotifyCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "常驻通知",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "通知栏显示断食计时和剩余时间，无声、不可划掉",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}


/**
 * 自由断食卡片（2026-11-11 专项）。
 *
 * 与上面的「方案断食」互不影响：这里只认一个开始时间戳（存在 DataStore，
 * 进程被杀再打开也能接着算），断了多久 = 现在 - 开始，所以跨日、跨多日都对。
 * 开始 / 结束都会 sync 常驻通知，两边状态永远一致。
 */
@Composable
internal fun FastingFreeCard(
    freeStart: Long,
    message: String,
    onStartNow: () -> Unit,
    onPickStart: () -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    val running = freeStart > 0L
    // 预约状态：开始时间在未来。这时不显示计时，显示「还有多久开始」
    val scheduled = running && FastingRules.freeIsScheduled(freeStart, System.currentTimeMillis())
    var elapsedSeconds by remember(freeStart) { mutableStateOf(0) }
    LaunchedEffect(freeStart) {
        while (freeStart > 0L) {
            elapsedSeconds = ((System.currentTimeMillis() - freeStart) / 1000L).toInt().coerceAtLeast(0)
            delay(1000L)
        }
        elapsedSeconds = 0
    }

    SectionCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "自由断食",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            when {
                scheduled -> {
                    Text(
                        text = "预约中 · ${FastingRules.freeScheduleLabel(freeStart, System.currentTimeMillis())}",
                        fontSize = 12.sp,
                        color = WarnOrange
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "到点自动开始计时并更新常驻通知，不需要守着",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                running -> {
                    val minutes = elapsedSeconds / 60
                    Text(
                        text = "已断食 ${TimeFormat.clock(elapsedSeconds)} · " +
                            FastingRules.phase(minutes / 60.0).first,
                        fontSize = 12.sp,
                        color = QingJiGreenDark
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "开始于 ${startLabel(freeStart)}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                else -> {
                    Text(
                        text = "不预设方案；可约未来 7 天内的开始时间，结束后记进历史",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            scheduled -> PillText(
                text = "取消预约",
                filled = false,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel
            )
            running -> PillText(
                text = "结束并保存",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnd
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillText(
                    text = "现在开始",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onStartNow
                )
                PillText(
                    text = "自定义时间",
                    filled = false,
                    modifier = Modifier.weight(1f),
                    onClick = onPickStart
                )
            }
        }

        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = message, fontSize = 11.sp, color = QingJiGreenDark)
        }
    }
}


/**
 * 未开启时的方案预览（2026-11-11 改版）。
 *
 * 以前这里只有一句「点右上角设置选个方案」：看不到具体时间，也不知道开完会怎样。
 * 现在直接把方案和时间摆出来，一个按钮开启，并写明「开启后全自动、不用手动」。
 */
@Composable
internal fun FastingPreviewCard(
    planHours: Int,
    eatStartMinute: Int,
    eatEndMinute: Int,
    onEnable: () -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${FastingRules.planLabel(planHours)} 方案",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "未开启", fontSize = 11.sp, color = WarnOrange)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "断食开始时间", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "今天 ${FastingRules.minuteLabel(eatEndMinute)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Text(text = "→", fontSize = 18.sp, color = QingJiGreen)
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "可进食时间", fontSize = 11.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "明天 ${FastingRules.minuteLabel(eatStartMinute)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = QingJiGreenDark
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "开启后按这个时间自动开始和结束，到点前会提醒，不需要你手动操作；" +
                "吃饭时间想改，右上角「设置」里能调。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(QingJiGreen)
                .clickable { onEnable() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "开启轻断食",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}


/**
 * 断食方案卡（2026-11-12 从设置弹窗里提出来）：点一颗直接切换并保存。
 * 进食窗口 / 生效星期是精确数字，仍保留在右上角的设置弹窗里。
 */
@Composable
internal fun FastingPlanCard(
    planHours: Int,
    onSelect: (Int) -> Unit
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "断食方案",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(text = "进食窗口在右上角「设置」里调", fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FastingRules.PLANS.forEach { hours ->
                val selected = hours == planHours
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) QingJiGreenLight else PageBackground)
                        .clickable { onSelect(hours) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = FastingRules.planLabel(hours),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) QingJiGreenDark else TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = FastingRules.planSubtitle(hours),
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}


@Composable
internal fun NotesCard() {
    val notes = listOf(
        "充分补水" to "优先喝白水；无糖茶、黑咖啡也可以，别喝含糖饮料。",
        "关注电解质" to "头晕乏力可少量补点海盐或电解质；仍不适就结束断食。",
        "规律作息" to "睡够、作息固定，能明显减轻饥饿感和白天疲劳。",
        "轻度活动" to "散步、拉伸、瑜伽这类低强度活动更合适，别硬上高强度训练。",
        "守住断食边界" to "断食期间不要摄入含糖饮品、零食，否则整段断食白费。",
        "识别危险信号" to "持续头晕、心悸、出冷汗、剧烈饥饿伴抖动时请立即结束并就医。"
    )

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🛡", fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "断食期间注意事项",
                fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.TinyCorner))
                .background(QingJiGreenLight)
                .padding(10.dp)
        ) {
            Text(
                text = "断食期间若感到明显不适，请立即结束断食；症状持续或加重时请及时就医，别勉强坚持。",
                fontSize = 11.sp,
                color = QingJiGreenDark
            )
        }
        Spacer(Modifier.height(10.dp))
        notes.forEachIndexed { index, (title, detail) ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row {
                Text(text = "·", fontSize = 13.sp, color = QingJiGreen)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Text(text = detail, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}
