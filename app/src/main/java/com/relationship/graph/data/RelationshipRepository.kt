package com.relationship.graph.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.relationship.graph.data.local.AppDatabase
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.GraphPositionEntity
import com.relationship.graph.data.local.InferenceDismissalEntity
import com.relationship.graph.data.local.MergeRecordEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PersonTagEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.RelativeAgeOrderEntity
import com.relationship.graph.data.local.TagEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

data class GraphData(
    val people: List<PersonEntity>,
    val tags: List<TagEntity>,
    val personTags: List<PersonTagEntity>,
    val relationTypes: List<RelationTypeEntity>,
    val relationships: List<RelationshipEntity>,
    val graphPositions: List<GraphPositionEntity>,
    val inferenceDismissals: List<InferenceDismissalEntity>,
    val relativeAgeOrders: List<RelativeAgeOrderEntity>,
)

class RelationshipRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val dao = database.relationshipDao()
    private val gson = Gson()

    val people: Flow<List<PersonEntity>> = dao.observePeople()
    val tagEntities: Flow<List<TagEntity>> = dao.observeTags()
    val personTags: Flow<List<PersonTagEntity>> = dao.observePersonTags()
    val relationTypes: Flow<List<RelationTypeEntity>> = dao.observeRelationTypes()
    val relationships: Flow<List<RelationshipEntity>> = dao.observeRelationships()
    val graphPositions: Flow<List<GraphPositionEntity>> = dao.observeGraphPositions()
    val inferenceDismissals: Flow<List<InferenceDismissalEntity>> =
        dao.observeInferenceDismissals()
    val relativeAgeOrders: Flow<List<RelativeAgeOrderEntity>> =
        dao.observeRelativeAgeOrders()

    suspend fun ensurePresetRelationTypes() {
        dao.insertRelationTypes(PresetRelationTypes.all)
    }

    suspend fun getPerson(personId: String): PersonEntity? = dao.getPerson(personId)

    suspend fun getRelationship(relationshipId: String): RelationshipEntity? =
        dao.getRelationship(relationshipId)

    suspend fun getTagNamesForPerson(personId: String): List<String> =
        dao.getTagNamesForPerson(personId)

    suspend fun relationshipCountForPerson(personId: String): Int =
        dao.relationshipCountForPerson(personId)

    suspend fun savePerson(
        person: PersonEntity,
        tagNames: List<String>,
    ) {
        val now = System.currentTimeMillis()
        val normalizedPerson = person.copy(updatedAt = now)
        val uniqueTags = tagNames
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy { it.lowercase() }
        val tags = uniqueTags.map { tagName ->
            TagEntity(
                id = stableTagId(tagName),
                name = tagName,
                normalizedName = tagName.lowercase(),
            )
        }
        val personTags = tags.map { PersonTagEntity(personId = person.id, tagId = it.id) }
        dao.savePersonWithTags(normalizedPerson, tags, personTags)
    }

    suspend fun deletePerson(person: PersonEntity) {
        dao.deletePerson(person)
        deleteAvatarIfUnused(person.avatarPath)
    }

    suspend fun saveRelationType(type: RelationTypeEntity) {
        dao.upsertRelationType(type)
    }

    suspend fun createCustomRelationType(type: RelationTypeEntity): Result<Unit> = runCatching {
        require(dao.findRelationTypeByName(type.name) == null) {
            "关系名称已存在"
        }
        dao.insertRelationType(type)
    }

    suspend fun saveRelationship(relationship: RelationshipEntity) {
        dao.upsertRelationship(relationship.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRelationship(relationship: RelationshipEntity) {
        dao.deleteRelationship(relationship)
    }

    suspend fun saveGraphPosition(
        personId: String,
        mode: GraphMode,
        x: Float,
        y: Float,
    ) {
        dao.saveGraphPosition(personId, mode, x, y)
    }

    suspend fun clearGraphPositions(mode: GraphMode) {
        dao.deleteGraphPositionsForMode(mode)
    }

    suspend fun restoreGraphPositions(positions: List<GraphPositionEntity>) {
        if (positions.isNotEmpty()) dao.upsertGraphPositions(positions)
    }

    // ===== 人物合并（v6） =====

    /**
     * 把 absorbed 合并进 survivor：字段空白处补齐、备注拼接、关系迁移并去重、
     * 标签并集；整个操作在一个事务里，并写入快照供一键撤销。
     */
    suspend fun mergePersons(survivorId: String, absorbedId: String): MergeRecordEntity {
        require(survivorId != absorbedId) { "不能与自己合并" }
        var absorbedAvatarPath: String? = null
        val record = database.withTransaction {
            val survivor = dao.getPerson(survivorId) ?: error("主人物不存在")
            val absorbed = dao.getPerson(absorbedId) ?: error("被合并人物不存在")
            val now = System.currentTimeMillis()
            val allRelations = dao.getAllRelationships()
            val absorbedRelations = allRelations.filter {
                it.fromPersonId == absorbedId || it.toPersonId == absorbedId
            }
            val survivorPairTypes = allRelations
                .filter { it.fromPersonId == survivorId || it.toPersonId == survivorId }
                .map { relationKey(it.fromPersonId, it.toPersonId, it.relationTypeId) }
                .toSet()
            val survivorTags = dao.getAllPersonTags().filter { it.personId == survivorId }
            val absorbedTags = dao.getAllPersonTags().filter { it.personId == absorbedId }
            val dismissals = dao.getAllInferenceDismissals().filter {
                it.fromPersonId == absorbedId || it.toPersonId == absorbedId
            }
            val ageOrders = dao.getAllRelativeAgeOrders().filter {
                it.firstPersonId == absorbedId || it.secondPersonId == absorbedId
            }
            val positions = dao.getAllGraphPositions().filter { it.personId == absorbedId }

            val snapshot = MergeSnapshot(
                survivor = survivor,
                absorbed = absorbed,
                survivorTags = survivorTags,
                absorbedTags = absorbedTags,
                absorbedRelations = absorbedRelations,
                dismissals = dismissals,
                ageOrders = ageOrders,
                positions = positions,
            )
            val record = MergeRecordEntity(
                id = "merge_" + UUID.randomUUID(),
                survivorId = survivorId,
                snapshotJson = gson.toJson(snapshot),
            )
            dao.insertMergeRecord(record)

            val merged = survivor.copy(
                phone = survivor.phone.ifBlank { absorbed.phone },
                birthday = survivor.birthday.ifBlank { absorbed.birthday },
                birthdayCalendar = survivor.birthdayCalendar ?: absorbed.birthdayCalendar,
                lunarMonth = survivor.lunarMonth ?: absorbed.lunarMonth,
                lunarDay = survivor.lunarDay ?: absorbed.lunarDay,
                isLeapMonth = survivor.isLeapMonth ?: absorbed.isLeapMonth,
                address = survivor.address.ifBlank { absorbed.address },
                notes = listOf(survivor.notes, absorbed.notes)
                    .filter(String::isNotBlank)
                    .joinToString("\n———\n"),
                avatarPath = survivor.avatarPath ?: absorbed.avatarPath,
                isDeceased = survivor.isDeceased ?: absorbed.isDeceased,
                deathDate = survivor.deathDate ?: absorbed.deathDate,
                deathCalendar = survivor.deathCalendar ?: absorbed.deathCalendar,
                lunarDeathMonth = survivor.lunarDeathMonth ?: absorbed.lunarDeathMonth,
                lunarDeathDay = survivor.lunarDeathDay ?: absorbed.lunarDeathDay,
                isLeapDeathMonth = survivor.isLeapDeathMonth ?: absorbed.isLeapDeathMonth,
                updatedAt = now,
            )
            dao.upsertPerson(merged)

            absorbedRelations.forEach { relation ->
                val repointFrom =
                    if (relation.fromPersonId == absorbedId) survivorId else relation.fromPersonId
                val repointTo =
                    if (relation.toPersonId == absorbedId) survivorId else relation.toPersonId
                val duplicated = relationKey(repointFrom, repointTo, relation.relationTypeId) in
                    survivorPairTypes
                if (duplicated) {
                    dao.deleteRelationship(relation)
                } else {
                    dao.upsertRelationship(
                        relation.copy(
                            fromPersonId = repointFrom,
                            toPersonId = repointTo,
                            updatedAt = now,
                        ),
                    )
                }
            }

            val newTagRows = absorbedTags
                .filter { absorbedTag -> survivorTags.none { it.tagId == absorbedTag.tagId } }
                .map { PersonTagEntity(personId = survivorId, tagId = it.tagId) }
            if (newTagRows.isNotEmpty()) dao.insertPersonTags(newTagRows)
            dao.deletePersonTags(absorbedId)
            dismissals.forEach {
                dao.deleteInferenceDismissal(it.fromPersonId, it.toPersonId, it.ruleId)
            }
            ageOrders.forEach {
                dao.deleteRelativeAgeOrder(it.firstPersonId, it.secondPersonId)
            }
            dao.deletePerson(absorbed)
            absorbedAvatarPath = absorbed.avatarPath
            record
        }
        deleteAvatarIfUnused(absorbedAvatarPath)
        return record
    }

    /** 按快照撤销一次合并：恢复被合并者、关系指向、标签与相关记录。 */
    suspend fun restoreMerge(recordId: String) {
        database.withTransaction {
            val record = dao.getMergeRecord(recordId) ?: error("合并记录不存在或已撤销")
            val snapshot = gson.fromJson(record.snapshotJson, MergeSnapshot::class.java)
            dao.upsertPerson(snapshot.survivor)
            dao.upsertPerson(snapshot.absorbed)
            snapshot.absorbedRelations.forEach { dao.upsertRelationship(it) }
            dao.deletePersonTags(snapshot.survivor.id)
            dao.insertPersonTags(snapshot.survivorTags + snapshot.absorbedTags)
            snapshot.dismissals.forEach { dao.upsertInferenceDismissal(it) }
            snapshot.ageOrders.forEach { dao.upsertRelativeAgeOrder(it) }
            snapshot.positions.forEach { dao.upsertGraphPosition(it) }
            dao.deleteMergeRecord(recordId)
        }
    }

    private fun relationKey(from: String, to: String, typeId: String): String =
        listOf(from, to).sorted().joinToString("\u0000") + "\u0000" + typeId

    private data class MergeSnapshot(
        val survivor: PersonEntity,
        val absorbed: PersonEntity,
        val survivorTags: List<PersonTagEntity>,
        val absorbedTags: List<PersonTagEntity>,
        val absorbedRelations: List<RelationshipEntity>,
        val dismissals: List<InferenceDismissalEntity>,
        val ageOrders: List<RelativeAgeOrderEntity>,
        val positions: List<GraphPositionEntity>,
    )

    suspend fun dismissInference(dismissal: InferenceDismissalEntity) {
        dao.upsertInferenceDismissal(dismissal)
    }

    suspend fun clearInferenceDismissal(
        fromPersonId: String,
        toPersonId: String,
        ruleId: String,
    ) {
        dao.deleteInferenceDismissal(fromPersonId, toPersonId, ruleId)
    }

    suspend fun saveRelativeAgeOrder(order: RelativeAgeOrderEntity) {
        dao.upsertRelativeAgeOrder(order)
    }

    suspend fun deleteRelativeAgeOrder(firstPersonId: String, secondPersonId: String) {
        dao.deleteRelativeAgeOrder(firstPersonId, secondPersonId)
    }

    suspend fun getGraphData(): GraphData = GraphData(
        people = dao.getAllPeople(),
        tags = dao.getAllTags(),
        personTags = dao.getAllPersonTags(),
        relationTypes = dao.getAllRelationTypes(),
        relationships = dao.getAllRelationships(),
        graphPositions = dao.getAllGraphPositions(),
        inferenceDismissals = dao.getAllInferenceDismissals(),
        relativeAgeOrders = dao.getAllRelativeAgeOrders(),
    )

    suspend fun replaceAll(data: GraphData) {
        val existingTypeIds = data.relationTypes.mapTo(mutableSetOf()) { it.id }
        val mergedRelationTypes = data.relationTypes +
            PresetRelationTypes.all.filter { it.id !in existingTypeIds }
        dao.replaceAll(
            people = data.people,
            tags = data.tags,
            personTags = data.personTags,
            relationTypes = mergedRelationTypes,
            relationships = data.relationships,
            graphPositions = data.graphPositions,
            inferenceDismissals = data.inferenceDismissals,
            relativeAgeOrders = data.relativeAgeOrders,
        )
        cleanupUnusedAvatars(data.people.mapNotNull { it.avatarPath }.toSet())
    }

    suspend fun importAvatarFromUri(personId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val target = avatarFile(personId)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("无法读取所选图片")
        target.absolutePath
    }

    suspend fun importAvatarBitmap(personId: String, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val target = avatarFile(personId)
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            target.absolutePath
        }

    fun readAvatar(path: String): ByteArray? = File(path).takeIf(File::exists)?.readBytes()

    suspend fun writeAvatar(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        File(path).parentFile?.mkdirs()
        File(path).writeBytes(bytes)
    }

    fun avatarFile(personId: String): File {
        val directory = File(context.filesDir, "avatars").apply { mkdirs() }
        return File(directory, "$personId.jpg")
    }

    private suspend fun deleteAvatarIfUnused(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        val referenced = dao.getAllPeople().any { it.avatarPath == path }
        if (!referenced) File(path).delete()
    }

    private suspend fun cleanupUnusedAvatars(referencedPaths: Set<String>) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "avatars")
        directory.listFiles()?.forEach { file ->
            if (file.absolutePath !in referencedPaths) {
                file.delete()
            }
        }
    }

    private fun stableTagId(name: String): String =
        "tag_" + UUID.nameUUIDFromBytes(name.lowercase().toByteArray()).toString()
}
