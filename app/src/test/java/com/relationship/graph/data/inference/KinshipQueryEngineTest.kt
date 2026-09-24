package com.relationship.graph.data.inference

import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KinshipQueryEngineTest {
    @Test
    fun directRelationshipReturnsBothDirections() {
        val relationship = RelationshipEntity(
            id = "edge",
            fromPersonId = "parent",
            toPersonId = "child",
            relationTypeId = "preset_parent_child",
        )

        val result = KinshipQueryEngine.query(
            referencePersonId = "child",
            targetPersonId = "parent",
            relationships = listOf(relationship),
            relationTypes = PresetRelationTypes.all,
            inferredCandidates = emptyList(),
        )

        assertEquals("父母", result?.referenceCallsTarget)
        assertEquals("子女", result?.targetCallsReference)
        assertTrue(result?.isDirect == true)
    }

    @Test
    fun inferredRelationshipIsUsedWhenNoDirectRelationExists() {
        val candidate = InferredRelationshipCandidate(
            fromPersonId = "grandparent",
            toPersonId = "child",
            relationTypeId = "preset_grandparent",
            labelForFrom = "爷爷",
            labelForTo = "孙辈",
            confidence = InferenceConfidence.HIGH,
            rule = InferenceRule.GRANDPARENT,
            reason = "父亲的父母",
            supportingRelationshipIds = setOf("one", "two"),
        )

        val result = KinshipQueryEngine.query(
            referencePersonId = "child",
            targetPersonId = "grandparent",
            relationships = emptyList(),
            relationTypes = PresetRelationTypes.all,
            inferredCandidates = listOf(candidate),
        )

        assertEquals("孙辈", result?.referenceCallsTarget)
        assertEquals("爷爷", result?.targetCallsReference)
        assertFalse(result?.isDirect == true)
    }

    @Test
    fun returnsNullWhenNoRelationshipCanBeResolved() {
        val result = KinshipQueryEngine.query(
            referencePersonId = "one",
            targetPersonId = "two",
            relationships = emptyList(),
            relationTypes = PresetRelationTypes.all,
            inferredCandidates = emptyList(),
        )

        assertNull(result)
    }
}
