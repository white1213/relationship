package com.relationship.graph.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
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

    @Query("SELECT * FROM graph_positions")
    fun observeGraphPositions(): Flow<List<GraphPositionEntity>>

    @Query("SELECT * FROM inference_dismissals")
    fun observeInferenceDismissals(): Flow<List<InferenceDismissalEntity>>

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

    @Query("SELECT * FROM graph_positions")
    suspend fun getAllGraphPositions(): List<GraphPositionEntity>

    @Query("SELECT * FROM inference_dismissals")
    suspend fun getAllInferenceDismissals(): List<InferenceDismissalEntity>

    @Query("SELECT COUNT(*) FROM relation_types")
    suspend fun relationTypeCount(): Int

    @Query("SELECT COUNT(*) FROM relationships WHERE fromPersonId = :personId OR toPersonId = :personId")
    suspend fun relationshipCountForPerson(personId: String): Int

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPerson(personId: String): PersonEntity?

    @Query("SELECT * FROM relationships WHERE id = :relationshipId LIMIT 1")
    suspend fun getRelationship(relationshipId: String): RelationshipEntity?

    @Upsert
    suspend fun upsertPerson(person: PersonEntity)

    @Upsert
    suspend fun upsertPeople(people: List<PersonEntity>)

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPersonTags(personTags: List<PersonTagEntity>)

    @Query("DELETE FROM person_tags WHERE personId = :personId")
    suspend fun deletePersonTags(personId: String)

    @Upsert
    suspend fun upsertRelationType(type: RelationTypeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRelationType(type: RelationTypeEntity)

    @Query("SELECT * FROM relation_types WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findRelationTypeByName(name: String): RelationTypeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelationTypes(types: List<RelationTypeEntity>)

    @Upsert
    suspend fun upsertRelationships(relationships: List<RelationshipEntity>)

    @Upsert
    suspend fun upsertRelationship(relationship: RelationshipEntity)

    @Upsert
    suspend fun upsertGraphPosition(position: GraphPositionEntity)

    @Upsert
    suspend fun upsertGraphPositions(positions: List<GraphPositionEntity>)

    @Upsert
    suspend fun upsertInferenceDismissal(dismissal: InferenceDismissalEntity)

    @Upsert
    suspend fun upsertInferenceDismissals(dismissals: List<InferenceDismissalEntity>)

    @Query(
        "DELETE FROM inference_dismissals WHERE fromPersonId = :fromPersonId " +
            "AND toPersonId = :toPersonId AND ruleId = :ruleId",
    )
    suspend fun deleteInferenceDismissal(
        fromPersonId: String,
        toPersonId: String,
        ruleId: String,
    )

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

    @Query("DELETE FROM graph_positions")
    suspend fun deleteAllGraphPositions()

    @Query("DELETE FROM graph_positions WHERE mode = :mode")
    suspend fun deleteGraphPositionsForMode(mode: GraphMode)

    @Query("DELETE FROM inference_dismissals")
    suspend fun deleteAllInferenceDismissals()

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
    suspend fun saveGraphPosition(
        personId: String,
        mode: GraphMode,
        x: Float,
        y: Float,
    ) {
        val now = System.currentTimeMillis()
        upsertGraphPosition(
            GraphPositionEntity(
                personId = personId,
                mode = mode,
                x = x,
                y = y,
                isManuallyPinned = true,
                updatedAt = now,
            ),
        )
    }

    @Transaction
    suspend fun replaceAll(
        people: List<PersonEntity>,
        tags: List<TagEntity>,
        personTags: List<PersonTagEntity>,
        relationTypes: List<RelationTypeEntity>,
        relationships: List<RelationshipEntity>,
        graphPositions: List<GraphPositionEntity>,
        inferenceDismissals: List<InferenceDismissalEntity>,
    ) {
        deleteAllRelationships()
        deleteAllPersonTags()
        deleteAllPeople()
        deleteAllTags()
        deleteAllRelationTypes()
        deleteAllGraphPositions()
        deleteAllInferenceDismissals()

        upsertPeople(people)
        upsertGraphPositions(graphPositions)
        upsertInferenceDismissals(inferenceDismissals)
        insertTags(tags)
        insertPersonTags(personTags)
        insertRelationTypes(relationTypes)
        upsertRelationships(relationships)
    }
}
