package com.qingji.app.ui.screen.training

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * ============================================================
 * 嵌入式历史列表的通用「默认 N 条 + 展开全部」容器（QJ 动态列表治理）
 * ------------------------------------------------------------
 * 用于训练计划页两处 A 类嵌入式历史列表：
 *   · 从历史训练生成（默认最近 5 次独立训练）
 *   · 计划历史（默认最近 5 个历史计划）
 *
 * 行为：
 * - 默认只展示前 [collapsedCount] 条；超过才有「查看全部」入口；
 * - 展开后显示全部，最大高度 [maxExpandedHeight]（默认 300dp），超出在组件内部滚动；
 * - 提供「收起」，展开/收起用 animateContentSize 平滑过渡；
 * - 只约束展示层，数据由调用方全量传入，不截断、不删除任何记录。
 *
 * 注意：这是嵌入式卡片，不是全屏历史列表；全屏列表请用 LazyColumn。
 * ============================================================
 */
@Composable
fun <T> ExpandableHistoryList(
    items: List<T>,
    collapsedCount: Int = 5,
    maxExpandedHeight: Dp = 300.dp,
    /** 稳定 key，避免重组时错位（用 UUID 等） */
    keyOf: (T) -> Any,
    expandText: String = "查看全部",
    collapseText: String = "收起",
    itemContent: @Composable (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    if (items.isEmpty()) return

    val shown = if (expanded) items else items.take(collapsedCount)
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.heightIn(max = maxExpandedHeight) else Modifier)
                .then(if (expanded) Modifier.verticalScroll(listScroll) else Modifier)
        ) {
            shown.forEach { item ->
                itemContent(item)
                Spacer(Modifier.height(8.dp))
            }
        }
        if (items.size > collapsedCount) {
            Text(
                text = if (expanded) collapseText else "$expandText（${items.size}）",
                fontSize = TextSizes.Caption,
                color = QingJiGreen,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
                    .clickable { expanded = !expanded }
            )
        }
    }
}
