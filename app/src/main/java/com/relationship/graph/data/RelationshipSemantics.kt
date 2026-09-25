package com.relationship.graph.data

import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.AgeComparison
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

enum class FamilyRelationKind {
    PARENT_CHILD,
    SPOUSE,
    SIBLING,
    AUNT_UNCLE,
    AUNT_UNCLE_IN_LAW,
    SIBLING_IN_LAW,
    COUSIN,
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
            type.id == "preset_mother_daughter" -> FamilyRelationKind.PARENT_CHILD
            type.id == "preset_spouse" -> FamilyRelationKind.SPOUSE
            type.id == "preset_sibling" -> FamilyRelationKind.SIBLING
            type.id == "preset_older_sister" -> FamilyRelationKind.SIBLING
            type.id == "preset_brother_in_law" -> FamilyRelationKind.SIBLING_IN_LAW
            type.id == "preset_aunt" -> FamilyRelationKind.AUNT_UNCLE
            type.id == "preset_aunt_husband" -> FamilyRelationKind.AUNT_UNCLE_IN_LAW
            type.id == "preset_biao_cousin" -> FamilyRelationKind.COUSIN
            type.id == "preset_tang_cousin" -> FamilyRelationKind.COUSIN
            isParentChildName(type) -> FamilyRelationKind.PARENT_CHILD
            normalize(type.name) in SPOUSE_NAMES -> FamilyRelationKind.SPOUSE
            normalize(type.name) in SIBLING_NAMES -> FamilyRelationKind.SIBLING
            normalize(type.name) in AUNT_UNCLE_NAMES -> FamilyRelationKind.AUNT_UNCLE
            normalize(type.name) in AUNT_UNCLE_IN_LAW_NAMES ->
                FamilyRelationKind.AUNT_UNCLE_IN_LAW
            normalize(type.name) in SIBLING_IN_LAW_NAMES ->
                FamilyRelationKind.SIBLING_IN_LAW
            normalize(type.name) in COUSIN_NAMES -> FamilyRelationKind.COUSIN
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
            type.id == "preset_parent_child" || type.id == "preset_mother_daughter" -> true
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
            kind(type) != FamilyRelationKind.OTHER ||
            type?.let { normalize(it.name) in IN_LAW_NAMES } == true

    fun siblingAgeOrder(
        relationship: RelationshipEntity,
        type: RelationTypeEntity?,
    ): Triple<String, String, AgeComparison>? {
        if (type == null || kind(type) != FamilyRelationKind.SIBLING) return null
        val name = normalize(type.name)
        val comparison = when {
            name in OLDER_SIBLING_NAMES -> AgeComparison.FIRST_OLDER
            name in YOUNGER_SIBLING_NAMES -> AgeComparison.SECOND_OLDER
            else -> return null
        }
        val first = minOf(relationship.fromPersonId, relationship.toPersonId)
        val second = maxOf(relationship.fromPersonId, relationship.toPersonId)
        val normalizedComparison = if (first == relationship.fromPersonId) {
            comparison
        } else {
            when (comparison) {
                AgeComparison.FIRST_OLDER -> AgeComparison.SECOND_OLDER
                AgeComparison.SECOND_OLDER -> AgeComparison.FIRST_OLDER
                AgeComparison.SAME_AGE -> AgeComparison.SAME_AGE
            }
        }
        return Triple(first, second, normalizedComparison)
    }

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
        "哥哥",
        "弟弟",
        "姐姐",
        "妹妹",
    )

    private val AUNT_UNCLE_NAMES = setOf(
        "大爷",
        "伯父",
        "伯伯",
        "叔叔",
        "叔父",
        "姑姑",
        "姑母",
        "舅舅",
        "姨妈",
        "姨母",
    )

    private val AUNT_UNCLE_IN_LAW_NAMES = setOf(
        "大娘",
        "伯母",
        "婶",
        "婶婶",
        "婶母",
        "姑父",
        "舅妈",
        "姨父",
    )

    private val SIBLING_IN_LAW_NAMES = setOf(
        "嫂子",
        "弟妹",
        "弟媳",
        "姐夫",
        "妹夫",
    )

    private val COUSIN_NAMES = setOf(
        "堂兄弟姐妹",
        "堂表亲",
        "表兄弟姐妹",
        "堂兄",
        "堂哥",
        "堂弟",
        "堂姐",
        "堂妹",
        "表哥",
        "表弟",
        "表姐",
        "表妹",
    )

    private val OLDER_SIBLING_NAMES = setOf(
        "哥哥",
        "姐姐",
    )

    private val YOUNGER_SIBLING_NAMES = setOf(
        "弟弟",
        "妹妹",
    )

    private val IN_LAW_NAMES = setOf(
        "嫂子",
        "弟妹",
        "弟媳",
        "姐夫",
        "妹夫",
        "大娘",
        "婶婶",
        "婶母",
        "姑父",
        "舅妈",
        "姨父",
        "大伯子",
        "小叔子",
        "大姑子",
        "小姑子",
        "大舅子",
        "小舅子",
        "大姨子",
        "小姨子",
    )
}
