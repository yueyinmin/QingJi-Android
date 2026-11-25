package com.qingji.app.ai.memory

import com.qingji.app.data.ai.MemoryStore
import com.qingji.app.domain.memory.MemoryCategory
import com.qingji.app.domain.memory.MemoryInjection
import com.qingji.app.domain.memory.MemoryNote
import com.qingji.app.domain.memory.MemoryRecord
import com.qingji.app.domain.memory.MemorySource
import com.qingji.app.domain.memory.MemoryStatus
import com.qingji.app.domain.memory.MemoryTruth
import java.security.MessageDigest
import java.util.UUID

/**
 * 阶段 I：长期记忆规则层。
 *
 * 谁能写、写成什么、怎么读，全部在这里定，存储层只负责落库：
 *
 * 1. 只有**用户明确说「记住…」**才自动写，标 USER_FACT；聊天内容不会被自动收集；
 * 2. AI 推断只能写成 INFERRED，**没有任何自动升级路径**，必须用户点「确认」；
 * 3. 注入按任务类别筛选，只给少量、带可信度标签的记忆；
 * 4. 过期/失效/删除都不碰正式业务数据，删除会话也不会动记忆。
 */
object MemoryManager {

    /** 太短没有信息量（「记住我」之类），太长就不是一条结构化记忆了 */
    const val MIN_CONTENT_CHARS = 2
    const val MAX_CONTENT_CHARS = 120

    /** 单轮注入上限：条数与字数都卡住，避免把上下文撑爆 */
    const val MAX_INJECT_ITEMS = 6
    const val MAX_INJECT_CHARS = 500

    /** 推断类最多注入条数：宁可少说，也别把猜测堆成事实 */
    const val MAX_INFERENCE_ITEMS = 1

    /** 推断置信度上限；超过就不该以「推断」身份存在 */
    const val MAX_INFERRED_CONFIDENCE = 0.8

    /** 用户明确要求记住的说法，只认句首，避免把正文里的「记住」误判 */
    private val EXPLICIT_PREFIXES = listOf(
        "请帮我记住", "帮我记住", "请记住", "记住",
        "记一下", "记下来", "记录一下",
        "以后都", "以后记得", "今后都"
    )

    data class ExplicitCandidate(val content: String, val category: MemoryCategory)

    /** 句首命中「记住…」才返回候选；内容过短直接判无效。 */
    fun detectExplicit(text: String): ExplicitCandidate? {
        val raw = text.trim()
        val prefix = EXPLICIT_PREFIXES.firstOrNull { raw.startsWith(it) } ?: return null
        var content = raw.removePrefix(prefix)
            .trimStart('，', ',', '：', ':', '。', '、', ' ')
            .trim()
            .trimEnd('。', '.', '！', '!')
        if (content.length < MIN_CONTENT_CHARS) return null
        if (content.length > MAX_CONTENT_CHARS) content = content.take(MAX_CONTENT_CHARS)
        return ExplicitCandidate(content, detectCategory(content))
    }

    /** 话题归类：先看目标词，再看训练/饮食词，最后落到偏好。 */
    fun detectCategory(content: String): MemoryCategory {
        val text = content.lowercase()
        return when {
            listOf("目标", "减脂", "增肌", "减到", "增到", "体重", "公斤", "kg", "斤", "腰围").any { text.contains(it) } ->
                MemoryCategory.GOAL
            listOf("训练", "练", "跑步", "有氧", "力量", "深蹲", "卧推", "哑铃", "健身", "hiit", "拉伸", "组数", "次数").any { text.contains(it) } ->
                MemoryCategory.TRAINING
            listOf("吃", "喝", "食物", "菜", "饭", "蛋白", "碳水", "脂肪", "忌口", "过敏", "乳糖", "辣", "甜", "早餐", "午餐", "晚餐", "零食", "咖啡").any { text.contains(it) } ->
                MemoryCategory.NUTRITION
            else -> MemoryCategory.PREFERENCE
        }
    }

    /**
     * 用户明说「记住…」时写入。返回 null 表示没写：没触发词 / 该类被用户关掉 / 已经记过同样内容。
     */
    suspend fun captureExplicit(
        store: MemoryStore,
        text: String,
        sourceRef: String,
        disabled: Set<MemoryCategory> = emptySet(),
        now: Long = System.currentTimeMillis(),
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): MemoryRecord? {
        val candidate = detectExplicit(text) ?: return null
        if (candidate.category in disabled) return null
        return save(
            store = store,
            category = candidate.category,
            content = candidate.content,
            truth = MemoryTruth.USER_FACT,
            source = MemorySource.USER_INPUT,
            sourceRef = sourceRef,
            confidence = null,
            now = now,
            idFactory = idFactory
        )
    }

    /**
     * AI 观察到的内容只能以 INFERRED 落库，并且置信度必须 <= [MAX_INFERRED_CONFIDENCE]。
     * 本阶段没有自动观察入口（写工具禁用），这里只提供受控写入点给后续阶段用。
     */
    suspend fun captureInferred(
        store: MemoryStore,
        content: String,
        category: MemoryCategory,
        confidence: Double,
        sourceRef: String,
        disabled: Set<MemoryCategory> = emptySet(),
        now: Long = System.currentTimeMillis(),
        idFactory: () -> String = { UUID.randomUUID().toString() }
    ): MemoryRecord? {
        val trimmed = content.trim()
        if (trimmed.length < MIN_CONTENT_CHARS) return null
        if (category in disabled) return null
        if (confidence <= 0.0 || confidence > MAX_INFERRED_CONFIDENCE) return null
        val key = dedupeKey(category, trimmed)
        // 已经有用户事实/已确认的同内容，不许用推断覆盖
        store.findByDedupeKey(key)?.let { return null }
        return save(
            store = store,
            category = category,
            content = trimmed.take(MAX_CONTENT_CHARS),
            truth = MemoryTruth.INFERRED,
            source = MemorySource.AI_INFERRED,
            sourceRef = sourceRef,
            confidence = confidence,
            now = now,
            idFactory = idFactory
        )
    }

    /** 用户点「确认」：只有 INFERRED 能升级成 CONFIRMED；其它情况返回 false。 */
    suspend fun confirm(store: MemoryStore, record: MemoryRecord, now: Long = System.currentTimeMillis()): Boolean {
        if (record.truthType != MemoryTruth.INFERRED) return false
        if (record.status == MemoryStatus.EXPIRED) return false
        store.upgradeTruth(record.id, MemoryTruth.CONFIRMED, MemorySource.USER_CONFIRMED, now)
        return true
    }

    /**
     * 用户纠正内容。改成 USER_FACT 并记为用户确认来源；如果改完和另一条重复，拒绝写入。
     */
    suspend fun correct(
        store: MemoryStore,
        record: MemoryRecord,
        newContent: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val trimmed = newContent.trim()
        if (trimmed.length < MIN_CONTENT_CHARS) return false
        val capped = trimmed.take(MAX_CONTENT_CHARS)
        val category = detectCategory(capped)
        val key = dedupeKey(category, capped)
        val conflict = store.findByDedupeKey(key)
        if (conflict != null && conflict.id != record.id) return false
        store.updateContent(
            id = record.id,
            content = capped,
            category = category,
            truth = MemoryTruth.USER_FACT,
            source = MemorySource.USER_CONFIRMED,
            dedupeKey = key,
            updatedAt = now
        )
        return true
    }

    /** 标记失效（例如目标变了、计划换了）：保留历史，不删除。 */
    suspend fun invalidate(store: MemoryStore, record: MemoryRecord, now: Long = System.currentTimeMillis()) {
        store.updateStatus(record.id, MemoryStatus.INVALIDATED, now)
    }

    /** 用户显式删除。只删记忆本身，不动饮食/体重/训练等正式记录。 */
    suspend fun delete(store: MemoryStore, record: MemoryRecord) {
        store.delete(record.id)
    }

    /** 过期清理：到期的标 EXPIRED，之后不再参与注入。 */
    suspend fun expire(store: MemoryStore, now: Long = System.currentTimeMillis()): Int =
        store.expireOverdue(now)

    /**
     * 按任务检索：只读相关类别、只读可用记录，条数与字数都受限。
     * 推断类最多注入 [MAX_INFERENCE_ITEMS] 条，且一定会带 truth 标签。
     */
    suspend fun retrieve(
        store: MemoryStore,
        taskKey: String,
        now: Long = System.currentTimeMillis(),
        maxItems: Int = MAX_INJECT_ITEMS,
        maxChars: Int = MAX_INJECT_CHARS
    ): MemoryInjection {
        val categories = categoriesFor(taskKey)
        val candidates = store.activeByCategories(categories, maxItems * 3)
            .filter { it.isUsable(now) }

        val notes = mutableListOf<MemoryNote>()
        var chars = 0
        var inferred = 0
        var truncated = false

        for (record in candidates) {
            if (notes.size >= maxItems) {
                truncated = true
                break
            }
            if (record.truthType == MemoryTruth.INFERRED) {
                if (inferred >= MAX_INFERENCE_ITEMS) {
                    truncated = true
                    continue
                }
                inferred++
            }
            if (chars + record.content.length > maxChars) {
                truncated = true
                continue
            }
            notes += MemoryNote(record.category, record.content, record.truthType)
            chars += record.content.length
        }

        return MemoryInjection(notes = notes, truncated = truncated)
    }

    /** 任务 → 相关类别。宁可少给，也别把训练记忆塞进饮食问题里。 */
    fun categoriesFor(taskKey: String): Set<MemoryCategory> {
        val key = taskKey.lowercase()
        val base = mutableSetOf(MemoryCategory.PREFERENCE, MemoryCategory.INFERENCE)
        return when {
            listOf("饮食", "吃", "食物", "diet", "nutrition", "meal").any { key.contains(it) } ->
                base + MemoryCategory.NUTRITION
            listOf("训练", "运动", "training", "workout").any { key.contains(it) } ->
                base + MemoryCategory.TRAINING + MemoryCategory.GOAL
            listOf("体重", "身体", "统计", "进展", "body", "progress", "weight").any { key.contains(it) } ->
                base + MemoryCategory.GOAL
            else -> base + MemoryCategory.GOAL
        }
    }

    /** 去重键：类别 + 去掉空白与标点的内容，SHA-256。 */
    fun dedupeKey(category: MemoryCategory, content: String): String {
        val normalized = content.lowercase().filterNot { ch ->
            ch.isWhitespace() || ch in "，。,.!！?？、；;：:（）()【】[]"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${category.key}|$normalized".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun save(
        store: MemoryStore,
        category: MemoryCategory,
        content: String,
        truth: MemoryTruth,
        source: MemorySource,
        sourceRef: String,
        confidence: Double?,
        now: Long,
        idFactory: () -> String
    ): MemoryRecord? {
        val key = dedupeKey(category, content)
        store.findByDedupeKey(key)?.let { return null }
        val record = MemoryRecord(
            id = idFactory(),
            category = category,
            content = content,
            truthType = truth,
            source = source,
            sourceRef = sourceRef,
            confidence = confidence,
            status = MemoryStatus.ACTIVE,
            expiresAt = 0L,
            dedupeKey = key,
            createdAt = now,
            updatedAt = now
        )
        return if (store.insert(record)) record else null
    }
}
