package com.qingji.app.ai

import com.qingji.app.data.remote.ai.AiError
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ToolExecutorTest {

    private val schema = """{"type":"object","properties":{"date":{"type":"string"}},"required":["date"]}"""

    private class DelayTool(
        override val name: String = "get_today_summary",
        private val active: AtomicInteger,
        private val maxActive: AtomicInteger,
        private val delayMs: Long = 20
    ) : AiTool {
        override val description = "读取今日摘要"
        override val parametersJson =
            """{"type":"object","properties":{"date":{"type":"string"}},"required":["date"]}"""
        override val readOnly = true

        override suspend fun execute(argumentsJson: String): String {
            val now = active.incrementAndGet()
            maxActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            delay(delayMs)
            active.decrementAndGet()
            return "{\"tool\":\"$name\",\"args\":$argumentsJson}"
        }
    }

    @Test
    fun execute_preservesOrderAndCallIdMapping() = runTest {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val registry = ToolRegistry().apply { register(DelayTool(active = active, maxActive = maxActive)) }
        val executor = ToolExecutor(registry, maxConcurrency = 2)

        val results = executor.execute(
            listOf(
                ToolRequest("a", "get_today_summary", """{"date":"2026-11-25"}"""),
                ToolRequest("b", "get_today_summary", """{"date":"2026-11-24"}"""),
                ToolRequest("c", "get_today_summary", """{"date":"2026-11-23"}""")
            )
        )

        assertEquals(listOf("a", "b", "c"), results.map { it.callId })
        assertTrue(results.all { it.ok })
        assertTrue(results[0].resultJson.contains("2026-11-25"))
        assertTrue(results[1].resultJson.contains("2026-11-24"))
        assertTrue(results[2].resultJson.contains("2026-11-23"))
    }

    @Test
    fun execute_respectsMaxConcurrency() = runTest {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val registry = ToolRegistry().apply { register(DelayTool(active = active, maxActive = maxActive, delayMs = 40)) }
        val executor = ToolExecutor(registry, maxConcurrency = 2)

        executor.execute(
            (1..6).map { ToolRequest("call_$it", "get_today_summary", """{"date":"2026-11-25"}""") }
        )

        assertTrue("maxActive=${maxActive.get()}", maxActive.get() <= 2)
    }

    @Test
    fun execute_duplicateCallId_returnsStructuredError() = runTest {
        val registry = ToolRegistry().apply {
            register(DelayTool(active = AtomicInteger(), maxActive = AtomicInteger()))
        }
        val executor = ToolExecutor(registry)

        val results = executor.execute(
            listOf(
                ToolRequest("same", "get_today_summary", """{"date":"2026-11-25"}"""),
                ToolRequest("same", "get_today_summary", """{"date":"2026-11-25"}""")
            )
        )

        assertTrue(results[0].ok)
        assertFalse(results[1].ok)
        assertTrue(results[1].error is AiError.ToolArgument)
    }

    @Test
    fun execute_missingRequiredArgument_failsBeforeToolRun() = runTest {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val registry = ToolRegistry().apply { register(DelayTool(active = active, maxActive = maxActive)) }
        val executor = ToolExecutor(registry)

        val results = executor.execute(
            listOf(ToolRequest("a", "get_today_summary", "{}"))
        )

        assertFalse(results[0].ok)
        assertEquals(0, active.get())
        assertTrue(results[0].error is AiError.ToolArgument)
    }

    @Test
    fun execute_writeTool_isRejected() = runTest {
        val writeTool = object : AiTool {
            override val name = "write_food_log"
            override val description = "写饮食记录"
            override val parametersJson = "{}"
            override val readOnly = false
            override suspend fun execute(argumentsJson: String) = "should-not-run"
        }
        val registry = ToolRegistry().apply { register(writeTool) }

        val results = ToolExecutor(registry).execute(
            listOf(ToolRequest("a", "write_food_log", "{}"))
        )

        assertFalse(results[0].ok)
        assertTrue(results[0].error is AiError.PermissionDenied)
    }

    @Test
    fun execute_unknownTool_returnsStructuredError() = runTest {
        val results = ToolExecutor(ToolRegistry()).execute(
            listOf(ToolRequest("a", "not_registered", "{}"))
        )

        assertFalse(results[0].ok)
        assertTrue(results[0].error is AiError.ToolArgument)
    }
}
