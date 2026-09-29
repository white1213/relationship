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
        val parentsByChild = parentChildEdges.groupBy(
            keySelector = { it.childPersonId },
            valueTransform = { it.parentPersonId },
        )
        val childrenByParent = parentChildEdges.groupBy(
            keySelector = { it.parentPersonId },
            valueTransform = { it.childPersonId },
        )
        val spousesByPerson = buildMap<String, MutableSet<String>> {
            spouseRelationshipsOf(relationships, typeById).forEach { relationship ->
                getOrPut(relationship.fromPersonId) { mutableSetOf() } += relationship.toPersonId
                getOrPut(relationship.toPersonId) { mutableSetOf() } += relationship.fromPersonId
            }
        }
        fun spousesOf(personId: String): Set<String> = spousesByPerson[personId].orEmpty()

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
                    parentsByChild[current].orEmpty().forEach { parentId ->
                        if (personIds.add(parentId)) queue.add(parentId)
                        personIds += spousesOf(parentId)
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
                personIds += spousesOf(anchorPersonId)
                val queue = ArrayDeque<String>()
                queue.add(anchorPersonId)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    childrenByParent[current].orEmpty().forEach { childId ->
                        if (personIds.add(childId)) queue.add(childId)
                        personIds += spousesOf(childId)
                        personIds += parentsByChild[childId].orEmpty()
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

    private fun spouseRelationshipsOf(
        relationships: List<RelationshipEntity>,
        typeById: Map<String, RelationTypeEntity>,
    ): List<RelationshipEntity> = relationships.filter {
        RelationshipSemantics.kind(typeById[it.relationTypeId]) == FamilyRelationKind.SPOUSE
    }

}
