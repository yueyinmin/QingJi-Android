package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.AppChangeLog
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.pressable

/**
 * ============================================================
 * 版本更新提示（升级后只弹一次）
 * ------------------------------------------------------------
 * 只有一个「我知道了」按钮，没有「取消」——这是通知，不是要用户做选择。
 * 点按钮 / 点弹窗外 / 按返回键都走同一个 onDismiss（由调用方记住已读），
 * 所以不会出现「不小心关掉了就再也看不到」的情况。
 * 内容和「设置 → 版本信息」共用 AppChangeLog，一处改两处同步。
 * ============================================================
 */
@Composable
fun UpdateNoticeDialog(
    releases: List<AppChangeLog.Release>,
    onDismiss: () -> Unit
) {
    val latest = releases.firstOrNull()?.versionName.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = CardWhite,
        title = {
            Text(
                text = if (latest.isBlank()) "轻记更新啦" else "轻记更新啦 · v$latest",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("这次更新了这些：", fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                releases.forEach { r ->
                    Text(
                        text = "v${r.versionName}（${r.date}）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = QingJiGreen
                    )
                    Spacer(Modifier.height(4.dp))
                    r.items.forEach { item ->
                        Text(
                            text = "· $item",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    text = "这些内容也会留在「设置 → 版本信息」里，随时可以回看。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreen)
                    .pressable { onDismiss() }
                    .padding(horizontal = 22.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "我知道了",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    )
}
