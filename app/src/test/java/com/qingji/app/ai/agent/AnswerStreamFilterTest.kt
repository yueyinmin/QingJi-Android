package com.qingji.app.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerStreamFilterTest {

    @Test
    fun accept_onlyEmitsAnswerFieldNotRawJson() {
        val filter = AnswerStreamFilter()
        val parts = listOf(
            "{\"ans", "wer\":\"今天", "热量稳定。\",", "\"conclusions\":[",
            "{\"text\":\"约 1800\",\"trust\":\"FACT\"}]}"
        )

        val output = parts.joinToString("") { filter.accept(it) }

        assertEquals("今天热量稳定。", output)
    }

    @Test
    fun accept_handlesEscapedQuoteInsideAnswer() {
        val filter = AnswerStreamFilter()
        val parts = listOf("{\"answer\":\"他说\\\"", "你好\\\"，可以。\"}")

        val output = parts.joinToString("") { filter.accept(it) }

        assertEquals("他说\\\"你好\\\"，可以。", output)
    }

    @Test
    fun finish_fallsBackToPlainTextWhenModelIgnoresJson() {
        val filter = AnswerStreamFilter()

        filter.accept("今天训练完成度不错。")

        assertEquals("今天训练完成度不错。", filter.finish())
    }

    @Test
    fun extractAnswer_returnsEmptyBeforeAnswerStarts() {
        assertEquals("", AnswerStreamFilter.extractAnswer("{\"conclusions\":[]}"))
    }
}
