package com.qingji.app.domain

/**
 * 日记隐私锁 V1 · 统一状态控制器（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 由 Activity 作用域 ViewModel 持有：进程存活期间保持；进程重建即全新实例 → Locked。
 * 不持久化 Unlocked。
 *
 * generation：每次「上锁 / 离开 / 切后台 / 开始新一轮认证」自增；
 * 迟到的认证回调携带旧 generation 一律丢弃，避免页面离开后错误解锁。
 */
class DiaryPrivacyController(
    private val BiometricAvailable: () -> Boolean = { true }
) {
    @Volatile
    private var _state: DiaryPrivacyState = DiaryPrivacyState.Disabled
    val state: DiaryPrivacyState get() = _state

    @Volatile
    private var _generation: Long = 0L
    val generation: Long get() = _generation

    /** 后台遮罩开关：进后台 true，回前台 false；不改变解锁会话本身。 */
    @Volatile
    private var _backgroundMasked: Boolean = false
    val backgroundMasked: Boolean get() = _backgroundMasked

    /** 当前是否处于日记区域（由导航层写入，不再依赖 onDispose）。 */
    @Volatile
    private var _inDiaryArea: Boolean = false
    val inDiaryArea: Boolean get() = _inDiaryArea

    /** 启用状态变化时调用：启用即 Locked（若生物不可用则 Unavailable）。 */
    fun onEnabledChanged(enabled: Boolean) {
        if (!enabled) {
            _generation++
            _state = DiaryPrivacyState.Disabled
            _inDiaryArea = false
            _backgroundMasked = false
        } else {
            // 启用：从 Disabled 进入 Locked/Unavailable（lock() 的 Disabled 保护是针对关闭态的）
            _generation++
            _state = if (BiometricAvailable()) DiaryPrivacyState.Locked else DiaryPrivacyState.Unavailable
        }
    }

    /** 进入日记区域：Disabled 保持 Disabled；否则 Locked（若能力不足 Unavailable）。 */
    fun enterDiaryArea() {
        _inDiaryArea = true
        if (_state == DiaryPrivacyState.Disabled) return
        if (_state == DiaryPrivacyState.Unlocked || _state == DiaryPrivacyState.Authenticating) {
            // 已在会话中（如内部切换/返回编辑页）不重新锁定
            return
        }
        _state = if (BiometricAvailable()) DiaryPrivacyState.Locked else DiaryPrivacyState.Unavailable
    }

    /**
     * 真正离开日记区域：立即锁定并废弃在途认证。
     * 由 currentRoute「日记区域→非日记区域」跃迁触发，内部弹窗/分类切换不会调用。
     */
    fun leaveDiaryArea() {
        _inDiaryArea = false
        lock()
    }

    fun lock() {
        _generation++          // 在途回调全部作废
        _backgroundMasked = false
        _state = if (_state == DiaryPrivacyState.Disabled) DiaryPrivacyState.Disabled
                 else if (BiometricAvailable()) DiaryPrivacyState.Locked
                 else DiaryPrivacyState.Unavailable
    }

    /** 开始认证：返回本次 generation，回调必须带回它。 */
    fun beginAuthentication(): Long {
        // 未启用或设备能力不足时不允许进入认证，防止绕过锁定
        if (_state == DiaryPrivacyState.Disabled || _state == DiaryPrivacyState.Unavailable) return -1L
        _generation++
        _state = DiaryPrivacyState.Authenticating
        return _generation
    }

    /** 用户取消/切换密码：回到 Locked（不解锁）。 */
    fun onCanceled(gen: Long): Boolean {
        if (gen != _generation || _state != DiaryPrivacyState.Authenticating) return false
        _state = if (BiometricAvailable()) DiaryPrivacyState.Locked else DiaryPrivacyState.Unavailable
        return true
    }

    /** 认证成功：仅当前 generation 且在 Authenticating 才解锁。 */
    fun onAuthenticated(gen: Long): Boolean {
        if (gen != _generation || _state != DiaryPrivacyState.Authenticating) return false
        if (!_inDiaryArea) { _state = DiaryPrivacyState.Locked; return false }
        _state = DiaryPrivacyState.Unlocked
        return true
    }

    /** 认证失败：保持锁定。 */
    fun onAuthenticationFailed(gen: Long): Boolean {
        if (gen != _generation || _state != DiaryPrivacyState.Authenticating) return false
        _state = if (BiometricAvailable()) DiaryPrivacyState.Locked else DiaryPrivacyState.Unavailable
        return true
    }

    fun onBiometricUnavailable() {
        if (_state == DiaryPrivacyState.Disabled) return
        _state = DiaryPrivacyState.Unavailable
    }

    fun onBiometricAvailableAgain() {
        if (_state != DiaryPrivacyState.Unavailable) return
        _state = DiaryPrivacyState.Locked
    }

    fun onEnterBackground() {
        _backgroundMasked = true
    }

    fun onForeground() {
        _backgroundMasked = false
        // 后台期间若已真正离开日记区域，保持锁定；会话本身不因普通切后台失效。
    }

    /** 内容是否应被遮蔽：未启用=false；已解锁且未后台=false；其他=true。 */
    fun shouldMask(): Boolean =
        _state != DiaryPrivacyState.Disabled &&
            (_state != DiaryPrivacyState.Unlocked || _backgroundMasked)

    /** 进程重建/会话重置（测试与极端场景用）。 */
    fun resetSession() {
        _generation++
        _state = if (_state == DiaryPrivacyState.Disabled) DiaryPrivacyState.Disabled else DiaryPrivacyState.Locked
        _inDiaryArea = false
        _backgroundMasked = false
    }
}
