package com.qingji.app.ai.privacy

import android.content.Context
import com.qingji.app.data.local.ai.AiDatabase
import com.qingji.app.data.prefs.AiMemoryPrefs
import com.qingji.app.data.prefs.NudgePrefs

/**
 * ============================================================
 * 阶段 K：AI 数据一键清除
 * ------------------------------------------------------------
 * 清什么（都属于「AI 产生的数据」）：
 * - ai_conversation / ai_message / ai_tool_call / ai_token_usage
 * - ai_memory（长期记忆）
 * - ai_nudge（提醒历史，顺便把频控计数一起清掉）
 * - ai_memory_prefs（哪几类不再记）、ai_nudge_prefs（开关 / 静默 / 条数）
 *
 * 不清什么（属于「用户配置」）：
 * - AI 服务配置（服务商 / 模型 / 地址）与 API Key ——
 *   这两个由「恢复出厂」负责；一键清 AI 数据不该把用户辛苦配的 Key 一起删掉。
 * - 提案执行表在主库 ai_proposal_execution：它是执行台账，不是 AI 内容，
 *   随「恢复出厂」清；单清 AI 数据时保留，避免出现「台账没了但计划已改过」的对不上。
 * ============================================================
 */
object AiPrivacyCleaner {

    /** 清除前后的规模，用来给用户一个「到底删了什么」的交代。 */
    data class Report(
        val conversations: Int = 0,
        val messages: Int = 0,
        val toolCalls: Int = 0,
        val tokenUsage: Int = 0,
        val memories: Int = 0,
        val nudges: Int = 0
    ) {
        val isEmpty: Boolean
            get() = conversations == 0 && messages == 0 && toolCalls == 0 &&
                tokenUsage == 0 && memories == 0 && nudges == 0

        /** 一句话摘要；UI 直接显示。 */
        fun summary(): String = buildList {
            if (conversations > 0) add("$conversations 个会话")
            if (messages > 0) add("$messages 条消息")
            if (memories > 0) add("$memories 条记忆")
            if (nudges > 0) add("$nudges 条提醒")
            if (toolCalls > 0) add("$toolCalls 条工具记录")
            if (tokenUsage > 0) add("$tokenUsage 条用量记录")
        }.joinToString("、").ifBlank { "没有可清除的数据" }
    }

    /** 只看规模，不删除；给确认弹窗显示数量用。 */
    suspend fun inspect(context: Context): Report = inspect(AiDatabase.get(context))

    /** 真正清除。先统计再删，返回删除前的规模。 */
    suspend fun clearAll(context: Context): Report = clearAll(AiDatabase.get(context), context)

    /** 内部/测试用：库可注入，避免测试去碰真实文件库。 */
    internal suspend fun inspect(db: AiDatabase): Report {
        val dao = db.aiChatDao()
        return Report(
            conversations = dao.countConversations(),
            messages = dao.countAllMessages(),
            toolCalls = dao.countAllToolCalls(),
            tokenUsage = dao.countAllTokenUsage(),
            memories = db.aiMemoryDao().countAll(),
            nudges = db.aiNudgeDao().countAll()
        )
    }

    /**
     * 顺序：先统计（删完就数不出来了）→ 清 AI 库（clearAll 内部先子表后主表）
     * → 清两个独立 DataStore。任一环节失败都直接抛给调用方，不假装成功。
     */
    internal suspend fun clearAll(db: AiDatabase, context: Context): Report {
        val report = inspect(db)
        db.aiChatDao().clearAll()
        AiMemoryPrefs.clear(context)
        NudgePrefs.clear(context)
        return report
    }
}
