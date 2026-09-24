package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun familyLayoutPlacesParentsAboveChildren() {
        val people = listOf(
            PersonEntity(id = "grandparent", name = "祖辈"),
            PersonEntity(id = "parent", name = "父辈"),
            PersonEntity(id = "child", name = "子辈"),
        )
        val parentType = RelationTypeEntity(
            id = "preset_parent_child",
            name = "父母",
            inverseName = "子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val relationships = listOf(
            relationship("first", "grandparent", "parent", parentType.id),
            relationship("second", "parent", "child", parentType.id),
        )

        val layout = GraphLayoutEngine.layout(
            people = people,
            relationships = relationships,
            relationTypes = listOf(parentType),
            mode = GraphMode.FAMILY,
            myPersonId = "child",
            pinnedPositions = emptyMap(),
        )

        assertEquals(0, layout.generationByPerson.getValue("child"))
        assertEquals(-1, layout.generationByPerson.getValue("parent"))
        assertEquals(-2, layout.generationByPerson.getValue("grandparent"))
        assertTrue(layout.positions.getValue("child").y > layout.positions.getValue("parent").y)
        assertTrue(layout.positions.getValue("parent").y > layout.positions.getValue("grandparent").y)
        assertTrue(layout.routes.all { it.style == GraphRouteStyle.PARENT_CHILD })
    }

    @Test
    fun pinnedPositionOverridesAutomaticFamilyLayout() {
        val people = listOf(
            PersonEntity(id = "parent", name = "父辈"),
            PersonEntity(id = "child", name = "子辈"),
        )
        val parentType = RelationTypeEntity(
            id = "preset_parent_child",
            name = "父母",
            inverseName = "子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val expected = LayoutPoint(321f, -45f)

        val layout = GraphLayoutEngine.layout(
            people = people,
            relationships = listOf(relationship("edge", "parent", "child", parentType.id)),
            relationTypes = listOf(parentType),
            mode = GraphMode.FAMILY,
            myPersonId = "child",
            pinnedPositions = mapOf("parent" to expected),
        )

        assertEquals(expected, layout.positions["parent"])
        assertNotNull(layout.positions["child"])
    }

    @Test
    fun spouseStaysOnSameGenerationAsPartner() {
        val people = listOf(
            PersonEntity(id = "me", name = "我"),
            PersonEntity(id = "spouse", name = "配偶"),
            PersonEntity(id = "child", name = "子女"),
        )
        val parentType = RelationTypeEntity(
            id = "preset_parent_child",
            name = "父母",
            inverseName = "子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val spouseType = RelationTypeEntity(
            id = "preset_spouse",
            name = "配偶/伴侣",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.BIDIRECTIONAL,
            isBuiltIn = true,
        )

        val layout = GraphLayoutEngine.layout(
            people = people,
            relationships = listOf(
                relationship("marriage", "me", "spouse", spouseType.id),
                relationship("child", "me", "child", parentType.id),
            ),
            relationTypes = listOf(parentType, spouseType),
            mode = GraphMode.FAMILY,
            myPersonId = "me",
            pinnedPositions = emptyMap(),
        )

        assertEquals(
            layout.generationByPerson["me"],
            layout.generationByPerson["spouse"],
        )
        assertEquals(
            layout.positions.getValue("me").y,
            layout.positions.getValue("spouse").y,
        )
        assertTrue(layout.positions.getValue("child").y > layout.positions.getValue("me").y)
    }

    @Test
    fun socialAndAllLayoutsIncludeEveryVisiblePerson() {
        val people = listOf(
            PersonEntity(id = "me", name = "我"),
            PersonEntity(id = "friend", name = "朋友"),
            PersonEntity(id = "family", name = "家人"),
        )
        val friendType = relationType("preset_friend", "朋友", RelationDirection.BIDIRECTIONAL)
        val parentType = RelationTypeEntity(
            id = "preset_parent_child",
            name = "父母",
            inverseName = "子女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val relationships = listOf(
            relationship("friend", "me", "friend", friendType.id),
            relationship("family", "family", "me", parentType.id),
        )

        val social = GraphLayoutEngine.layout(
            people = people.filter { it.id != "family" },
            relationships = listOf(relationships.first()),
            relationTypes = listOf(friendType),
            mode = GraphMode.SOCIAL,
            myPersonId = "me",
            pinnedPositions = emptyMap(),
        )
        val all = GraphLayoutEngine.layout(
            people = people,
            relationships = relationships,
            relationTypes = listOf(friendType, parentType),
            mode = GraphMode.ALL,
            myPersonId = "me",
            pinnedPositions = emptyMap(),
        )

        assertEquals(setOf("me", "friend"), social.positions.keys)
        assertEquals(setOf("me", "friend", "family"), all.positions.keys)
        assertTrue(social.routes.all { it.style == GraphRouteStyle.SOCIAL })
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
