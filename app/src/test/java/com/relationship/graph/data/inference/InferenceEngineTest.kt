package com.relationship.graph.data.inference

import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.InferenceDismissalEntity
import com.relationship.graph.data.local.InferenceRelationTypeIds
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.PresetRelationTypes
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.AgeComparison
import com.relationship.graph.data.local.RelativeAgeOrderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceEngineTest {
    private val parentChild = "preset_parent_child"
    private val spouse = "preset_spouse"
    private val sibling = "preset_sibling"

    @Test
    fun grandparentsUseGenderAndParentSideLabels() {
        val people = listOf(
            person("grandfather", "爷爷", Gender.MALE),
            person("grandmother", "奶奶", Gender.FEMALE),
            person("parent", "父亲", Gender.MALE),
            person("child", "孩子", Gender.FEMALE),
        )
        val relationships = listOf(
            relationship("edge1", "grandfather", "parent", parentChild),
            relationship("edge2", "grandmother", "parent", parentChild),
            relationship("edge3", "parent", "child", parentChild),
        )

        val candidates = infer(people, relationships)
        val grandfather = candidates.single {
            it.fromPersonId == "grandfather" && it.toPersonId == "child"
        }
        val grandmother = candidates.single {
            it.fromPersonId == "grandmother" && it.toPersonId == "child"
        }

        assertEquals("爷爷", grandfather.labelForFrom)
        assertEquals("孙女", grandfather.labelForTo)
        assertEquals("奶奶", grandmother.labelForFrom)
        assertEquals(InferenceConfidence.HIGH, grandfather.confidence)
    }

    @Test
    fun sharedParentInfersSiblingButDirectSiblingSuppressesCandidate() {
        val people = listOf(
            person("parent", "父母"),
            person("older", "老大", Gender.MALE, "1990-01-01"),
            person("younger", "老二", Gender.FEMALE, "1995-01-01"),
        )
        val parentRelationships = listOf(
            relationship("edge1", "parent", "older", parentChild),
            relationship("edge2", "parent", "younger", parentChild),
        )

        val inferred = infer(people, parentRelationships)
            .single { setOf(it.fromPersonId, it.toPersonId) == setOf("older", "younger") }
        assertTrue(inferred.labelForFrom in setOf("哥哥", "姐姐"))
        assertTrue(inferred.labelForTo in setOf("弟弟", "妹妹"))

        val withDirectSibling = infer(
            people,
            parentRelationships + relationship("direct", "older", "younger", sibling),
        )
        assertNull(
            withDirectSibling.firstOrNull {
                setOf(it.fromPersonId, it.toPersonId) == setOf("older", "younger")
            },
        )
    }

    @Test
    fun infersAuntAndCousinRelationships() {
        val people = listOf(
            person("grandparent", "祖辈"),
            person("parent", "父亲", Gender.MALE),
            person("aunt", "姑姑", Gender.FEMALE),
            person("child", "孩子"),
            person("cousin", "表妹", Gender.FEMALE, "2000-01-01"),
        )
        val relationships = listOf(
            relationship("edge1", "grandparent", "parent", parentChild),
            relationship("edge2", "grandparent", "aunt", parentChild),
            relationship("edge3", "parent", "child", parentChild),
            relationship("edge4", "aunt", "cousin", parentChild),
        )

        val candidates = infer(people, relationships)

        assertTrue(
            candidates.any {
                it.rule == InferenceRule.AUNT_UNCLE &&
                    it.fromPersonId == "aunt" &&
                    it.toPersonId == "child" &&
                    it.labelForFrom == "姑姑"
            },
        )
        assertTrue(
            candidates.any {
                it.rule == InferenceRule.COUSIN &&
                    setOf(it.fromPersonId, it.toPersonId) == setOf("child", "cousin")
            },
        )
    }

    @Test
    fun infersParentsInLawAndChildInLawLabels() {
        val people = listOf(
            person("husband", "丈夫", Gender.MALE),
            person("wife", "妻子", Gender.FEMALE),
            person("wifeFather", "妻子父亲", Gender.MALE),
        )
        val relationships = listOf(
            relationship("marriage", "husband", "wife", spouse),
            relationship("parent", "wifeFather", "wife", parentChild),
        )

        val candidate = infer(people, relationships).single {
            it.fromPersonId == "wifeFather" && it.toPersonId == "husband"
        }

        assertEquals(InferenceRelationTypeIds.IN_LAW, candidate.relationTypeId)
        assertEquals("岳父", candidate.labelForFrom)
        assertEquals("女婿", candidate.labelForTo)
    }

    @Test
    fun dismissedCandidateStaysHiddenWhileEvidenceFingerprintMatches() {
        val people = listOf(
            person("parent", "父母"),
            person("first", "老大"),
            person("second", "老二"),
        )
        val relationships = listOf(
            relationship("edge1", "parent", "first", parentChild),
            relationship("edge2", "parent", "second", parentChild),
        )
        val candidate = infer(people, relationships).first {
            setOf(it.fromPersonId, it.toPersonId) == setOf("first", "second")
        }
        val dismissal = InferenceDismissalEntity(
            fromPersonId = candidate.fromPersonId,
            toPersonId = candidate.toPersonId,
            ruleId = candidate.rule.id,
            evidenceFingerprint = candidate.evidenceFingerprint,
        )

        val afterDismissal = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all,
            dismissals = listOf(dismissal),
        )

        assertTrue(
            afterDismissal.none {
                setOf(it.fromPersonId, it.toPersonId) == setOf("first", "second")
            },
        )
    }

    @Test
    fun customSingleDirectionFatherRelationshipParticipatesInInference() {
        val fatherType = RelationTypeEntity(
            id = "custom_father",
            name = "父亲",
            inverseName = "子女",
            category = RelationCategory.CUSTOM,
            direction = RelationDirection.DIRECTED,
        )
        val people = listOf(
            person("grandfather", "爷爷", Gender.MALE),
            person("father", "父亲"),
            person("child", "孩子"),
        )
        val relationships = listOf(
            relationship("edge1", "grandfather", "father", fatherType.id),
            relationship("edge2", "father", "child", fatherType.id),
        )

        val candidate = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all + fatherType,
            dismissals = emptyList(),
        ).single {
            it.fromPersonId == "grandfather" && it.toPersonId == "child"
        }

        assertEquals("爷爷", candidate.labelForFrom)
        assertEquals("孙辈", candidate.labelForTo)
    }

    @Test
    fun customChildFirstDirectionIsNormalizedToParentToChild() {
        val childType = RelationTypeEntity(
            id = "custom_child",
            name = "子女",
            inverseName = "父母",
            category = RelationCategory.CUSTOM,
            direction = RelationDirection.DIRECTED,
        )
        val people = listOf(
            person("grandfather", "爷爷", Gender.MALE),
            person("father", "父亲", Gender.MALE),
            person("child", "孩子"),
        )
        val relationships = listOf(
            relationship("edge1", "father", "grandfather", childType.id),
            relationship("edge2", "child", "father", childType.id),
        )

        val candidate = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all + childType,
            dismissals = emptyList(),
        ).single {
            it.fromPersonId == "grandfather" && it.toPersonId == "child"
        }

        assertEquals("爷爷", candidate.labelForFrom)
    }

    @Test
    fun spouseChildCandidateDefaultsToChildButKeepsStepAlternative() {
        val people = listOf(
            person("parent", "父亲", Gender.MALE),
            person("spouse", "母亲", Gender.FEMALE),
            person("child", "孩子", Gender.MALE),
        )
        val relationships = listOf(
            relationship("marriage", "parent", "spouse", spouse),
            relationship("child", "spouse", "child", parentChild),
        )

        val candidate = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all,
            dismissals = emptyList(),
        ).single { it.rule == InferenceRule.STEP_PARENT }

        assertEquals(
            parentChild,
            candidate.relationTypeFor(InferenceConfirmationMode.AS_CHILD),
        )
        assertEquals("父亲", candidate.labelFor("parent", InferenceConfirmationMode.AS_CHILD))
        assertEquals("儿子", candidate.labelFor("child", InferenceConfirmationMode.AS_CHILD))
        assertEquals(
            InferenceRelationTypeIds.STEP_PARENT,
            candidate.relationTypeFor(InferenceConfirmationMode.AS_STEP_CHILD),
        )
        assertEquals(
            "继子",
            candidate.labelFor("child", InferenceConfirmationMode.AS_STEP_CHILD),
        )
    }

    @Test
    fun usesColloquialSiblingInLawLabelsWhenGenderAndBirthdayAreKnown() {
        val people = listOf(
            person("wife", "妻子", Gender.FEMALE),
            person("husband", "丈夫", Gender.MALE, "1990-01-01"),
            person("youngerBrother", "弟弟", Gender.MALE, "1995-01-01"),
        )
        val relationships = listOf(
            relationship("marriage", "husband", "wife", spouse),
            relationship("parent1", "grandparent", "husband", parentChild),
            relationship("parent2", "grandparent", "youngerBrother", parentChild),
        )
        val peopleWithParent = people + person("grandparent", "父母")

        val candidate = InferenceEngine.infer(
            people = peopleWithParent,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all,
            dismissals = emptyList(),
        ).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("wife", "youngerBrother")
        }

        assertEquals("小叔子", candidate.labelFor("wife"))
        assertEquals("嫂子", candidate.labelFor("youngerBrother"))
    }

    @Test
    fun siblingInLawLabelsUseCombinedNamesWithoutBirthday() {
        val people = listOf(
            person("wife", "妻子", Gender.FEMALE),
            person("husband", "丈夫", Gender.MALE),
            person("brother", "兄弟", Gender.MALE),
            person("grandparent", "父母"),
        )
        val relationships = listOf(
            relationship("marriage", "husband", "wife", spouse),
            relationship("parent1", "grandparent", "husband", parentChild),
            relationship("parent2", "grandparent", "brother", parentChild),
        )

        val candidate = InferenceEngine.infer(
            people = people,
            relationships = relationships,
            relationTypes = PresetRelationTypes.all,
            dismissals = emptyList(),
        ).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("wife", "brother")
        }

        assertEquals("大伯子/小叔子", candidate.labelFor("wife"))
        assertEquals("嫂子/弟妹", candidate.labelFor("brother"))
    }

    @Test
    fun motherDaughterSingleDirectionParticipatesInFamilyInference() {
        val motherDaughter = "preset_mother_daughter"
        val people = listOf(
            person("grandparent", "外祖父母"),
            person("mother", "母亲", Gender.FEMALE),
            person("aunt", "姨妈", Gender.FEMALE),
            person("daughter", "女儿", Gender.FEMALE),
        )
        val relationships = listOf(
            relationship("gp-mother", "grandparent", "mother", motherDaughter),
            relationship("gp-aunt", "grandparent", "aunt", motherDaughter),
            relationship("mother-daughter", "mother", "daughter", motherDaughter),
        )

        val candidates = infer(people, relationships)

        assertTrue(
            candidates.any {
                it.rule == InferenceRule.AUNT_UNCLE &&
                    it.fromPersonId == "aunt" &&
                    it.toPersonId == "daughter" &&
                    it.labelFor("aunt") == "姨妈"
            },
        )
    }

    @Test
    fun wifeCanFollowHusbandToHisCousin() {
        val people = listOf(
            person("wife", "妻子", Gender.FEMALE),
            person("husband", "丈夫", Gender.MALE, "1990-01-01"),
            person("grandparent", "祖父母"),
            person("husbandFather", "公公", Gender.MALE),
            person("uncle", "叔叔", Gender.MALE),
            person("cousin", "堂弟", Gender.MALE, "2000-01-01"),
        )
        val relationships = listOf(
            relationship("marriage", "husband", "wife", spouse).copy(
                marriageKinshipMode = com.relationship.graph.data.local.MarriageKinshipMode.FOLLOW_HUSBAND,
            ),
            relationship("gp-father", "grandparent", "husbandFather", parentChild),
            relationship("gp-uncle", "grandparent", "uncle", parentChild),
            relationship("father-husband", "husbandFather", "husband", parentChild),
            relationship("uncle-cousin", "uncle", "cousin", parentChild),
        )

        val candidate = infer(people, relationships).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("wife", "cousin")
        }

        assertEquals("堂弟", candidate.labelFor("wife"))
    }

    @Test
    fun followWifeUsesWifeSideSiblingNames() {
        val people = listOf(
            person("husband", "丈夫", Gender.MALE),
            person("wife", "妻子", Gender.FEMALE, "1990-01-01"),
            person("wifeOlderBrother", "哥哥", Gender.MALE, "1985-01-01"),
            person("grandparent", "父母"),
        )
        val relationships = listOf(
            relationship("marriage", "husband", "wife", spouse).copy(
                marriageKinshipMode = com.relationship.graph.data.local.MarriageKinshipMode.FOLLOW_WIFE,
            ),
            relationship("parent1", "grandparent", "wife", parentChild),
            relationship("parent2", "grandparent", "wifeOlderBrother", parentChild),
        )

        val candidate = infer(people, relationships).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("husband", "wifeOlderBrother")
        }

        assertEquals("哥哥", candidate.labelFor("husband"))
        assertEquals("妹夫", candidate.labelFor("wifeOlderBrother"))
    }

    @Test
    fun fatherBrotherChildIsTangCousin() {
        val candidate = cousinCandidate(
            parentGender = Gender.MALE,
            parentSiblingGender = Gender.MALE,
            anchorGender = Gender.MALE,
            cousinGender = Gender.MALE,
        )

        assertEquals("堂弟", candidate.labelFor("child"))
    }

    @Test
    fun fatherSisterChildAndMotherSiblingChildAreBiaoCousins() {
        val fatherSisterChild = cousinCandidate(
            parentGender = Gender.MALE,
            parentSiblingGender = Gender.FEMALE,
            anchorGender = Gender.MALE,
            cousinGender = Gender.FEMALE,
        )
        val motherBrotherChild = cousinCandidate(
            parentGender = Gender.FEMALE,
            parentSiblingGender = Gender.MALE,
            anchorGender = Gender.MALE,
            cousinGender = Gender.MALE,
        )

        assertEquals("表弟", fatherSisterChild.labelFor("child"))
        assertEquals("表弟", motherBrotherChild.labelFor("child"))
    }

    @Test
    fun equalDistanceCousinPathsKeepAllDistinctLabels() {
        val people = listOf(
            person("grandparent", "祖辈"),
            person("parent", "父亲", Gender.MALE),
            person("uncle", "伯父", Gender.MALE),
            person("aunt", "姑姑", Gender.FEMALE),
            person("child", "孩子", Gender.MALE, "2000-01-01"),
            person("cousin", "堂表亲", Gender.MALE, "1998-01-01"),
        )
        val relationships = listOf(
            relationship("gp-parent", "grandparent", "parent", parentChild),
            relationship("gp-uncle", "grandparent", "uncle", parentChild),
            relationship("gp-aunt", "grandparent", "aunt", parentChild),
            relationship("parent-child", "parent", "child", parentChild),
            relationship("uncle-cousin", "uncle", "cousin", parentChild),
            relationship("aunt-cousin", "aunt", "cousin", parentChild),
        )

        val candidate = infer(people, relationships).single {
            it.rule == InferenceRule.COUSIN &&
                setOf(it.fromPersonId, it.toPersonId) == setOf("child", "cousin")
        }

        assertEquals(
            setOf("堂弟", "表弟"),
            candidate.labelFor("child").split("/").toSet(),
        )
    }

    @Test
    fun paternalUncleWivesUseDaNiangAndShenShen() {
        val people = listOf(
            person("grandparent", "祖辈"),
            person("parent", "父亲", Gender.MALE, "1980-01-01"),
            person("olderUncle", "大爷", Gender.MALE, "1975-01-01"),
            person("youngerUncle", "叔叔", Gender.MALE, "1985-01-01"),
            person("olderUncleWife", "大娘", Gender.FEMALE),
            person("youngerUncleWife", "婶婶", Gender.FEMALE),
            person("child", "孩子", Gender.MALE),
        )
        val relationships = listOf(
            relationship("gp-parent", "grandparent", "parent", parentChild),
            relationship("gp-older", "grandparent", "olderUncle", parentChild),
            relationship("gp-younger", "grandparent", "youngerUncle", parentChild),
            relationship("older-wife", "olderUncle", "olderUncleWife", spouse),
            relationship("younger-wife", "youngerUncle", "youngerUncleWife", spouse),
            relationship("parent-child", "parent", "child", parentChild),
        )

        val candidates = infer(people, relationships)
        val olderWife = candidates.single {
            it.fromPersonId == "olderUncleWife" && it.toPersonId == "child"
        }
        val youngerWife = candidates.single {
            it.fromPersonId == "youngerUncleWife" && it.toPersonId == "child"
        }

        assertEquals("大娘", olderWife.labelForFrom)
        assertEquals("婶婶", youngerWife.labelForFrom)
        assertEquals("侄子", olderWife.labelForTo)
    }

    @Test
    fun maternalUncleWifeAndMaternalAuntHusbandUseColloquialLabels() {
        val people = listOf(
            person("grandparent", "祖辈"),
            person("mother", "母亲", Gender.FEMALE),
            person("uncle", "舅舅", Gender.MALE),
            person("aunt", "姨妈", Gender.FEMALE),
            person("uncleWife", "舅妈", Gender.FEMALE),
            person("auntHusband", "姨父", Gender.MALE),
            person("child", "孩子", Gender.FEMALE),
        )
        val relationships = listOf(
            relationship("gp-mother", "grandparent", "mother", parentChild),
            relationship("gp-uncle", "grandparent", "uncle", parentChild),
            relationship("gp-aunt", "grandparent", "aunt", parentChild),
            relationship("uncle-wife", "uncle", "uncleWife", spouse),
            relationship("aunt-husband", "aunt", "auntHusband", spouse),
            relationship("mother-child", "mother", "child", parentChild),
        )

        val candidates = infer(people, relationships)

        assertEquals(
            "舅妈",
            candidates.single {
                it.fromPersonId == "uncleWife" && it.toPersonId == "child"
            }.labelForFrom,
        )
        assertEquals(
            "姨父",
            candidates.single {
                it.fromPersonId == "auntHusband" && it.toPersonId == "child"
            }.labelForFrom,
        )
    }

    @Test
    fun manualAgeOrderRefinesLabelsWhenBirthdaysAreMissing() {
        val people = listOf(
            person("parent", "父母"),
            person("a", "甲", Gender.MALE),
            person("b", "乙", Gender.MALE),
        )
        val relationships = listOf(
            relationship("a-child", "parent", "a", parentChild),
            relationship("b-child", "parent", "b", parentChild),
        )
        val ageOrder = RelativeAgeOrderEntity(
            firstPersonId = "a",
            secondPersonId = "b",
            comparison = AgeComparison.FIRST_OLDER,
        )

        val candidate = infer(people, relationships, listOf(ageOrder)).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("a", "b")
        }

        assertEquals("哥哥", candidate.labelFor("a"))
        assertEquals("弟弟", candidate.labelFor("b"))
    }

    @Test
    fun manualAgeOrderCanResolveAgeTransitively() {
        val people = listOf(
            person("parent", "父母"),
            person("a", "甲", Gender.MALE),
            person("b", "乙", Gender.MALE),
            person("c", "丙", Gender.MALE),
        )
        val relationships = people
            .filter { it.id != "parent" }
            .mapIndexed { index, person ->
                relationship("edge$index", "parent", person.id, parentChild)
            }
        val ageOrders = listOf(
            RelativeAgeOrderEntity(
                firstPersonId = "a",
                secondPersonId = "b",
                comparison = AgeComparison.FIRST_OLDER,
            ),
            RelativeAgeOrderEntity(
                firstPersonId = "b",
                secondPersonId = "c",
                comparison = AgeComparison.FIRST_OLDER,
            ),
        )

        val candidate = infer(people, relationships, ageOrders).single {
            setOf(it.fromPersonId, it.toPersonId) == setOf("a", "c")
        }

        assertEquals("哥哥", candidate.labelFor("a"))
        assertEquals("弟弟", candidate.labelFor("c"))
    }

    private fun infer(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        ageOrders: List<RelativeAgeOrderEntity> = emptyList(),
    ): List<InferredRelationshipCandidate> = InferenceEngine.infer(
        people = people,
        relationships = relationships,
        relationTypes = PresetRelationTypes.all,
        dismissals = emptyList(),
        ageOrders = ageOrders,
    )

    private fun person(
        id: String,
        name: String,
        gender: Gender = Gender.UNSPECIFIED,
        birthday: String = "",
    ) = PersonEntity(
        id = id,
        name = name,
        gender = gender,
        birthday = birthday,
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

    private fun cousinCandidate(
        parentGender: Gender,
        parentSiblingGender: Gender,
        anchorGender: Gender,
        cousinGender: Gender,
    ): InferredRelationshipCandidate {
        val people = listOf(
            person("grandparent", "祖辈"),
            person("parent", "父辈", parentGender),
            person("parent-sibling", "叔姑舅姨", parentSiblingGender),
            person("child", "孩子", anchorGender, "2000-01-01"),
            person("cousin", "堂表亲", cousinGender, "1998-01-01"),
        )
        val relationships = listOf(
            relationship("gp-parent", "grandparent", "parent", parentChild),
            relationship(
                "gp-sibling",
                "grandparent",
                "parent-sibling",
                parentChild,
            ),
            relationship("parent-child", "parent", "child", parentChild),
            relationship(
                "sibling-cousin",
                "parent-sibling",
                "cousin",
                parentChild,
            ),
        )
        return infer(people, relationships).single {
            it.rule == InferenceRule.COUSIN &&
                setOf(it.fromPersonId, it.toPersonId) == setOf("child", "cousin")
        }
    }
}
