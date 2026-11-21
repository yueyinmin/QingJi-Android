package com.qingji.app.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ============================================================
 * AI 的 API Key 加密存储（AI 多服务改造）
 * ------------------------------------------------------------
 * 规矩（不变）：
 * 1. Key 绝不写进 Room 数据库（数据库文件可能被整包导出）；
 * 2. Key 绝不写日志、绝不随备份导出；
 * 3. 只用安卓系统自带 Keystore 保管加密钥匙，密文存普通 SharedPreferences。
 *
 * 多服务改造：每个配置按 configId 独立一组密文/IV（key 名带 id 后缀），
 * 互不覆盖；Keystore 仍共用同一把 alias（密文与 IV 已按 id 分离，安全等价，
 * 且避免每个 id 一把钥匙带来的 Keystore 膨胀）。
 *
 * 旧数据迁移：老版本只有一组固定 cipher_text / iv（单服务）。首次按 id 读取时，
 * 若发现旧字段存在而该 id 的新字段不存在，自动把旧密文/IV 迁移到 id（老配置 id=1），
 * 并清掉旧字段，保证新旧读取不互相覆盖、老用户无需重填 Key。
 * 迁移失败（解不开）时清残片并返回 null，由调用方明确提示重新配置，绝不静默显示已保存。
 * ============================================================
 */
object ApiKeyStore {

    private const val PREF_NAME = "qingji_secure_prefs"
    private const val KEY_ALIAS = "qingji_ai_api_key"
    // 旧单服务固定 key（迁移源）
    private const val LEGACY_CIPHER = "cipher_text"
    private const val LEGACY_IV = "iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private fun cipherKey(id: Int) = "cipher_text_$id"
    private fun ivKey(id: Int) = "iv_$id"

    /** 取出（或第一次创建）手机硬件里那把加密钥匙。 */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val old = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (old != null) return old.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** 保存某个配置的明文 Key。传空白 = 清该配置的 Key，不影响其它配置。 */
    fun save(context: Context, configId: Int, plainKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (plainKey.isBlank()) {
            clear(context, configId)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(cipherKey(configId), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey(configId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /**
     * 读某个配置的明文 Key。整段保护、绝不崩（同旧实现的 5 类异常恢复）。
     * 读取前先尝试旧字段迁移（仅当该 id 的新字段都不存在时）。
     */
    fun load(context: Context, configId: Int): String? = try {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        migrateLegacyIfNeeded(prefs, configId)
        val cipherText = prefs.getString(cipherKey(configId), null)
        val ivText = prefs.getString(ivKey(configId), null)
        if (cipherText.isNullOrBlank() || ivText.isNullOrBlank()) {
            if (cipherText != null || ivText != null) clearQuietly(context, configId)
            null
        } else {
            val plain = decode(cipherText, ivText)
            if (plain == null) clearQuietly(context, configId)
            plain
        }
    } catch (e: Exception) {
        clearQuietly(context, configId)
        null
    }

    /**
     * 把旧单服务 Key（cipher_text/iv）迁移到指定 id。
     * 条件：该 id 的新字段都还没有，且旧字段成对存在。
     * 迁移后清掉旧字段，避免新旧逻辑互相覆盖。
     * 迁移失败不抛（由 load 的兜底处理），返回 false。
     */
    private fun migrateLegacyIfNeeded(
        prefs: android.content.SharedPreferences,
        configId: Int
    ): Boolean {
        if (prefs.contains(cipherKey(configId)) || prefs.contains(ivKey(configId))) return false
        val legacyCipher = prefs.getString(LEGACY_CIPHER, null)
        val legacyIv = prefs.getString(LEGACY_IV, null)
        if (legacyCipher.isNullOrBlank() || legacyIv.isNullOrBlank()) return false
        // 先验证旧密文能解开， solvable 才迁移；解不开就交给 load 清残片并提示重配
        if (decode(legacyCipher, legacyIv) == null) return false
        prefs.edit()
            .putString(cipherKey(configId), legacyCipher)
            .putString(ivKey(configId), legacyIv)
            .remove(LEGACY_CIPHER)
            .remove(LEGACY_IV)
            .apply()
        return true
    }

    /** 兼容旧签名：默认读 id=1（老配置），供尚未改造的调用方过渡。 */
    fun load(context: Context): String? = load(context, 1)
    fun isSet(context: Context): Boolean = isSet(context, 1)
    fun clear(context: Context) = clear(context, 1)

    private fun decode(cipherText: String, ivText: String): String? {
        val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        if (encrypted.isEmpty() || iv.size != GCM_IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    private fun clearQuietly(context: Context, configId: Int) {
        runCatching { clear(context, configId) }
    }

    fun isSet(context: Context, configId: Int): Boolean = !load(context, configId).isNullOrBlank()

    fun clear(context: Context, configId: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(cipherKey(configId))
            .remove(ivKey(configId))
            .apply()
    }

    /** 恢复出厂：清掉所有配置的 Key + 旧字段。Key 名统一前缀，逐条删。 */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().remove(LEGACY_CIPHER).remove(LEGACY_IV)
        prefs.all.keys.filter { it.startsWith("cipher_text_") || it.startsWith("iv_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
