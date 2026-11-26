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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppClock
import com.qingji.app.domain.WaterQuickAdd
import com.qingji.app.domain.input.NumericEmptyPolicy
import com.qingji.app.domain.input.NumericInputState
import com.qingji.app.ui.components.RecordDateDialog
import com.qingji.app.ui.components.RecordDateTimeRow
import com.qingji.app.ui.components.RecordTimeDialog
import com.qingji.app.ui.components.SheetActionBar
import com.qingji.app.ui.components.SheetScaffoldContent
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.MultiColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.viewmodel.HomeUiState
import java.time.LocalDate

/** 饮水弹层的默认待记录水量（工单沿用 200ml）。 */
private const val DEFAULT_WATER_ML = 200


/**
 * 记录饮水（V2 工单 · 确认式）：首页水杯、四个一级页面的悬浮水滴、
 * 「＋」面板的喝水入口共用这一个弹层。
 *
 * 关键行为（工单第六部分）：
 * - 打开弹层**不写库**，只是把默认 200ml 摆出来；
 * - 快捷档位（100/200/300 累加）、[-][+]（±50）、数字键盘都只改「待记录」的数值，不落库；
 * - 只有点「确认记录」才回调 onAdd（一次），随后关闭；
 * - 点「取消」、下拉关闭、点遮罩都不写任何数据；
 * - submitted 标志防止极快的连续点击产生两条记录。
 *
 * onOpenDetail 传 null 时隐藏「查看今日饮水详情」——非首页的调用方
 * 没有对应的详情弹层，宁可不显示也不接一个空回调。
 * 容器色/遮罩/把手和 AppBottomSheet 完全一致，不写死任何蓝色。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuickWaterSheet(
    waterMl: Int,
    goalMl: Int,
    onAdd: (Int, LocalDate, java.time.LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onOpenDetail: (() -> Unit)? = null
) {
    // QJ-RECORD-INPUT-UNIFIED-V3 / B3：待记录水量交给统一数字状态机。
    // min = 1 沿用 WaterQuickAdd 口径；空值策略 = 恢复原值 ——
    // 清空后「完成」/失焦回到 200，绝不把空值偷偷变成 1ml（旧实现的毛病）。
    var mlState by remember {
        mutableStateOf(
            NumericInputState.of(
                value = DEFAULT_WATER_ML.toDouble(),
                min = WaterQuickAdd.MIN_ML.toDouble(),
                max = WaterQuickAdd.MAX_ML.toDouble(),
                decimals = 0,
                emptyPolicy = NumericEmptyPolicy.RESTORE_ORIGINAL,
                emptyFallback = DEFAULT_WATER_ML.toDouble()
            )
        )
    }
    var submitted by remember { mutableStateOf(false) }
    // QJ-HEALTH-DATE-WATER-004：日期默认今天、时间默认当前（走 AppClock 时区）；
    // 用户可用滚轮改历史日期与具体时间，取消不写库。
    var pendingDate by remember { mutableStateOf(AppClock.today()) }
    var pendingTime by remember { mutableStateOf(AppClock.now().toLocalTime().withSecond(0).withNano(0)) }
    var showDateWheel by remember { mutableStateOf(false) }
    var showTimeWheel by remember { mutableStateOf(false) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 一打开就展开到内容需要的高度：默认的半屏会把底部操作区挤出屏幕
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = CardWhite,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.28f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DividerColor)
            )
        }
    ) {
        // 三段式：标题固定 / 中间可滚 / 底部固定。
        // Insets 由 ModalBottomSheet 自己消费，这里绝不再叠 imePadding。
        SheetScaffoldContent(
            header = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💧", fontSize = 18.sp)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = s("记录饮水"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "已喝 $waterMl / 目标 ${goalMl}ml",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(14.dp))
            },
            content = {

            // ---------- 当前待记录的水量（大字 + 加减） ----------
            Text(text = s("今日饮水"), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QingJiGreenLight)
                        .pressable {
                            // 加减沿用 WaterQuickAdd.step 的 1..3000 夹紧口径，不重写业务规则；
                            // 基准值取当前文本，文本为空时退回最近一次确认值（不会从 0 起算）。
                            val base = mlState.value?.toInt() ?: mlState.committedValue.toInt()
                            mlState = mlState.setValue(WaterQuickAdd.step(base, -50).toDouble())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 20.sp, color = QingJiGreenDark)
                }
                // 中间数字整体可点：进入编辑态并自动全选，直接打 350 就是 350（不再是 200350）
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (mlState.isEditing) QingJiGreenLight else Color.Transparent)
                        .pressable { mlState = mlState.beginEdit() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mlState.text.ifEmpty { "0" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MultiColor.WaterText
                    )
                    Text("ml", fontSize = 12.sp, color = TextSecondary)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(QingJiGreenLight)
                        .pressable {
                            val base = mlState.value?.toInt() ?: mlState.committedValue.toInt()
                            mlState = mlState.setValue(WaterQuickAdd.step(base, 50).toDouble())
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 20.sp, color = QingJiGreenDark)
                }
            }
            // 空值/越界时给明确反馈：确认按钮同步置灰，不写库、不静默失败
            if (!mlState.canSubmit) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = s("请输入 1–3000 之间的饮水量"),
                    fontSize = 11.sp,
                    color = com.qingji.app.ui.theme.DangerRed
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---------- 常用档位：QJ-WATER-SHEET-V2.1 的「累加」口径原样保留 ----------
            Text(text = s("常用饮水量"), fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 200, 300).forEach { ml ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QingJiGreenLight)
                            .pressable {
                                // 累加到当前待记录值（WaterQuickAdd 负责 1..3000 夹紧与非法基准兜底）
                                mlState = mlState.setValue(WaterQuickAdd.add(mlState.text, ml).toDouble())
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$ml ml",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = QingJiGreenDark,
                            maxLines = 1
                        )
                    }
                }
            }

            // ---------- 整数数字键盘，仅编辑态显示；点「完成」即归一化退出 ----------
            if (mlState.isEditing) {
                Spacer(Modifier.height(10.dp))
                WaterNumberKeypad(
                    value = mlState.text,
                    onValueChange = { mlState = mlState.onTextChange(it) },
                    // 完成时统一收尾：空值/越界恢复原值（不是 1ml），合法值提交
                    onDone = { mlState = mlState.endEdit() }
                )
            }

            Spacer(Modifier.height(12.dp))
            // 日期/时间统一走 RecordDateTimeRow（与食物记录弹层同一套组件与格式）
            RecordDateTimeRow(
                date = pendingDate,
                time = pendingTime,
                onPickDate = { showDateWheel = true },
                onPickTime = { showTimeWheel = true }
            )
            },
            footer = {
                // 固定底部：取消 / 确认。这是本弹层唯一的写库时机。
                SheetActionBar(
                    confirmText = s("确认记录"),
                    onConfirm = {
                        if (submitted) return@SheetActionBar
                        // 合法值才提交；按钮已按 canSubmit 置灰，这里再兜一层防重复
                        val committed = mlState.commit() ?: return@SheetActionBar
                        submitted = true
                        mlState = committed
                        onAdd(committed.committedValue.toInt(), pendingDate, pendingTime)
                        onDismiss()
                    },
                    confirmEnabled = mlState.canSubmit && !submitted,
                    cancelText = s("取消"),
                    onCancel = onDismiss,
                    tertiaryText = if (onOpenDetail != null) s("查看饮水详情") else null,
                    onTertiary = onOpenDetail?.let { open ->
                        {
                            onDismiss()
                            open.invoke()
                        }
                    }
                )
            }
        )
    }

    // ---------- 日期 / 时间滚轮：统一组件（禁未来 + 最近一年 + 非法日期拦截） ----------
    if (showDateWheel) {
        RecordDateDialog(
            initialDate = pendingDate,
            onDismiss = { showDateWheel = false },
            onConfirm = { picked ->
                pendingDate = picked
                showDateWheel = false
            }
        )
    }

    if (showTimeWheel) {
        RecordTimeDialog(
            initialTime = pendingTime,
            onDismiss = { showTimeWheel = false },
            onConfirm = { picked ->
                pendingTime = picked
                showTimeWheel = false
            }
        )
    }
}


/**
 * 一周日期条：参考吃有数的样式 —— 没选中的是虚线圆圈，选中那天
 * 变成主题浅色的圆角方块，下面带一颗主题色小点。
 * 颜色全部取主题色，切换主题时一起变。
 */
@Composable
internal fun DateStrip(
    state: HomeUiState,
    onSelect: (LocalDate) -> Unit
) {
    val weekLabels = listOf(s("周一"), s("周二"), s("周三"), s("周四"), s("周五"), s("周六"), s("周日"))
    val monday = state.date.minusDays((state.date.dayOfWeek.value - 1).toLong())
    val today = AppClock.today()

    // drawBehind 的 lambda 不是 @Composable，颜色要在这里先取出来。
    // 虚线圈不能用 DividerColor：它太淡，压在顶部浅色渐变上等于没画；
    // 选中块也不能用 QingJiGreenLight：和渐变背景同色，看不出方块。
    val dashColor = QingJiGreen.copy(alpha = 0.42f)
    val selectedBg = QingJiGreen.copy(alpha = 0.16f)
    val selectedText = QingJiGreenDark
    val dotColor = QingJiGreen

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekLabels.forEachIndexed { index, label ->
            val date = monday.plusDays(index.toLong())
            val selected = date == state.date
            val isFuture = date.isAfter(today)

            Column(
                modifier = Modifier
                    // 固定宽度：个位数（8）和两位数（18）原本宽度不同，
                    // 翻周时会左右抽动，固定住就不抖了
                    .width(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isFuture) { onSelect(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = when {
                        isFuture -> DividerColor
                        selected -> selectedText
                        else -> TextSecondary
                    }
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) selectedBg else Color.Transparent)
                        .then(
                            if (selected) {
                                Modifier
                            } else {
                                Modifier.drawBehind {
                                    drawCircle(
                                        color = dashColor,
                                        radius = (size.minDimension - 2.dp.toPx()) / 2,
                                        style = Stroke(
                                            width = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(
                                                floatArrayOf(6f, 5f)
                                            )
                                        )
                                    )
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${date.dayOfMonth}",
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isFuture -> DividerColor
                            selected -> selectedText
                            else -> TextPrimary
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) dotColor else Color.Transparent)
                )
            }
        }
    }
}
