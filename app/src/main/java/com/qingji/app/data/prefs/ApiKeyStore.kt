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
 * AI 的 API Key 加密存储（对应需求文档 1.4 的第 3 个坑）
 * ------------------------------------------------------------
 * 规矩：
 * 1. Key 绝不写进 Room 数据库（数据库文件可能被整包导出）。
 * 2. Key 绝不写日志、绝不随备份导出。
 * 3. 只用安卓系统自带的「Keystore」保管加密钥匙，密文存在普通
 *    SharedPreferences 里。别人就算把 App 数据拷走，没有手机里的
 *    那把钥匙也解不开。
 *
 * 为什么不用 EncryptedSharedPreferences：那个库是 alpha 版，
 * 在新系统上容易出兼容问题；这里用系统 API 自己写，60 行搞定，
 * 不额外引依赖，minSdk 26 完全支持。
 * ============================================================
 */
object ApiKeyStore {

    private const val PREF_NAME = "qingji_secure_prefs"
    private const val KEY_ALIAS = "qingji_ai_api_key"
    private const val KEY_CIPHER_TEXT = "cipher_text"
    private const val KEY_IV = "iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val GCM_IV_LENGTH = 12      // GCM 推荐的 IV 字节数
    private const val GCM_TAG_BITS = 128      // 认证标签位数，和加密侧一致

    /** 取出（或第一次创建）手机硬件里那把加密钥匙。 */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        // 已经建过钥匙就直接用，别重复生成（重复生成会导致旧密文解不开）
        val old = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (old != null) return old.secretKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
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

    /** 保存明文 Key：先加密，再把密文和随机 IV 存起来。传空字符串等于清空。 */
    fun save(context: Context, plainKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (plainKey.isBlank()) {
            clear(context)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainKey.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /**
     * 读出明文 Key。
     *
     * 换机、清过数据、升级系统、密钥被 Keystore 作废之后，这份密文就永远解不开了。
     * 以前只在 catch 里返回 null，残片还留在 SharedPreferences 里，
     * 结果是「明明解不开却一直显示已配置」，用户也没法重新填。
     * 现在遇到以下任一情况都先清干净再返回 null，让 isSet() 变 false：
     *   ① 密文 / IV 只存了一半或为空；
     *   ② Base64 坏了、密文为空、IV 长度不是 12 字节；
     *   ③ 解密抛异常（AEADBadTag / KeyStore 失效 / 数据被改）；
     *   ④ 解出来是空白字符串；
     *   ⑤ 连 SharedPreferences 本身都读不出来（文件损坏、字段被写成别的类型
     *      导致 getString 抛 ClassCastException）。
     * 全程不抛异常，App 不会因为一把旧钥匙打不开。
     *
     * 第 ⑤ 条是后补的：以前 getString 写在 try 外面，真遇到存坏的字段会直接把异常
     * 抛到界面上，设置页一进去就崩。现在整段读取都在保护里。
     */
    fun load(context: Context): String? = try {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cipherText = prefs.getString(KEY_CIPHER_TEXT, null)
        val ivText = prefs.getString(KEY_IV, null)

        if (cipherText.isNullOrBlank() || ivText.isNullOrBlank()) {
            // 两个字段必须成对出现；缺一半就是残片
            if (cipherText != null || ivText != null) clearQuietly(context)
            null
        } else {
            val plain = decode(cipherText, ivText)
            if (plain == null) clearQuietly(context)
            plain
        }
    } catch (e: Exception) {
        // 解密失败最常见的原因就是换机后 Keystore 里没有原来那把钥匙
        clearQuietly(context)
        null
    }

    /** 真正的解密。返回 null 表示密文格式不对，由 load 统一清理。 */
    private fun decode(cipherText: String, ivText: String): String? {
        val encrypted = Base64.decode(cipherText, Base64.NO_WRAP)
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        if (encrypted.isEmpty() || iv.size != GCM_IV_LENGTH) return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.isNotBlank() }
    }

    /** 清理时再出错也不能往外抛：load() 是「绝不崩」的兜底路径。 */
    private fun clearQuietly(context: Context) {
        runCatching { clear(context) }
    }

    fun isSet(context: Context): Boolean = !load(context).isNullOrBlank()

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CIPHER_TEXT)
            .remove(KEY_IV)
            .apply()
    }
}
