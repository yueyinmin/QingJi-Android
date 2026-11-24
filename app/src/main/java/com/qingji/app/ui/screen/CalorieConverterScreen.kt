package com.qingji.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.CalorieConverter
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * 卡路里换算页（2026-11-24）：按截图实现。
 * 输入重量(g)与标签上的千焦/100g，自动算出总大卡；顶栏「清除」一键清空。
 */
@Composable
fun CalorieConverterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var weight by remember { mutableStateOf("") }
    var kj by remember { mutableStateOf("") }

    val kcal = CalorieConverter.kcal(
        weight.toDoubleOrNull() ?: 0.0,
        kj.toDoubleOrNull() ?: 0.0
    )

    Column(modifier = modifier.fillMaxSize().background(PageBackground)) {
        PageTopBar(
            title = "卡路里转换",
            onBack = onBack,
            actionText = "清除",
            onAction = { weight = ""; kj = "" }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(12.dp))

            // 重量卡片：# 图标 + g 单位
            SectionCard {
                RowWithLabel(
                    icon = { Text("#", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = QingJiGreenDark) },
                    label = "重量",
                    unit = "g"
                )
                Spacer(Modifier.height(10.dp))
                NumberField(value = weight, onValueChange = { weight = it }, placeholder = "请输入重量")
            }

            Spacer(Modifier.height(12.dp))

            // 千焦卡片：火焰图标 + /100g 单位
            SectionCard {
                RowWithLabel(
                    icon = {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFE5A044),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = "千焦 (kJ)",
                    unit = "/100g"
                )
                Spacer(Modifier.height(10.dp))
                NumberField(
                    value = kj,
                    onValueChange = { kj = it },
                    placeholder = "请输入数值",
                    largePlaceholder = true
                )
            }

            Spacer(Modifier.height(12.dp))

            // 结果卡片：有输入才显示数字，否则给空状态说明
            SectionCard {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    if (kcal != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = CalorieConverter.format(kcal),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(text = "大卡 (kcal)", fontSize = TextSizes.Caption, color = TextSecondary)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFFB8BEC4),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("请输入重量和千焦值", fontSize = TextSizes.Body, color = TextSecondary)
                            Text(
                                "系统将自动计算总卡路里",
                                fontSize = TextSizes.Tiny,
                                color = Color(0xFFB8BEC4)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 公式说明
            SectionCard {
                Text("转换公式", fontSize = TextSizes.CardTitle,
                    fontWeight = FontWeight.SemiBold, color = QingJiGreenDark)
                Spacer(Modifier.height(8.dp))
                Text("1 大卡 (kcal) = 4.184 千焦 (kJ)", fontSize = TextSizes.Body, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text("总大卡 = (重量 × 千焦/100g) ÷ 100 ÷ 4.184", fontSize = TextSizes.Body, color = TextSecondary)
            }
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

@Composable
private fun RowWithLabel(
    icon: @Composable () -> Unit,
    label: String,
    unit: String
) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(QingJiGreenLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.padding(start = 10.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.padding(start = 6.dp))
        Text(unit, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    largePlaceholder: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(
            fontSize = if (largePlaceholder) 24.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        ),
        placeholder = {
            Text(
                placeholder,
                fontSize = if (largePlaceholder) 24.sp else 16.sp,
                color = Color(0xFFC4C9CE)
            )
        },
        shape = RoundedCornerShape(Dimens.ItemCorner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PageBackground,
            unfocusedContainerColor = PageBackground,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
