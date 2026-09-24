package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphCanvasTest {
    @Test
    fun multipleRelationshipsBetweenSamePeopleShareOneEdgeGroup() {
        val types = listOf(
            relationType("friend", "朋友", RelationDirection.BIDIRECTIONAL),
            relationType("colleague", "同事", RelationDirection.BIDIRECTIONAL),
        )
        val relationships = listOf(
            relationship("one", "a", "b", "friend"),
            relationship("two", "b", "a", "colleague"),
        )

        val groups = buildEdgeGroups(relationships, types)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().relationships.size)
        assertEquals(setOf("friend", "colleague"), groups.single().relationTypes.map { it.id }.toSet())
    }

    @Test
    fun automaticLayoutInitializesEveryPersonWithinBounds() {
        val people = (1..12).map { index ->
            PersonEntity(id = "p$index", name = "人物$index")
        }
        val edges = (1 until people.size).map { index ->
            GraphEdgeGroup(
                key = "p$index\u0000p${index + 1}",
                firstPersonId = "p$index",
                secondPersonId = "p${index + 1}",
                relationships = emptyList(),
                relationTypes = emptyList(),
            )
        }

        val positions = computeGraphLayout(people, edges, emptyMap())

        assertEquals(people.size, positions.size)
        assertTrue(positions.values.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(positions.values.all { it.x in -650f..650f && it.y in -650f..650f })
    }

    private fun relationType(
        id: String,
        name: String,
        direction: RelationDirection,
    ) = RelationTypeEntity(
        id = id,
        name = name,
        category = RelationCategory.SOCIAL,
        direction = direction,
    )

    private fun relationship(
        id: String,
        from: String,
        to: String,
        typeId: String,
    ) = RelationshipEntity(
        id = id,
        fromPersonId = from,
        toPersonId = to,
        relationTypeId = typeId,
    )
}
