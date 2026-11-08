package com.qingji.app.ui.screen.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.pressable
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 底部中间的「+」弹出来的记录面板
 * ------------------------------------------------------------
 * 需求文档 M3 里一共 5 种记录方式 + 1 个体重入口，现在六项全部可用：
 * 拍照识别 / 语音识别 / 文字录入 / 条形码 / 食物库 / 记体重。
 * 五种饮食记录方式最终都落到同一张 food_log 表。
 *
 * ModalBottomSheet 在 Material3 里还标着实验性，加一个 @OptIn
 * 就能正常用，这是官方推荐写法，不影响稳定性。
 * ============================================================
 */

private data class SheetEntry(
    val title: String,
    val subtitle: String,
    val color: Color,
    val enabled: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    onDismiss: () -> Unit,
    onSearchFood: () -> Unit,
    onTextAi: () -> Unit,
    onWeight: () -> Unit,
    onImage: () -> Unit,
    onVoice: () -> Unit,
    onBarcode: () -> Unit
) {
    // 需求 M3：五种记录方式 + 体重入口，M3 做完六项全部可用
    val entries = listOf(
        SheetEntry("拍照识别", "拍照片估算", Color(0xFFF5A623), true),
        SheetEntry("语音识别", "说一句转文字", Color(0xFF26A69A), true),
        SheetEntry("文字录入", "打一句描述", Color(0xFF5B8DEF), true),
        SheetEntry("条形码", "扫码查营养", Color(0xFFEC407A), true),
        SheetEntry("食物库", "从食物库里挑", QingJiGreen, true),
        SheetEntry("记体重", "体重与体脂", Color(0xFF9B8AFB), true)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "记一笔",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "数据只存在这台手机里",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            // 两列排布，六个入口刚好三行
            entries.chunked(2).forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowEntries.forEach { entry ->
                        SheetEntryCell(
                            entry = entry,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                when (entry.title) {
                                    "食物库" -> onSearchFood()
                                    "文字录入" -> onTextAi()
                                    "记体重" -> onWeight()
                                    "拍照识别" -> onImage()
                                    "语音识别" -> onVoice()
                                    "条形码" -> onBarcode()
                                }
                            }
                        )
                    }
                    // 补位，保证奇数个时不会拉宽
                    if (rowEntries.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SheetEntryCell(
    entry: SheetEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            // 固定高度：六个入口的说明文字长短不一，
            // 不锁高度就会出现「有的高有的矮」，两列对不齐很难看。
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (entry.enabled) PageBackground else PageBackground.copy(alpha = 0.55f))
            .pressable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(entry.color.copy(alpha = if (entry.enabled) 0.16f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(entry.color.copy(alpha = if (entry.enabled) 1f else 0.4f))
            )
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (entry.enabled) TextPrimary else TextSecondary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                // 说明统一只占一行，超出用省略号，保证六格高度一致
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
