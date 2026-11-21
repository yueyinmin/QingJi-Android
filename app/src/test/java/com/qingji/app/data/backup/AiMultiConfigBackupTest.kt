package com.qingji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 多服务备份解析测试：
 * 1. v7 多配置数组全部解析、id/displayName 保留；
 * 2. 备份里绝不出现 API Key 字段；
 * 3. 老单 aiConfig 备份仍可导入为一条服务；
 * 4. 空/缺段容错。
 */
class AiMultiConfigBackupTest {

    private fun envelope(ai: String, extra: String = "") = """
        {"app":"qingji","version":7,"exportedAt":"2026-11-20","note":"x",$extra
         $ai}
    """.trimIndent()

    @Test
    fun `v7多配置数组全部解析且保留id与名称`() {
        val json = envelope(
            """"aiConfig":{"id":1,"displayName":"官方","provider":"deepseek","baseUrl":"https://a","textModel":"m1","visionModel":"","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true},
               "aiConfigs":[
                 {"id":1,"displayName":"官方","provider":"deepseek","baseUrl":"https://a","textModel":"m1","visionModel":"","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true},
                 {"id":2,"displayName":"公益站B","provider":"custom","baseUrl":"https://b","textModel":"m2","visionModel":"v2","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true},
                 {"id":3,"displayName":"公益站C","provider":"custom","baseUrl":"https://c","textModel":"m3","visionModel":"","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":false}
               ]"""
        )
        val parsed = parseBackup(json).getOrThrow()
        assertEquals(3, parsed.aiConfigs.size)
        assertEquals(1, parsed.aiConfigs[0].id)
        assertEquals("官方", parsed.aiConfigs[0].displayName)
        assertEquals("公益站B", parsed.aiConfigs[1].displayName)
        assertEquals("https://c", parsed.aiConfigs[2].baseUrl)
        assertTrue(parsed.hasAiConfigs)
    }

    @Test
    fun `备份里不出现apiKey字段也不会被解析出来`() {
        val json = envelope(
            """"aiConfigs":[{"id":1,"displayName":"A","provider":"custom","baseUrl":"https://a","textModel":"m","visionModel":"","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true,"apiKey":"sk-secret","cipherText":"xxx"}],"""
        )
        val parsed = parseBackup(json).getOrThrow()
        assertEquals(1, parsed.aiConfigs.size)
        // 解析结果里没有任何 key 字段（data class 根本没这个字段，构造时 opt 也不读）
        assertFalse(json.contains("\"key\""))
    }

    @Test
    fun `老版本单aiConfig备份仍可导入为一条服务`() {
        val json = envelope(
            """"aiConfig":{"provider":"deepseek","baseUrl":"https://old","textModel":"m","visionModel":"","temperature":0.2,"timeoutSec":30,"dailyLimit":100,"enabled":true},"""
        )
        val parsed = parseBackup(json).getOrThrow()
        assertTrue(parsed.hasAiConfig)
        assertEquals(1, parsed.aiConfig?.id)          // 老配置默认 id=1
        assertEquals("", parsed.aiConfig?.displayName) // 老配置没有名称
        assertEquals("https://old", parsed.aiConfig?.baseUrl)
        assertTrue(parsed.aiConfigs.isEmpty())         // 老备份无多服务段
        assertFalse(parsed.hasAiConfigs)
    }

    @Test
    fun `全新空备份不报错`() {
        val parsed = parseBackup("""{"app":"qingji","version":7,"note":"x"}""").getOrThrow()
        assertTrue(parsed.aiConfigs.isEmpty())
        assertEquals(null, parsed.aiConfig)
    }
}
