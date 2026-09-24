package com.relationship.graph.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE, createdAt")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM relation_types ORDER BY category, isBuiltIn DESC, name COLLATE NOCASE")
    fun observeRelationTypes(): Flow<List<RelationTypeEntity>>

    @Query("SELECT * FROM relationships ORDER BY createdAt")
    fun observeRelationships(): Flow<List<RelationshipEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM person_tags")
    fun observePersonTags(): Flow<List<PersonTagEntity>>

    @Query("SELECT * FROM people")
    suspend fun getAllPeople(): List<PersonEntity>

    @Query("SELECT * FROM relation_types")
    suspend fun getAllRelationTypes(): List<RelationTypeEntity>

    @Query("SELECT * FROM relationships")
    suspend fun getAllRelationships(): List<RelationshipEntity>

    @Query("SELECT * FROM tags")
    suspend fun getAllTags(): List<TagEntity>

    @Query("SELECT * FROM person_tags")
    suspend fun getAllPersonTags(): List<PersonTagEntity>

    @Query("SELECT COUNT(*) FROM relation_types")
    suspend fun relationTypeCount(): Int

    @Query("SELECT COUNT(*) FROM relationships WHERE fromPersonId = :personId OR toPersonId = :personId")
    suspend fun relationshipCountForPerson(personId: String): Int

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPerson(personId: String): PersonEntity?

    @Query("SELECT * FROM relationships WHERE id = :relationshipId LIMIT 1")
    suspend fun getRelationship(relationshipId: String): RelationshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeople(people: List<PersonEntity>)

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query(
        "UPDATE people SET graphX = :x, graphY = :y, positionInitialized = 1, updatedAt = :updatedAt " +
            "WHERE id = :personId",
    )
    suspend fun updateGraphPosition(
        personId: String,
        x: Float,
        y: Float,
        updatedAt: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPersonTags(personTags: List<PersonTagEntity>)

    @Query("DELETE FROM person_tags WHERE personId = :personId")
    suspend fun deletePersonTags(personId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationType(type: RelationTypeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelationTypes(types: List<RelationTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationships(relationships: List<RelationshipEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationship(relationship: RelationshipEntity)

    @Delete
    suspend fun deleteRelationship(relationship: RelationshipEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM relationships")
    suspend fun deleteAllRelationships()

    @Query("DELETE FROM person_tags")
    suspend fun deleteAllPersonTags()

    @Query("DELETE FROM people")
    suspend fun deleteAllPeople()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM relation_types")
    suspend fun deleteAllRelationTypes()

    @Transaction
    suspend fun savePersonWithTags(
        person: PersonEntity,
        tags: List<TagEntity>,
        personTags: List<PersonTagEntity>,
    ) {
        upsertPerson(person)
        insertTags(tags)
        deletePersonTags(person.id)
        insertPersonTags(personTags)
    }

    @Transaction
    suspend fun updateGraphPositions(positions: Map<String, Pair<Float, Float>>) {
        val now = System.currentTimeMillis()
        positions.forEach { (personId, point) ->
            updateGraphPosition(personId, point.first, point.second, now)
        }
    }

    @Transaction
    suspend fun replaceAll(
        people: List<PersonEntity>,
        tags: List<TagEntity>,
        personTags: List<PersonTagEntity>,
        relationTypes: List<RelationTypeEntity>,
        relationships: List<RelationshipEntity>,
    ) {
        deleteAllRelationships()
        deleteAllPersonTags()
        deleteAllPeople()
        deleteAllTags()
        deleteAllRelationTypes()

        upsertPeople(people)
        insertTags(tags)
        insertPersonTags(personTags)
        insertRelationTypes(relationTypes)
        upsertRelationships(relationships)
    }
}
