package com.qingji.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingji.app.domain.DiaryPasscode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 日记隐私锁安全配置存储。
 *
 * 与 qingji_settings 的边界：
 * - qingji_settings 存普通偏好（主题/单位等）；
 * - 本文件独立的 `diary_privacy.pb` 只存隐私锁必要配置：启用开关、密码派生结果(盐+hash)、指纹开关。
 * 仍是 DataStore Preferences 体系，不是新存储架构。
 *
 * 绝不保存：明文密码、解锁状态、生物识别结果、Keystore 密钥。
 */
private val Context.diaryPrivacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "diary_privacy")

object DiaryPrivacyStore {
    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_SALT = stringPreferencesKey("passcode_salt")
    private val KEY_HASH = stringPreferencesKey("passcode_hash")
    private val KEY_ITERATIONS = intPreferencesKey("passcode_iterations")
    private val KEY_BIOMETRIC = booleanPreferencesKey("biometric_enabled")

    data class Config(
        val enabled: Boolean = false,
        val passcode: DiaryPasscode.Stored? = null,
        val biometricEnabled: Boolean = false
    ) {
        val hasPasscode: Boolean get() = passcode != null
    }

    fun observe(context: Context): Flow<Config> =
        context.diaryPrivacyDataStore.data.map { p ->
            val salt = p[KEY_SALT]
            val hash = p[KEY_HASH]
            Config(
                enabled = p[KEY_ENABLED] ?: false,
                passcode = if (salt != null && hash != null)
                    DiaryPasscode.Stored(salt, hash, p[KEY_ITERATIONS] ?: DiaryPasscode.ITERATIONS)
                else null,
                biometricEnabled = p[KEY_BIOMETRIC] ?: false
            )
        }

    suspend fun get(context: Context): Config = observe(context).first()

    /** 首次启用：写入密码派生结果并开启锁。必须先通过密码设置流程调用。 */
    suspend fun enableWithPasscode(context: Context, pin: String) {
        val stored = DiaryPasscode.create(pin)
        context.diaryPrivacyDataStore.updateData { p ->
            p.toMutablePreferences().apply {
                this[KEY_ENABLED] = true
                this[KEY_SALT] = stored.saltB64
                this[KEY_HASH] = stored.hashB64
                this[KEY_ITERATIONS] = stored.iterations
            }.toPreferences()
        }
    }

    suspend fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.diaryPrivacyDataStore.updateData { p ->
            p.toMutablePreferences().apply { this[KEY_BIOMETRIC] = enabled }.toPreferences()
        }
    }

    suspend fun changePasscode(context: Context, pin: String) {
        val stored = DiaryPasscode.create(pin)
        context.diaryPrivacyDataStore.updateData { p ->
            p.toMutablePreferences().apply {
                this[KEY_SALT] = stored.saltB64
                this[KEY_HASH] = stored.hashB64
                this[KEY_ITERATIONS] = stored.iterations
            }.toPreferences()
        }
    }

    suspend fun disable(context: Context) {
        context.diaryPrivacyDataStore.updateData { p ->
            p.toMutablePreferences().apply {
                this[KEY_ENABLED] = false
                this[KEY_BIOMETRIC] = false
                remove(KEY_SALT); remove(KEY_HASH); remove(KEY_ITERATIONS)
            }.toPreferences()
        }
    }
}
