package com.qingji.app.domain

/**
 * 日记隐私锁 V1 · 统一状态机（纯 Kotlin，便于 JVM 单测）。
 *
 * 状态流转：
 *   Disabled → 正常显示（未启用）
 *   Locked → Authenticating → Unlocked（成功）/ Locked（失败/取消）
 *   Locked → Unavailable（设备无可用强认证能力）
 *   Unavailable → Locked（设备恢复，等待用户主动验证）
 *   离开日记区域：Unlocked/Authenticating → Locked
 *
 * 安全约束：
 *   - Unlocked 只在内存，由 Controller 持有，绝不序列化/持久化；
 *   - 迟到的认证回调（离开日记页/会话换代后）必须被丢弃（generation 机制）。
 */
enum class DiaryPrivacyState {
    Disabled, Locked, Authenticating, Unlocked, Unavailable
}

/** 当前验证方式（仅用于 UI 文案，不影响状态机正确性）。 */
enum class DiaryAuthMethod { BIOMETRIC, PASSCODE }

/** 生物识别能力探测结果（由 BiometricGateway 返回；单测用 Fake）。 */
enum class BiometricAvailability {
    SUCCESS,                 // 可用强认证器
    NO_BIOMETRICS,           // 未录入
    HW_NOT_PRESENT,          // 无硬件
    HW_UNAVAILABLE,          // 硬件临时不可用
    NONE_ENROLLED,           // 无已录入生物特征
    UNSUPPORTED,             // 系统版本不支持
    UNKNOWN                  // 其他错误
}

/** 认证结果（系统回调或数字密码验证统一映射）。 */
sealed interface DiaryAuthResult {
    data object Success : DiaryAuthResult
    data object Failure : DiaryAuthResult       // 指纹不匹配 / 密码错误
    data object Canceled : DiaryAuthResult      // 用户取消 / 主动切换密码
    data object Error : DiaryAuthResult         // 系统错误 / 锁定
}
