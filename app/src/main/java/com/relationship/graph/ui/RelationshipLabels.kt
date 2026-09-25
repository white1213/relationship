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
                        parentTitle(otherPerson, type)
                    } else {
                        childTitle(otherPerson, type)
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
            FamilyRelationKind.AUNT_UNCLE,
            FamilyRelationKind.AUNT_UNCLE_IN_LAW,
            -> return auntUncleLabelForPerson(
                relationship = relationship,
                type = type,
                personId = personId,
                otherPerson = otherPerson,
            )
            FamilyRelationKind.SIBLING_IN_LAW -> {
                return if (isInLawAlias(type.name.trim())) {
                    if (personId == relationship.toPersonId) {
                        type.name
                    } else {
                        inverseInLawTitle(type.name.trim(), otherPerson)
                    }
                } else {
                    directedAliasLabel(
                        relationship = relationship,
                        type = type,
                        personId = personId,
                    )
                }
            }
            FamilyRelationKind.COUSIN -> {
                val relative = people.firstOrNull { it.id == personId }
                return if (relative == null) {
                    directGenericLabel(relationship, type, personId)
                } else {
                    cousinTitle(
                        target = otherPerson,
                        relative = relative,
                        type = type,
                        resolver = resolver,
                    )
                }
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

private fun directedAliasLabel(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    personId: String,
): String {
    val describesFromPerson = personId == relationship.toPersonId
    return if (describesFromPerson) {
        type.name
    } else {
        type.inverseName ?: type.name
    }
}

private fun auntUncleLabelForPerson(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    personId: String,
    otherPerson: PersonEntity,
): String {
    if (personId == relationship.toPersonId) return type.name
    return when (type.name.trim()) {
        "姑姑", "姑父" -> when (otherPerson.gender) {
            Gender.MALE -> "侄子"
            Gender.FEMALE -> "侄女"
            Gender.UNSPECIFIED -> "侄子/侄女"
        }
        else -> type.inverseName ?: type.name
    }
}

private fun directGenericLabel(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    personId: String,
): String {
    if (type.direction == RelationDirection.BIDIRECTIONAL) {
        return relationship.labelOverride ?: type.name
    }
    return if (relationship.fromPersonId == personId) {
        relationship.labelOverride ?: type.name
    } else {
        relationship.inverseLabelOverride ?: type.inverseName ?: type.name
    }
}

private fun cousinTitle(
    target: PersonEntity,
    relative: PersonEntity,
    type: RelationTypeEntity,
    resolver: RelativeAgeResolver,
): String {
    val side = if (type.name.startsWith("堂")) "堂" else if (type.name.startsWith("表")) "表" else "堂表"
    if (side == "堂表") return type.name
    return when (resolver.compare(target, relative)) {
        com.relationship.graph.data.inference.RelativeAge.OLDER -> when (target.gender) {
            Gender.MALE -> "${side}哥"
            Gender.FEMALE -> "${side}姐"
            Gender.UNSPECIFIED -> if (side == "堂") "堂表亲" else "表亲"
        }
        com.relationship.graph.data.inference.RelativeAge.YOUNGER -> when (target.gender) {
            Gender.MALE -> "${side}弟"
            Gender.FEMALE -> "${side}妹"
            Gender.UNSPECIFIED -> if (side == "堂") "堂表亲" else "表亲"
        }
        com.relationship.graph.data.inference.RelativeAge.UNKNOWN -> type.name
    }
}

private fun parentTitle(person: PersonEntity, type: RelationTypeEntity): String {
    if (type.id == "preset_mother_daughter") return "母亲"
    return when (person.gender) {
    Gender.MALE -> "父亲"
    Gender.FEMALE -> "母亲"
    Gender.UNSPECIFIED -> "父母"
    }
}

private fun childTitle(person: PersonEntity, type: RelationTypeEntity): String {
    if (type.id == "preset_mother_daughter") return "女儿"
    return when (person.gender) {
    Gender.MALE -> "儿子"
    Gender.FEMALE -> "女儿"
    Gender.UNSPECIFIED -> "子女"
    }
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
