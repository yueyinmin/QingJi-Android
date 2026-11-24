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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.NumericKeypadLogic
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.pressable

/**
 * 统一数字录入面板（QJ-FOOD-NUMERIC-PAD-V1）。
 *
 * 职责边界：只管输入交互与展示，不保存记录、不算营养、不依赖 ViewModel。
 * 受控状态 value: String，保留 "0." 这类中间态；最终合法性由调用方用
 * [NumericKeypadLogic.isValid] 校验后再提交。
 *
 * @param value 当前输入的字符串
 * @param onValueChange 数值变化回调
 * @param unit 单位文字（克 / 份 / 毫升等，由调用方按营养基准传入）
 * @param step 加减步长（克数默认 10g，由调用方配置，组件不写死）
 */
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
    fun applyStep(delta: Double) {
        onValueChange(NumericKeypadLogic.step(value, delta, min, max, decimals))
    }

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
            KeypadSymbolButton(text = "−") { applyStep(-step) }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value.ifEmpty { "0" },
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
            KeypadSymbolButton(text = "+") { applyStep(step) }
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
                                    .pressable { onValueChange(NumericKeypadLogic.backspace(value)) },
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
                                    .pressable { onValueChange(NumericKeypadLogic.appendDot(value, decimals)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("·", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            else -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .background(CardWhite, RoundedCornerShape(10.dp))
                                    .pressable { onValueChange(NumericKeypadLogic.appendDigit(value, key[0], decimals)) },
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
