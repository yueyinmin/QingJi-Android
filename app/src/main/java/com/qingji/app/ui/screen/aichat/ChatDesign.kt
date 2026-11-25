package com.qingji.app.ui.screen.aichat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.DividerColor
import com.qingji.app.ui.theme.QingJiGreenLight

/**
 * AI 管家聊天页专属设计 token。
 *
 * 为什么单独放一个文件：聊天页的圆角 / 触控尺寸比全 App 的通用 Dimens 更细
 * （气泡 18、输入容器 22、图标按钮 40 这种），写进 Dimens 会污染其他页面；
 * 但颜色一律复用主题变量，不新造色板——换肤、深色模式都自动跟随。
 */
object ChatDimens {
    /** 顶栏高度：固定，不随键盘与内容变化（工单 3.1） */
    val BarHeight = 56.dp

    /** 页面左右留白 */
    val HPad = 16.dp

    /** 图标按钮触控区：40dp，保证不挤压、不换行 */
    val ActionSize = 40.dp

    /** 图标绘制尺寸 */
    val ActionIcon = 20.dp

    /** 圆形发送按钮直径 */
    val SendSize = 36.dp

    /** 发送图标尺寸 */
    val SendIcon = 18.dp

    /** 气泡圆角 */
    val BubbleCorner = 18.dp

    /** 输入容器圆角 */
    val ComposerCorner = 22.dp

    /** 输入框最多显示几行，超出后内部滚动（不撑高整个页面） */
    const val ComposerMaxLines = 5

    /** 快捷问题胶囊高度：44 的触控高度靠外层 padding 补，胶囊本身 34 */
    val QuickPromptHeight = 34.dp

    /** 消息列表左右留白 */
    val ListHPad = 16.dp

    /** 消息之间的间距 */
    val ListGap = 10.dp

    /** 用户气泡最大占屏宽比例 */
    const val BubbleMaxWidthUser = 0.82f

    /** AI 气泡最大占屏宽比例 */
    const val BubbleMaxWidthAi = 0.94f
}

/** 用户气泡：主题浅色底（默认松绿=浅绿，切「海蓝」主题即浅蓝），不写死颜色 */
val ChatUserBubble: Color get() = QingJiGreenLight

/** AI 气泡：卡片白 */
val ChatAiBubble: Color get() = CardWhite

/** 输入容器默认描边：几乎看不见，避免粗蓝边框 */
val ChatComposerBorder: Color get() = DividerColor
