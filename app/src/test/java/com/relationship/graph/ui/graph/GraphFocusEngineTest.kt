package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphFocusEngineTest {
    private val parentChild = RelationTypeEntity(
        id = "preset_parent_child",
        name = "父母",
        inverseName = "子女",
        category = RelationCategory.FAMILY,
        direction = RelationDirection.DIRECTED,
        isBuiltIn = true,
    )
    private val spouse = RelationTypeEntity(
        id = "preset_spouse",
        name = "配偶/伴侣",
        category = RelationCategory.FAMILY,
        direction = RelationDirection.BIDIRECTIONAL,
        isBuiltIn = true,
    )
    private val relationships = listOf(
        relationship("grandparent-parent", "grandparent", "parent", parentChild.id),
        relationship("parent-child", "parent", "child", parentChild.id),
        relationship("parent-spouse", "parent", "other-parent", spouse.id),
        relationship("child-spouse", "child", "child-spouse", spouse.id),
    )

    @Test
    fun ancestorsIncludeParentsAndTheirSpouses() {
        val result = GraphFocusEngine.resolve(
            anchorPersonId = "child",
            scope = GraphFocusScope.ANCESTORS,
            relationships = relationships,
            relationTypes = listOf(parentChild, spouse),
        )

        assertTrue("child" in result.personIds)
        assertTrue("parent" in result.personIds)
        assertTrue("other-parent" in result.personIds)
        assertTrue("grandparent" in result.personIds)
        assertEquals(setOf("child", "parent", "other-parent", "grandparent"), result.personIds)
    }

    @Test
    fun branchIncludesDescendantsSpousesAndCoParents() {
        val result = GraphFocusEngine.resolve(
            anchorPersonId = "parent",
            scope = GraphFocusScope.BRANCH,
            relationships = relationships,
            relationTypes = listOf(parentChild, spouse),
        )

        assertEquals(
            setOf("parent", "other-parent", "child", "child-spouse"),
            result.personIds,
        )
        assertEquals(
            setOf("parent-spouse", "parent-child", "child-spouse"),
            result.relationshipIds,
        )
    }

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
