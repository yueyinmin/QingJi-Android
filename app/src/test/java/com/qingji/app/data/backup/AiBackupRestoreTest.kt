package com.qingji.app.data.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.ai.proposal.ProposalStatus
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.ai.AiConversationEntity
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiMemoryEntity
import com.qingji.app.data.local.ai.AiMessageEntity
import com.qingji.app.data.local.ai.AiNudgeEntity
import com.qingji.app.data.local.entity.AiProposalExecutionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 阶段 L：备份的 aiData 段往返 + 恢复后的收尾动作。
 *
 * 三条硬要求：
 * 1. 新备份（v9）恢复后，AI 数据换成备份里的那套；
 * 2. 老备份（v8 及以前）恢复时，本机 AI 数据一条都不能动；
 * 3. 恢复后，卡在 EXECUTING 的执行台账标 STALE，来源失效的记忆标 INVALIDATED。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiBackupRestoreTest {

    private lateinit var mainDb: AppDatabase
    private lateinit var aiDb: AiDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        mainDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        aiDb = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java)
            .allowMainThreadQueries().build()
        manager = BackupManager(app, dbOverride = mainDb)
    }

    @After
    fun tearDown() {
        mainDb.close()
        aiDb.close()
    }

    // ---------- 造数据 ----------

    private fun conversation(id: String) = AiConversationEntity(
        id = id, title = "对话 $id", entryPage = "首页", createdAt = 1_000L, updatedAt = 2_000L
    )

    private fun message(id: String, conversationId: String, sequence: Int) = AiMessageEntity(
        id = id, conversationId = conversationId, sequence = sequence,
        role = "user", content = "内容 $id", createdAt = 1_000L
    )

    private fun memory(id: String, sourceRef: String = "", status: String = "ACTIVE") = AiMemoryEntity(
        id = id, category = "nutrition", content = "不吃香菜", truthType = "USER_FACT",
        source = "USER_INPUT", sourceRef = sourceRef, status = status,
        dedupeKey = "dk-$id", createdAt = 1_000L, updatedAt = 1_000L
    )

    private fun nudge(id: String) = AiNudgeEntity(
        id = id, kind = "protein_low", reason = "PROTEIN_BELOW", dateKey = "2026-11-20",
        title = "蛋白差一点", body = "今天蛋白还差 30g", shownAt = 1_000L, updatedAt = 1_000L
    )

    private fun v9Backup(
        conversations: List<AiConversationEntity>,
        messages: List<AiMessageEntity>,
        memories: List<AiMemoryEntity> = emptyList(),
        nudges: List<AiNudgeEntity> = emptyList()
    ) = ParsedBackup(
        version = 9,
        hasAiData = true,
        aiData = AiBackup(conversations, messages, memories, nudges)
    )

    // ---------- 用例 ----------

    @Test
    fun `新备份恢复后 AI 数据换成备份里的那套`() = runBlocking {
        // 本机原有数据（应被覆盖）
        aiDb.aiChatDao().insertConversation(conversation("old"))
        aiDb.aiChatDao().insertMessage(message("old-m", "old", 1))

        manager.restoreAiData(
            v9Backup(
                conversations = listOf(conversation("c1")),
                messages = listOf(message("m1", "c1", 1), message("m2", "c1", 2)),
                memories = listOf(memory("mem1", sourceRef = "m1")),
                nudges = listOf(nudge("n1"))
            ),
            aiDb = aiDb
        )

        val dao = aiDb.aiChatDao()
        assertNull(dao.conversation("old"))
        assertNotNull(dao.conversation("c1"))
        assertEquals(2, dao.countMessages("c1"))
        assertEquals(1, aiDb.aiMemoryDao().countAll())
        assertEquals(1, aiDb.aiNudgeDao().countAll())
    }

    @Test
    fun `老备份不动本机 AI 数据`() = runBlocking {
        aiDb.aiChatDao().insertConversation(conversation("keep"))
        aiDb.aiChatDao().insertMessage(message("keep-m", "keep", 1))
        aiDb.aiMemoryDao().insert(memory("keep-mem", sourceRef = "keep-m"))

        // v8 备份：没有 aiData 段
        manager.restoreAiData(ParsedBackup(version = 8, hasAiData = false), aiDb = aiDb)

        assertNotNull(aiDb.aiChatDao().conversation("keep"))
        assertEquals(1, aiDb.aiChatDao().countMessages("keep"))
        assertEquals(1, aiDb.aiMemoryDao().countAll())
    }

    @Test
    fun `恢复后卡住的执行台账标 STALE`() = runBlocking {
        mainDb.aiProposalDao().insertExecution(
            AiProposalExecutionEntity(
                proposalId = "p1",
                proposalType = "PLAN_CHANGE",
                status = ProposalStatus.EXECUTING,
                idempotencyKey = "key-1",
                createdAt = 1_000L,
                updatedAt = 1_000L
            )
        )

        manager.restoreAiData(
            v9Backup(
                conversations = listOf(conversation("c1")),
                messages = listOf(message("m1", "c1", 1))
            ),
            aiDb = aiDb
        )

        val record = mainDb.aiProposalDao().latestByProposal("p1")!!
        assertEquals(ProposalStatus.STALE, record.status)
        assertEquals("RESTORED", record.errorCode)
    }

    @Test
    fun `恢复后来源找不到的记忆标失效`() = runBlocking {
        manager.restoreAiData(
            v9Backup(
                conversations = listOf(conversation("c1")),
                messages = listOf(message("m1", "c1", 1)),
                // 一条能对上 m1，一条指向不存在的消息
                memories = listOf(
                    memory("ok", sourceRef = "m1"),
                    memory("gone", sourceRef = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
                )
            ),
            aiDb = aiDb
        )

        assertEquals("ACTIVE", aiDb.aiMemoryDao().findById("ok")!!.status)
        assertEquals("INVALIDATED", aiDb.aiMemoryDao().findById("gone")!!.status)
    }

    @Test
    fun `备份里缺 id 或去重键的条目直接跳过不炸整段`() {
        val json = org.json.JSONObject(
            """
            {
              "conversations": [{"id": "", "title": "坏的"}, {"id": "c1", "entryPage": "首页", "createdAt": 1, "updatedAt": 2}],
              "messages": [{"id": "m1", "conversationId": "", "sequence": 1}, {"id": "m2", "conversationId": "c1", "sequence": 1}],
              "memories": [{"id": "mem1", "content": "缺去重键"}, {"id": "mem2", "content": "正常", "dedupeKey": "k2"}],
              "nudges": [{"id": "n1", "kind": "", "dateKey": "2026-11-20"}, {"id": "n2", "kind": "protein_low", "dateKey": "2026-11-20"}]
            }
            """.trimIndent()
        )

        val backup = AiBackup.fromJson(json)

        assertEquals(1, backup.conversations.size)
        assertEquals(1, backup.messages.size)
        assertEquals(1, backup.memories.size)
        assertEquals(1, backup.nudges.size)
        assertEquals("c1", backup.conversations.first().id)
        assertEquals("m2", backup.messages.first().id)
        assertEquals("mem2", backup.memories.first().id)
        assertEquals("n2", backup.nudges.first().id)
        assertFalse(backup.isEmpty())
    }

    @Test
    fun `空段算空备份`() {
        assertTrue(AiBackup.fromJson(org.json.JSONObject("{}")).isEmpty())
        assertTrue(AiBackup.fromJson(null).isEmpty())
    }
}
