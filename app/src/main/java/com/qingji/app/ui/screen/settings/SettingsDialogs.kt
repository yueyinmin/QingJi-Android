package com.qingji.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 设置页用的一堆小弹窗（M7）
 * ------------------------------------------------------------
 * 为什么不用新页面：设置项都是一两个值，弹窗比跳页快，
 * 返回也不用多按一次。全部用 Material3 的 AlertDialog，
 * 内容区统一限高 + 可滚动，选项多（比如 24 个小时）也不会爆屏。
 * ============================================================
 */

/** 单选：一组选项里挑一个。options 是「值 to 显示名」。 */
@Composable
fun OptionPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFFE8F5EF) else Color.Transparent)
                            .clickable { onPick(value) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            color = if (isSelected) QingJiGreen else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Text(text = "✓", fontSize = 14.sp, color = QingJiGreen)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) }
        }
    )
}

/** 数字输入（饮水目标、小时、克数都用它）。 */
@Composable
fun NumberInputDialog(
    title: String,
    hint: String,
    initial: String,
    suffix: String = "",
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column {
                Text(hint, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = "" },
                    singleLine = true,
                    suffix = if (suffix.isBlank()) null else {
                        { Text(suffix, fontSize = 13.sp, color = TextSecondary) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, fontSize = 12.sp, color = DangerRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = text.trim().toIntOrNull()
                if (value == null) {
                    error = "请输入数字"
                } else {
                    onConfirm(value)
                }
            }) { Text("保存", color = QingJiGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/** 文字输入（食谱偏好、昵称）。 */
@Composable
fun TextInputDialog(
    title: String,
    hint: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    singleLine: Boolean = false
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column {
                Text(hint, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存", color = QingJiGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/** 三大营养素比例：三个数字框 + 合计提示。 */
@Composable
fun MacroRatioDialog(
    proteinInit: Int,
    carbInit: Int,
    fatInit: Int,
    onConfirm: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var protein by remember { mutableStateOf(proteinInit.toString()) }
    var carb by remember { mutableStateOf(carbInit.toString()) }
    var fat by remember { mutableStateOf(fatInit.toString()) }

    val p = protein.toIntOrNull() ?: 0
    val c = carb.toIntOrNull() ?: 0
    val f = fat.toIntOrNull() ?: 0
    val total = p + c + f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("营养素分配方案", fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    text = "按占总热量的百分比填。常见配比：均衡 25/50/25，高蛋白 35/40/25。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                MacroField("蛋白质", protein) { protein = it }
                Spacer(Modifier.height(8.dp))
                MacroField("碳水", carb) { carb = it }
                Spacer(Modifier.height(8.dp))
                MacroField("脂肪", fat) { fat = it }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前合计 $total%（建议 100%）",
                    fontSize = 12.sp,
                    color = if (total == 100) QingJiGreen else DangerRed
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(p, c, f) },
                enabled = total in 50..150
            ) { Text("保存并重算", color = QingJiGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

@Composable
private fun MacroField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.length <= 3 && input.all { it.isDigit() }) onChange(input)
            },
            singleLine = true,
            suffix = { Text("%", fontSize = 12.sp, color = TextSecondary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(0.45f)
        )
    }
}

/** 纯说明弹窗（应用图标、关于、小组件引导都用它）。 */
@Composable
fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(body, fontSize = 13.sp, color = TextPrimary, lineHeight = 21.sp)
            }
        },
        confirmButton = {
            if (actionText != null && onAction != null) {
                // 注意：这里只执行动作，不自动关弹窗。
                // 以前写的是 { onAction(); onDismiss() }，结果「清空本地记录」点下去
                // 动作刚把二次确认设成显示，紧跟着就被 onDismiss 关掉了 —— 看起来就像点了没反应。
                // 什么时候关，交给调用方决定。
                TextButton(onClick = { onAction() }) {
                    Text(actionText, color = QingJiGreen)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("知道了", color = QingJiGreen) }
            }
        },
        dismissButton = if (actionText != null) {
            { TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) } }
        } else null
    )
}

/** 二次确认（清数据这种不可撤销的操作必须走它）。 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp) },
        text = { Text(body, fontSize = 13.sp, color = TextPrimary, lineHeight = 21.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) DangerRed else QingJiGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
        }
    )
}

/** 设置页里的一行：左边标题+副标题，右边当前值 + 箭头。 */
@Composable
fun SettingValueRow(
    title: String,
    subtitle: String,
    value: String,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(iconColor.copy(alpha = 0.14f))
                .padding(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconColor)
                    .padding(6.dp)
            ) {}
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(text = title, fontSize = 14.sp, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = QingJiGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE8F5EF))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = "›", fontSize = 18.sp, color = Color(0xFFEEF0F2))
    }
}

/** 设置页分组标题。 */
@Composable
fun GroupTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
    )
}

/** 分组里的分隔线（左侧留白，和图标对齐）。 */
@Composable
fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 58.dp)
            .height(1.dp)
            .background(Color(0xFFEEF0F2))
    )
}

/** 卡片里的一行开关。 */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextPrimary)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/** 卡片外形给设置页复用。 */
@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
    ) {
        content()
    }
}

/** 提示条：说明这段设置的含义，避免用户瞎点。 */
@Composable
fun SettingsNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PageBackground)
            .padding(10.dp)
    ) {
        Text(text = text, fontSize = 11.sp, color = TextSecondary, lineHeight = 17.sp)
    }
}

/** 小时选项（提醒时间用）。 */
fun hourOptions(): List<Pair<String, String>> = (5..23).map { "$it" to "$it:00" }

/** 星期选项。 */
fun weekdayOptions(): List<Pair<String, String>> = listOf(
    "1" to "周一", "2" to "周二", "3" to "周三", "4" to "周四",
    "5" to "周五", "6" to "周六", "7" to "周日"
)
