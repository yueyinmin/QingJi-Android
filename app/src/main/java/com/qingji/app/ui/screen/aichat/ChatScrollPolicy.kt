package com.qingji.app.ui.screen.aichat

/**
 * 消息列表的滚动跟随策略（纯函数，方便单测）。
 *
 * 背景：老实现是「消息条数一变就 animateScrollToItem 到末尾」，
 * 用户往上翻历史时只要 AI 还在流式返回，就会被一次次拽回底部。
 * 现在改成：只有用户本来就贴着底部，才跟随新内容。
 */
object ChatScrollPolicy {

    /**
     * 是否应该跟随滚动到最新一条。
     *
     * @param lastVisibleIndex 当前屏幕上最后一个可见 item 的下标（-1 = 列表还没测量）
     * @param totalCount 列表 item 总数
     * @param threshold 允许「差几条」就算在底部（默认 2：最后一条 + 上一条都还看得见）
     */
    fun shouldFollow(lastVisibleIndex: Int, totalCount: Int, threshold: Int = 2): Boolean {
        if (totalCount <= 0) return false
        if (lastVisibleIndex < 0) return true // 首次布局，直接贴底
        return lastVisibleIndex >= totalCount - 1 - threshold
    }

    /**
     * 消息列表里除消息本体之外还有几个 header item（更早消息按钮 + 来源标签）。
     * 滚动目标下标 = headerCount + messages.lastIndex。
     */
    fun headerCount(hasOlderMessages: Boolean): Int = if (hasOlderMessages) 2 else 1

    /**
     * 列表最后一个 item 的下标。
     *
     * 欢迎态时快捷问题也是列表里的一个 item（挂在欢迎气泡下面），
     * 所以「滚到底」不能简单用 header + messages.lastIndex，否则会差一条。
     */
    fun lastItemIndex(
        hasOlderMessages: Boolean,
        messageCount: Int,
        quickPromptsAsItem: Boolean
    ): Int {
        if (messageCount <= 0) return -1
        val total = headerCount(hasOlderMessages) + messageCount + if (quickPromptsAsItem) 1 else 0
        return total - 1
    }
}
