package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.qingji.app.domain.MealRules
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * M1 新增页面共用的小零件
 * ------------------------------------------------------------
 * 为什么不直接用 Material3 的 Chip / TopAppBar：
 * 那些 API 在部分版本上还带着「实验性」标记，要到处加 @OptIn，
 * 而且样式跟 M0 已经画好的页面不统一。这里自己画，代码多点但
 * 版本无关、不会突然编译不过，颜色也和首页完全一致。
 * ============================================================
 */

/** 统一的子页面标题栏：左边返回箭头，中间标题，右边可放一个按钮。 */
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    actionText: String? = null,
    onAction: () -> Unit = {},
    showBack: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(CardWhite)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        if (actionText != null) {
            Text(
                text = actionText,
                fontSize = 14.sp,
                color = QingJiGreenDark,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** 主色实心按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // 按下时轻微缩小：这是全 App 主要操作按钮的统一按压反馈
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = com.qingji.app.ui.theme.Motion.bouncy(),
        label = "primaryPress"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = QingJiGreen,
            contentColor = Color.White,
            disabledContainerColor = DividerColor,
            disabledContentColor = TextSecondary
        )
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 带说明文字的输入框。数字输入要传 numberOnly = true，省得弹出全键盘。 */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    numberOnly: Boolean = false,
    decimalOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 传空字符串表示「不要标题」（多行输入框常用），别留一块空白
        if (label.isNotEmpty()) {
            Text(text = label, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, fontSize = 14.sp, color = TextSecondary) },
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    decimalOnly -> KeyboardType.Decimal
                    numberOnly -> KeyboardType.Number
                    else -> KeyboardType.Text
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                focusedIndicatorColor = QingJiGreen,
                unfocusedIndicatorColor = DividerColor,
                cursorColor = QingJiGreen
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 四选一的餐次选择条：早餐 / 午餐 / 晚餐 / 加餐。 */
@Composable
fun MealSelector(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MealRules.ALL.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) QingJiGreen else PageBackground)
                    .clickable { onSelect(type) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = MealRules.label(type),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

/** 一行浅绿底的提示文字，用来放错误说明、操作提示。 */
@Composable
fun HintBox(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isError) Color(0xFFFDECEA) else QingJiGreenLight)
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (isError) Color(0xFFC0392B) else QingJiGreenDark
        )
    }
}
