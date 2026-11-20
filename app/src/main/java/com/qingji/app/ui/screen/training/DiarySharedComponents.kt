package com.qingji.app.ui.screen.training

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.theme.TextSizes

/**
 * ============================================================
 * 训练日记 / 心情日记的**公共组件标准**
 * ------------------------------------------------------------
 * 主人 2026-11-15 反馈：两个 Tab 切换时「像直接切屏」，因为两边内容
 * 看起来不是同一套东西。核对后发现三处不一致：
 *
 * 1. 月份各管各的（训练已外提、心情还在自己 remember）→ 翻过月份后
 *    切回来日历跳到另一月；
 * 2. 三栏统计一个是 SummaryItem(label, value)、另一个是
 *    SummaryColumn(value, label)，参数顺序相反、实现却几乎一样；
 * 3. 主按钮各自手写 Button，文案不同但结构一样。
 *
 * 现在两个 Tab 一律用这里的同一套组件 + 同一个 month 状态，
 * 位置、尺寸、字号、间距完全对齐；切换时只有「下面的记录列表」在变。
 * ============================================================
 */

/** 统一的整宽主操作按钮：训练日记「开始训练」、心情日记「写一篇日记」。 */
@Composable
fun DiaryPrimaryButton(text: String, onClick: () -> Unit) {
    // v1.4.2：统一走全局 PrimaryButton（主题色实心 + 白字 + 按压缩放反馈），
    // 和 App 其它主要操作保持一致，不再各写一份裸 Button。
    com.qingji.app.ui.components.PrimaryButton(text = text, onClick = onClick)
}

/** 统一的三栏统计项：value 在上、18sp 粗体，label 在下、Tiny 灰字。 */
@Composable
fun DiarySummaryItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = TextSizes.Tiny, color = TextSecondary)
    }
}

/** 统一的区块标题（「训练记录」/「日记记录」）。 */
@Composable
fun DiarySectionTitle(text: String) {
    Text(
        text = text,
        fontSize = TextSizes.CardTitle,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        modifier = Modifier.fillMaxWidth()
    )
}
