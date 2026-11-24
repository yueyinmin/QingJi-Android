package com.qingji.app.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.NumericKeypadLogic
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.pressable

/**
 * 饮水弹层专用的整数数字键盘（QJ-WATER-SHEET-V2.1 第 3.2 节）。
 *
 * 三列四行：1-9 / 完成-0-⌫；无小数点、无运算符、不唤起系统输入法。
 * 输入逻辑复用 NumericKeypadLogic（decimals=0 即整数模式），
 * 只负责 UI 与交互，不写库、不校验最终值（由调用方点完成时校验）。
 */
@Composable
internal fun WaterNumberKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("done", "0", "back")
    )
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val onClick: () -> Unit = when (key) {
                        "done" -> onDone
                        "back" -> ({ onValueChange(NumericKeypadLogic.backspace(value)) })
                        else -> ({ onValueChange(NumericKeypadLogic.appendDigit(value, key[0], decimals = 0)) })
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (key == "done") QingJiGreenLight else CardWhite)
                            .pressable(onClick = onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "done" -> Text(
                                "完成", fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, color = TextPrimary
                            )
                            "back" -> Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "退格",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            else -> Text(
                                key, fontSize = 20.sp,
                                fontWeight = FontWeight.Medium, color = TextPrimary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
