package com.qingji.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.qingji.app.domain.BiometricAvailability
import com.qingji.app.domain.DiaryPrivacyState
import com.qingji.app.ui.theme.CardWhite
import com.qingji.app.ui.theme.QingJiGreen
import com.qingji.app.ui.theme.QingJiGreenDark
import com.qingji.app.ui.theme.TextPrimary
import com.qingji.app.ui.theme.TextSecondary
import com.qingji.app.ui.viewmodel.DiaryPrivacyViewModel

/**
 * 日记隐私锁统一保护容器（阶段 D/E）。
 *
 * 未解锁时：完全不渲染 children（真实正文/图片根本不进入组合），
 * 用占位锁卡替代——比 blur 更彻底：视觉、无障碍语义、长按复制均无敏感内容。
 * 已解锁时：原样渲染 children。
 *
 * 用法：DiaryPrivacyGate(vm) { 原来的敏感内容 }
 */
@Composable
fun DiaryPrivacyGate(
    vm: DiaryPrivacyViewModel,
    modifier: Modifier = Modifier,
    /** 锁定占位区文案（各页面可自定义，如"训练记录已隐藏"）。 */
    hiddenText: String = "日记内容已隐藏",
    content: @Composable () -> Unit
) {
    var showPasscode by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    // 未启用 → 放行；启用且已解锁且非后台 → 放行；其余遮蔽
    val shouldHide = vm.state != DiaryPrivacyState.Disabled &&
        !(vm.state == DiaryPrivacyState.Unlocked && !vm.backgroundMasked)

    if (!shouldHide) {
        content()
        return
    }
    // 关键点：未解锁时 content() 根本没有进入组合树，
    // 真实正文/图片/语义节点都不存在——比 blur+clearAndSetSemantics 更彻底。
    // 占位卡本身保留完整按钮语义，TalkBack 用户可正常点"生物识别/数字密码"。
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        DiaryLockPlaceholder(
            hiddenText = hiddenText,
            availability = vm.availability,
            message = vm.message,
            onBiometric = { ctx ->
                (ctx as? FragmentActivity)?.let { vm.authenticateWithBiometric(it) }
                    ?: run { vm.clearMessage() }
            },
            onPasscode = { showPasscode = true }
        )
    }

    // 数字密码输入框（所有页面复用，不在各模块重复实现）
    if (showPasscode) {
        AppDialog(
            title = "数字密码解锁",
            onDismiss = { showPasscode = false; pinInput = "" },
            confirmText = "解锁",
            onConfirm = {
                val ok = vm.verifyPasscode(pinInput)
                if (ok) { showPasscode = false; pinInput = "" }
            },
            confirmEnabled = pinInput.length >= com.qingji.app.domain.DiaryPasscode.MIN_LENGTH
        ) {
            OutlinedTextField(
                value = pinInput,
                onValueChange = { if (it.length <= com.qingji.app.domain.DiaryPasscode.MAX_LENGTH) pinInput = it.filter { c -> c.isDigit() } },
                label = { Text("${com.qingji.app.domain.DiaryPasscode.MIN_LENGTH}~${com.qingji.app.domain.DiaryPasscode.MAX_LENGTH}位数字密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(vm.message, fontSize = 12.sp, color = com.qingji.app.ui.theme.DangerRed)
            }
        }
    }
}

/** 统一锁定占位卡：锁图标 + 提示 + 生物识别/数字密码两个入口。 */
@Composable
private fun DiaryLockPlaceholder(
    hiddenText: String,
    availability: BiometricAvailability,
    message: String,
    onBiometric: (Context) -> Unit,
    onPasscode: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "日记已锁定",
            tint = QingJiGreen,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(hiddenText, fontSize = 15.sp, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("完成验证后查看", fontSize = 12.sp, color = TextSecondary)
        if (availability == BiometricAvailability.SUCCESS) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onBiometric(context) }) {
                Text("系统生物识别解锁")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPasscode) {
            Text("使用数字密码")
        }
        if (message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 11.sp, color = QingJiGreenDark, textAlign = TextAlign.Center)
        }
    }
}
