package com.relationship.graph.data

import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

enum class FamilyRelationKind {
    PARENT_CHILD,
    SPOUSE,
    SIBLING,
    OTHER,
}

data class ParentChildEdge(
    val relationshipId: String,
    val parentPersonId: String,
    val childPersonId: String,
    val parentRoleGender: Gender? = null,
)

object RelationshipSemantics {
    fun kind(type: RelationTypeEntity?): FamilyRelationKind {
        if (type == null) return FamilyRelationKind.OTHER
        return when {
            type.id == "preset_parent_child" -> FamilyRelationKind.PARENT_CHILD
            type.id == "preset_spouse" -> FamilyRelationKind.SPOUSE
            type.id == "preset_sibling" -> FamilyRelationKind.SIBLING
            isParentChildName(type) -> FamilyRelationKind.PARENT_CHILD
            normalize(type.name) in SPOUSE_NAMES -> FamilyRelationKind.SPOUSE
            normalize(type.name) in SIBLING_NAMES -> FamilyRelationKind.SIBLING
            else -> FamilyRelationKind.OTHER
        }
    }

    fun parentChildEdge(
        relationship: RelationshipEntity,
        type: RelationTypeEntity?,
    ): ParentChildEdge? {
        if (kind(type) != FamilyRelationKind.PARENT_CHILD || type == null) return null
        val name = normalize(type.name)
        val inverseName = normalize(type.inverseName.orEmpty())
        val parentIsFrom = when {
            type.id == "preset_parent_child" -> true
            name in CHILD_NAMES && inverseName in PARENT_NAMES -> false
            name in CHILD_NAMES && inverseName.isBlank() -> false
            else -> true
        }
        return if (parentIsFrom) {
            ParentChildEdge(
                relationshipId = relationship.id,
                parentPersonId = relationship.fromPersonId,
                childPersonId = relationship.toPersonId,
                parentRoleGender = type.parentRoleGender(),
            )
        } else {
            ParentChildEdge(
                relationshipId = relationship.id,
                parentPersonId = relationship.toPersonId,
                childPersonId = relationship.fromPersonId,
                parentRoleGender = type.parentRoleGender(),
            )
        }
    }

    fun isFamilyLike(type: RelationTypeEntity?): Boolean =
        type?.category == RelationCategory.FAMILY ||
            kind(type) != FamilyRelationKind.OTHER

    private fun isParentChildName(type: RelationTypeEntity): Boolean {
        val name = normalize(type.name)
        val inverseName = normalize(type.inverseName.orEmpty())
        return (name in PARENT_NAMES && (inverseName.isBlank() || inverseName in CHILD_NAMES)) ||
            (name in CHILD_NAMES && inverseName in PARENT_NAMES)
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(" ", "")
            .replace("/", "")
            .replace("或", "")
            .removeSuffix("关系")

    private fun RelationTypeEntity.parentRoleGender(): Gender? {
        val normalizedName = normalize(name)
        return when {
            normalizedName in setOf("父亲", "爸爸", "父", "养父", "继父") -> Gender.MALE
            normalizedName in setOf("母亲", "妈妈", "母", "养母", "继母") -> Gender.FEMALE
            else -> null
        }
    }

    private val PARENT_NAMES = setOf(
        "父母",
        "双亲",
        "家长",
        "父亲",
        "爸爸",
        "父",
        "母亲",
        "妈妈",
        "母",
        "养父",
        "养母",
        "继父",
        "继母",
        "监护人",
    )

    private val CHILD_NAMES = setOf(
        "子女",
        "孩子",
        "儿女",
        "儿子",
        "女儿",
        "子",
        "女",
        "养子",
        "养女",
        "继子",
        "继女",
    )

    private val SPOUSE_NAMES = setOf(
        "配偶",
        "伴侣",
        "爱人",
        "丈夫",
        "老公",
        "妻子",
        "老婆",
    )

    private val SIBLING_NAMES = setOf(
        "兄弟姐妹",
        "兄弟",
        "姐妹",
        "哥哥",
        "弟弟",
        "姐姐",
        "妹妹",
        "兄妹",
        "姐弟",
    )
}
