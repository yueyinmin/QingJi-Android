package com.qingji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份 v3（训练段）的解析测试。
 * 重点不是「能不能读」，而是「老备份不会被误判、只含训练数据的备份不会被当空文件」。
 */
class TrainingBackupParsingTest {

    private val v3 = """
        {
          "app": "qingji",
          "version": 3,
          "training": {
            "exercises": [
              {"uuid":"ex-chest-01","name":"哑铃卧推","category":"chest","isBuiltIn":true,"isFavorite":false}
            ],
            "plans": [],
            "planDays": [],
            "planExercises": [],
            "sessions": [
              {"uuid":"s1","date":"2026-11-13","startedAt":100,"endedAt":200,"durationMinutes":10,"note":"胸","source":"manual","createdAt":200}
            ],
            "workoutExercises": [
              {"id":1,"sessionUuid":"s1","exerciseUuid":"ex-chest-01","exerciseNameSnapshot":"哑铃卧推","orderInSession":0,"setsJson":"[{\"w\":10,\"r\":12}]","note":""}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `v3 备份能读出训练段六张表`() {
        val p = parseBackup(v3).getOrThrow()
        assertEquals(3, p.version)
        assertTrue(p.hasTraining)
        assertEquals("哑铃卧推", p.training?.exercises?.first()?.name)
        assertEquals(1, p.training?.sessions?.size)
        assertEquals("2026-11-13", p.training?.sessions?.first()?.date.toString())
        assertEquals(1, p.training?.workoutExercises?.size)
        assertTrue(p.training?.workoutExercises?.first()?.setsJson?.contains("10") == true)
    }

    @Test
    fun `只含训练数据的备份不会被当成空文件`() {
        assertFalse(parseBackup(v3).getOrThrow().isEmpty)
    }

    @Test
    fun `v2 老备份没有训练段也能正常读`() {
        val v2 = """
            {"app":"qingji","version":2,"weights":[]}
        """.trimIndent()
        val p = parseBackup(v2).getOrThrow()
        assertEquals(2, p.version)
        assertFalse(p.hasTraining)
        assertNull(p.training)
    }

    @Test
    fun `训练段里一条坏会话只跳过那一条`() {
        val bad = """
            {
              "app": "qingji",
              "version": 3,
              "training": {
                "sessions": [
                  {"uuid":"bad","date":"不是日期"},
                  {"uuid":"good","date":"2026-11-12","durationMinutes":20}
                ],
                "workoutExercises": []
              }
            }
        """.trimIndent()
        val p = parseBackup(bad).getOrThrow()
        // 日期坏掉的那条被跳过，另一条正常留下；整段不会变成 null
        assertEquals(1, p.training?.sessions?.size)
        assertEquals("2026-11-12", p.training?.sessions?.first()?.date.toString())
        assertFalse(p.isEmpty)
    }
}
