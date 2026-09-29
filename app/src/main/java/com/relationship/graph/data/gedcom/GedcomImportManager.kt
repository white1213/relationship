package com.relationship.graph.data.gedcom

import com.google.gson.Gson
import com.relationship.graph.data.local.AppDatabase
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.ImportBatchEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.RelationshipSource
import com.relationship.graph.data.local.StagedDecision
import com.relationship.graph.data.local.StagedPersonEntity
import com.relationship.graph.data.local.StagedRelationshipEntity
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

/**
 * GEDCOM 导入：解析结果全部进入暂存表，绝不直接写主库；
 * 用户逐条决定「新建/并入已有/忽略」后，应用阶段才在一个事务里写库，并留回滚快照。
 */
class GedcomImportManager(private val database: AppDatabase) {

    private val dao = database.relationshipDao()
    private val gson = Gson()

    /** 解析并暂存一个导入批次；会先丢弃之前未应用的批次。 */
    suspend fun stageImport(fileName: String, stream: InputStream): ImportBatchEntity {
        val document = withContext(Dispatchers.IO) { GedcomParser.parse(stream) }
        return database.withTransaction {
            dao.getStagedBatches().forEach { dao.deleteImportBatch(it.id) }
            val batch = ImportBatchEntity(
                id = "imp_" + UUID.randomUUID(),
                fileName = fileName,
                status = "STAGED",
                personCount = document.individuals.size,
                relationshipCount = document.families.size,
            )
            dao.insertImportBatch(batch)

            val existingPeople = dao.getAllPeople()
            val xrefToStaged = mutableMapOf<String, String>()
            val stagedPeople = document.individuals.map { individual ->
                val stagedId = "sp_" + UUID.randomUUID()
                xrefToStaged[individual.xref] = stagedId
                val trimmedName = individual.name.trim()
                val suggestion = existingPeople.firstOrNull {
                    it.name.trim() == trimmedName && trimmedName.isNotBlank()
                }?.id
                StagedPersonEntity(
                    id = stagedId,
                    batchId = batch.id,
                    xref = individual.xref,
                    name = trimmedName.ifBlank { "未命名" },
                    gender = when (individual.sex) {
                        "M" -> Gender.MALE
                        "F" -> Gender.FEMALE
                        else -> Gender.UNSPECIFIED
                    },
                    birthDate = individual.birthDate,
                    deathDate = individual.deathDate,
                    payloadJson = gson.toJson(
                        mapOf(
                            "notes" to individual.notes.joinToString("\n———\n"),
                            "deceased" to individual.deathDate.isNotBlank(),
                        ),
                    ),
                    suggestedMergePersonId = suggestion,
                    decision = if (suggestion != null) StagedDecision.MERGE else StagedDecision.CREATE,
                    mergePersonId = suggestion,
                )
            }
            if (stagedPeople.isNotEmpty()) dao.insertStagedPeople(stagedPeople)

            val stagedRelationships = mutableListOf<StagedRelationshipEntity>()
            fun addRelation(fromRef: String, toRef: String, typeId: String, note: String = "") {
                if (fromRef.isBlank() || toRef.isBlank() || fromRef == toRef) return
                stagedRelationships += StagedRelationshipEntity(
                    id = "sr_" + UUID.randomUUID(),
                    batchId = batch.id,
                    fromRef = fromRef,
                    toRef = toRef,
                    relationTypeId = typeId,
                    note = note,
                )
            }
            document.families.forEach { family ->
                val husband = family.husband?.let(xrefToStaged::get)
                val wife = family.wife?.let(xrefToStaged::get)
                if (husband != null && wife != null) addRelation(husband, wife, "preset_spouse")
                family.children.forEach { childXref ->
                    val child = xrefToStaged[childXref] ?: return@forEach
                    husband?.let { addRelation(it, child, "preset_parent_child") }
                    wife?.let { addRelation(wife, child, "preset_parent_child") }
                }
            }
            document.associations.forEach { association ->
                val from = xrefToStaged[association.fromXref] ?: return@forEach
                val to = xrefToStaged[association.toXref] ?: return@forEach
                val typeName = association.type.ifBlank { "相关" }
                addRelation(from, to, "name:$typeName", association.note)
            }
            val deduped = stagedRelationships
                .distinctBy { listOf(it.fromRef, it.toRef).sorted() + it.relationTypeId }
            if (deduped.isNotEmpty()) dao.insertStagedRelationships(deduped)
            batch.copy(relationshipCount = deduped.size)
        }
    }

    /** 应用已确认的暂存数据；一个事务写库，回滚快照存入批次。 */
    suspend fun applyImport(batchId: String): String = database.withTransaction {
        val batch = dao.getImportBatch(batchId) ?: error("导入批次不存在")
        require(batch.status == "STAGED") { "该批次已处理" }
        val stagedPeople = dao.getStagedPeople(batchId)
        val stagedRelationships = dao.getStagedRelationships(batchId)
            .filter { it.decision != StagedDecision.IGNORE }
        val existingTypes = dao.getAllRelationTypes()
        val now = System.currentTimeMillis()

        val refToPersonId = mutableMapOf<String, String>()
        val createdPeople = mutableListOf<PersonEntity>()
        stagedPeople.forEach { staged ->
            when (staged.decision) {
                StagedDecision.MERGE -> {
                    val target = staged.mergePersonId
                        ?: staged.suggestedMergePersonId
                        ?: staged.decisionFallbackId()
                    if (target != null && dao.getPerson(target) != null) {
                        refToPersonId[staged.id] = target
                    }
                }
                StagedDecision.CREATE, StagedDecision.PENDING -> {
                    val payload = runCatching {
                        gson.fromJson(staged.payloadJson, StagedPersonPayload::class.java)
                    }.getOrNull()
                    val person = PersonEntity(
                        id = UUID.randomUUID().toString(),
                        name = staged.name,
                        gender = staged.gender,
                        birthday = staged.birthDate,
                        notes = payload?.notes.orEmpty(),
                        isDeceased = staged.deathDate.isNotBlank(),
                        deathDate = staged.deathDate.takeIf(String::isNotBlank),
                        createdAt = now,
                        updatedAt = now,
                    )
                    dao.upsertPerson(person)
                    createdPeople += person
                    refToPersonId[staged.id] = person.id
                }
            }
        }

        // ASSO 类型名 → 自定义关系类型（已存在则复用）。
        val customTypesByName = mutableMapOf<String, RelationTypeEntity>()
        stagedRelationships
            .mapNotNull { stagedRel -> stagedRel.relationTypeId.takeIf { id -> id.startsWith("name:") } }
            .map { it.removePrefix("name:") }
            .distinct()
            .forEach { name ->
                val existing = existingTypes.firstOrNull { it.name == name }
                val type = existing ?: RelationTypeEntity(
                    id = "custom_" + UUID.randomUUID(),
                    name = name,
                    inverseName = null,
                    category = RelationCategory.SOCIAL,
                    direction = RelationDirection.BIDIRECTIONAL,
                )
                if (existing == null) dao.upsertRelationType(type)
                customTypesByName[name] = type
            }
        val presetTypeIds = PresetRelationTypes.all.mapTo(mutableSetOf()) { it.id }
        val existingTypeIds = existingTypes.mapTo(mutableSetOf()) { it.id }

        val existingPairKeys = dao.getAllRelationships()
            .map { relationKey(it.fromPersonId, it.toPersonId, it.relationTypeId) }
            .toMutableSet()
        val createdRelationships = mutableListOf<RelationshipEntity>()
        stagedRelationships.forEach { staged ->
            val from = resolveRef(staged.fromRef, refToPersonId) ?: return@forEach
            val to = resolveRef(staged.toRef, refToPersonId) ?: return@forEach
            if (from == to) return@forEach
            val typeId = if (staged.relationTypeId.startsWith("name:")) {
                customTypesByName[staged.relationTypeId.removePrefix("name:")]?.id
            } else {
                staged.relationTypeId.takeIf {
                    it in presetTypeIds || it in existingTypeIds
                }
            } ?: return@forEach
            val key = relationKey(from, to, typeId)
            if (key in existingPairKeys) return@forEach
            existingPairKeys += key
            createdRelationships += RelationshipEntity(
                id = UUID.randomUUID().toString(),
                fromPersonId = from,
                toPersonId = to,
                relationTypeId = typeId,
                source = RelationshipSource.MANUAL,
                note = staged.note,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (createdRelationships.isNotEmpty()) {
            dao.upsertRelationships(createdRelationships)
        }

        dao.updateImportBatchStatus(
            id = batchId,
            status = "APPLIED",
            rollbackJson = gson.toJson(
                RollbackPayload(
                    personIds = createdPeople.map { it.id },
                    relationshipIds = createdRelationships.map { it.id },
                    typeIds = customTypesByName.values.map { it.id },
                ),
            ),
        )
        "已导入 ${createdPeople.size} 人、${createdRelationships.size} 条关系"
    }

    /** 撤销一次已应用的导入：删除该批次创建的人物/关系/类型。 */
    suspend fun rollbackImport(batchId: String) {
        database.withTransaction {
            val batch = dao.getImportBatch(batchId) ?: return@withTransaction
            val payload = batch.rollbackJson
                ?.let { runCatching { gson.fromJson(it, RollbackPayload::class.java) }.getOrNull() }
                ?: return@withTransaction
            payload.relationshipIds.forEach { dao.deleteRelationshipById(it) }
            payload.typeIds.forEach { runCatching { dao.deleteRelationTypeById(it) } }
            payload.personIds.forEach { dao.deletePersonById(it) }
            dao.updateImportBatchStatus(batchId, "DISCARDED", null)
        }
    }

    private fun resolveRef(ref: String, refToPersonId: Map<String, String>): String? {
        if (ref.startsWith("sp_")) return refToPersonId[ref]
        return ref
    }

    private fun relationKey(from: String, to: String, typeId: String): String =
        listOf(from, to).sorted().joinToString("\u0000") + "\u0000" + typeId

    private data class StagedPersonPayload(
        val notes: String? = null,
        val deceased: Boolean? = null,
    )

    private data class RollbackPayload(
        val personIds: List<String>,
        val relationshipIds: List<String>,
        val typeIds: List<String>,
    )
}

private fun StagedPersonEntity.decisionFallbackId(): String? = null
