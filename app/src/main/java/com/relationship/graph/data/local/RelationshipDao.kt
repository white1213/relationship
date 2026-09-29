package com.relationship.graph.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM relative_age_orders ORDER BY firstPersonId, secondPersonId")
    fun observeRelativeAgeOrders(): Flow<List<RelativeAgeOrderEntity>>

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

    @Query("SELECT * FROM relative_age_orders")
    suspend fun getAllRelativeAgeOrders(): List<RelativeAgeOrderEntity>

    @Query("SELECT COUNT(*) FROM relationships WHERE fromPersonId = :personId OR toPersonId = :personId")
    suspend fun relationshipCountForPerson(personId: String): Int

    @Query("SELECT * FROM people WHERE id = :personId LIMIT 1")
    suspend fun getPerson(personId: String): PersonEntity?

    @Query("SELECT * FROM relationships WHERE id = :relationshipId LIMIT 1")
    suspend fun getRelationship(relationshipId: String): RelationshipEntity?

    @Query(
        "SELECT tags.name FROM tags " +
            "INNER JOIN person_tags ON tags.id = person_tags.tagId " +
            "WHERE person_tags.personId = :personId ORDER BY tags.name",
    )
    suspend fun getTagNamesForPerson(personId: String): List<String>

    @Upsert
    suspend fun upsertPerson(person: PersonEntity)

    @Upsert
    suspend fun upsertPeople(people: List<PersonEntity>)

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

    @Upsert
    suspend fun upsertRelativeAgeOrder(order: RelativeAgeOrderEntity)

    @Upsert
    suspend fun upsertRelativeAgeOrders(orders: List<RelativeAgeOrderEntity>)

    @Query(
        "DELETE FROM inference_dismissals WHERE fromPersonId = :fromPersonId " +
            "AND toPersonId = :toPersonId AND ruleId = :ruleId",
    )
    suspend fun deleteInferenceDismissal(
        fromPersonId: String,
        toPersonId: String,
        ruleId: String,
    )

    @Query(
        "DELETE FROM relative_age_orders WHERE firstPersonId = :firstPersonId " +
            "AND secondPersonId = :secondPersonId",
    )
    suspend fun deleteRelativeAgeOrder(firstPersonId: String, secondPersonId: String)

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

    @Query("DELETE FROM relative_age_orders")
    suspend fun deleteAllRelativeAgeOrders()

    // ===== AI 候选（v6） =====

    @Query("SELECT * FROM ai_candidates WHERE status = 'PENDING' ORDER BY createdAt")
    fun observePendingAiCandidates(): Flow<List<AiCandidateEntity>>

    @Query("SELECT * FROM ai_candidates WHERE status = 'PENDING' ORDER BY createdAt")
    suspend fun getPendingAiCandidates(): List<AiCandidateEntity>

    @Upsert
    suspend fun upsertAiCandidates(candidates: List<AiCandidateEntity>)

    @Query("UPDATE ai_candidates SET status = :status WHERE id = :id")
    suspend fun updateAiCandidateStatus(id: String, status: String)

    @Query("DELETE FROM ai_candidates WHERE status = 'DISMISSED'")
    suspend fun deleteDismissedAiCandidates()

    // ===== GEDCOM 暂存（v6） =====

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportBatch(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getImportBatch(id: String): ImportBatchEntity?

    @Query("UPDATE import_batches SET status = :status, rollbackJson = :rollbackJson WHERE id = :id")
    suspend fun updateImportBatchStatus(id: String, status: String, rollbackJson: String?)

    @Query("DELETE FROM import_batches WHERE id = :id")
    suspend fun deleteImportBatch(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStagedPeople(people: List<StagedPersonEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStagedRelationships(relationships: List<StagedRelationshipEntity>)

    @Query("SELECT * FROM staged_people WHERE batchId = :batchId ORDER BY name COLLATE NOCASE")
    suspend fun getStagedPeople(batchId: String): List<StagedPersonEntity>

    @Query("SELECT * FROM staged_relationships WHERE batchId = :batchId")
    suspend fun getStagedRelationships(batchId: String): List<StagedRelationshipEntity>

    @Query(
        "UPDATE staged_people SET decision = :decision, mergePersonId = :mergePersonId " +
            "WHERE id = :id",
    )
    suspend fun updateStagedPersonDecision(
        id: String,
        decision: String,
        mergePersonId: String?,
    )

    @Query("UPDATE staged_relationships SET decision = :decision WHERE id = :id")
    suspend fun updateStagedRelationshipDecision(id: String, decision: String)

    // ===== 人物合并记录（v6） =====

    @Insert
    suspend fun insertMergeRecord(record: MergeRecordEntity)

    @Query("SELECT * FROM merge_records WHERE id = :id")
    suspend fun getMergeRecord(id: String): MergeRecordEntity?

    @Query("DELETE FROM merge_records WHERE id = :id")
    suspend fun deleteMergeRecord(id: String)

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
        relativeAgeOrders: List<RelativeAgeOrderEntity>,
    ) {
        deleteAllRelationships()
        deleteAllPersonTags()
        deleteAllPeople()
        deleteAllTags()
        deleteAllRelationTypes()
        deleteAllGraphPositions()
        deleteAllInferenceDismissals()
        deleteAllRelativeAgeOrders()

        upsertPeople(people)
        upsertGraphPositions(graphPositions)
        upsertInferenceDismissals(inferenceDismissals)
        upsertRelativeAgeOrders(relativeAgeOrders)
        insertTags(tags)
        insertPersonTags(personTags)
        insertRelationTypes(relationTypes)
        upsertRelationships(relationships)
    }
}
