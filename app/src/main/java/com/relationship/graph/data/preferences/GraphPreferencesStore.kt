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
    val graphDisplayMode: Flow<GraphDisplayMode> = dataStore.data.map {
        it[GRAPH_DISPLAY_MODE]
            ?.let(GraphDisplayMode::valueOf)
            ?: GraphDisplayMode.SIMPLE
    }

    suspend fun setMyPersonId(personId: String) {
        dataStore.edit { it[MY_PERSON_ID] = personId }
    }

    suspend fun clearMyPersonId() {
        dataStore.edit { it.remove(MY_PERSON_ID) }
    }

    suspend fun setShowInferenceSuggestions(enabled: Boolean) {
        dataStore.edit { it[SHOW_INFERENCE_SUGGESTIONS] = enabled }
    }

    suspend fun setGraphDisplayMode(mode: GraphDisplayMode) {
        dataStore.edit { it[GRAPH_DISPLAY_MODE] = mode.name }
    }

    private companion object {
        val MY_PERSON_ID = stringPreferencesKey("my_person_id")
        val SHOW_INFERENCE_SUGGESTIONS =
            booleanPreferencesKey("show_inference_suggestions")
        val GRAPH_DISPLAY_MODE = stringPreferencesKey("graph_display_mode")
    }
}
