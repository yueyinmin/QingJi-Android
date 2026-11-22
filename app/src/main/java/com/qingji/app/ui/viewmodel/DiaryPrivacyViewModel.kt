package com.qingji.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingji.app.biometric.BiometricGateway
import com.qingji.app.biometric.SystemBiometricGateway
import com.qingji.app.data.prefs.DiaryPrivacyStore
import com.qingji.app.domain.BiometricAvailability
import com.qingji.app.domain.DiaryAuthResult
import com.qingji.app.domain.DiaryPasscode
import com.qingji.app.domain.DiaryPasscodeGuard
import com.qingji.app.domain.DiaryPrivacyController
import com.qingji.app.domain.DiaryPrivacyState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 日记隐私锁统一 ViewModel（Activity 作用域）。
 *
 * 唯一持有 [DiaryPrivacyController]（内存解锁态）与密码失败限制；
 * 页面只读状态、只调用统一入口，不各自维护 Boolean。
 *
 * 进程重建 → 新 ViewModel → 新 Controller → Locked；解锁态绝不写入任何持久化存储。
 */
class DiaryPrivacyViewModel(
    app: Application
) : AndroidViewModel(app) {

    /**
     * 可注入网关；生产为 SystemBiometricGateway。
     *
     * 注意：不能把 gateway 放主构造函数并给默认值。Kotlin 会因此只生成
     * (Application, BiometricGateway[, ...]) 构造，而 AndroidViewModel 默认
     * factory 反射查找的是精确的 (Application) 构造——找不到即
     * NoSuchMethodException，覆盖安装后启动闪退。测试注入改用 [gatewayOverride]。
     */
    private var gatewayOverride: BiometricGateway? = null
    private val gateway: BiometricGateway
        get() = gatewayOverride ?: SystemBiometricGateway()

    /** 仅供测试注入 Fake 网关，不影响生产反射构造。 */
    fun setGatewayForTest(gateway: BiometricGateway) { gatewayOverride = gateway }

    val controller = DiaryPrivacyController {
        gateway.availability(getApplication()) == BiometricAvailability.SUCCESS
    }
    private val store = DiaryPrivacyStore
    private val guard = DiaryPasscodeGuard()

    /** Compose 观察用：状态变化触发重组（Controller 是普通对象，需显式快照）。 */
    var state by mutableStateOf(DiaryPrivacyState.Disabled)
        private set
    var backgroundMasked by mutableStateOf(false)
        private set
    var config by mutableStateOf(DiaryPrivacyStore.Config())
        private set
    var availability by mutableStateOf(BiometricAvailability.UNKNOWN)
        private set
    /** UI 提示（错误/冷却等），一次性消费由页面读后清空也可，这里保持简单字符串。 */
    var message by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            config = store.get(getApplication())
            availability = gateway.availability(getApplication())
            controller.onEnabledChanged(config.enabled)
            sync()
        }
    }

    private fun sync() {
        state = controller.state
        backgroundMasked = controller.backgroundMasked
    }

    fun refreshAvailability() {
        availability = gateway.availability(getApplication())
        if (availability == BiometricAvailability.SUCCESS) controller.onBiometricAvailableAgain()
        else controller.onBiometricUnavailable()
        sync()
    }

    // ---------- 导航/生命周期 ----------
    fun enterDiaryArea() { controller.enterDiaryArea(); sync() }
    fun leaveDiaryArea() { controller.leaveDiaryArea(); sync() }
    fun onEnterBackground() { controller.onEnterBackground(); sync() }
    fun onForeground() { controller.onForeground(); sync() }

    // ---------- 数字密码设置/修改/验证 ----------
    fun setupPasscode(pin: String, confirm: String, onDone: (Boolean) -> Unit) {
        if (pin != confirm) { message = "两次密码不一致"; onDone(false); return }
        if (!DiaryPasscode.isValidFormat(pin)) {
            message = "密码需 ${DiaryPasscode.MIN_LENGTH}~${DiaryPasscode.MAX_LENGTH} 位数字"
            onDone(false); return
        }
        viewModelScope.launch {
            store.enableWithPasscode(getApplication(), pin)
            config = store.get(getApplication())
            controller.onEnabledChanged(true)
            guard.reset()
            message = "日记隐私锁已开启"
            sync()
            onDone(true)
        }
    }

    fun changePasscode(oldPin: String, newPin: String, confirm: String, onDone: (Boolean) -> Unit) {
        val stored = config.passcode
        if (stored == null || !DiaryPasscode.verify(oldPin, stored)) {
            message = "原密码不正确"; onDone(false); return
        }
        if (newPin != confirm) { message = "两次密码不一致"; onDone(false); return }
        if (!DiaryPasscode.isValidFormat(newPin)) {
            message = "密码需 ${DiaryPasscode.MIN_LENGTH}~${DiaryPasscode.MAX_LENGTH} 位数字"
            onDone(false); return
        }
        viewModelScope.launch {
            store.changePasscode(getApplication(), newPin)
            config = store.get(getApplication())
            message = "密码已修改"; sync(); onDone(true)
        }
    }

    fun verifyPasscode(pin: String): Boolean {
        if (!guard.canAttempt()) {
            message = "尝试次数过多，请 ${(guard.cooldownRemainingMs / 1000 + 1)} 秒后重试"
            sync()
            return false
        }
        val stored = config.passcode
        val ok = stored != null && DiaryPasscode.verify(pin, stored)
        if (ok) {
            guard.onSuccess()
            val gen = controller.beginAuthentication()
            controller.onAuthenticated(gen)
            message = ""
        } else {
            guard.onFailure()
            message = if (guard.isCoolingDown) "密码错误次数过多，已暂时锁定"
                      else "密码错误，还可尝试 ${guard.remainingAttempts} 次"
        }
        sync()
        return ok
    }

    // ---------- 生物识别 ----------
    fun authenticateWithBiometric(
        activity: androidx.fragment.app.FragmentActivity,
        title: String = "解锁日记",
        subtitle: String = "使用系统生物识别验证"
    ) {
        refreshAvailability()
        if (availability != BiometricAvailability.SUCCESS) {
            message = when (availability) {
                BiometricAvailability.NONE_ENROLLED, BiometricAvailability.NO_BIOMETRICS ->
                    "设备未录入生物识别，请使用数字密码"
                BiometricAvailability.HW_NOT_PRESENT, BiometricAvailability.HW_UNAVAILABLE ->
                    "生物识别硬件不可用，请使用数字密码"
                else -> "系统生物识别不可用，请使用数字密码"
            }
            sync()
            return
        }
        val gen = controller.beginAuthentication()
        if (gen < 0) { sync(); return }
        gateway.authenticate(activity, title, subtitle) { result ->
            // 回调必须在主线程更新 Compose 状态（BiometricGateway 已用主线程 executor）
            when (result) {
                DiaryAuthResult.Success -> controller.onAuthenticated(gen)
                DiaryAuthResult.Failure -> controller.onAuthenticationFailed(gen)
                DiaryAuthResult.Canceled -> controller.onCanceled(gen)
                DiaryAuthResult.Error -> controller.onAuthenticationFailed(gen)
            }
            sync()
        }
    }

    fun cancelAuthentication() { controller.lock(); sync() }

    suspend fun disablePrivacy(onDone: (Boolean) -> Unit) {
        store.disable(getApplication())
        config = store.get(getApplication())
        guard.reset()
        controller.onEnabledChanged(false)
        message = "日记隐私锁已关闭"
        sync()
        onDone(true)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        store.setBiometricEnabled(getApplication(), enabled)
        config = store.get(getApplication())
        sync()
    }

    fun clearMessage() { message = "" }
}
