package com.qingji.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模板数据的自检：动作名必须都能在内置动作库里找到，周几不能越界。 */
class TrainingTemplateDataTest {

    private val builtInNames = TrainingSeed.builtIns().map { it.name }.toSet()

    @Test
    fun `一共 7 类模板且 id 不重复`() {
        assertEquals(7, TrainingTemplateData.templates.size)
        assertEquals(7, TrainingTemplateData.templates.map { it.id }.distinct().size)
    }

    @Test
    fun `每个模板的周几都在 1 到 7 且不重复`() {
        TrainingTemplateData.templates.forEach { t ->
            t.days.forEach { d -> assertTrue("${t.name} 的 weekday 越界", d.weekday in 1..7) }
            assertEquals("${t.name} 同一天排了两次", t.days.size, t.days.map { it.weekday }.distinct().size)
        }
    }

    @Test
    fun `模板引用的动作都在内置动作库里`() {
        TrainingTemplateData.templates.forEach { t ->
            t.days.forEach { d ->
                d.exerciseNames.forEach { name ->
                    assertTrue("${t.name} 引用了不存在的动作：$name", name in builtInNames)
                }
            }
        }
    }

    @Test
    fun `fullWeek 永远返回 7 天 没排的就是休息`() {
        TrainingTemplateData.templates.forEach { t ->
            val week = t.fullWeek()
            assertEquals(7, week.size)
            assertEquals((1..7).toList(), week.map { it.first })
            val restCount = week.count { it.second == null }
            assertEquals(7 - t.days.size, restCount)
        }
    }

    @Test
    fun `模板不带重量建议 合规提示不为空`() {
        TrainingTemplateData.templates.forEach { t ->
            assertTrue(t.summary.isNotBlank())
        }
        assertTrue(TrainingTemplateData.NOTICE.contains("非训练处方"))
    }
}
