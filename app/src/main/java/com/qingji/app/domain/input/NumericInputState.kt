package com.qingji.app.domain.input

import com.qingji.app.domain.NumericKeypadLogic

/**
 * ============================================================
 * 编辑中「空字符串」的收尾策略（QJ-RECORD-INPUT-UNIFIED-V3 / B1）
 * ------------------------------------------------------------
 * 训练时长允许清空，最终按 0 记；饮水/食物重量必须回到原值。
 * 这两种业务规则不同，但「清空 → 失焦」这个交互动作一样，
 * 所以把差异收敛成这一个枚举，而不是三个页面各写一套 if。
 * ============================================================
 */
enum class NumericEmptyPolicy {
    /** 空值失焦/提交时归一化为 [NumericInputState.emptyFallback]（训练时长 = 0）。 */
    NORMALIZE,

    /** 空值失焦时恢复编辑前的原值；提交时为空一律不合法（饮水、食物重量）。 */
    RESTORE_ORIGINAL
}

/**
 * ============================================================
 * 统一数字输入状态机（QJ-RECORD-INPUT-UNIFIED-V3 / B1）
 * ------------------------------------------------------------
 * 为什么单独抽一个纯逻辑类：
 * 训练时长（分钟）、食物重量（克）、饮水量（毫升）三个输入的
 * 「全选替换 / 允许临时为空 / 取消恢复 / 加减同步」行为必须一致，
 * 但业务下限、单位、空值收尾规则又各不相同。
 * 统一交互、不统一业务 —— 就是这个类的职责边界。
 *
 * 四个状态必须分清（工单 5.1）：
 * · [originalValue]  —— 进入编辑前的有效值，取消时恢复它；
 * · [text]           —— 当前编辑文本，允许为空、允许 "0." 这类中间态；
 * · [committedValue] —— 当前已确认的有效值（业务层只认它）；
 * · [isEditing]      —— 是否正在编辑。
 *
 * 纯 Kotlin、不依赖 Compose / Android，可直接 JVM 单测。
 * Compose 侧由 [com.qingji.app.ui.components.SelfSelectingNumberField]
 * 把 [pendingSelectAll] 翻译成 TextRange 全选。
 * ============================================================
 */
data class NumericInputState(
    val min: Double = 0.0,
    val max: Double = 600.0,
    val decimals: Int = 0,
    val emptyPolicy: NumericEmptyPolicy = NumericEmptyPolicy.NORMALIZE,
    val emptyFallback: Double = 0.0,
    val originalValue: Double = 0.0,
    val committedValue: Double = 0.0,
    val text: String = "",
    val isEditing: Boolean = false,
    /** 下一次聚焦需要把内容整段选中（Compose 侧消费，自绘键盘忽略）。 */
    val pendingSelectAll: Boolean = false
) {

    /** 当前文本解析出的数值；空串 / "0." / 非法字符返回 null（≠ 0）。 */
    val value: Double? get() = NumericKeypadLogic.asDouble(text)

    val isBlank: Boolean get() = text.isBlank()

    /** 当前文本能否直接提交（空值一律 false，主动输入的 0 只要在范围内就是 true）。 */
    val canSubmit: Boolean get() = value?.let { it >= min && it <= max } ?: false

    /** 进入编辑：保留原值、请求全选，稍后由输入框把选区设成整段。 */
    fun beginEdit(): NumericInputState = copy(
        text = if (text.isNotBlank()) text else formatValue(committedValue, decimals),
        originalValue = committedValue,
        isEditing = true,
        pendingSelectAll = true
    )

    /**
     * 系统输入框的文字变化。
     * 这里不把空串回填成 0 —— 一回填用户就永远打不出两位数（工单 5.3 明令禁止）。
     */
    fun onTextChange(raw: String): NumericInputState = copy(
        text = sanitize(raw, decimals),
        isEditing = true,
        pendingSelectAll = false
    )

    /**
     * 自绘键盘：追加一位数字。
     * 刚进入编辑（[pendingSelectAll] = true，等价于「已全选」）时，
     * 第一位数字直接替换整段内容 —— 100 打 1 就是 1，不会变成 1001。
     */
    fun onDigit(digit: Char): NumericInputState {
        if (!digit.isDigit()) return this
        if (pendingSelectAll) {
            val onlyZero = digit == '0'
            return copy(
                text = if (onlyZero) "0" else digit.toString(),
                isEditing = true,
                pendingSelectAll = false
            )
        }
        return onTextChange(NumericKeypadLogic.appendDigit(text, digit, decimals))
    }

    /** 自绘键盘：小数点（整数模式会被忽略）；全选状态下从 "0." 起算。 */
    fun onDot(): NumericInputState {
        if (pendingSelectAll) {
            return copy(
                text = if (decimals > 0) "0." else "0",
                isEditing = true,
                pendingSelectAll = false
            )
        }
        return onTextChange(NumericKeypadLogic.appendDot(text, decimals))
    }

    /** 自绘键盘：退格；全选状态下等价于清空。 */
    fun onBackspace(): NumericInputState {
        if (pendingSelectAll) {
            return copy(text = "", isEditing = true, pendingSelectAll = false)
        }
        return onTextChange(NumericKeypadLogic.backspace(text))
    }

    /**
     * 加减按钮。基准值取「当前文本」；文本为空/非法时退回最近一次确认值，
     * 避免 NaN、避免从 0 起算把 200 变成 100。
     * 结果同时写进 text 与 committedValue，保证界面显示值与保存值一致。
     */
    fun applyDelta(delta: Double): NumericInputState {
        val base = value ?: committedValue
        val next = (base + delta).coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min))
        return copy(
            text = formatValue(next, decimals),
            committedValue = next,
            isEditing = false,
            pendingSelectAll = false
        )
    }

    /** 直接写入一个确定值（快捷档位、模式切换、外部同步）。 */
    fun setValue(newValue: Double): NumericInputState {
        val clamped = newValue.coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min))
        return copy(
            text = formatValue(clamped, decimals),
            committedValue = clamped,
            isEditing = false,
            pendingSelectAll = false
        )
    }

    /**
     * 失焦归一化（工单 5.3）。
     * 合法 → 提交该值；为空/非法 → 按空值策略收尾，绝不留下空字符串进业务层。
     */
    fun endEdit(): NumericInputState {
        val v = value
        val resolved = if (v != null && v >= min && v <= max) {
            v
        } else when (emptyPolicy) {
            NumericEmptyPolicy.NORMALIZE -> (v ?: emptyFallback).coerceIn(min, max)
            NumericEmptyPolicy.RESTORE_ORIGINAL -> originalValue.coerceIn(min, max)
        }
        return copy(
            text = formatValue(resolved, decimals),
            committedValue = resolved,
            isEditing = false,
            pendingSelectAll = false
        )
    }

    /** 提交：非法返回 null，调用方据此禁用按钮 / 提示，不得落库。 */
    fun commit(): NumericInputState? = if (canSubmit) endEdit().copy(isEditing = false) else null

    /** 取消编辑：恢复进入编辑前的值。 */
    fun cancelEdit(): NumericInputState = copy(
        text = formatValue(originalValue, decimals),
        committedValue = originalValue,
        isEditing = false,
        pendingSelectAll = false
    )

    /** 外部重置（换食物、换运动项目、重新打开弹层）。 */
    fun resetTo(newValue: Double): NumericInputState = copy(
        originalValue = newValue.coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min)),
        committedValue = newValue.coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min)),
        text = formatValue(newValue, decimals),
        isEditing = false,
        pendingSelectAll = false
    )

    companion object {

        /** 常用构造：默认显示 [value] 对应的文本，非编辑态。 */
        fun of(
            value: Double,
            min: Double,
            max: Double,
            decimals: Int = 0,
            emptyPolicy: NumericEmptyPolicy = NumericEmptyPolicy.NORMALIZE,
            emptyFallback: Double = 0.0
        ): NumericInputState {
            val clamped = value.coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min))
            return NumericInputState(
                min = min,
                max = max,
                decimals = decimals,
                emptyPolicy = emptyPolicy,
                emptyFallback = emptyFallback,
                originalValue = clamped,
                committedValue = clamped,
                text = formatValue(clamped, decimals)
            )
        }

        /**
         * 文本清洗：只留数字和最多一个小数点（decimals = 0 时连点都不留）。
         * 粘贴进来的中文、空格、减号一律丢掉；前导零归一化（030 → 30），
         * 但单独的 "0"、"0.5" 保持原样。
         */
        fun sanitize(raw: String, decimals: Int): String {
            val sb = StringBuilder()
            var hasDot = false
            var fraction = 0
            var integerDigits = 0
            for (c in raw) {
                when {
                    c.isDigit() -> {
                        if (hasDot) {
                            if (decimals <= 0 || fraction >= decimals) continue
                            fraction++
                        } else {
                            // 整数位上限 7 位：够 3000g / 600 分钟 / 3000ml，也挡住无意义长串
                            if (integerDigits >= 7) continue
                            integerDigits++
                        }
                        sb.append(c)
                    }
                    (c == '.' || c == '。' || c == '·') && decimals > 0 && !hasDot -> {
                        hasDot = true
                        sb.append('.')
                    }
                    else -> Unit
                }
            }
            var out = sb.toString()
            if (out.startsWith(".")) out = "0$out"
            // 前导零：整数部分多于一位时去掉多余 0（030 → 30），全 0 保留一个
            if (out.contains('.') || out.length > 1) {
                val head = out.substringBefore('.')
                val tail = if (out.contains('.')) "." + out.substringAfter('.') else ""
                val stripped = head.trimStart('0')
                out = when {
                    stripped.isEmpty() -> "0$tail"
                    else -> stripped + tail
                }
            }
            return out
        }

        /** 数值 → 文本；复用全 App 唯一的小数格式化口径（去掉无意义的 .0）。 */
        fun formatValue(value: Double, decimals: Int): String =
            NumericKeypadLogic.format(value, decimals)
    }
}
