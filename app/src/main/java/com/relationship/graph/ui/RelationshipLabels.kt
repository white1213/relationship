package com.relationship.graph.ui

import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.RelativeAgeOrderEntity
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.inference.RelativeAgeResolver

fun relationshipLabelForPerson(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    personId: String,
    otherPerson: PersonEntity? = null,
    people: List<PersonEntity> = emptyList(),
    ageOrders: List<RelativeAgeOrderEntity> = emptyList(),
): String {
    if (otherPerson != null) {
        val resolver = RelativeAgeResolver(
            peopleById = (people + otherPerson).associateBy { it.id },
            ageOrders = ageOrders,
        )
        when (RelationshipSemantics.kind(type)) {
            FamilyRelationKind.PARENT_CHILD -> {
                val edge = RelationshipSemantics.parentChildEdge(relationship, type)
                if (edge != null) {
                    return if (personId == edge.childPersonId) {
                        parentTitle(otherPerson)
                    } else {
                        childTitle(otherPerson)
                    }
                }
            }
            FamilyRelationKind.SPOUSE -> return spouseTitle(otherPerson)
            FamilyRelationKind.SIBLING -> {
                return siblingTitle(
                    target = otherPerson,
                    relative = people.firstOrNull { it.id == personId },
                    type = type,
                    relationship = relationship,
                    resolver = resolver,
                )
            }
            FamilyRelationKind.OTHER -> {
                val name = type.name.trim()
                if (isSiblingAlias(name)) {
                    return if (personId == relationship.fromPersonId) {
                        name
                    } else {
                        inverseSiblingTitle(name, otherPerson)
                    }
                }
                if (isInLawAlias(name)) {
                    return if (personId == relationship.toPersonId) {
                        name
                    } else {
                        inverseInLawTitle(name, otherPerson)
                    }
                }
            }
        }
    }
    if (type.direction == RelationDirection.BIDIRECTIONAL) {
        return if (relationship.fromPersonId == personId) {
            relationship.labelOverride ?: type.name
        } else {
            relationship.inverseLabelOverride
                ?: relationship.labelOverride
                ?: type.name
        }
    }
    return if (relationship.fromPersonId == personId) {
        relationship.labelOverride ?: type.name
    } else {
        relationship.inverseLabelOverride ?: type.inverseName ?: type.name
    }
}

private fun parentTitle(person: PersonEntity): String = when (person.gender) {
    Gender.MALE -> "父亲"
    Gender.FEMALE -> "母亲"
    Gender.UNSPECIFIED -> "父母"
}

private fun childTitle(person: PersonEntity): String = when (person.gender) {
    Gender.MALE -> "儿子"
    Gender.FEMALE -> "女儿"
    Gender.UNSPECIFIED -> "子女"
}

private fun spouseTitle(person: PersonEntity): String = when (person.gender) {
    Gender.MALE -> "丈夫"
    Gender.FEMALE -> "妻子"
    Gender.UNSPECIFIED -> "配偶"
}

private fun siblingTitle(
    target: PersonEntity,
    relative: PersonEntity?,
    type: RelationTypeEntity,
    relationship: RelationshipEntity,
    resolver: RelativeAgeResolver,
): String {
    val explicitName = type.name.trim()
    if (isSiblingAlias(explicitName)) {
        return if (target.id == relationship.fromPersonId) {
            explicitName
        } else {
            inverseSiblingTitle(explicitName, target)
        }
    }
    if (relative != null) {
        when (resolver.compare(target, relative)) {
            com.relationship.graph.data.inference.RelativeAge.OLDER ->
                return if (target.gender == Gender.FEMALE) "姐姐" else if (
                    target.gender == Gender.MALE
                ) {
                    "哥哥"
                } else {
                    "哥哥/姐姐"
                }
            com.relationship.graph.data.inference.RelativeAge.YOUNGER ->
                return if (target.gender == Gender.FEMALE) "妹妹" else if (
                    target.gender == Gender.MALE
                ) {
                    "弟弟"
                } else {
                    "弟弟/妹妹"
                }
            com.relationship.graph.data.inference.RelativeAge.UNKNOWN -> Unit
        }
    }
    return when (target.gender) {
        Gender.MALE -> "兄弟"
        Gender.FEMALE -> "姐妹"
        Gender.UNSPECIFIED -> "兄弟姐妹"
    }
}

private fun isSiblingAlias(name: String): Boolean =
    name in setOf("哥哥", "弟弟", "姐姐", "妹妹")

private fun inverseSiblingTitle(name: String, target: PersonEntity): String = when (name) {
    "哥哥", "姐姐" -> when (target.gender) {
        Gender.MALE -> "弟弟"
        Gender.FEMALE -> "妹妹"
        Gender.UNSPECIFIED -> "弟弟/妹妹"
    }
    "弟弟", "妹妹" -> when (target.gender) {
        Gender.MALE -> "哥哥"
        Gender.FEMALE -> "姐姐"
        Gender.UNSPECIFIED -> "哥哥/姐姐"
    }
    else -> "兄弟姐妹"
}

private fun isInLawAlias(name: String): Boolean = name in setOf(
    "嫂子",
    "弟妹",
    "弟媳",
    "姐夫",
    "妹夫",
)

private fun inverseInLawTitle(name: String, target: PersonEntity): String = when (name) {
    "姐夫", "妹夫" -> when (target.gender) {
        Gender.MALE -> "小舅子"
        Gender.FEMALE -> "小姨子"
        Gender.UNSPECIFIED -> "小舅子/小姨子"
    }
    "嫂子", "弟妹", "弟媳" -> when (target.gender) {
        Gender.MALE -> "小叔子"
        Gender.FEMALE -> "小姑子"
        Gender.UNSPECIFIED -> "小叔子/小姑子"
    }
    else -> "亲属"
}

fun relationshipSentence(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    fromName: String,
    toName: String,
): String = when (type.direction) {
    RelationDirection.BIDIRECTIONAL -> "$fromName 与 $toName：${relationship.labelOverride ?: type.name}"
    RelationDirection.DIRECTED ->
        "$fromName 是 $toName 的${relationship.labelOverride ?: type.name}"
}
