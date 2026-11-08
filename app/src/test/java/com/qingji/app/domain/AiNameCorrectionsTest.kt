package com.qingji.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3.4.0：AI 名字纠错的解析 / 写入 / 套用。 */
class AiNameCorrectionsTest {

    @Test
    fun emptyRawIsSafe() {
        assertTrue(AiNameCorrections.parse("").isEmpty())
        assertEquals("地锅炖", AiNameCorrections.apply("", "地锅炖"))
    }

    @Test
    fun parseIgnoresBrokenLines() {
        val raw = """
            坏行没有分隔符
            地锅炖=>地锅鸡
            空值=>
            =>没有原名
            米饭=>米饭
            白饭 => 米饭
        """.trimIndent()
        val map = AiNameCorrections.parse(raw)
        assertEquals(2, map.size)
        assertEquals("地锅鸡", map["地锅炖"])
        assertEquals("米饭", map["白饭"])
    }

    @Test
    fun putAddsAndOverwrites() {
        var raw = AiNameCorrections.put("", "地锅炖", "地锅鸡")
        assertEquals("地锅鸡", AiNameCorrections.apply(raw, "地锅炖"))

        raw = AiNameCorrections.put(raw, "地锅炖", "地锅炖排骨")
        assertEquals("地锅炖排骨", AiNameCorrections.apply(raw, "地锅炖"))
        assertEquals(1, AiNameCorrections.parse(raw).size)
    }

    @Test
    fun putIgnoresNoOpCorrections() {
        val before = AiNameCorrections.put("", "米饭", "白米饭")
        assertEquals(before, AiNameCorrections.put(before, "米饭", "米饭"))
        assertEquals(before, AiNameCorrections.put(before, "", " something "))
        assertEquals(before, AiNameCorrections.put(before, "  ", ""))
    }

    @Test
    fun applyOnlyMatchesExactName() {
        val raw = AiNameCorrections.put("", "米饭", "白米饭")
        assertEquals("白米饭", AiNameCorrections.apply(raw, " 米饭 "))
        assertEquals("米饭（熟）", AiNameCorrections.apply(raw, "米饭（熟）"))
        assertEquals("炒饭", AiNameCorrections.apply(raw, "炒饭"))
    }

    @Test
    fun encodeIsStableAndSorted() {
        val raw = AiNameCorrections.put(
            AiNameCorrections.put("", "地锅炖", "地锅鸡"),
            "白饭", "米饭"
        )
        // 排序用 Unicode 自然顺序（中文不是笔画序），这里验证的是「稳定 + 可解析 + 真有序」，
        // 不把某个中文的具体先后写死，免得以后加映射就误报
        assertEquals(raw, AiNameCorrections.encode(AiNameCorrections.parse(raw)))
        val map = AiNameCorrections.parse(raw)
        assertEquals(2, map.size)
        assertEquals("米饭", map["白饭"])
        assertEquals("地锅鸡", map["地锅炖"])
        val keysInText = raw.lines().map { it.substringBefore("=>") }
        assertEquals(keysInText.sorted(), keysInText)
        assertTrue(raw.lines() == raw.lines().sorted())
    }

    @Test
    fun multipleCorrectionsCanBeAppliedInSequence() {
        var raw = ""
        listOf("地锅炖" to "地锅鸡", "番茄" to "西红柿", "土豆" to "马铃薯").forEach { (f, t) ->
            raw = AiNameCorrections.put(raw, f, t)
        }
        assertEquals("地锅鸡", AiNameCorrections.apply(raw, "地锅炖"))
        assertEquals("西红柿", AiNameCorrections.apply(raw, "番茄"))
        assertEquals("马铃薯", AiNameCorrections.apply(raw, "土豆"))
    }
}
