package com.qingji.app.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.qingji.app.domain.BiometricAvailability
import com.qingji.app.domain.DiaryAuthResult

/**
 * 生物识别网关：把系统 BiometricPrompt 封装成可注入接口，便于单测用 Fake 替代。
 *
 * 认证器：BIOMETRIC_STRONG（指纹/系统认定的强生物特征）。
 * 不使用 DEVICE_CREDENTIAL：设备无生物能力时只能走数字密码。
 * 文案注意：BIOMETRIC_STRONG 可能包含指纹或系统认定的安全面容，
 * UI 统一说「系统生物识别」，不冒充具体模态。
 */
interface BiometricGateway {
    fun availability(context: Context): BiometricAvailability
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (DiaryAuthResult) -> Unit
    )
}

class SystemBiometricGateway : BiometricGateway {

    override fun availability(context: Context): BiometricAvailability {
        val can = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return when (can) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.SUCCESS
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.HW_NOT_PRESENT
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailability.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricAvailability.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricAvailability.UNKNOWN
            else -> BiometricAvailability.UNKNOWN
        }
    }

    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (DiaryAuthResult) -> Unit
    ) {
        // 能力前置检查：不可用时不弹 Prompt，直接回错误让上层降级数字密码
        if (availability(activity) != BiometricAvailability.SUCCESS) {
            onResult(DiaryAuthResult.Error)
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(DiaryAuthResult.Success)
            }
            override fun onAuthenticationFailed() {
                // 指纹不匹配：系统可能继续等待下一次尝试，这里只上报失败状态
                onResult(DiaryAuthResult.Failure)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val r = when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> DiaryAuthResult.Canceled
                    else -> DiaryAuthResult.Error // 含 LOCKOUT / LOCKOUT_PERMANENT / 系统错误
                }
                onResult(r)
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                // 明确提供"使用密码"负按钮：点击回到数字密码入口，不绕过隐私锁
                .setNegativeButtonText("使用数字密码")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }
}
