package com.relationship.graph.data.inference

import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

data class KinshipQueryResult(
    val referenceCallsTarget: String,
    val targetCallsReference: String,
    val explanation: String,
    val confidence: InferenceConfidence?,
    val isDirect: Boolean,
)

object KinshipQueryEngine {
    fun query(
        referencePersonId: String,
        targetPersonId: String,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        inferredCandidates: List<InferredRelationshipCandidate>,
    ): KinshipQueryResult? {
        if (referencePersonId == targetPersonId) {
            return KinshipQueryResult(
                referenceCallsTarget = "本人",
                targetCallsReference = "本人",
                explanation = "选择了同一个人",
                confidence = InferenceConfidence.HIGH,
                isDirect = true,
            )
        }
        val typeById = relationTypes.associateBy { it.id }
        val direct = relationships.filter {
            setOf(it.fromPersonId, it.toPersonId) ==
                setOf(referencePersonId, targetPersonId)
        }
        if (direct.isNotEmpty()) {
            val directLabels = direct.mapNotNull { relationship ->
                typeById[relationship.relationTypeId]?.let { type ->
                    relationship to endpointLabels(relationship, type)
                }
            }
            val referenceLabels = directLabels.map { (relationship, labels) ->
                if (relationship.fromPersonId == targetPersonId) {
                    labels.first
                } else {
                    labels.second
                }
            }.distinct()
            val targetLabels = directLabels.map { (relationship, labels) ->
                if (relationship.fromPersonId == referencePersonId) {
                    labels.first
                } else {
                    labels.second
                }
            }.distinct()
            if (referenceLabels.isNotEmpty()) {
                return KinshipQueryResult(
                    referenceCallsTarget = referenceLabels.joinToString("、"),
                    targetCallsReference = targetLabels.joinToString("、"),
                    explanation = "已录入的直接关系",
                    confidence = InferenceConfidence.HIGH,
                    isDirect = true,
                )
            }
        }

        val inferred = inferredCandidates.firstOrNull {
            setOf(it.fromPersonId, it.toPersonId) ==
                setOf(referencePersonId, targetPersonId)
        } ?: return null
        return KinshipQueryResult(
            referenceCallsTarget = inferred.labelFor(targetPersonId),
            targetCallsReference = inferred.labelFor(referencePersonId),
            explanation = inferred.reasonText,
            confidence = inferred.confidence,
            isDirect = false,
        )
    }

    private fun endpointLabels(
        relationship: RelationshipEntity,
        type: RelationTypeEntity,
    ): Pair<String, String> {
        if (type.direction == RelationDirection.BIDIRECTIONAL) {
            return (
                relationship.labelOverride ?: type.name
                ) to (
                relationship.inverseLabelOverride
                    ?: relationship.labelOverride
                    ?: type.name
                )
        }
        val fromLabel = relationship.labelOverride ?: type.name
        val toLabel = relationship.inverseLabelOverride
            ?: type.inverseName
            ?: type.name
        return fromLabel to toLabel
    }
}
