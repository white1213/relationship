package com.relationship.graph.ui

import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipLabelsTest {
    private val parentType = RelationTypeEntity(
        id = "parent",
        name = "父母",
        inverseName = "子女",
        category = RelationCategory.FAMILY,
        direction = RelationDirection.DIRECTED,
        isBuiltIn = true,
    )
    private val friendType = RelationTypeEntity(
        id = "friend",
        name = "朋友",
        category = RelationCategory.SOCIAL,
        direction = RelationDirection.BIDIRECTIONAL,
        isBuiltIn = true,
    )

    @Test
    fun directedRelationshipUsesPerspectiveLabel() {
        val relationship = RelationshipEntity(
            id = "relationship",
            fromPersonId = "parent",
            toPersonId = "child",
            relationTypeId = parentType.id,
        )

        assertEquals(
            "父母",
            relationshipLabelForPerson(relationship, parentType, "parent"),
        )
        assertEquals(
            "子女",
            relationshipLabelForPerson(relationship, parentType, "child"),
        )
    }

    @Test
    fun bidirectionalRelationshipUsesSameLabelForBothPeople() {
        val relationship = RelationshipEntity(
            id = "relationship",
            fromPersonId = "first",
            toPersonId = "second",
            relationTypeId = friendType.id,
        )

        assertEquals("朋友", relationshipLabelForPerson(relationship, friendType, "first"))
        assertEquals("朋友", relationshipLabelForPerson(relationship, friendType, "second"))
    }
}
