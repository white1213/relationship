package com.relationship.graph.data.inference

import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.RelationshipSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 称谓方向约定（与关系编辑页「A 是 B 的X」一致）：
 * from 就是 type.name 这个称谓，因此 to 称呼 from 为 type.name，
 * from 称呼 to 为 type.inverseName（或 labelOverride / inverseLabelOverride）。
 */
class KinshipDirectionTest {

    private fun person(id: String, name: String) = PersonEntity(id = id, name = name)

    private fun query(
        referenceId: String,
        targetId: String,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
    ) = KinshipQueryEngine.query(
        referencePersonId = referenceId,
        targetPersonId = targetId,
        relationships = relationships,
        relationTypes = relationTypes,
        inferredCandidates = emptyList(),
        people = listOf(
            person("a", "甲"),
            person("b", "乙"),
        ),
    )

    @Test
    fun confirmedInferenceGrandparentLabelsAreNotSwapped() {
        // 已确认的推导关系：from=爷爷，to=孙辈。
        // confirmInference 存的是 labelOverride = labelFor(from) = 爷爷，inverseLabelOverride = 孙辈。
        val relationship = RelationshipEntity(
            id = "r",
            fromPersonId = "a",
            toPersonId = "b",
            relationTypeId = "preset_grandparent",
            source = RelationshipSource.CONFIRMED_INFERENCE,
            labelOverride = "爷爷",
            inverseLabelOverride = "孙辈",
        )

        // 乙(孙辈) 称呼 甲(爷爷) 应为 爷爷；甲称呼乙应为 孙辈。
        val result = query("b", "a", listOf(relationship), PresetRelationTypes.all)

        assertEquals("爷爷", result?.referenceCallsTarget)
        assertEquals("孙辈", result?.targetCallsReference)
    }

    @Test
    fun directedCustomTypeLabelsAreNotSwapped() {
        // 自定义有向类型：甲 是 乙 的 师傅（from=甲, to=乙）。
        val customType = RelationTypeEntity(
            id = "custom_shifu",
            name = "师傅",
            inverseName = "徒弟",
            category = RelationCategory.CUSTOM,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = false,
        )
        val relationship = RelationshipEntity(
            id = "r",
            fromPersonId = "a",
            toPersonId = "b",
            relationTypeId = customType.id,
        )

        // 乙 称呼 甲 为 师傅；甲 称呼 乙 为 徒弟。
        val fromTargetView = query("b", "a", listOf(relationship), listOf(customType))
        assertEquals("师傅", fromTargetView?.referenceCallsTarget)
        assertEquals("徒弟", fromTargetView?.targetCallsReference)

        // 反向查询同样成立。
        val fromReferenceView = query("a", "b", listOf(relationship), listOf(customType))
        assertEquals("徒弟", fromReferenceView?.referenceCallsTarget)
        assertEquals("师傅", fromReferenceView?.targetCallsReference)
    }

    @Test
    fun directedParentChildPresetIsNotSwapped() {
        // 甲 是 乙 的母亲（preset_mother_daughter: 母亲/女儿）。
        val relationship = RelationshipEntity(
            id = "r",
            fromPersonId = "a",
            toPersonId = "b",
            relationTypeId = "preset_mother_daughter",
        )

        val childView = query("b", "a", listOf(relationship), PresetRelationTypes.all)
        assertEquals("母亲", childView?.referenceCallsTarget)
        assertEquals("女儿", childView?.targetCallsReference)
    }

    @Test
    fun confirmedInferenceInLawLabelsAreNotSwapped() {
        // preset_in_law（姻亲长辈，名称不在预设集合中，会走通用分支）。
        val relationship = RelationshipEntity(
            id = "r",
            fromPersonId = "a",
            toPersonId = "b",
            relationTypeId = "preset_in_law",
            source = RelationshipSource.CONFIRMED_INFERENCE,
            labelOverride = "岳父",
            inverseLabelOverride = "女婿",
        )

        val sonInLawView = query("b", "a", listOf(relationship), PresetRelationTypes.all)
        assertEquals("岳父", sonInLawView?.referenceCallsTarget)
        assertEquals("女婿", sonInLawView?.targetCallsReference)
    }
}
