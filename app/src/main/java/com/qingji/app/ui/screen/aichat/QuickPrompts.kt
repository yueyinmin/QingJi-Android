package com.qingji.app.ui.screen.aichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.QingJiGreenLight

/**
 * 快捷提问（工单第六节）。
 *
 * 只在一个地方出现：输入区上方。旧版页面顶部还有一份一模一样的，
 * 两处重复既是视觉噪音，也容易让人以为功能不一样——所以顶部那份删掉，
 * 能力（点一下直接发问）原样保留在 [onPick]。
 *
 * 每一项固定 40dp 触控高度：横向滑动时不会因为胶囊太矮点不中，也不会被列表裁掉。
 */
@Composable
fun QuickPrompts(
    prompts: List<String>,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = ChatDimens.HPad)
) {
    if (prompts.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(prompts, key = { it }) { question ->
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(QingJiGreenLight)
                    .clickable(enabled = enabled) { onPick(question) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = question,
                    fontSize = 12.sp,
                    color = QingJiGreenDark,
                    maxLines = 1
                )
            }
        }
    }
}
