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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

/**
 * 二次确认（清数据这种不可撤销的操作必须走它）。
 *
 * v3.5.0：容器从裸 Material AlertDialog 迁到全 App 统一的 AppDialog
 * （20dp 圆角、CardWhite 底、胶囊按钮），签名保持不变，调用点零改动。
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true
) {
    com.qingji.app.ui.components.AppDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = "取消",
        danger = danger
    ) {
        Text(body, fontSize = 13.sp, color = TextPrimary, lineHeight = 21.sp)
    }
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
