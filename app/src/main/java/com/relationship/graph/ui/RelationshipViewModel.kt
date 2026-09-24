package com.relationship.graph.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relationship.graph.RelationshipApplication
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PersonTagEntity
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.GraphPositionEntity
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.InferenceDismissalEntity
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipSource
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.TagEntity
import com.relationship.graph.data.inference.InferenceEngine
import com.relationship.graph.data.inference.InferenceConfirmationMode
import com.relationship.graph.data.inference.InferredRelationshipCandidate
import com.relationship.graph.data.preferences.GraphDisplayMode
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val isLoading: Boolean = true,
    val people: List<PersonEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val personTags: List<PersonTagEntity> = emptyList(),
    val relationTypes: List<RelationTypeEntity> = emptyList(),
    val relationships: List<RelationshipEntity> = emptyList(),
    val graphPositions: List<GraphPositionEntity> = emptyList(),
    val inferenceDismissals: List<InferenceDismissalEntity> = emptyList(),
    val inferredCandidates: List<InferredRelationshipCandidate> = emptyList(),
    val showInferenceSuggestions: Boolean = true,
    val graphDisplayMode: GraphDisplayMode = GraphDisplayMode.SIMPLE,
    val graphMode: GraphMode = GraphMode.FAMILY,
    val myPersonId: String? = null,
    val searchQuery: String = "",
    val selectedCategory: RelationCategory? = null,
) {
    val tagsByPerson: Map<String, List<TagEntity>>
        get() {
            val tagById = tags.associateBy { it.id }
            return personTags
                .groupBy { it.personId }
                .mapValues { (_, refs) -> refs.mapNotNull { tagById[it.tagId] }.sortedBy { it.name } }
        }

    fun person(personId: String?): PersonEntity? = people.firstOrNull { it.id == personId }

    fun relationType(typeId: String?): RelationTypeEntity? =
        relationTypes.firstOrNull { it.id == typeId }

    fun relationship(relationshipId: String?): RelationshipEntity? =
        relationships.firstOrNull { it.id == relationshipId }

    fun relationshipsForPerson(personId: String): List<RelationshipEntity> =
        relationships.filter { it.fromPersonId == personId || it.toPersonId == personId }

    fun inferenceCandidatesFor(personId: String): List<InferredRelationshipCandidate> =
        inferredCandidates.filter {
            it.fromPersonId == personId || it.toPersonId == personId
        }
}

class RelationshipViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RelationshipApplication
    private val repository = app.container.repository
    private val backupManager = requireNotNull(app.container.backupManager)

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<RelationCategory?>(null)
    private val graphMode = MutableStateFlow(GraphMode.FAMILY)
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val inferenceCandidatesFlow = combine(
        repository.people,
        repository.relationships,
        repository.relationTypes,
        repository.inferenceDismissals,
    ) { people, relationships, relationTypes, dismissals ->
        runCatching {
            InferenceEngine.infer(
                people = people,
                relationships = relationships,
                relationTypes = relationTypes,
                dismissals = dismissals,
            )
        }.getOrDefault(emptyList())
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    val uiState: StateFlow<AppUiState> = combine(
        repository.people,
        repository.tagEntities,
        repository.personTags,
        repository.relationTypes,
        repository.relationships,
        repository.graphPositions,
        repository.inferenceDismissals,
        app.container.graphPreferencesStore.showInferenceSuggestions,
        app.container.graphPreferencesStore.graphDisplayMode,
        graphMode,
        app.container.graphPreferencesStore.myPersonId,
        searchQuery,
        selectedCategory,
        inferenceCandidatesFlow,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AppUiState(
            isLoading = false,
            people = values[0] as List<PersonEntity>,
            tags = values[1] as List<TagEntity>,
            personTags = values[2] as List<PersonTagEntity>,
            relationTypes = values[3] as List<RelationTypeEntity>,
            relationships = values[4] as List<RelationshipEntity>,
            graphPositions = values[5] as List<GraphPositionEntity>,
            inferenceDismissals = values[6] as List<InferenceDismissalEntity>,
            showInferenceSuggestions = values[7] as Boolean,
            graphDisplayMode = values[8] as GraphDisplayMode,
            graphMode = values[9] as GraphMode,
            myPersonId = values[10] as String?,
            searchQuery = values[11] as String,
            selectedCategory = values[12] as RelationCategory?,
            inferredCandidates = values[13] as List<InferredRelationshipCandidate>,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.ensurePresetRelationTypes() }
                .onFailure { messageChannel.send(it.message ?: "关系类型初始化失败") }
        }
        viewModelScope.launch {
            combine(
                repository.people,
                app.container.graphPreferencesStore.myPersonId,
            ) { people, myPersonId -> people.map { it.id }.toSet() to myPersonId }
                .collect { (personIds, myPersonId) ->
                    if (myPersonId != null && myPersonId !in personIds) {
                        app.container.graphPreferencesStore.clearMyPersonId()
                    }
                }
        }
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun setCategory(value: RelationCategory?) {
        selectedCategory.value = value
    }

    fun setGraphMode(value: GraphMode) {
        graphMode.value = value
    }

    fun setMyPerson(personId: String) {
        viewModelScope.launch {
            app.container.graphPreferencesStore.setMyPersonId(personId)
            sendMessage("已设置我的信息")
        }
    }

    fun savePerson(
        id: String,
        name: String,
        gender: Gender,
        avatarPath: String?,
        phone: String,
        birthday: String,
        address: String,
        notes: String,
        tagNames: List<String>,
        existing: PersonEntity?,
    ) {
        if (name.isBlank()) {
            sendMessage("请输入姓名")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.savePerson(
                person = PersonEntity(
                    id = id,
                    name = name.trim(),
                    gender = gender,
                    avatarPath = avatarPath,
                    phone = phone.trim(),
                    birthday = birthday.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
                tagNames = tagNames,
            )
            sendMessage("人物资料已保存")
        }
    }

    fun deletePerson(person: PersonEntity) {
        viewModelScope.launch {
            runCatching { repository.deletePerson(person) }
                .onSuccess {
                    if (person.id == uiState.value.myPersonId) {
                        app.container.graphPreferencesStore.clearMyPersonId()
                    }
                    sendMessage("人物已删除")
                }
                .onFailure { sendMessage(it.message ?: "删除失败") }
        }
    }

    fun saveRelationship(
        relationshipId: String?,
        fromPersonId: String,
        toPersonId: String,
        relationTypeId: String,
        note: String,
        existing: RelationshipEntity?,
    ) {
        if (fromPersonId == toPersonId) {
            sendMessage("不能把一个人与自己建立关系")
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.saveRelationship(
                RelationshipEntity(
                    id = relationshipId ?: UUID.randomUUID().toString(),
                    fromPersonId = fromPersonId,
                    toPersonId = toPersonId,
                    relationTypeId = relationTypeId,
                    source = existing?.source ?: RelationshipSource.MANUAL,
                    labelOverride = if (existing?.relationTypeId == relationTypeId) {
                        existing.labelOverride
                    } else {
                        null
                    },
                    inverseLabelOverride = if (existing?.relationTypeId == relationTypeId) {
                        existing.inverseLabelOverride
                    } else {
                        null
                    },
                    note = note.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            sendMessage("关系已保存")
        }
    }

    fun deleteRelationship(relationship: RelationshipEntity) {
        viewModelScope.launch {
            repository.deleteRelationship(relationship)
            sendMessage("关系已删除")
        }
    }

    suspend fun createCustomRelationType(
        name: String,
        inverseName: String?,
        category: RelationCategory,
        direction: RelationDirection,
    ): String? {
        if (name.isBlank()) {
            sendMessage("请输入关系名称")
            return null
        }
        val type = RelationTypeEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            inverseName = inverseName?.trim()?.takeIf(String::isNotEmpty),
            category = category,
            direction = direction,
            isBuiltIn = false,
        )
        return repository.createCustomRelationType(type)
            .onSuccess { sendMessage("自定义关系已创建") }
            .onFailure {
                sendMessage(it.message ?: "关系名称已存在")
            }
            .map { type.id }
            .getOrNull()
    }

    fun confirmInference(
        candidate: InferredRelationshipCandidate,
        confirmationMode: InferenceConfirmationMode = InferenceConfirmationMode.AS_CHILD,
    ) {
        val relationTypeId = candidate.relationTypeFor(confirmationMode)
        val alreadyExists = uiState.value.relationships.any {
            it.relationTypeId == relationTypeId &&
                setOf(it.fromPersonId, it.toPersonId) ==
                setOf(candidate.fromPersonId, candidate.toPersonId)
        }
        if (alreadyExists) {
            sendMessage("该关系已经存在")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.clearInferenceDismissal(
                    candidate.fromPersonId,
                    candidate.toPersonId,
                    candidate.rule.id,
                )
                repository.saveRelationship(
                    RelationshipEntity(
                        id = UUID.randomUUID().toString(),
                        fromPersonId = candidate.fromPersonId,
                        toPersonId = candidate.toPersonId,
                        relationTypeId = relationTypeId,
                        source = RelationshipSource.CONFIRMED_INFERENCE,
                        labelOverride = candidate.labelFor(
                            candidate.fromPersonId,
                            confirmationMode,
                        ),
                        inverseLabelOverride = candidate.labelFor(
                            candidate.toPersonId,
                            confirmationMode,
                        ),
                    ),
                )
            }
                .onSuccess { sendMessage("已添加推导关系") }
                .onFailure { sendMessage(it.message ?: "添加失败") }
        }
    }

    fun dismissInference(candidate: InferredRelationshipCandidate) {
        viewModelScope.launch {
            repository.dismissInference(
                InferenceDismissalEntity(
                    fromPersonId = candidate.fromPersonId,
                    toPersonId = candidate.toPersonId,
                    ruleId = candidate.rule.id,
                    evidenceFingerprint = candidate.evidenceFingerprint,
                ),
            )
            sendMessage("已忽略该候选关系")
        }
    }

    fun setShowInferenceSuggestions(enabled: Boolean) {
        viewModelScope.launch {
            app.container.graphPreferencesStore.setShowInferenceSuggestions(enabled)
        }
    }

    fun setGraphDisplayMode(mode: GraphDisplayMode) {
        viewModelScope.launch {
            app.container.graphPreferencesStore.setGraphDisplayMode(mode)
        }
    }

    suspend fun importAvatarFromUri(personId: String, uri: Uri): String =
        repository.importAvatarFromUri(personId, uri)

    suspend fun importAvatarBitmap(personId: String, bitmap: Bitmap): String =
        repository.importAvatarBitmap(personId, bitmap)

    fun saveGraphPosition(personId: String, x: Float, y: Float) {
        viewModelScope.launch {
            repository.saveGraphPosition(personId, graphMode.value, x, y)
        }
    }

    fun clearGraphPositions(mode: GraphMode) {
        viewModelScope.launch {
            repository.clearGraphPositions(mode)
        }
    }

    fun restoreGraphPositions(positions: List<GraphPositionEntity>) {
        viewModelScope.launch {
            repository.restoreGraphPositions(positions)
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            runCatching { backupManager.export(uri, password) }
                .onSuccess { sendMessage("备份已导出") }
                .onFailure { sendMessage(it.message ?: "备份失败") }
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            runCatching {
                val imported = backupManager.import(uri, password)
                repository.replaceAll(imported)
            }
                .onSuccess { sendMessage("备份已恢复") }
                .onFailure { sendMessage(it.message ?: "恢复失败，现有数据未改变") }
        }
    }

    private fun sendMessage(message: String) {
        messageChannel.trySend(message)
    }
}
