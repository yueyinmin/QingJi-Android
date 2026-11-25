package com.qingji.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingji.app.domain.nudge.NudgeKind
import com.qingji.app.domain.nudge.NudgePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 主动提醒的用户设置（J 阶段）。
 *
 * 单独一个 DataStore 文件，理由和长期记忆一样：这块设置与主题/单位无关，
 * 整块清除时不影响主设置，也不会把 qingji_settings 快照撑大。
 *
 * 默认值即「克制的默认」：开、每天最多 1 条、22:00～08:00 不打扰。
 */
private val Context.aiNudgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_nudge_prefs")

object NudgePrefs {

    private val KEY_ENABLED = booleanPreferencesKey("enabled")
    private val KEY_DISABLED_KINDS = stringPreferencesKey("disabled_kinds")
    private val KEY_DAILY_LIMIT = intPreferencesKey("daily_limit")
    private val KEY_SILENT_START = intPreferencesKey("silent_start_minute")
    private val KEY_SILENT_END = intPreferencesKey("silent_end_minute")

    /** 硬上限：再多就成骚扰了，UI 只提供 1～2 档。 */
    const val MAX_DAILY_LIMIT = 2

    fun observe(context: Context): Flow<NudgePolicy> =
        context.aiNudgeDataStore.data.map { prefs -> prefs.toPolicy() }

    suspend fun policy(context: Context): NudgePolicy = observe(context).first()

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.aiNudgeDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setKindEnabled(context: Context, kind: NudgeKind, enabled: Boolean) {
        context.aiNudgeDataStore.edit { prefs ->
            val current = parseKinds(prefs[KEY_DISABLED_KINDS]).toMutableSet()
            if (enabled) current.remove(kind) else current.add(kind)
            prefs[KEY_DISABLED_KINDS] = current.joinToString(",") { it.key }
        }
    }

    suspend fun setDailyLimit(context: Context, limit: Int) {
        context.aiNudgeDataStore.edit {
            it[KEY_DAILY_LIMIT] = limit.coerceIn(1, MAX_DAILY_LIMIT)
        }
    }

    /** 起止都按「当天第几分钟」存，支持跨午夜（start > end）。 */
    suspend fun setSilentWindow(context: Context, startMinute: Int, endMinute: Int) {
        context.aiNudgeDataStore.edit {
            it[KEY_SILENT_START] = startMinute.coerceIn(0, 24 * 60 - 1)
            it[KEY_SILENT_END] = endMinute.coerceIn(0, 24 * 60 - 1)
        }
    }

    suspend fun clear(context: Context) {
        context.aiNudgeDataStore.edit { it.clear() }
    }

    private fun Preferences.toPolicy(): NudgePolicy {
        val defaults = NudgePolicy()
        return NudgePolicy(
            enabled = this[KEY_ENABLED] ?: defaults.enabled,
            disabledKinds = parseKinds(this[KEY_DISABLED_KINDS]),
            dailyLimit = (this[KEY_DAILY_LIMIT] ?: defaults.dailyLimit)
                .coerceIn(1, MAX_DAILY_LIMIT),
            silentStartMinute = this[KEY_SILENT_START] ?: defaults.silentStartMinute,
            silentEndMinute = this[KEY_SILENT_END] ?: defaults.silentEndMinute,
            cooldownHours = defaults.cooldownHours
        )
    }

    private fun parseKinds(raw: String?): Set<NudgeKind> =
        raw.orEmpty()
            .split(',')
            .mapNotNull { NudgeKind.fromKey(it.trim()) }
            .toSet()
}
