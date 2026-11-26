package com.qingji.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.qingji.app.domain.input.NumericInputState

/**
 * ============================================================
 * 会「自动全选」的数字输入框（QJ-RECORD-INPUT-UNIFIED-V3 / B1）
 * ------------------------------------------------------------
 * 为什么不用 value: String 的受控输入框：
 * String 受控输入框表达不了「选区」——点击后光标永远在末尾，
 * 用户想改成 30 就得先删掉原来的 1800。这里用 TextFieldValue 管文本 + 选区，
 * 首次聚焦把整段选中，用户直接打字就是替换。
 *
 * 三条硬约束（工单 3.1 / 5.2）：
 * 1. 只在「聚焦那一刻」全选一次；每次重组都全选会让光标乱跳；
 * 2. 用户自己挪了光标之后不再干预（只有外部改值才把光标放到末尾）；
 * 3. 空串不回填 0 —— 中间态由调用方（NumericInputState）负责收尾。
 *
 * 只用于系统输入法场景（训练页）。食物与饮水仍用自绘数字键盘，
 * 但两边共用同一个 [NumericInputState]，业务规则各自保留。
 * ============================================================
 */
@Composable
fun SelfSelectingNumberField(
    state: NumericInputState,
    onStateChange: (NumericInputState) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 单位后缀，例如「分钟」「公里」「克」；null 时只显示标签里的单位 */
    suffix: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeDone: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.text, TextRange(0, state.text.length))) }
    var focused by remember { mutableStateOf(false) }

    // 外部改值（加减按钮 / 模式切换 / 失焦归一化）才同步进输入框；
    // 用户自己打字时 state.text 与 fieldValue.text 相同，不会抢光标。
    LaunchedEffect(state.text) {
        if (state.text != fieldValue.text) {
            val selection = if (state.pendingSelectAll) {
                TextRange(0, state.text.length)
            } else {
                TextRange(state.text.length)
            }
            fieldValue = TextFieldValue(state.text, selection)
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { raw ->
            // 过滤非法字符（粘贴带中文/空格也不会污染业务值）
            val clean = NumericInputState.sanitize(raw.text, state.decimals)
            fieldValue = if (clean == raw.text) {
                raw
            } else {
                // 被清洗过：光标落到末尾，避免选区和旧文本错位
                TextFieldValue(clean, TextRange(clean.length))
            }
            onStateChange(state.onTextChange(clean))
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (state.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                onImeDone()
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !focused) {
                    focused = true
                    // 首次聚焦：进编辑态 + 立刻全选（不等重组，避免闪一下光标在末尾）
                    onStateChange(state.beginEdit())
                    fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                } else if (!focusState.isFocused && focused) {
                    focused = false
                    // 失焦：空值/非法值按各自业务策略收尾，不留空串给业务层
                    onStateChange(state.endEdit())
                }
            }
    )
}
