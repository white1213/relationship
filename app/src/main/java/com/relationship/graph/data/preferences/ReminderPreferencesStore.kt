package com.relationship.graph.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.reminderPreferencesDataStore by preferencesDataStore(name = "reminder_preferences")

class ReminderPreferencesStore(context: Context) {
    private val dataStore = context.reminderPreferencesDataStore

    /** 提醒总开关；关闭后 Worker 直接返回，不计算也不通知。 */
    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: true }

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[ENABLED] = value }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
    }
}
