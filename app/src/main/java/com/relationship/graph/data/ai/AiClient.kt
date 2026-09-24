package com.relationship.graph.data.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiCompletion(
    val answer: String,
    val proposedActions: List<AiActionPayload>,
)

data class AiActionPayload(
    val type: String = "",
    val person: AiPersonPayload? = null,
    val relationship: AiRelationshipPayload? = null,
)

data class AiPersonPayload(
    val id: String? = null,
    val name: String? = null,
    val gender: String? = null,
    val phone: String? = null,
    val birthday: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

data class AiRelationshipPayload(
    val id: String? = null,
    val fromPersonId: String? = null,
    val toPersonId: String? = null,
    val relationTypeId: String? = null,
    val note: String? = null,
)

class AiClient(
    private val gson: Gson,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun complete(
        settings: AiSettings,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): Result<AiCompletion> = withContext(Dispatchers.IO) {
        runCatching {
            val requestJson = JsonObject().apply {
                addProperty("model", settings.model)
                addProperty("temperature", 0.2)
                add(
                    "messages",
                    gson.toJsonTree(
                        listOf(
                            mapOf("role" to "system", "content" to systemPrompt),
                            mapOf("role" to "user", "content" to userPrompt),
                        ),
                    ),
                )
            }
            val request = Request.Builder()
                .url(chatCompletionsUrl(settings.baseUrl))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(
                    gson.toJson(requestJson)
                        .toRequestBody("application/json; charset=utf-8".toMediaType()),
                )
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("AI 服务返回 ${response.code}：${body.take(300)}")
                }
                val root = gson.fromJson(body, JsonObject::class.java)
                val content = root.getAsJsonArray("choices")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
                    ?: error("AI 服务没有返回文本内容")
                parseCompletion(content)
            }
        }
    }

    private fun parseCompletion(content: String): AiCompletion {
        val jsonText = content
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = jsonText.indexOf('{')
        val end = jsonText.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching {
                val root = gson.fromJson(jsonText.substring(start, end + 1), JsonObject::class.java)
                val answer = root.get("answer")?.asString.orEmpty()
                val actions = root.getAsJsonArray("actions")?.mapNotNull { element ->
                    runCatching {
                        gson.fromJson(element, AiActionPayload::class.java)
                    }.getOrNull()
                }.orEmpty()
                if (answer.isNotBlank()) {
                    return AiCompletion(answer = answer, proposedActions = actions)
                }
            }
        }
        return AiCompletion(answer = content.trim(), proposedActions = emptyList())
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }
}
