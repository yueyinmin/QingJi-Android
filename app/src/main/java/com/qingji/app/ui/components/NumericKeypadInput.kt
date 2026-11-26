package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import com.qingji.app.domain.input.NumericEmptyPolicy
import com.qingji.app.domain.input.NumericInputState
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.pressable

/**
 * 统一数字录入面板（QJ-FOOD-NUMERIC-PAD-V1 → QJ-RECORD-INPUT-UNIFIED-V3）。
 *
 * 职责边界：只管输入交互与展示，不保存记录、不算营养、不依赖 ViewModel。
 *
 * V3 变化：
 * · 新增以 [NumericInputState] 为状态的版本 —— 点数字区进入编辑（等价全选），
 *   第一下按数字直接替换原值（100 → 打 1 就是 1，不会变成 1001）；
 * · 旧的 String 版本保留为薄包装，行为与之前一致（追加式），
 *   方便还没迁移的调用点继续用。
 *
 * @param unit 单位文字（克 / 份 / 毫升等，由调用方按营养基准传入）
 * @param step 加减步长（克数默认 10g，由调用方配置，组件不写死）
 */
@Composable
fun NumericKeypadInput(
    state: NumericInputState,
    onStateChange: (NumericInputState) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "克",
    min: Double = 1.0,
    max: Double = 3000.0,
    step: Double = 10.0,
    decimals: Int = state.decimals
) {
    NumericKeypadInputImpl(
        state = state,
        onStateChange = onStateChange,
        unit = unit,
        min = min,
        max = max,
        step = step,
        decimals = decimals,
        modifier = modifier
    )
}

/** 兼容旧调用：String 受控版本（不会自动全选，追加式输入）。 */
@Composable
fun NumericKeypadInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String = "克",
    min: Double = 1.0,
    max: Double = 3000.0,
    step: Double = 10.0,
    decimals: Int = 1,
    modifier: Modifier = Modifier
) {
    val state = NumericInputState(
        min = min,
        max = max,
        decimals = decimals,
        emptyPolicy = NumericEmptyPolicy.NORMALIZE,
        emptyFallback = min,
        originalValue = min,
        committedValue = min,
        text = value
    )
    NumericKeypadInputImpl(
        state = state,
        onStateChange = { onValueChange(it.text) },
        unit = unit,
        min = min,
        max = max,
        step = step,
        decimals = decimals,
        modifier = modifier
    )
}

@Composable
private fun NumericKeypadInputImpl(
    state: NumericInputState,
    onStateChange: (NumericInputState) -> Unit,
    unit: String,
    min: Double,
    max: Double,
    step: Double,
    decimals: Int,
    modifier: Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ---------- 数量显示区：−  数字+单位  + ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QingJiGreenLight, RoundedCornerShape(Dimens.ItemCorner))
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KeypadSymbolButton(text = "−") { onStateChange(state.applyDelta(-step)) }
            // 点数字区进入编辑态：第一下按数字直接覆盖，不用先删掉原值
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (state.isEditing) CardWhite else Color.Transparent)
                    .pressable { onStateChange(state.beginEdit()) }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.text.ifEmpty { "0" },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = QingJiGreenDark
                    )
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(
                        text = unit,
                        fontSize = 13.sp,
                        color = QingJiGreenDark,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }
            KeypadSymbolButton(text = "+") { onStateChange(state.applyDelta(step)) }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- 三列四行数字键盘 ----------
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "back")
        )
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f)) {
                        when (key) {
                            "back" -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(CardWhite, RoundedCornerShape(10.dp))
                                    .pressable { onStateChange(state.onBackspace()) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "退格",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            "." -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(CardWhite, RoundedCornerShape(10.dp))
                                    .pressable { onStateChange(state.onDot()) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("·", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            else -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(CardWhite, RoundedCornerShape(10.dp))
                                    .pressable {
                                        // 还没进入编辑时，第一下按数字也按「替换」处理
                                        val effective = if (state.isEditing) state else state.beginEdit()
                                        onStateChange(effective.onDigit(key[0]))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeypadSymbolButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(CardWhite, RoundedCornerShape(10.dp))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = QingJiGreenDark)
    }
}
