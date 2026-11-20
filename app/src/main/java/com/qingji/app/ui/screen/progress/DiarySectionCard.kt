package com.qingji.app.ui.screen.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.domain.progress.DiaryGrouping
import com.qingji.app.ui.components.SectionCard
import com.qingji.app.ui.i18n.s
import com.qingji.app.ui.theme.Dimens
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenLight
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes
import java.time.format.DateTimeFormatter

/**
 * ============================================================
 * 进展页日记卡 · 共用渲染组件（工单 P1a/P1b + v1.4.2 高度约束）
 * ------------------------------------------------------------
 * 三种日记（睡眠 / 运动 / 饮食）信息组织形式完全一致：
 *   卡片标题 + 按日期分组的滚动流 + 日期组头 + 每组的若干条目行。
 * 所以只画一份 [DiarySectionCard]，行内容通过 [rowContent] 注入，
 * 三处复用，避免复制三份只差文案的代码。
 *
 * v1.4.2（工单三）：以前展开后是完整 Column、没有高度上限，记录一多就把
 * 首页其它内容无限往下挤、还跟底部悬浮导航打架。现在改成：
 *   · 列表区固定最大高度 [maxListHeight]，超出后【卡片内部滚动】，首页不再被撑高；
 *   · 默认折叠只显示最近 [collapsedDays] 天，点「查看全部」展开全部（仍限高内部滚动）；
 *   · 展开/收起用 animateContentSize 做轻量过渡；
 *   · 只约束展示层，数据一行不删、统计口径不变。
 * 嵌套滚动：进展页本身是 verticalScroll，内层列表用 verticalScroll 时，
 * Compose 的 nestedScroll 天然让内层先消费、到顶/底再把事件交回外层，不会手势打架。
 * ============================================================
 */
@Composable
fun <T> DiarySectionCard(
    title: String,
    groups: List<DiaryGrouping.DayGroup<T>>,
    emptyText: String,
    emptyActionText: String? = null,
    onEmptyAction: () -> Unit = {},
    collapsedDays: Int = 3,
    /** 列表区最大高度；超出后卡片内部滚动。按 dp 设定，适配不同屏幕，不写死屏幕高度。 */
    maxListHeight: androidx.compose.ui.unit.Dp = 300.dp,
    /** 行内容放在 RowScope 里，调用方可以直接用 Modifier.weight 撑开文字列 */
    rowContent: @Composable RowScope.(T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    SectionCard(contentPadding = Dimens.CardPaddingCompact) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = TextSizes.CardTitle,
                fontWeight = FontWeight.SemiBold, color = TextPrimary,
                modifier = Modifier.weight(1f))
            if (groups.size > collapsedDays) {
                Text(
                    text = if (expanded) s("收起") else s("查看全部"),
                    fontSize = TextSizes.Caption, color = QingJiGreen,
                    modifier = Modifier.clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (groups.isEmpty()) {
            Text(emptyText, fontSize = TextSizes.Caption, color = TextSecondary)
            if (emptyActionText != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.PillCorner))
                        .background(QingJiGreenLight)
                        .clickable { onEmptyAction() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(emptyActionText, fontSize = TextSizes.Caption,
                        color = QingJiGreen, fontWeight = FontWeight.SemiBold)
                }
            }
            return@SectionCard
        }

        val shown = if (expanded) groups else groups.take(collapsedDays)
        // 固定最大高度 + 内部滚动：记录再多也只占这么多，首页不再被无限撑高（T06/T07/T08）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
                .verticalScroll(listScroll)
                .animateContentSize()
        ) {
            shown.forEachIndexed { index, group ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                // 日期组头：小绿点 + 日期
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(QingJiGreen))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = group.date.format(DATE_CN),
                        fontSize = TextSizes.Caption, color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                ) {
                    group.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowContent(item)
                        }
                    }
                }
            }
        }
        if (!expanded && groups.size > collapsedDays) {
            Spacer(Modifier.height(6.dp))
            Text("… ${s("还有")} ${groups.size - collapsedDays} ${s("天")}",
                fontSize = 10.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor.copy(alpha = 0.5f))
        )
    }
}

private val DATE_CN: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
