package com.relationship.graph.data.kinship

import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.RelationTypeEntity

/**
 * 家庭类关系的重复检测：同一对人（不区分方向）之间同一种关系类型只允许一条。
 * 社交类关系（同学、同事等可多段）不参与查重；不同类型可共存（如既是姑姑又干妈）。
 */
object RelationshipDuplicates {

    fun find(
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        fromPersonId: String,
        toPersonId: String,
        relationTypeId: String,
        excludeRelationshipId: String? = null,
    ): RelationshipEntity? {
        val typeById = relationTypes.associateBy { it.id }
        if (!RelationshipSemantics.isFamilyLike(typeById[relationTypeId])) return null
        val pair = setOf(fromPersonId, toPersonId)
        return relationships.firstOrNull { relationship ->
            relationship.id != excludeRelationshipId &&
                relationship.relationTypeId == relationTypeId &&
                setOf(relationship.fromPersonId, relationship.toPersonId) == pair
        }
    }
}
