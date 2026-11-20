package com.qingji.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.PageBackground
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary

/**
 * ============================================================
 * 食物库通用双栏骨架（v1.6 · TASK-01/02/03）
 * ------------------------------------------------------------
 * 主人明确要双栏：单列显示效果差。标准库 / 家庭厨房 / 外卖三处统一用它。
 *
 * 结构：左侧固定宽度的竖排分类导航（独立滚动），右侧内容区（独立滚动，
 * 互不影响）。左侧选中项高亮，切分类只换右侧数据、不重建整页。
 *
 * 不绑定任何业务数据，只负责「左分类 + 右内容」的排布和交互，
 * 具体分类渲染、右侧列表由调用方用 slot 提供，避免复制业务逻辑。
 * ============================================================
 */
@Composable
fun FoodTwoPane(
    modifier: Modifier = Modifier,
    /** 左侧分类标题列表（已按展示顺序）。 */
    categories: List<String>,
    /** 当前选中的分类值（"" 或哨兵值表示「全部」也行，由调用方约定）。 */
    selected: String,
    /** 每个分类的显示文案（可带 emoji），返回 null 用原值。 */
    labelOf: (String) -> String? = { null },
    onSelect: (String) -> Unit,
    /** 右侧内容（顶部筛选 + 列表），占满剩余宽度与高度。 */
    rightContent: @Composable () -> Unit
) {
    Row(modifier = modifier.fillMaxSize()) {
        // ---------- 左侧：竖排分类导航 ----------
        LazyColumn(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(PageBackground)
        ) {
            items(categories, key = { it }) { cat ->
                val active = cat == selected
                val label = labelOf(cat) ?: cat
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) CardWhite else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(cat) }
                        .padding(horizontal = 6.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = if (active) QingJiGreenDark else TextSecondary,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        // ---------- 右侧：内容区（独立滚动/组合） ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(CardWhite)
        ) {
            rightContent()
        }
    }
}
