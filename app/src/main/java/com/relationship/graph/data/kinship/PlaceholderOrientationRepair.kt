package com.relationship.graph.data.kinship

import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

/**
 * v1.10.0 的快建面板把关系方向建反了（占位名「xx的儿子」实际存成「对方是xx的父亲」）。
 * 这里针对占位人物做一次性修复：只处理名字形如「{某人}的{称谓}」、且与该某人之间
 * 只有一条同类家庭关系的占位人物；方向与称谓语义不符时对调 from/to。
 * 双向类型、找不到对应人物或类型不符时一律跳过，不做猜测。
 */
object PlaceholderOrientationRepair {

    data class Flip(
        val relationshipId: String,
        val fromPersonId: String,
        val toPersonId: String,
    )

    fun plan(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
    ): List<Flip> {
        val typeById = relationTypes.associateBy { it.id }
        return people.filter { it.isPlaceholder }.mapNotNull { placeholder ->
            val splitIndex = placeholder.name.lastIndexOf('的')
            if (splitIndex <= 0) return@mapNotNull null
            val anchorName = placeholder.name.substring(0, splitIndex)
            val term = placeholder.name.substring(splitIndex + 1)
            val spec = KinshipTermCatalog.lookup(term) ?: return@mapNotNull null
            val anchor = people.firstOrNull {
                it.id != placeholder.id && it.name == anchorName
            } ?: return@mapNotNull null

            val pair = setOf(placeholder.id, anchor.id)
            val between = relationships.filter {
                setOf(it.fromPersonId, it.toPersonId) == pair &&
                    RelationshipSemantics.isFamilyLike(typeById[it.relationTypeId])
            }
            val relationship = between.singleOrNull() ?: return@mapNotNull null
            val type = typeById[relationship.relationTypeId] ?: return@mapNotNull null
            if (type.direction == RelationDirection.BIDIRECTIONAL) return@mapNotNull null

            val typeMatches = relationship.relationTypeId == spec.presetTypeId ||
                (spec.customName != null && type.name.trim() == spec.customName)
            if (!typeMatches) return@mapNotNull null

            val (expectedFrom, expectedTo) = spec.orientation(placeholder.id, anchor.id)
            if (relationship.fromPersonId == expectedFrom &&
                relationship.toPersonId == expectedTo
            ) {
                null
            } else {
                Flip(
                    relationshipId = relationship.id,
                    fromPersonId = expectedFrom,
                    toPersonId = expectedTo,
                )
            }
        }
    }
}
