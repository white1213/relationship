package com.relationship.graph.ui

import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

fun relationshipLabelForPerson(
    relationship: RelationshipEntity,
    type: RelationTypeEntity,
    personId: String,
): String {
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
