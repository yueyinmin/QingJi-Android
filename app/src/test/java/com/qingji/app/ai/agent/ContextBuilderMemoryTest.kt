package com.qingji.app.ai.agent

import com.qingji.app.data.local.entity.AiConfig
import com.qingji.app.data.remote.ai.AiContentPart
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryNote
import com.qingji.app.domain.memory.MemoryTruth
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 I：长期记忆注入的边界——带可信度、不冒充本轮发言、没有就说没有。 */
class ContextBuilderMemoryTest {

    private fun request(memories: List<MemoryNote>, page: String = "饮食页") = AgentRequest(
        config = AiConfig(id = 1),
        key = "k",
        question = "我今天吃得怎么样？",
        taskType = "ai_chat",
        date = LocalDate.of(2026, 11, 25),
        page = page,
        recentSummary = "今天 1800 kcal",
        memories = memories
    )

    private fun systemPrompt(request: AgentRequest): String =
        ContextBuilder().build(request, emptyList()).messages.first()
            .parts.filterIsInstance<AiContentPart.Text>()
            .joinToString("") { it.text }

    @Test
    fun `记忆带可信度与类别标签`() {
        val prompt = systemPrompt(
            request(
                listOf(
                    MemoryNote(MemoryCategory.NUTRITION, "我不吃香菜", MemoryTruth.USER_FACT),
                    MemoryNote(MemoryCategory.TRAINING, "可能更喜欢早上训练", MemoryTruth.INFERRED)
                )
            )
        )

        assertTrue(prompt.contains("[用户事实][饮食] 我不吃香菜"))
        assertTrue(prompt.contains("[AI 推断][训练] 可能更喜欢早上训练"))
        assertTrue(prompt.contains("长期记忆（按当前任务筛选，共 2 条"))
    }

    @Test
    fun `没有记忆时明确说没有而不是留空`() {
        val prompt = systemPrompt(request(emptyList()))
        assertTrue(prompt.contains("本轮没有筛选出可用记忆"))
        assertFalse(prompt.contains("长期记忆（按当前任务筛选"))
    }

    @Test
    fun `提示词要求区分推断并禁止复述全部记忆`() {
        val prompt = systemPrompt(
            request(listOf(MemoryNote(MemoryCategory.GOAL, "目标体重 72 公斤", MemoryTruth.USER_FACT)))
        )
        assertTrue(prompt.contains("长期记忆是过去保存的信息，不是本轮用户说的话"))
        assertTrue(prompt.contains("不能当事实"))
        assertTrue(prompt.contains("不要主动复述全部记忆"))
    }
}
