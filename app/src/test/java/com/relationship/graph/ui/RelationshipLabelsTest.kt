package com.relationship.graph.ui

import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.Gender
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

    @Test
    fun directParentChildLabelsRespectTargetGender() {
        val parent = PersonEntity(id = "parent", name = "母亲", gender = Gender.FEMALE)
        val child = PersonEntity(id = "child", name = "女儿", gender = Gender.FEMALE)
        val relationship = RelationshipEntity(
            id = "relationship",
            fromPersonId = parent.id,
            toPersonId = child.id,
            relationTypeId = parentType.id,
        )

        assertEquals(
            "母亲",
            relationshipLabelForPerson(
                relationship,
                parentType,
                child.id,
                otherPerson = parent,
                people = listOf(parent, child),
            ),
        )
        assertEquals(
            "女儿",
            relationshipLabelForPerson(
                relationship,
                parentType,
                parent.id,
                otherPerson = child,
                people = listOf(parent, child),
            ),
        )
    }

    @Test
    fun customOlderSisterAndBrotherInLawRelationshipsUseReciprocalLabels() {
        val olderSister = PersonEntity(id = "sister", name = "姐姐", gender = Gender.FEMALE)
        val youngerBrother = PersonEntity(
            id = "brother",
            name = "弟弟",
            gender = Gender.MALE,
        )
        val sisterType = RelationTypeEntity(
            id = "custom_sister",
            name = "姐姐",
            category = RelationCategory.CUSTOM,
            direction = RelationDirection.BIDIRECTIONAL,
        )
        val sisterRelationship = RelationshipEntity(
            id = "sister_relationship",
            fromPersonId = olderSister.id,
            toPersonId = youngerBrother.id,
            relationTypeId = sisterType.id,
        )

        assertEquals(
            "姐姐",
            relationshipLabelForPerson(
                sisterRelationship,
                sisterType,
                youngerBrother.id,
                otherPerson = olderSister,
                people = listOf(olderSister, youngerBrother),
            ),
        )
        assertEquals(
            "弟弟",
            relationshipLabelForPerson(
                sisterRelationship,
                sisterType,
                olderSister.id,
                otherPerson = youngerBrother,
                people = listOf(olderSister, youngerBrother),
            ),
        )

        val brotherInLawType = RelationTypeEntity(
            id = "custom_brother_in_law",
            name = "姐夫",
            category = RelationCategory.CUSTOM,
            direction = RelationDirection.BIDIRECTIONAL,
        )
        val brotherInLaw = PersonEntity(
            id = "brother_in_law",
            name = "姐夫",
            gender = Gender.MALE,
        )
        val spouseRelationship = RelationshipEntity(
            id = "brother_in_law_relationship",
            fromPersonId = brotherInLaw.id,
            toPersonId = youngerBrother.id,
            relationTypeId = brotherInLawType.id,
        )

        assertEquals(
            "姐夫",
            relationshipLabelForPerson(
                spouseRelationship,
                brotherInLawType,
                youngerBrother.id,
                otherPerson = brotherInLaw,
                people = listOf(brotherInLaw, youngerBrother),
            ),
        )
        assertEquals(
            "小舅子",
            relationshipLabelForPerson(
                spouseRelationship,
                brotherInLawType,
                brotherInLaw.id,
                otherPerson = youngerBrother,
                people = listOf(brotherInLaw, youngerBrother),
            ),
        )
    }

    @Test
    fun motherDaughterTypeKeepsFixedDirectionalNames() {
        val type = RelationTypeEntity(
            id = "preset_mother_daughter",
            name = "母亲",
            inverseName = "女儿",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val mother = PersonEntity(id = "mother", name = "母亲")
        val daughter = PersonEntity(id = "daughter", name = "女儿")
        val relationship = RelationshipEntity(
            id = "mother-daughter",
            fromPersonId = mother.id,
            toPersonId = daughter.id,
            relationTypeId = type.id,
        )

        assertEquals(
            "母亲",
            relationshipLabelForPerson(
                relationship,
                type,
                daughter.id,
                otherPerson = mother,
                people = listOf(mother, daughter),
            ),
        )
        assertEquals(
            "女儿",
            relationshipLabelForPerson(
                relationship,
                type,
                mother.id,
                otherPerson = daughter,
                people = listOf(mother, daughter),
            ),
        )
    }

    @Test
    fun auntAndAuntHusbandUseGenderedNieceNephewLabels() {
        val auntType = RelationTypeEntity(
            id = "preset_aunt",
            name = "姑姑",
            inverseName = "侄子/侄女",
            category = RelationCategory.FAMILY,
            direction = RelationDirection.DIRECTED,
            isBuiltIn = true,
        )
        val aunt = PersonEntity(id = "aunt", name = "姑姑", gender = Gender.FEMALE)
        val nephew = PersonEntity(id = "nephew", name = "侄子", gender = Gender.MALE)
        val relationship = RelationshipEntity(
            id = "aunt-nephew",
            fromPersonId = aunt.id,
            toPersonId = nephew.id,
            relationTypeId = auntType.id,
        )

        assertEquals(
            "姑姑",
            relationshipLabelForPerson(
                relationship,
                auntType,
                nephew.id,
                otherPerson = aunt,
                people = listOf(aunt, nephew),
            ),
        )
        assertEquals(
            "侄子",
            relationshipLabelForPerson(
                relationship,
                auntType,
                aunt.id,
                otherPerson = nephew,
                people = listOf(aunt, nephew),
            ),
        )
    }
}
