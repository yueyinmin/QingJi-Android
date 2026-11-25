package com.qingji.app.ui.screen.aichat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工单 QJ-AI-CHAT-UI-V2 · 4.4：消息滚动跟随策略。
 *
 * 这几个断言覆盖的就是「往上翻历史时不被拽回底部」这条硬要求：
 * 只要用户已经离开底部超过阈值，就不再跟随新内容。
 */
class ChatScrollPolicyTest {

    @Test
    fun shouldFollow_whenListNotMeasuredYet_returnsTrue() {
        // 空列表没有可滚的东西
        assertFalse(ChatScrollPolicy.shouldFollow(lastVisibleIndex = -1, totalCount = 0))
        // 有内容但还没测量（首帧）→ 直接贴底
        assertTrue(ChatScrollPolicy.shouldFollow(lastVisibleIndex = -1, totalCount = 12))
    }

    @Test
    fun shouldFollow_whenAtBottom_returnsTrue() {
        assertTrue(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 9, totalCount = 10))
        assertTrue(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 8, totalCount = 10))
        assertTrue(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 7, totalCount = 10))
    }

    @Test
    fun shouldFollow_whenScrolledUp_returnsFalse() {
        assertFalse(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 6, totalCount = 10))
        assertFalse(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 0, totalCount = 10))
    }

    @Test
    fun shouldFollow_emptyList_returnsFalse() {
        assertFalse(ChatScrollPolicy.shouldFollow(lastVisibleIndex = 0, totalCount = 0))
    }

    @Test
    fun headerCount_countsOlderLoaderAndContextChip() {
        // 没有更早消息时只有「来源 · 首页」这一行
        assertEquals(1, ChatScrollPolicy.headerCount(hasOlderMessages = false))
        // 多一个「加载更早的消息」
        assertEquals(2, ChatScrollPolicy.headerCount(hasOlderMessages = true))
    }

    @Test
    fun lastItemIndex_countsWelcomeQuickPromptsItem() {
        // 欢迎态：header(来源) + 1 条欢迎消息 + 1 个快捷问题 item
        assertEquals(
            2,
            ChatScrollPolicy.lastItemIndex(
                hasOlderMessages = false,
                messageCount = 1,
                quickPromptsAsItem = true
            )
        )
        // 有历史消息后快捷问题不再进列表
        assertEquals(
            4,
            ChatScrollPolicy.lastItemIndex(
                hasOlderMessages = true,
                messageCount = 3,
                quickPromptsAsItem = false
            )
        )
        assertEquals(
            -1,
            ChatScrollPolicy.lastItemIndex(
                hasOlderMessages = false,
                messageCount = 0,
                quickPromptsAsItem = false
            )
        )
    }
}
