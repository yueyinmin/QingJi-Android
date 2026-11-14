package com.qingji.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.components.AppBottomSheet
import com.qingji.app.ui.components.HintBox
import com.qingji.app.ui.components.PageTopBar
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * ============================================================
 * 设置二级页（v2.1）
 * ------------------------------------------------------------
 * 为什么要有这个文件：
 * 原来设置里点一项是「弹一个 AlertDialog」，而饮食偏好、桌面小组件
 * 又是「进一整个页面」。同一个设置列表里两种交互混着，
 * 用户一眼就能看出风格不齐。
 *
 * 现在统一成：点进去就是一个完整页面，顶栏有返回、下面是卡片。
 * 和主设置页、和 App 里其它页面完全同一套视觉。
 *
 * 这里放 4 个通用壳子，覆盖所有设置项：
 * - Option：一排选项里选一个（主题、单位、时区…）
 * - Toggle：多个开关并列（首页显示、底部 Tab…）
 * - Number：填一个数字（饮水目标…）
 * - Info：纯说明（关于、版本、隐私…）
 * ============================================================
 */

/**
 * 二级页骨架：顶栏返回 + 可滚动内容。
 *
 * v1.0.7：`useSheet = true` 时换成「从底部浮起的操作面板」，和轻断食
 * 设置（FastingSettingsDialog 用的 AppBottomSheet）同一套形态。
 * 设置里所有二级项都用这一套，不再整屏翻页；条目多的在弹层里滚动。
 * 内容区完全复用，交互也保持「点一下就生效」，只是容器换了。
 */
@Composable
fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String = "",
    useSheet: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (useSheet) {
        SettingsDetailSheet(title = title, subtitle = subtitle, onDismiss = onBack) {
            content()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
    ) {
        PageTopBar(title = title, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding)
        ) {
            Spacer(Modifier.height(Dimens.Gap))
            content()
            Spacer(Modifier.height(Dimens.FabSpace))
        }
    }
}

/**
 * 底部弹层壳子（设置二级项通用）。
 *
 * 和轻断食设置同款：白色圆角卡片、顶部小横条、标题、轻遮罩，
 * 下滑或点外面关闭。
 *
 * 高度跟内容走（wrapContent）：只有两个选项时弹层就只有两行高，
 * 不会出现下方一大片空白；最多长到 420dp，超了才在弹层内部滚动，
 * 这样「关于」这类长说明也不会把弹层撑满整屏。
 * 另外跳过 Material 默认的「半屏」档（skipPartiallyExpanded）：
 * 营养素 / 通知提醒这类表单底部有「保存」按钮，半屏会把它挤到屏幕外。
 * 壳子本身不设「保存」主按钮 —— 点选即生效的设置项不加保存，
 * 需要保存的表单由页面自己在内容里放按钮。
 */
@Composable
fun SettingsDetailSheet(
    title: String,
    subtitle: String = "",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppBottomSheet(
        title = title,
        subtitle = subtitle.takeIf { it.isNotBlank() },
        // 设置二级项里有表单（营养素 / 通知提醒），底部带「保存」按钮；
        // 默认的「半屏」档会把主按钮挤到屏幕外，所以一打开就展开到位。
        skipPartiallyExpanded = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
    }
}

/** 一个选项：key 给代码用，label 给人看，hint 是右侧补充说明。 */
data class OptionItem(
    val key: String,
    val label: String,
    val hint: String = ""
)

/** 单选型二级页：一排选项里挑一个，选中打勾。 */
@Composable
fun OptionDetailPage(
    title: String,
    subtitle: String,
    options: List<OptionItem>,
    selectedKey: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(
        title = title,
        onBack = onBack,
        subtitle = if (useSheet) subtitle else "",
        useSheet = useSheet
    ) {
        if (subtitle.isNotBlank() && !useSheet) {
            HintBox(text = subtitle)
            Spacer(Modifier.height(Dimens.Gap))
        }

        SectionCard(contentPadding = 0.dp) {
            options.forEachIndexed { index, option ->
                val selected = option.key == selectedKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option.key) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            fontSize = 15.sp,
                            color = if (selected) QingJiGreenDark else TextPrimary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (option.hint.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(text = option.hint, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    if (selected) {
                        Text(text = "✓", fontSize = 17.sp, color = QingJiGreen)
                    }
                }
                if (index < options.lastIndex) RowDivider()
            }
        }
    }
}

/** 一个开关项。 */
data class ToggleItem(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit
)

/** 多个开关并列的二级页（首页显示、底部 Tab 这种）。 */
@Composable
fun ToggleDetailPage(
    title: String,
    subtitle: String,
    items: List<ToggleItem>,
    onBack: () -> Unit,
    /** 列表下方额外的自定义内容（比如气泡量选择器） */
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(
        title = title,
        onBack = onBack,
        subtitle = if (useSheet) subtitle else "",
        useSheet = useSheet
    ) {
        if (subtitle.isNotBlank() && !useSheet) {
            HintBox(text = subtitle)
            Spacer(Modifier.height(Dimens.Gap))
        }

        SectionCard(contentPadding = 0.dp) {
            items.forEachIndexed { index, item ->
                SwitchRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    checked = item.checked,
                    onCheckedChange = item.onToggle
                )
                if (index < items.lastIndex) RowDivider()
            }
        }

        if (extra != null) {
            Spacer(Modifier.height(Dimens.Gap))
            SectionCard { extra() }
        }
    }
}

/** 纯说明型二级页。 */
@Composable
fun InfoDetailPage(
    title: String,
    body: String,
    onBack: () -> Unit,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    useSheet: Boolean = false
) {
    SettingsDetailScaffold(title = title, onBack = onBack, useSheet = useSheet) {
        SectionCard {
            Text(
                text = body,
                fontSize = 13.sp,
                color = TextPrimary,
                lineHeight = 21.sp
            )
        }

        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Dimens.Gap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(QingJiGreenLight)
                    .pressable { onAction() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = QingJiGreenDark
                )
            }
        }
    }
}

/** 填一个数字的二级页。 */
@Composable
fun NumberDetailPage(
    title: String,
    subtitle: String,
    label: String,
    initial: String,
    suffix: String,
    hint: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
    useSheet: Boolean = false
) {
    // 本地先存着，点「保存」才写进去：避免输入到一半（比如清空了）就把 0 存下来
    var text by remember { mutableStateOf(initial) }

    SettingsDetailScaffold(
        title = title,
        onBack = onBack,
        subtitle = if (useSheet) subtitle else "",
        useSheet = useSheet
    ) {
        if (subtitle.isNotBlank() && !useSheet) {
            HintBox(text = subtitle)
            Spacer(Modifier.height(Dimens.Gap))
        }

        SectionCard {
            Text(text = label, fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.weight(1f)
                )
                if (suffix.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Text(text = suffix, fontSize = 14.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = hint, fontSize = 11.sp, color = TextSecondary)
        }

        Spacer(Modifier.height(Dimens.Gap))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.ItemCorner))
                .background(QingJiGreen)
                .pressable {
                    onSave(text)
                    onBack()
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "保存", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

/** 一行「文字 + 右侧值」的展示，二级页里当只读信息用。 */
@Composable
fun DetailValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = TextPrimary)
        Text(text = value, fontSize = 13.sp, color = TextSecondary)
    }
}

/** 二级页里的分组小标题。 */
@Composable
fun DetailGroupTitle(text: String) {
    Text(
        text = text,
        fontSize = TextSizes.Caption,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

/** 危险操作按钮（清数据这种）。 */
@Composable
fun DangerActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.ItemCorner))
            // 深浅自适应：浅色是淡红底，深色下用红色 18% 透明，不会突然亮一块
            .background(
                if (com.qingji.app.ui.theme.QingJiThemeState.darkMode)
                    Color(0xFFE5544B).copy(alpha = 0.18f)
                else Color(0xFFFDECEC)
            )
            .pressable { onClick() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = com.qingji.app.ui.theme.DangerRed
        )
    }
}
