package com.qingji.app.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/**
 * 日记隐私锁 V1 · 数字密码安全原语（纯逻辑，可 JVM 单测；android.util.Base64 在 Robolectric 可用）。
 *
 * - PBKDF2WithHmacSHA256，每用户独立随机盐（16 字节），迭代 120000（API 26+ 支持）。
 * - 恒定时间比较，不写日志，不保存明文。
 * - 失败次数限制 + 冷却时间由 DiaryPasscodeGuard 管理（内存态，不持久化解锁）。
 */
object DiaryPasscode {
    const val ITERATIONS = 120_000
    const val KEY_LENGTH_BITS = 256
    const val SALT_BYTES = 16
    /** 密码长度 4~8 位数字；统一规则，UI 与校验共用。 */
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    data class Stored(val saltB64: String, val hashB64: String, val iterations: Int)

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun derive(pin: String, saltB64: String): String {
        val salt = Base64.getDecoder().decode(saltB64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(hash)
    }

    fun create(pin: String): Stored {
        require(isValidFormat(pin)) { "密码必须是 $MIN_LENGTH~$MAX_LENGTH 位数字" }
        val salt = newSalt()
        return Stored(salt, derive(pin, salt), ITERATIONS)
    }

    /** 恒定时间比较，避免时序侧信道。 */
    fun verify(pin: String, stored: Stored): Boolean {
        if (!isValidFormat(pin)) return false
        val actual = derive(pin, stored.saltB64)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            stored.hashB64.toByteArray(Charsets.UTF_8)
        )
    }
}
