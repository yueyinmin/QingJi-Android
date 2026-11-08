package com.qingji.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ============================================================
 * 全 App 统一的尺寸与字号（设计 token）
 * ------------------------------------------------------------
 * 为什么要这个文件：
 * 之前每个页面各写各的——卡片标题有的 15sp、有的 16sp、有的 18sp，
 * 圆角一会儿 12 一会儿 16，内边距有的 12 有的 16。
 * 单个页面看没问题，几个页面来回切就明显「风格不统一」。
 *
 * 规矩：新写界面一律从这里取；老代码逐步换成这里。
 * 要整体调风格，只改这一个文件。
 * ============================================================
 */
object Dimens {
    /** 页面左右留白，所有页面一样 */
    val ScreenPadding = 16.dp

    /** 卡片圆角 */
    val CardCorner = 16.dp

    /** 卡片内边距 */
    val CardPadding = 16.dp

    /** 卡片内边距（紧凑版，用于列表项） */
    val CardPaddingCompact = 12.dp

    /** 小圆角：输入框、标签、按钮 */
    val ItemCorner = 10.dp

    /** 更小的圆角：图标底、色块 */
    val TinyCorner = 8.dp

    /** 胶囊圆角（999dp 等效，用于筛选项、标签） */
    val PillCorner = 999.dp

    /** 竖向间距：紧挨着的两个元素 */
    val GapSmall = 8.dp

    /** 竖向间距：卡片之间 / 区块之间 */
    val Gap = 12.dp

    /** 竖向间距：大区块之间 */
    val GapLarge = 20.dp

    /** 底部给悬浮按钮留的空白 */
    val FabSpace = 96.dp
}

object TextSizes {
    /** 页面大标题（日期、页面名） */
    val PageTitle = 22.sp

    /** 卡片标题 */
    val CardTitle = 16.sp

    /** 区块小标题 */
    val SectionTitle = 15.sp

    /** 正文、列表主文字、按钮文字 */
    val Body = 14.sp

    /** 次要说明文字 */
    val Caption = 12.sp

    /** 最小号：角标、图例、单位 */
    val Tiny = 11.sp

    /** 超大数字（今日剩余、最新体重） */
    val HugeNumber = 34.sp
}
