package com.qingji.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 备份 v4（自律段）的解析测试。
 * 对齐 TrainingBackupParsingTest：老备份不会被误判、只含自律数据的备份不会被当空文件。
 */
class SelfDisciplineBackupParsingTest {

    private val v4 = """
        {
          "app": "qingji",
          "version": 4,
          "selfDiscipline": {
            "goals": [
              {"id":42,"title":"戒色","goalType":"porn","isEnabled":true,"startDate":"2026-11-01","targetDays":30,"reminderEnabled":false,"reminderMinute":-1,"note":"加油","createdAt":100}
            ],
            "periods": [
              {"id":7,"goalId":42,"startAt":"2026-11-01","endAt":null,"endReason":null,"status":"active","createdAt":101}
            ],
            "checkins": [
              {"id":9,"goalId":42,"localDate":"2026-11-02","status":"on_target","mood":"平静","urgeLevel":2,"triggers":"压力","copingMethods":"运动","note":"","createdAt":102,"updatedAt":103}
            ],
            "urgeLogs": [
              {"id":11,"goalId":42,"occurredAt":200,"intensity":3,"trigger":"无聊","copingMethod":"呼吸","intensityAfter":1,"resolved":true,"note":"","createdAt":104}
            ],
            "settings": [
              {"key":"default_goal","value":"42"}
            ],
            "milestones": [
              {"id":13,"goalId":42,"milestoneDays":7,"achievedAt":"2026-11-08","createdAt":105}
            ],
            "interventions": [
              {"id":15,"goalId":42,"type":"breath","occurredAt":300,"beforeIntensity":4,"afterIntensity":1,"effective":true,"note":"","createdAt":106}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `v4 备份能读出自律段七张表`() {
        val p = parseBackup(v4).getOrThrow()
        assertEquals(4, p.version)
        assertTrue(p.hasSelfDiscipline)
        val sd = p.selfDiscipline!!
        assertEquals(1, sd.goals.size)
        assertEquals(1, sd.periods.size)
        assertEquals(1, sd.checkins.size)
        assertEquals(1, sd.urgeLogs.size)
        assertEquals(1, sd.settings.size)
        assertEquals(1, sd.milestones.size)
        assertEquals(1, sd.interventions.size)
        // id / goalId 必须原样读出，不能重生成
        assertEquals(42L, sd.goals.first().id)
        assertEquals("戒色", sd.goals.first().title)
        assertEquals(LocalDate.of(2026, 11, 1), sd.goals.first().startDate)
        assertEquals(42L, sd.periods.first().goalId)
        assertNull(sd.periods.first().endAt)
        assertNull(sd.periods.first().endReason)
        assertEquals(LocalDate.of(2026, 11, 2), sd.checkins.first().localDate)
        assertTrue(sd.urgeLogs.first().resolved)
        assertEquals("default_goal", sd.settings.first().key)
        assertEquals("42", sd.settings.first().value)
        assertEquals(LocalDate.of(2026, 11, 8), sd.milestones.first().achievedAt)
        assertTrue(sd.interventions.first().effective == true)
    }

    @Test
    fun `只含自律数据的备份不会被当成空文件`() {
        assertFalse(parseBackup(v4).getOrThrow().isEmpty)
    }

    @Test
    fun `自律段里一条坏数据只跳过那一条`() {
        val bad = """
            {
              "app": "qingji",
              "version": 4,
              "selfDiscipline": {
                "goals": [
                  {"id":1,"title":"坏","startDate":"不是日期"},
                  {"id":2,"title":"好","startDate":"2026-11-01"}
                ]
              }
            }
        """.trimIndent()
        val p = parseBackup(bad).getOrThrow()
        val sd = p.selfDiscipline
        assertTrue("整段不该因为一条坏数据变成 null", sd != null)
        assertEquals(1, sd!!.goals.size)
        assertEquals(2L, sd.goals.first().id)
        assertEquals("好", sd.goals.first().title)
        assertFalse(p.isEmpty)
    }

    @Test
    fun `v1 老备份没有自律段`() {
        val p = parseBackup("""{"app":"qingji","version":1,"weights":[]}""").getOrThrow()
        assertEquals(1, p.version)
        assertFalse(p.hasSelfDiscipline)
        assertNull(p.selfDiscipline)
    }

    @Test
    fun `v2 老备份没有自律段`() {
        val p = parseBackup("""{"app":"qingji","version":2,"weights":[]}""").getOrThrow()
        assertEquals(2, p.version)
        assertFalse(p.hasSelfDiscipline)
        assertNull(p.selfDiscipline)
    }

    @Test
    fun `v3 老备份没有自律段`() {
        val p = parseBackup("""{"app":"qingji","version":3,"weights":[]}""").getOrThrow()
        assertEquals(3, p.version)
        assertFalse(p.hasSelfDiscipline)
        assertNull(p.selfDiscipline)
    }
}
