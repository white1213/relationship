package com.relationship.graph.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.graphPreferencesDataStore by preferencesDataStore(name = "graph_preferences")

class GraphPreferencesStore(context: Context) {
    private val dataStore = context.graphPreferencesDataStore

    val myPersonId: Flow<String?> = dataStore.data.map { it[MY_PERSON_ID] }
    val showInferenceSuggestions: Flow<Boolean> =
        dataStore.data.map { it[SHOW_INFERENCE_SUGGESTIONS] ?: true }

    suspend fun setMyPersonId(personId: String) {
        dataStore.edit { it[MY_PERSON_ID] = personId }
    }

    suspend fun clearMyPersonId() {
        dataStore.edit { it.remove(MY_PERSON_ID) }
    }

    suspend fun setShowInferenceSuggestions(enabled: Boolean) {
        dataStore.edit { it[SHOW_INFERENCE_SUGGESTIONS] = enabled }
    }

    private companion object {
        val MY_PERSON_ID = stringPreferencesKey("my_person_id")
        val SHOW_INFERENCE_SUGGESTIONS =
            booleanPreferencesKey("show_inference_suggestions")
    }
}
