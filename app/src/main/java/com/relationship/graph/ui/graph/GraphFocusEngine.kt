package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

enum class GraphFocusScope {
    RELATED,
    ANCESTORS,
    DESCENDANTS,
    BRANCH,
}

data class GraphFocusResult(
    val personIds: Set<String>,
    val relationshipIds: Set<String>,
)

object GraphFocusEngine {
    fun resolve(
        anchorPersonId: String,
        scope: GraphFocusScope,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
    ): GraphFocusResult {
        val typeById = relationTypes.associateBy { it.id }
        val parentChildRelationships = relationships.filter {
            isParentChild(typeById[it.relationTypeId])
        }
        val spouseRelationships = relationships.filter {
            isSpouse(typeById[it.relationTypeId])
        }

        return when (scope) {
            GraphFocusScope.RELATED -> {
                val related = relationships.filter {
                    it.fromPersonId == anchorPersonId || it.toPersonId == anchorPersonId
                }
                GraphFocusResult(
                    personIds = buildSet {
                        add(anchorPersonId)
                        related.forEach {
                            add(it.fromPersonId)
                            add(it.toPersonId)
                        }
                    },
                    relationshipIds = related.map { it.id }.toSet(),
                )
            }
            GraphFocusScope.ANCESTORS -> {
                val personIds = mutableSetOf(anchorPersonId)
                val queue = ArrayDeque<String>()
                queue.add(anchorPersonId)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    parentChildRelationships
                        .filter { it.toPersonId == current }
                        .forEach { relationship ->
                            val parentId = relationship.fromPersonId
                            if (personIds.add(parentId)) queue.add(parentId)
                            spousesOf(parentId, spouseRelationships).forEach(personIds::add)
                        }
                }
                GraphFocusResult(
                    personIds = personIds,
                    relationshipIds = relationships
                        .filter { it.fromPersonId in personIds && it.toPersonId in personIds }
                        .map { it.id }
                        .toSet(),
                )
            }
            GraphFocusScope.DESCENDANTS,
            GraphFocusScope.BRANCH,
            -> {
                val personIds = mutableSetOf(anchorPersonId)
                personIds += spousesOf(anchorPersonId, spouseRelationships)
                val queue = ArrayDeque<String>()
                queue.add(anchorPersonId)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    parentChildRelationships
                        .filter { it.fromPersonId == current }
                        .forEach { relationship ->
                            val childId = relationship.toPersonId
                            if (personIds.add(childId)) queue.add(childId)
                            personIds += spousesOf(childId, spouseRelationships)
                            personIds += parentChildRelationships
                                .filter { it.toPersonId == childId }
                                .map { it.fromPersonId }
                        }
                }
                GraphFocusResult(
                    personIds = personIds,
                    relationshipIds = relationships
                        .filter { it.fromPersonId in personIds && it.toPersonId in personIds }
                        .map { it.id }
                        .toSet(),
                )
            }
        }
    }

    private fun spousesOf(
        personId: String,
        spouseRelationships: List<RelationshipEntity>,
    ): Set<String> = spouseRelationships.mapNotNull { relationship ->
        when (personId) {
            relationship.fromPersonId -> relationship.toPersonId
            relationship.toPersonId -> relationship.fromPersonId
            else -> null
        }
    }.toSet()

    private fun isParentChild(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_parent_child"

    private fun isSpouse(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_spouse"
}
