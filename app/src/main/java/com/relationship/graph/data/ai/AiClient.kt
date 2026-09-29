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

    // ===== 文本提取（v6）：只发送用户粘贴的文本，不含本地图谱 =====

    data class ExtractedPerson(
        val name: String,
        val gender: String? = null,
        val birthday: String? = null,
        val note: String? = null,
    )

    data class ExtractedRelation(
        val from: String,
        val to: String,
        val type: String? = null,
        val note: String? = null,
    )

    data class ExtractionPayload(
        val persons: List<ExtractedPerson> = emptyList(),
        val relations: List<ExtractedRelation> = emptyList(),
    )

    suspend fun extract(
        settings: AiSettings,
        apiKey: String,
        text: String,
    ): Result<ExtractionPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val requestJson = JsonObject().apply {
                addProperty("model", settings.model)
                addProperty("temperature", 0.0)
                add(
                    "messages",
                    gson.toJsonTree(
                        listOf(
                            mapOf("role" to "system", "content" to extractionSystemPrompt()),
                            mapOf("role" to "user", "content" to text),
                        ),
                    ),
                )
            }
            val request = Request.Builder()
                .url(chatCompletionsUrl(settings.baseUrl))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(requestJson).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("AI 服务返回 ${response.code}：${body.take(300)}")
                }
                val content = gson.fromJson(body, JsonObject::class.java)
                    .getAsJsonArray("choices")?.firstOrNull()
                    ?.asJsonObject?.getAsJsonObject("message")
                    ?.get("content")?.asString
                    ?: error("AI 服务没有返回文本内容")
                parseExtraction(content)
            }
        }
    }

    private fun extractionSystemPrompt(): String = """
        你是中文人际与家庭关系信息抽取助手。从用户粘贴的文本中抽取人物及其之间的关系。
        只输出一个 JSON 对象，不要输出任何解释：
        {"persons":[{"name":"姓名","gender":"MALE|FEMALE|UNSPECIFIED","birthday":"YYYY-MM-DD 或空","note":"补充信息"}],
         "relations":[{"from":"人物A姓名","to":"人物B姓名","type":"关系类型中文名","note":"备注"}]}
        要求：
        1. relations 的 from/to 必须是 persons 中出现过的姓名，逐字一致。
        2. type 使用常见中文关系词（如 父母、配偶、兄弟姐妹、朋友、同事、同学、邻居、祖父母、堂表亲），无法判断时用 相关。
        3. 文本中没提到的字段留空字符串，不要编造。
    """.trimIndent()

    private fun parseExtraction(content: String): ExtractionPayload {
        val jsonText = content.replace("```json", "").replace("```", "").trim()
        val start = jsonText.indexOf('{')
        val end = jsonText.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI 返回内容无法解析" }
        val root = gson.fromJson(jsonText.substring(start, end + 1), JsonObject::class.java)
        val persons = root.getAsJsonArray("persons")?.mapNotNull { element ->
            runCatching { gson.fromJson(element, ExtractedPerson::class.java) }.getOrNull()
        }.orEmpty().filter { it.name.isNotBlank() }
        val personNames = persons.map { it.name }.toSet()
        val relations = root.getAsJsonArray("relations")?.mapNotNull { element ->
            runCatching { gson.fromJson(element, ExtractedRelation::class.java) }.getOrNull()
        }.orEmpty().filter {
            it.from.isNotBlank() && it.to.isNotBlank() && it.from in personNames && it.to in personNames
        }
        return ExtractionPayload(persons = persons, relations = relations)
    }
}
