package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics

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
        val parentChildEdges = relationships.mapNotNull {
            RelationshipSemantics.parentChildEdge(it, typeById[it.relationTypeId])
        }
        val spouseRelationships = relationships.filter {
            RelationshipSemantics.kind(typeById[it.relationTypeId]) == FamilyRelationKind.SPOUSE
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
                    parentChildEdges
                        .filter { it.childPersonId == current }
                        .forEach { edge ->
                            val parentId = edge.parentPersonId
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
                    parentChildEdges
                        .filter { it.parentPersonId == current }
                        .forEach { edge ->
                            val childId = edge.childPersonId
                            if (personIds.add(childId)) queue.add(childId)
                            personIds += spousesOf(childId, spouseRelationships)
                            personIds += parentChildEdges
                                .filter { it.childPersonId == childId }
                                .map { it.parentPersonId }
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

}
