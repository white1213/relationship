package com.relationship.graph.data.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.relationship.graph.data.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.aiSettingsDataStore by preferencesDataStore(name = "ai_settings")

data class AiSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = "",
    val hasApiKey: Boolean = false,
    val consentGranted: Boolean = false,
    val extraPrompt: String = "",
    val promptReplaceDefault: Boolean = false,
) {
    val isReady: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && hasApiKey && consentGranted

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}

class AiSettingsStore(
    context: Context,
    private val secretStore: SecretStore,
) {
    private val dataStore = context.aiSettingsDataStore

    // Keystore 解密不能阻塞 DataStore 读取线程，统一放到 IO 上执行。
    val settings: Flow<AiSettings> = dataStore.data.map { preferences ->
        AiSettings(
            baseUrl = preferences[BASE_URL] ?: AiSettings.DEFAULT_BASE_URL,
            model = preferences[MODEL].orEmpty(),
            hasApiKey = !secretStore.getSecret(API_KEY_SECRET).isNullOrBlank(),
            consentGranted = preferences[CONSENT_GRANTED] ?: false,
            extraPrompt = preferences[EXTRA_PROMPT].orEmpty(),
            promptReplaceDefault = preferences[PROMPT_REPLACE_DEFAULT] ?: false,
        )
    }.flowOn(Dispatchers.IO)

    suspend fun apiKey(): String? = withContext(Dispatchers.IO) {
        secretStore.getSecret(API_KEY_SECRET)
    }

    suspend fun save(
        baseUrl: String,
        model: String,
        apiKey: String?,
        consentGranted: Boolean,
        extraPrompt: String = "",
        promptReplaceDefault: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (apiKey != null) {
            secretStore.putSecret(API_KEY_SECRET, apiKey.trim())
        }
        dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl.trim().ifBlank { AiSettings.DEFAULT_BASE_URL }
            preferences[MODEL] = model.trim()
            preferences[CONSENT_GRANTED] = consentGranted
            preferences[EXTRA_PROMPT] = extraPrompt
            preferences[PROMPT_REPLACE_DEFAULT] = promptReplaceDefault
        }
        Unit
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val CONSENT_GRANTED = booleanPreferencesKey("consent_granted")
        val EXTRA_PROMPT = stringPreferencesKey("extra_prompt")
        val PROMPT_REPLACE_DEFAULT = booleanPreferencesKey("prompt_replace_default")
        const val API_KEY_SECRET = "ai_api_key"
    }
}
