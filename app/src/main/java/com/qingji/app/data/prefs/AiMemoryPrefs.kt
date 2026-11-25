package com.qingji.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qingji.app.domain.memory.MemoryCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 长期记忆的用户开关（「不再记住这类内容」）。
 *
 * 单独一个 DataStore 文件，不塞进 qingji_settings：
 * 记忆开关和主题/单位无关，单独放便于整块清除，也避免主设置快照越滚越大。
 */
private val Context.aiMemoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_memory_prefs")

object AiMemoryPrefs {

    private val KEY_DISABLED = stringPreferencesKey("disabled_categories")

    /** 被关掉的类别，逗号分隔；空串 = 全部允许。 */
    fun observeDisabled(context: Context): Flow<Set<MemoryCategory>> =
        context.aiMemoryDataStore.data.map { prefs -> parse(prefs[KEY_DISABLED]) }

    suspend fun disabled(context: Context): Set<MemoryCategory> = observeDisabled(context).first()

    /** 只允许用户关掉「话题类」，推断类不单独开关（它本身就不能当事实用）。 */
    suspend fun setEnabled(context: Context, category: MemoryCategory, enabled: Boolean) {
        context.aiMemoryDataStore.edit { prefs ->
            val current = parse(prefs[KEY_DISABLED]).toMutableSet()
            if (enabled) current.remove(category) else current.add(category)
            prefs[KEY_DISABLED] = current.joinToString(",") { it.key }
        }
    }

    suspend fun clear(context: Context) {
        context.aiMemoryDataStore.edit { it.clear() }
    }

    private fun parse(raw: String?): Set<MemoryCategory> =
        raw.orEmpty()
            .split(',')
            .mapNotNull { MemoryCategory.fromKey(it.trim()) }
            .toSet()
}
