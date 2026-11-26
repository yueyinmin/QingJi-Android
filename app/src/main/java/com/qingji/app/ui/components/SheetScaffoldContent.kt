package com.qingji.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable

/**
 * ============================================================
 * 弹层「标题固定 + 中间可滚 + 底部固定」三段式骨架
 * （QJ-RECORD-INPUT-UNIFIED-V3 / B3·B4）
 * ------------------------------------------------------------
 * 为什么不用 imePadding：
 * 项目用的是 Material3 1.3.1，ModalBottomSheet 内部已经做了
 * `fillMaxSize().imePadding()` 且 API 30+ 走 ADJUST_NOTHING。
 * 在内容里再叠一层 imePadding，就会在键盘上方多出一条
 * 「导航栏高度」的空白 —— 这是审计里明确点名要避开的坑。
 * 这里只负责把内容区从「无限高」收成「剩余高度」，
 * 让 verticalScroll 真正能滚，而 Insets 交给 sheet 自己处理。
 *
 * 实现选型（工单 5.3 要求先评估最简方案）：
 * Column + weight(fill = false) + verticalScroll 就够用，
 * 没有引入 SubcomposeLayout —— 后者要自己测量剩余空间，
 * 维护成本高，而这里只需要「内容短就短、内容长就滚」。
 * ============================================================
 */
@Composable
fun SheetScaffoldContent(
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
    ) {
        header()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // fill = false：内容不足时不硬撑满，内容超长时被限制在剩余高度内滚动
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
        footer()
    }
}

/**
 * 弹层底部操作条（工单 7.4）。
 *
 * 两个主操作并排（取消 / 确认），第三个入口（记录饮食详情、查看饮水详情）
 * 独占一行做成描边按钮 —— 和饮水弹层原来的结构一致，食物弹层照此复用。
 * 主体按钮文字用 maxLines = 1 + 自适应字号，任何屏幕/字体倍率下都不折行。
 */
@Composable
fun SheetActionBar(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    cancelText: String = "取消",
    onCancel: () -> Unit = {},
    /** 第三入口（可选）：整行描边的次级操作，例如「记录饮食详情」 */
    tertiaryText: String? = null,
    tertiaryEnabled: Boolean = true,
    onTertiary: (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(QingJiGreenLight)
                    .pressable { onCancel() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cancelText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = QingJiGreenDark,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.6f)
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (confirmEnabled) QingJiGreen else QingJiGreen.copy(alpha = 0.4f))
                    .pressable(enabled = confirmEnabled) { onConfirm() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = confirmText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
        if (tertiaryText != null && onTertiary != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.ItemCorner))
                    .background(if (tertiaryEnabled) CardWhite else PageBackground)
                    .pressable(enabled = tertiaryEnabled) { onTertiary() }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tertiaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = QingJiGreenDark,
                    maxLines = 1
                )
            }
        }
    }
}

/** 弹层里的小节标题（统一字号与颜色，避免每处各写 12sp/13sp）。 */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = modifier
    )
}
