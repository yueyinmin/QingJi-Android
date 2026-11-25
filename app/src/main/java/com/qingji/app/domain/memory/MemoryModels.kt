package com.qingji.app.domain.memory

/**
 * 长期记忆的领域模型（阶段 I）。
 *
 * 刻意和 Room 实体分开：规则层只认这里的类型，换存储不影响记忆规则。
 */

/** 记忆分类：按「话题」分，不按可信度分；可信度看 [MemoryTruth]。 */
enum class MemoryCategory(val key: String, val label: String) {
    PREFERENCE("preference", "偏好"),
    TRAINING("training", "训练"),
    NUTRITION("nutrition", "饮食"),
    GOAL("goal", "目标"),
    EVENT("event", "事件"),
    INFERENCE("inference", "推断");

    companion object {
        fun fromKey(key: String): MemoryCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 可信度。
 *
 * [USER_FACT] 用户自己说的 / 正式记录派生的（本阶段只有用户口述）；
 * [CONFIRMED] 用户确认过的（包括把 AI 推断确认为事实）；
 * [INFERRED] AI 推断，**不能当事实用**，也不能被系统自动升级。
 */
enum class MemoryTruth(val label: String) {
    USER_FACT("用户事实"),
    CONFIRMED("已确认"),
    INFERRED("AI 推断")
}

enum class MemorySource(val label: String) {
    USER_INPUT("用户口述"),
    BUSINESS_RECORD("正式记录"),
    USER_CONFIRMED("用户确认"),
    AI_INFERRED("AI 推断")
}

enum class MemoryStatus(val label: String) {
    ACTIVE("生效中"),
    INVALIDATED("已失效"),
    EXPIRED("已过期")
}

data class MemoryRecord(
    val id: String,
    val category: MemoryCategory,
    val content: String,
    val truthType: MemoryTruth,
    val source: MemorySource,
    val sourceRef: String = "",
    val confidence: Double? = null,
    val status: MemoryStatus = MemoryStatus.ACTIVE,
    val expiresAt: Long = 0L,
    val dedupeKey: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 能不能参与本轮回答：只有生效中且没过期。 */
    fun isUsable(now: Long): Boolean =
        status == MemoryStatus.ACTIVE && (expiresAt <= 0L || expiresAt > now)
}

/** 注入给模型的一条记忆：只带必要字段，不把 id/sourceRef 泄漏进 prompt。 */
data class MemoryNote(
    val category: MemoryCategory,
    val text: String,
    val truth: MemoryTruth
)

/** 检索结果。truncated 表示因为条数/字数预算被截过，供界面或日志说明。 */
data class MemoryInjection(
    val notes: List<MemoryNote> = emptyList(),
    val truncated: Boolean = false
) {
    val isEmpty: Boolean get() = notes.isEmpty()
}
