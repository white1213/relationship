package com.relationship.graph.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.relationship.graph.RelationshipApplication
import com.relationship.graph.data.ai.AiActionPayload
import com.relationship.graph.data.ai.AiPersonPayload
import com.relationship.graph.data.ai.AiRelationshipPayload
import com.relationship.graph.data.ai.AiSettings
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.MarriageKinshipMode
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.RelationshipSource
import com.relationship.graph.data.inference.InferenceEngine
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AiMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

data class AiProposedAction(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val person: AiPersonPayload? = null,
    val relationship: AiRelationshipPayload? = null,
)

data class AiChatMessage(
    val id: String,
    val role: AiMessageRole,
    val text: String,
    val proposedActions: List<AiProposedAction> = emptyList(),
)

data class AiAssistantUiState(
    val settings: AiSettings = AiSettings(),
    val messages: List<AiChatMessage> = emptyList(),
    val isSending: Boolean = false,
)

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RelationshipApplication
    private val repository = app.container.repository
    private val settingsStore = app.container.aiSettingsStore
    private val client = app.container.aiClient
    private val gson = Gson()
    private val messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val sending = MutableStateFlow(false)

    val uiState: StateFlow<AiAssistantUiState> = combine(
        settingsStore.settings,
        messages,
        sending,
    ) { settings, messages, sending ->
        AiAssistantUiState(
            settings = settings,
            messages = messages,
            isSending = sending,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiAssistantUiState(),
    )

    fun saveSettings(
        baseUrl: String,
        model: String,
        apiKey: String?,
        consentGranted: Boolean,
    ) {
        viewModelScope.launch {
            settingsStore.save(baseUrl, model, apiKey, consentGranted)
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isBlank() || sending.value) return
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val apiKey = settingsStore.apiKey()
            if (!settings.isReady || apiKey.isNullOrBlank()) {
                messages.value = messages.value + AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = AiMessageRole.SYSTEM,
                    text = "请先在 AI 设置中填写接口地址、模型和 API Key，并确认数据发送授权。",
                )
                return@launch
            }
            messages.value = messages.value + AiChatMessage(
                id = UUID.randomUUID().toString(),
                role = AiMessageRole.USER,
                text = message,
            )
            sending.value = true
            val result = client.complete(
                settings = settings,
                apiKey = apiKey,
                systemPrompt = systemPrompt(),
                userPrompt = buildUserPrompt(message),
            )
            sending.value = false
            result.onSuccess { completion ->
                val proposals = completion.proposedActions.mapNotNull(::toProposal)
                messages.value = messages.value + AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = AiMessageRole.ASSISTANT,
                    text = completion.answer,
                    proposedActions = proposals,
                )
            }.onFailure {
                messages.value = messages.value + AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = AiMessageRole.SYSTEM,
                    text = it.message ?: "AI 请求失败",
                )
            }
        }
    }

    fun confirmAction(messageId: String, action: AiProposedAction) {
        viewModelScope.launch {
            runCatching { executeAction(action) }
                .onSuccess {
                    removeAction(messageId, action.id)
                    messages.value = messages.value + AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiMessageRole.SYSTEM,
                        text = "已执行：${action.title}",
                    )
                }
                .onFailure {
                    messages.value = messages.value + AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = AiMessageRole.SYSTEM,
                        text = "执行失败：${it.message ?: "参数无效"}",
                    )
                }
        }
    }

    fun rejectAction(messageId: String, actionId: String) {
        removeAction(messageId, actionId)
    }

    fun clearConversation() {
        messages.value = emptyList()
    }

    private suspend fun executeAction(action: AiProposedAction) {
        when (action.type) {
            "create_person" -> createPerson(action.person)
            "update_person" -> updatePerson(action.person)
            "add_relationship" -> addRelationship(action.relationship)
            "update_relationship" -> updateRelationship(action.relationship)
            else -> error("不支持的 AI 操作")
        }
    }

    private suspend fun createPerson(payload: AiPersonPayload?) {
        val personPayload = payload ?: error("缺少人物参数")
        val name = personPayload.name?.trim().orEmpty()
        require(name.isNotBlank()) { "缺少人物姓名" }
        val now = System.currentTimeMillis()
        repository.savePerson(
            person = PersonEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                gender = personPayload.gender.toGender(),
                phone = personPayload.phone.orEmpty(),
                birthday = personPayload.birthday.orEmpty(),
                address = personPayload.address.orEmpty(),
                notes = personPayload.notes.orEmpty(),
                createdAt = now,
                updatedAt = now,
            ),
            tagNames = emptyList(),
        )
    }

    private suspend fun updatePerson(payload: AiPersonPayload?) {
        val personPayload = payload ?: error("缺少人物参数")
        val personId = personPayload.id?.takeIf(String::isNotBlank) ?: error("缺少人物 ID")
        val existing = repository.getPerson(personId) ?: error("人物不存在")
        repository.savePerson(
            person = existing.copy(
                name = personPayload.name?.trim()?.takeIf(String::isNotBlank) ?: existing.name,
                gender = personPayload.gender?.let { it.toGender() } ?: existing.gender,
                phone = personPayload.phone ?: existing.phone,
                birthday = personPayload.birthday ?: existing.birthday,
                address = personPayload.address ?: existing.address,
                notes = personPayload.notes ?: existing.notes,
            ),
            tagNames = repository.getTagNamesForPerson(personId),
        )
    }

    private suspend fun addRelationship(payload: AiRelationshipPayload?) {
        val relationship = payload ?: error("缺少关系参数")
        val fromPersonId = relationship.fromPersonId?.takeIf(String::isNotBlank)
            ?: error("缺少关系起点")
        val toPersonId = relationship.toPersonId?.takeIf(String::isNotBlank)
            ?: error("缺少关系终点")
        val relationTypeId = relationship.relationTypeId?.takeIf(String::isNotBlank)
            ?: error("缺少关系类型")
        val fromPerson = repository.getPerson(fromPersonId) ?: error("关系起点不存在")
        val toPerson = repository.getPerson(toPersonId) ?: error("关系终点不存在")
        val relationType = repository.relationTypes.first().firstOrNull {
            it.id == relationTypeId
        } ?: error("关系类型不存在")
        repository.saveRelationship(
            RelationshipEntity(
                id = UUID.randomUUID().toString(),
                fromPersonId = fromPersonId,
                toPersonId = toPersonId,
                relationTypeId = relationTypeId,
                source = RelationshipSource.MANUAL,
                marriageKinshipMode = if (
                    RelationshipSemantics.kind(relationType) == FamilyRelationKind.SPOUSE
                ) {
                    defaultMarriageMode(fromPerson.gender, toPerson.gender)
                } else {
                    MarriageKinshipMode.RESPECTIVE
                },
                note = relationship.note.orEmpty(),
            ),
        )
    }

    private suspend fun updateRelationship(payload: AiRelationshipPayload?) {
        val relationshipPayload = payload ?: error("缺少关系参数")
        val relationshipId = relationshipPayload.id?.takeIf(String::isNotBlank)
            ?: error("缺少关系 ID")
        val existing = repository.getRelationship(relationshipId) ?: error("关系不存在")
        val fromPersonId = relationshipPayload.fromPersonId?.takeIf(String::isNotBlank)
            ?: existing.fromPersonId
        val toPersonId = relationshipPayload.toPersonId?.takeIf(String::isNotBlank)
            ?: existing.toPersonId
        val relationTypeId = relationshipPayload.relationTypeId?.takeIf(String::isNotBlank)
            ?: existing.relationTypeId
        val relationType = repository.relationTypes.first().firstOrNull {
            it.id == relationTypeId
        } ?: error("关系类型不存在")
        val fromPerson = repository.getPerson(fromPersonId) ?: error("关系起点不存在")
        val toPerson = repository.getPerson(toPersonId) ?: error("关系终点不存在")
        repository.saveRelationship(
            existing.copy(
                fromPersonId = fromPersonId,
                toPersonId = toPersonId,
                relationTypeId = relationTypeId,
                marriageKinshipMode = if (
                    RelationshipSemantics.kind(relationType) == FamilyRelationKind.SPOUSE
                ) {
                    if (
                        existing.relationTypeId == relationTypeId &&
                        RelationshipSemantics.kind(
                            repository.relationTypes.first().firstOrNull {
                                it.id == existing.relationTypeId
                            },
                        ) == FamilyRelationKind.SPOUSE
                    ) {
                        existing.marriageKinshipMode
                    } else {
                        defaultMarriageMode(fromPerson.gender, toPerson.gender)
                    }
                } else {
                    existing.marriageKinshipMode
                },
                note = relationshipPayload.note ?: existing.note,
            ),
        )
    }

    private fun toProposal(payload: AiActionPayload): AiProposedAction? {
        val person = payload.person
        val relationship = payload.relationship
        val title = when (payload.type) {
            "create_person" -> "新增人物：${person?.name.orEmpty()}"
            "update_person" -> "修改人物：${person?.name ?: person?.id.orEmpty()}"
            "add_relationship" -> "新增关系"
            "update_relationship" -> "修改关系"
            else -> return null
        }
        return AiProposedAction(
            id = UUID.randomUUID().toString(),
            type = payload.type,
            title = title,
            detail = gson.toJson(payload),
            person = person,
            relationship = relationship,
        )
    }

    private fun removeAction(messageId: String, actionId: String) {
        messages.value = messages.value.map { message ->
            if (message.id == messageId) {
                message.copy(
                    proposedActions = message.proposedActions.filterNot { it.id == actionId },
                )
            } else {
                message
            }
        }
    }

    private suspend fun buildUserPrompt(question: String): String {
        val people = repository.people.first()
        val relationTypes = repository.relationTypes.first()
        val relationships = repository.relationships.first()
        val dismissals = repository.inferenceDismissals.first()
        val ageOrders = repository.relativeAgeOrders.first()
        val inferredCandidates = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = relationTypes,
            dismissals = dismissals,
            ageOrders = ageOrders,
        )
        val context = mapOf(
            "people" to people.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "gender" to it.gender.name,
                    "birthday" to it.birthday,
                )
            },
            "relationTypes" to relationTypes.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "inverseName" to it.inverseName,
                    "direction" to it.direction.name,
                    "category" to it.category.name,
                )
            },
            "relationships" to relationships.map {
                mapOf(
                    "id" to it.id,
                    "fromPersonId" to it.fromPersonId,
                    "toPersonId" to it.toPersonId,
                    "relationTypeId" to it.relationTypeId,
                    "source" to it.source.name,
                    "marriageKinshipMode" to it.marriageKinshipMode.name,
                    "labelOverride" to it.labelOverride,
                    "inverseLabelOverride" to it.inverseLabelOverride,
                )
            },
            "derivedRelationships" to inferredCandidates.map {
                mapOf(
                    "fromPersonId" to it.fromPersonId,
                    "toPersonId" to it.toPersonId,
                    "fromPersonLabel" to it.labelForFrom,
                    "toPersonLabel" to it.labelForTo,
                    "rule" to it.rule.id,
                    "reason" to it.reasonText,
                )
            },
        )
        return "当前关系数据：\n${gson.toJson(context)}\n\n用户问题：\n$question"
    }

    private fun systemPrompt(): String = """
        你是中文人际与家庭关系助手。根据用户问题和提供的关系数据回答。
        只输出一个 JSON 对象，格式：
        {"answer":"中文回答","actions":[]}
        actions 表示待用户确认的修改建议，不能宣称已经执行。
        可用 action：
        1. {"type":"create_person","person":{"name":"姓名","gender":"MALE|FEMALE|UNSPECIFIED","phone":"","birthday":"YYYY-MM-DD","address":"","notes":""}}
        2. {"type":"update_person","person":{"id":"已有ID","name":"新姓名","gender":"...","phone":"","birthday":"","address":"","notes":""}}
        3. {"type":"add_relationship","relationship":{"fromPersonId":"已有ID","toPersonId":"已有ID","relationTypeId":"已有类型ID","note":""}}
        4. {"type":"update_relationship","relationship":{"id":"已有ID","fromPersonId":"","toPersonId":"","relationTypeId":"","note":""}}
        仅当用户明确要求修改时生成 actions。查询、称谓和普通问答应返回空 actions。
        涉及亲属称谓时，必须以 derivedRelationships 中同一对人物的 fromPersonLabel 和 toPersonLabel 为准。
        多个有效称谓用斜杠连接，不得自行改用其他地区叫法或替换方向。
        不确定时先提问，不得猜测人物或关系 ID。
    """.trimIndent()

    private fun String?.toGender(): Gender = when (this?.uppercase()) {
        "MALE" -> Gender.MALE
        "FEMALE" -> Gender.FEMALE
        else -> Gender.UNSPECIFIED
    }

    private fun defaultMarriageMode(first: Gender, second: Gender): MarriageKinshipMode =
        if (
            (first == Gender.MALE && second == Gender.FEMALE) ||
            (first == Gender.FEMALE && second == Gender.MALE)
        ) {
            MarriageKinshipMode.FOLLOW_HUSBAND
        } else {
            MarriageKinshipMode.RESPECTIVE
        }
}
