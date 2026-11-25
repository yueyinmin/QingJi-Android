package com.qingji.app.ai.proposal

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qingji.app.data.local.AppDatabase
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.local.ai.AiProposalEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProposalManagerTest {

    private open class FakeExecutor(
        private var shouldSucceed: Boolean = true
    ) : ProposalExecutor {
        override val supportedTypes = setOf("TEST_PLAN")
        var executeCount = 0
        var alreadyExecuted = false

        override suspend fun execute(proposal: AiProposalEntity): Boolean {
            executeCount++
            return shouldSucceed
        }
        override suspend fun alreadyExecuted(proposal: AiProposalEntity) = alreadyExecuted
        override suspend fun targetExists(proposal: AiProposalEntity) = true
        open override suspend fun currentVersion(proposal: AiProposalEntity): Long = proposal.baseVersion.toLong()
        fun failNext() { shouldSucceed = false }
    }

    private fun fixture(executor: FakeExecutor): Triple<ProposalManager, AppDatabase, AiDatabase> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val main = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val ai = Room.inMemoryDatabaseBuilder(app, AiDatabase::class.java).allowMainThreadQueries().build()
        return Triple(ProposalManager(main, ai, listOf(executor), clock = { 10_000L }), main, ai)
    }

    @Test
    fun `确认成功后重复确认不重复执行`() = runBlocking {
        val executor = FakeExecutor()
        val (manager, main, ai) = fixture(executor)
        try {
            manager.createProposal(
                id = "p1", proposalType = "TEST_PLAN", title = "调整计划", reason = "测试",
                beforeJson = "{}", afterJson = """{"version":4}""",
                targetTable = "training_plan", targetRecordId = "plan-1", baseVersion = 3L
            )

            assertEquals(ProposalStatus.EXECUTED, manager.confirm("p1").status)
            assertEquals(1, executor.executeCount)
            assertEquals(ProposalStatus.EXECUTED, manager.confirm("p1").status)
            assertEquals(1, executor.executeCount)
            assertEquals(1, main.aiProposalDao().findByIdempotencyKey(manager.idempotencyKey(
                ai.aiChatDao().proposal("p1")!!
            ))!!.id)
        } finally {
            main.close(); ai.close()
        }
    }

    @Test
    fun `CAS失败标STALE不写业务成功记录`() = runBlocking {
        val executor = FakeExecutor().apply { failNext() }
        val (manager, main, ai) = fixture(executor)
        try {
            manager.createProposal(
                id = "p2", proposalType = "TEST_PLAN", title = "调整计划", reason = "测试",
                beforeJson = "{}", afterJson = "{}",
                targetTable = "training_plan", targetRecordId = "plan-2", baseVersion = 1L
            )

            assertEquals(ProposalStatus.STALE, manager.confirm("p2").status)
            assertEquals(ProposalStatus.STALE, ai.aiChatDao().proposal("p2")!!.status)
            val record = main.aiProposalDao().latestByProposal("p2")!!
            assertEquals(ProposalStatus.STALE, record.status)
            assertEquals("VERSION_CONFLICT", record.errorCode)
        } finally {
            main.close(); ai.close()
        }
    }

    @Test
    fun `EXECUTING恢复已变更提案标记EXECUTED`() = runBlocking {
        val executor = FakeExecutor().apply { alreadyExecuted = true }
        val (manager, main, ai) = fixture(executor)
        try {
            manager.createProposal(
                id = "p3", proposalType = "TEST_PLAN", title = "调整计划", reason = "测试",
                beforeJson = "{}", afterJson = "{}",
                targetTable = "training_plan", targetRecordId = "plan-3", baseVersion = 1L
            )
            val proposal = ai.aiChatDao().proposal("p3")!!
            main.aiProposalDao().insertExecution(
                com.qingji.app.data.local.entity.AiProposalExecutionEntity(
                    proposalId = "p3",
                    proposalType = "TEST_PLAN",
                    status = ProposalStatus.EXECUTING,
                    idempotencyKey = manager.idempotencyKey(proposal),
                    createdAt = 1L
                )
            )

            manager.recoverExecuting(maxAgeMs = 1000L)

            assertEquals(ProposalStatus.EXECUTED, main.aiProposalDao().latestByProposal("p3")!!.status)
            assertEquals(ProposalStatus.EXECUTED, ai.aiChatDao().proposal("p3")!!.status)
        } finally {
            main.close(); ai.close()
        }
    }

    @Test
    fun `PENDING提案版本不匹配时对账为STALE`() = runBlocking {
        val executor = object : FakeExecutor() {
            override suspend fun currentVersion(proposal: AiProposalEntity): Long =
                proposal.baseVersion.toLong() + 1
        }
        val (manager, main, ai) = fixture(executor)
        try {
            manager.createProposal(
                id = "p4", proposalType = "TEST_PLAN", title = "调整计划", reason = "测试",
                beforeJson = "{}", afterJson = "{}",
                targetTable = "training_plan", targetRecordId = "plan-4", baseVersion = 1L
            )

            manager.reconcile()

            assertTrue(ai.aiChatDao().proposal("p4")!!.status == ProposalStatus.STALE)
        } finally {
            main.close(); ai.close()
        }
    }
}
