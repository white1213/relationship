package com.relationship.graph.data.kinship

import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelationshipDuplicatesTest {

    private val types = PresetRelationTypes.all

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

    @Test
    fun samePairSameFamilyTypeIsDuplicateRegardlessOfDirection() {
        val existing = listOf(
            relationship("r1", "aunt", "nephew", "preset_aunt"),
        )

        // 同方向
        assertEquals(
            "r1",
            RelationshipDuplicates.find(
                existing, types, "aunt", "nephew", "preset_aunt",
            )?.id,
        )
        // 反方向存同一有向类型，同样算重复（防方向建反后再建一遍）
        assertEquals(
            "r1",
            RelationshipDuplicates.find(
                existing, types, "nephew", "aunt", "preset_aunt",
            )?.id,
        )
    }

    @Test
    fun samePairDifferentFamilyTypeIsAllowed() {
        val existing = listOf(
            relationship("r1", "aunt", "nephew", "preset_aunt"),
        )

        assertNull(
            RelationshipDuplicates.find(
                existing, types, "aunt", "nephew", "preset_aunt_husband",
            ),
        )
    }

    @Test
    fun socialSameTypeIsNeverDuplicate() {
        val existing = listOf(
            relationship("r1", "a", "b", "preset_classmate"),
        )

        assertNull(
            RelationshipDuplicates.find(
                existing, types, "a", "b", "preset_classmate",
            ),
        )
    }

    @Test
    fun editingExistingRelationshipExcludesItself() {
        val existing = listOf(
            relationship("r1", "aunt", "nephew", "preset_aunt"),
        )

        assertNull(
            RelationshipDuplicates.find(
                existing, types, "aunt", "nephew", "preset_aunt",
                excludeRelationshipId = "r1",
            ),
        )
    }

    @Test
    fun unrelatedPairsDoNotCollide() {
        val existing = listOf(
            relationship("r1", "aunt", "nephew", "preset_aunt"),
        )

        assertNull(
            RelationshipDuplicates.find(
                existing, types, "aunt", "other", "preset_aunt",
            ),
        )
    }
}
