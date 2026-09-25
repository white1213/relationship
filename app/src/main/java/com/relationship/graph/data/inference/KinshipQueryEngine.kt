package com.relationship.graph.data.inference

import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelativeAgeOrderEntity
import com.relationship.graph.ui.relationshipLabelForPerson

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
        people: List<PersonEntity> = emptyList(),
        ageOrders: List<RelativeAgeOrderEntity> = emptyList(),
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
            val peopleById = people.associateBy { it.id }
            val referenceLabels = direct.mapNotNull { relationship ->
                typeById[relationship.relationTypeId]?.let { type ->
                    relationshipLabelForPerson(
                        relationship = relationship,
                        type = type,
                        personId = referencePersonId,
                        otherPerson = peopleById[targetPersonId],
                        people = people,
                        ageOrders = ageOrders,
                    )
                }
            }.distinct()
            val targetLabels = direct.mapNotNull { relationship ->
                typeById[relationship.relationTypeId]?.let { type ->
                    relationshipLabelForPerson(
                        relationship = relationship,
                        type = type,
                        personId = targetPersonId,
                        otherPerson = peopleById[referencePersonId],
                        people = people,
                        ageOrders = ageOrders,
                    )
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

}
