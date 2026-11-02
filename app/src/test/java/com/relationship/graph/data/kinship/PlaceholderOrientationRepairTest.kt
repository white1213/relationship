package com.relationship.graph.data.kinship

import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderOrientationRepairTest {

    private val types = PresetRelationTypes.all
    private val parentChild = "preset_parent_child"

    private fun person(id: String, name: String, placeholder: Boolean = false) = PersonEntity(
        id = id,
        name = name,
        isPlaceholder = placeholder,
    )

    private fun relationship(id: String, from: String, to: String, typeId: String) =
        RelationshipEntity(
            id = id,
            fromPersonId = from,
            toPersonId = to,
            relationTypeId = typeId,
        )

    @Test
    fun invertedSonRelationIsFlipped() {
        // v1.10.0 bug 数据：占位名「老王的儿子」，却存成 from=占位, to=老王（占位是老王的父亲）。
        val people = listOf(
            person("wang", "老王"),
            person("ph", "老王的儿子", placeholder = true),
        )
        val relationships = listOf(relationship("r", "ph", "wang", parentChild))

        val flips = PlaceholderOrientationRepair.plan(people, relationships, types)

        assertEquals(
            listOf(PlaceholderOrientationRepair.Flip("r", "wang", "ph")),
            flips,
        )
    }

    @Test
    fun alreadyCorrectRelationIsLeftAlone() {
        val people = listOf(
            person("wang", "老王"),
            person("ph", "老王的舅舅", placeholder = true),
        )
        val relationships = listOf(
            relationship("r", "ph", "wang", "preset_aunt"),
        )

        // 舅舅用的是 AUNT_UNCLE 通用类型，类型不匹配 → 不处理（由存在句建出的正确数据不动）。
        assertTrue(PlaceholderOrientationRepair.plan(people, relationships, types).isEmpty())
    }

    @Test
    fun nonPlaceholderIsIgnored() {
        val people = listOf(
            person("wang", "老王"),
            person("kid", "老王的儿子"),
        )
        val relationships = listOf(relationship("r", "kid", "wang", parentChild))

        assertTrue(PlaceholderOrientationRepair.plan(people, relationships, types).isEmpty())
    }

    @Test
    fun ambiguousMultipleRelationsAreSkipped() {
        val people = listOf(
            person("wang", "老王"),
            person("ph", "老王的儿子", placeholder = true),
        )
        val relationships = listOf(
            relationship("r1", "ph", "wang", parentChild),
            relationship("r2", "wang", "ph", parentChild),
        )

        assertTrue(PlaceholderOrientationRepair.plan(people, relationships, types).isEmpty())
    }
}
