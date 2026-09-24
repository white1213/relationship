package com.relationship.graph.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.relationship.graph.data.local.AppDatabase
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PersonTagEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.TagEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class GraphData(
    val people: List<PersonEntity>,
    val tags: List<TagEntity>,
    val personTags: List<PersonTagEntity>,
    val relationTypes: List<RelationTypeEntity>,
    val relationships: List<RelationshipEntity>,
)

class RelationshipRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val dao = database.relationshipDao()

    val people: Flow<List<PersonEntity>> = dao.observePeople()
    val tagEntities: Flow<List<TagEntity>> = dao.observeTags()
    val personTags: Flow<List<PersonTagEntity>> = dao.observePersonTags()
    val relationTypes: Flow<List<RelationTypeEntity>> = dao.observeRelationTypes()
    val relationships: Flow<List<RelationshipEntity>> = dao.observeRelationships()

    suspend fun ensurePresetRelationTypes() {
        if (dao.relationTypeCount() == 0) {
            dao.insertRelationTypes(PresetRelationTypes.all)
        }
    }

    suspend fun getPerson(personId: String): PersonEntity? = dao.getPerson(personId)

    suspend fun getRelationship(relationshipId: String): RelationshipEntity? =
        dao.getRelationship(relationshipId)

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

    suspend fun saveRelationship(relationship: RelationshipEntity) {
        dao.upsertRelationship(relationship.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRelationship(relationship: RelationshipEntity) {
        dao.deleteRelationship(relationship)
    }

    suspend fun updateGraphPositions(positions: Map<String, Pair<Float, Float>>) {
        if (positions.isNotEmpty()) {
            dao.updateGraphPositions(positions)
        }
    }

    suspend fun getGraphData(): GraphData = GraphData(
        people = dao.getAllPeople(),
        tags = dao.getAllTags(),
        personTags = dao.getAllPersonTags(),
        relationTypes = dao.getAllRelationTypes(),
        relationships = dao.getAllRelationships(),
    )

    suspend fun replaceAll(data: GraphData) {
        dao.replaceAll(
            people = data.people,
            tags = data.tags,
            personTags = data.personTags,
            relationTypes = data.relationTypes,
            relationships = data.relationships,
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
