package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.DangerRed
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 弹窗统一规范（v2.6）
 * ------------------------------------------------------------
 * 之前全 App 有二十多处弹窗，标题字号、按钮样式、圆角各写各的。
 * 现在收敛成两个标准组件，所有弹窗二选一：
 *
 * 1. **AppBottomSheet** —— 带输入框、有多个控件的操作面板
 *    （填克数、录运动、选时间…）。从底部浮起，键盘弹出来也不挤。
 * 2. **AppDialog** —— 确认、说明这类「看一眼、点一下」的轻量交互
 *    （删除确认、清缓存…）。居中小窗，快进快出。
 *
 * 共同样式：标题 18sp 粗体、正文 13sp、主按钮绿色胶囊、圆角统一。
 * ============================================================
 */

/** 底部弹层骨架：标题 + 副标题 + 内容 + 底部主按钮（可选）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String,
    subtitle: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    /** 校验失败不关弹窗的场景传 false（例如填错日期要用户改完再关） */
    closeOnConfirm: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 统一弹层配色：和 App 卡片同一个白，不用 Material 默认的灰紫容器
        containerColor = CardWhite,
        contentColor = TextPrimary,
        // 遮罩调淡：0.42 会把整页压成灰的，和 App 的浅色体系割裂；
        // 0.28 只压暗一点点，弹层开着也能看清后面的主题色。
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = com.qingji.app.ui.i18n.s(title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(text = com.qingji.app.ui.i18n.s(subtitle), fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            content()
            if (confirmText != null && onConfirm != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.ItemCorner))
                        .background(if (confirmEnabled) QingJiGreen else QingJiGreen.copy(alpha = 0.4f))
                        .pressable(enabled = confirmEnabled) {
                            onConfirm()
                            if (closeOnConfirm) onDismiss()
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = com.qingji.app.ui.i18n.s(confirmText),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** 居中确认弹窗骨架：标题 + 内容 + 主按钮 + 次按钮。 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    onConfirm: (() -> Unit)? = null,
    dismissText: String = "取消",
    danger: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = CardWhite,
        title = {
            Text(
                text = com.qingji.app.ui.i18n.s(title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        },
        confirmButton = {
            if (onConfirm != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (danger) DangerRed else QingJiGreen)
                        .pressable {
                            onConfirm()
                            onDismiss()
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = com.qingji.app.ui.i18n.s(confirmText), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(QingJiGreenLight)
                        .pressable { onDismiss() }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = com.qingji.app.ui.i18n.s(confirmText), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = QingJiGreenDark)
                }
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .pressable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = com.qingji.app.ui.i18n.s(dismissText), fontSize = 13.sp, color = TextSecondary)
            }
        }
    )
}
