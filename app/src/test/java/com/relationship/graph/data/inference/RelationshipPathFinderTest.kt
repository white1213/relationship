package com.relationship.graph.data.inference

import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipPathFinderTest {

    private val relationTypes = PresetRelationTypes.all

    private fun person(id: String, gender: Gender = Gender.UNSPECIFIED) =
        PersonEntity(id = id, name = id, gender = gender)

    private fun rel(id: String, from: String, to: String, typeId: String) = RelationshipEntity(
        id = id, fromPersonId = from, toPersonId = to, relationTypeId = typeId,
    )

    private val people = listOf(
        person("anchor", Gender.MALE),
        person("father", Gender.MALE),
        person("mother", Gender.FEMALE),
        person("uncle", Gender.MALE), // 母亲的哥哥
        person("cousin", Gender.FEMALE), // 舅舅的女儿
        person("spouse", Gender.FEMALE),
        person("stranger", Gender.MALE),
    )

    private val relationships = listOf(
        rel("r1", "father", "anchor", "preset_parent_child"),
        rel("r2", "mother", "anchor", "preset_parent_child"),
        rel("r3", "mother", "uncle", "preset_sibling"),
        rel("r4", "uncle", "cousin", "preset_parent_child"),
        rel("r5", "anchor", "spouse", "preset_spouse"),
    )

    private fun find(anchor: String, target: String) = RelationshipPathFinder.find(
        anchorPersonId = anchor,
        targetPersonId = target,
        relationships = relationships,
        relationTypes = relationTypes,
        people = people,
        ageOrders = emptyList(),
    )

    @Test
    fun `direct parent path has one hop`() {
        val paths = find("anchor", "father")
        assertEquals(1, paths.size)
        assertEquals(listOf("anchor", "father"), paths.first().personIds)
        assertEquals(1, paths.first().hops.size)
        assertTrue(paths.first().hops.first().label.isNotBlank())
    }

    @Test
    fun `cousin path goes through mother and uncle`() {
        val paths = find("anchor", "cousin")
        assertTrue(paths.isNotEmpty())
        val best = paths.first()
        assertEquals(listOf("anchor", "mother", "uncle", "cousin"), best.personIds)
        assertEquals(3, best.hops.size)
    }

    @Test
    fun `disconnected person has no path`() {
        assertTrue(find("anchor", "stranger").isEmpty())
    }

    @Test
    fun `self query returns empty`() {
        assertTrue(find("anchor", "anchor").isEmpty())
    }

    @Test
    fun `spouse edge is preferred over long detour`() {
        val paths = find("spouse", "anchor")
        assertEquals(1, paths.first().hops.size)
    }
}
