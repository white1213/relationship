package com.relationship.graph.data.inference

import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.ParentChildEdge
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.InferenceDismissalEntity
import com.relationship.graph.data.local.InferenceRelationTypeIds
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import java.time.LocalDate

enum class InferenceConfidence {
    HIGH,
    MEDIUM_HIGH,
    MEDIUM,
}

enum class InferenceConfirmationMode {
    AS_CHILD,
    AS_STEP_CHILD,
}

private enum class RelativeAge {
    OLDER,
    YOUNGER,
    UNKNOWN,
}

enum class InferenceRule(
    val id: String,
    val steps: Int,
    val priority: Int,
    val confidence: InferenceConfidence,
) {
    GRANDPARENT("grandparent", 2, 0, InferenceConfidence.HIGH),
    SIBLING("sibling", 2, 1, InferenceConfidence.HIGH),
    AUNT_UNCLE("aunt_uncle", 2, 2, InferenceConfidence.MEDIUM_HIGH),
    COUSIN("cousin", 3, 3, InferenceConfidence.MEDIUM_HIGH),
    PARENT_IN_LAW("parent_in_law", 2, 4, InferenceConfidence.HIGH),
    CHILD_IN_LAW("child_in_law", 2, 5, InferenceConfidence.HIGH),
    SIBLING_IN_LAW("sibling_in_law", 2, 6, InferenceConfidence.HIGH),
    STEP_PARENT("step_parent", 2, 7, InferenceConfidence.MEDIUM),
}

data class InferredRelationshipCandidate(
    val fromPersonId: String,
    val toPersonId: String,
    val relationTypeId: String,
    val labelForFrom: String,
    val labelForTo: String,
    val confidence: InferenceConfidence,
    val rule: InferenceRule,
    val reason: String,
    val supportingRelationshipIds: Set<String>,
    val alternativeRelationTypeId: String? = null,
    val alternativeLabelForFrom: String? = null,
    val alternativeLabelForTo: String? = null,
    val secondaryLabelsForFrom: List<String> = emptyList(),
    val secondaryLabelsForTo: List<String> = emptyList(),
    val secondaryReasons: List<String> = emptyList(),
) {
    val evidenceFingerprint: String
        get() = supportingRelationshipIds.sorted().joinToString(",")

    fun labelFor(
        personId: String,
        confirmationMode: InferenceConfirmationMode = InferenceConfirmationMode.AS_CHILD,
    ): String {
        val useAlternative =
            rule == InferenceRule.STEP_PARENT &&
                confirmationMode == InferenceConfirmationMode.AS_STEP_CHILD
        val primary = when (personId) {
            fromPersonId -> if (useAlternative) {
                alternativeLabelForFrom ?: labelForFrom
            } else {
                labelForFrom
            }
            toPersonId -> if (useAlternative) {
                alternativeLabelForTo ?: labelForTo
            } else {
                labelForTo
            }
            else -> ""
        }
        if (useAlternative || primary.isBlank()) return primary
        val secondary = if (personId == fromPersonId) {
            secondaryLabelsForFrom
        } else {
            secondaryLabelsForTo
        }
        return (listOf(primary) + secondary).distinct().joinToString("/")
    }

    val reasonText: String
        get() = (listOf(reason) + secondaryReasons).distinct().joinToString("；")

    fun relationTypeFor(
        confirmationMode: InferenceConfirmationMode = InferenceConfirmationMode.AS_CHILD,
    ): String = if (
        rule == InferenceRule.STEP_PARENT &&
        confirmationMode == InferenceConfirmationMode.AS_STEP_CHILD
    ) {
        alternativeRelationTypeId ?: relationTypeId
    } else {
        relationTypeId
    }
}

object InferenceEngine {
    fun infer(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        dismissals: List<InferenceDismissalEntity>,
    ): List<InferredRelationshipCandidate> {
        if (people.size < 2) return emptyList()
        val peopleById = people.associateBy { it.id }
        val typeById = relationTypes.associateBy { it.id }
        val familyRelationships = relationships.filter {
            RelationshipSemantics.isFamilyLike(typeById[it.relationTypeId])
        }
        val parentChildEdges = familyRelationships.mapNotNull {
            RelationshipSemantics.parentChildEdge(it, typeById[it.relationTypeId])
        }
        val spouseRelationships = familyRelationships.filter {
            RelationshipSemantics.kind(typeById[it.relationTypeId]) == FamilyRelationKind.SPOUSE
        }
        val siblingRelationships = familyRelationships.filter {
            RelationshipSemantics.kind(typeById[it.relationTypeId]) == FamilyRelationKind.SIBLING
        }
        val parentsByChild = parentChildEdges.groupBy { it.childPersonId }
        val childrenByParent = parentChildEdges.groupBy { it.parentPersonId }
        val spousesByPerson = buildSymmetricMap(spouseRelationships)
        val explicitSiblingsByPerson = buildSymmetricMap(siblingRelationships)
        val familyPairs = familyRelationships.map { pairKey(it.fromPersonId, it.toPersonId) }.toSet()
        val dismissedKeys = dismissals.associateBy {
            dismissalKey(it.fromPersonId, it.toPersonId, it.ruleId)
        }

        fun parents(personId: String): List<PersonEntity> =
            parentsByChild[personId].orEmpty().mapNotNull { peopleById[it.parentPersonId] }

        fun children(personId: String): List<PersonEntity> =
            childrenByParent[personId].orEmpty().mapNotNull { peopleById[it.childPersonId] }

        fun spouses(personId: String): List<PersonEntity> =
            spousesByPerson[personId].orEmpty().mapNotNull(peopleById::get)

        fun siblings(personId: String): List<PersonEntity> {
            val siblingIds = mutableSetOf<String>()
            siblingIds += explicitSiblingsByPerson[personId].orEmpty()
            parents(personId).forEach { parent ->
                siblingIds += children(parent.id).map { it.id }
            }
            siblingIds.remove(personId)
            return siblingIds.mapNotNull(peopleById::get)
        }

        val candidates = mutableListOf<InferredRelationshipCandidate>()
        fun addDirected(
            from: PersonEntity,
            to: PersonEntity,
            rule: InferenceRule,
            typeId: String,
            fromLabel: String,
            toLabel: String,
            reason: String,
            evidence: Collection<String>,
            alternativeRelationTypeId: String? = null,
            alternativeFromLabel: String? = null,
            alternativeToLabel: String? = null,
        ) {
            if (from.id == to.id) return
            candidates += InferredRelationshipCandidate(
                fromPersonId = from.id,
                toPersonId = to.id,
                relationTypeId = typeId,
                labelForFrom = fromLabel,
                labelForTo = toLabel,
                confidence = rule.confidence,
                rule = rule,
                reason = reason,
                supportingRelationshipIds = evidence.toSet(),
                alternativeRelationTypeId = alternativeRelationTypeId,
                alternativeLabelForFrom = alternativeFromLabel,
                alternativeLabelForTo = alternativeToLabel,
            )
        }

        fun addSymmetric(
            first: PersonEntity,
            second: PersonEntity,
            rule: InferenceRule,
            typeId: String,
            firstLabel: String,
            secondLabel: String,
            reason: String,
            evidence: Collection<String>,
        ) {
            if (first.id == second.id) return
            val ordered = listOf(first, second).sortedBy { it.id }
            val labels = if (ordered[0].id == first.id) {
                firstLabel to secondLabel
            } else {
                secondLabel to firstLabel
            }
            candidates += InferredRelationshipCandidate(
                fromPersonId = ordered[0].id,
                toPersonId = ordered[1].id,
                relationTypeId = typeId,
                labelForFrom = labels.first,
                labelForTo = labels.second,
                confidence = rule.confidence,
                rule = rule,
                reason = reason,
                supportingRelationshipIds = evidence.toSet(),
            )
        }

        people.forEach { anchor ->
            parents(anchor.id).forEach { parent ->
                val parentEdge = parentsByChild[anchor.id].orEmpty()
                    .firstOrNull { it.parentPersonId == parent.id }
                parents(parent.id).forEach { grandparent ->
                    val side = parentSide(parent, parentEdge)
                    addDirected(
                        from = grandparent,
                        to = anchor,
                        rule = InferenceRule.GRANDPARENT,
                        typeId = InferenceRelationTypeIds.GRANDPARENT,
                        fromLabel = grandparentLabel(grandparent, side),
                        toLabel = grandchildLabel(anchor, side),
                        reason = "${parent.name}的父母",
                        evidence = listOfNotNull(
                            parentsByChild[anchor.id]
                                ?.firstOrNull { it.parentPersonId == parent.id }
                                ?.relationshipId,
                            parentsByChild[parent.id]?.firstOrNull {
                                it.parentPersonId == grandparent.id
                            }?.relationshipId,
                        ),
                    )
                }

                siblings(parent.id).forEach { auntOrUncle ->
                    if (auntOrUncle.id != anchor.id && auntOrUncle.id !in parents(anchor.id).map { it.id }) {
                        val side = parentSide(parent, parentEdge)
                        addDirected(
                            from = auntOrUncle,
                            to = anchor,
                            rule = InferenceRule.AUNT_UNCLE,
                            typeId = InferenceRelationTypeIds.AUNT_UNCLE,
                            fromLabel = auntUncleLabel(auntOrUncle, parent, side),
                            toLabel = nieceNephewLabel(anchor, parent),
                            reason = "${parent.name}的兄弟姐妹",
                            evidence = siblingEvidence(
                                parent.id,
                                auntOrUncle.id,
                                parentsByChild,
                                siblingRelationships,
                            ) + listOfNotNull(
                                parentsByChild[anchor.id]
                                    ?.firstOrNull { it.parentPersonId == parent.id }
                                    ?.relationshipId,
                            ),
                        )

                        children(auntOrUncle.id).forEach { cousin ->
                            if (cousin.id != anchor.id) {
                                val cousinSide = cousinSide(
                                    parent = parent,
                                    parentEdge = parentEdge,
                                    auntOrUncle = auntOrUncle,
                                )
                                addSymmetric(
                                    first = anchor,
                                    second = cousin,
                                    rule = InferenceRule.COUSIN,
                                    typeId = InferenceRelationTypeIds.COUSIN,
                                    firstLabel = cousinLabel(anchor, cousin, cousinSide),
                                    secondLabel = cousinLabel(cousin, anchor, cousinSide),
                                    reason = "${parent.name}的兄弟姐妹的子女",
                                    evidence = siblingEvidence(
                                        parent.id,
                                        auntOrUncle.id,
                                        parentsByChild,
                                        siblingRelationships,
                                    ) + listOfNotNull(
                                        parentsByChild[anchor.id]
                                            ?.firstOrNull { it.parentPersonId == parent.id }
                                            ?.relationshipId,
                                    ) + childrenByParent[auntOrUncle.id].orEmpty()
                                        .map { it.relationshipId },
                                )
                            }
                        }
                    }
                }
            }

            siblings(anchor.id).forEach { sibling ->
                addSymmetric(
                    first = anchor,
                    second = sibling,
                    rule = InferenceRule.SIBLING,
                    typeId = "preset_sibling",
                    firstLabel = siblingLabel(anchor, sibling),
                    secondLabel = siblingLabel(sibling, anchor),
                    reason = "有共同的父母或已录入兄弟姐妹关系",
                    evidence = siblingEvidence(
                        anchor.id,
                        sibling.id,
                        parentsByChild,
                        siblingRelationships,
                    ),
                )
            }

            spouses(anchor.id).forEach { spouse ->
                parents(spouse.id).forEach { parentInLaw ->
                    addDirected(
                        from = parentInLaw,
                        to = anchor,
                        rule = InferenceRule.PARENT_IN_LAW,
                        typeId = InferenceRelationTypeIds.IN_LAW,
                        fromLabel = parentInLawLabel(parentInLaw, spouse, anchor),
                        toLabel = childSpouseLabel(anchor),
                        reason = "${spouse.name}的父母",
                        evidence = listOfNotNull(
                            spousesByPerson[anchor.id]?.firstOrNull { it == spouse.id },
                            parentsByChild[spouse.id]?.firstOrNull {
                                it.parentPersonId == parentInLaw.id
                            }?.relationshipId,
                        ),
                    )
                }
                children(spouse.id).forEach { child ->
                    if (child.id !in children(anchor.id).map { it.id }) {
                        addDirected(
                            from = anchor,
                            to = child,
                            rule = InferenceRule.STEP_PARENT,
                            typeId = "preset_parent_child",
                            fromLabel = parentLabel(anchor),
                            toLabel = childLabel(child),
                            reason = "${spouse.name}的子女",
                            evidence = listOfNotNull(
                                spousesByPerson[anchor.id]?.firstOrNull { it == spouse.id },
                                childrenByParent[spouse.id]?.firstOrNull {
                                    it.childPersonId == child.id
                                }?.relationshipId,
                            ),
                            alternativeRelationTypeId = InferenceRelationTypeIds.STEP_PARENT,
                            alternativeFromLabel = stepParentLabel(anchor),
                            alternativeToLabel = stepChildLabel(child),
                        )
                    }
                }

                siblings(spouse.id).forEach { spouseSibling ->
                    val labels = spouseSiblingLabels(anchor, spouse, spouseSibling)
                    addSymmetric(
                        first = anchor,
                        second = spouseSibling,
                        rule = InferenceRule.SIBLING_IN_LAW,
                        typeId = InferenceRelationTypeIds.SIBLING_IN_LAW,
                        firstLabel = labels.first,
                        secondLabel = labels.second,
                        reason = "${spouse.name}的兄弟姐妹",
                        evidence = siblingEvidence(
                            spouse.id,
                            spouseSibling.id,
                            parentsByChild,
                            siblingRelationships,
                        ),
                    )
                }
            }

            children(anchor.id).forEach { child ->
                spouses(child.id).forEach { childSpouse ->
                    addDirected(
                        from = anchor,
                        to = childSpouse,
                        rule = InferenceRule.CHILD_IN_LAW,
                        typeId = InferenceRelationTypeIds.IN_LAW,
                        fromLabel = parentInLawLabel(anchor, child, childSpouse),
                        toLabel = childSpouseLabel(childSpouse),
                        reason = "${child.name}的配偶",
                        evidence = listOfNotNull(
                            childrenByParent[anchor.id]?.firstOrNull {
                                it.childPersonId == child.id
                            }?.relationshipId,
                            spousesByPerson[child.id]?.firstOrNull { it == childSpouse.id },
                        ),
                    )
                }
            }

            siblings(anchor.id).forEach { sibling ->
                spouses(sibling.id).forEach { siblingSpouse ->
                    val labels = siblingSpouseLabels(anchor, sibling, siblingSpouse)
                    addSymmetric(
                        first = anchor,
                        second = siblingSpouse,
                        rule = InferenceRule.SIBLING_IN_LAW,
                        typeId = InferenceRelationTypeIds.SIBLING_IN_LAW,
                        firstLabel = labels.first,
                        secondLabel = labels.second,
                        reason = "${sibling.name}的配偶",
                        evidence = listOfNotNull(
                            siblingEvidence(
                                anchor.id,
                                sibling.id,
                                parentsByChild,
                                siblingRelationships,
                            ).firstOrNull(),
                            spousesByPerson[sibling.id]?.firstOrNull { it == siblingSpouse.id },
                        ),
                    )
                }
            }

            parents(anchor.id).forEach { parent ->
                spouses(parent.id).forEach { stepParent ->
                    if (stepParent.id != anchor.id && stepParent.id !in parents(anchor.id).map { it.id }) {
                        addDirected(
                            from = stepParent,
                            to = anchor,
                            rule = InferenceRule.STEP_PARENT,
                            typeId = "preset_parent_child",
                            fromLabel = parentLabel(stepParent),
                            toLabel = childLabel(anchor),
                            reason = "${parent.name}的配偶",
                            evidence = listOfNotNull(
                                parentsByChild[anchor.id]?.firstOrNull {
                                    it.parentPersonId == parent.id
                                }?.relationshipId,
                                spousesByPerson[parent.id]?.firstOrNull {
                                    it == stepParent.id
                                },
                            ),
                            alternativeRelationTypeId = InferenceRelationTypeIds.STEP_PARENT,
                            alternativeFromLabel = stepParentLabel(stepParent),
                            alternativeToLabel = stepChildLabel(anchor),
                        )
                    }
                }
            }
        }

        return candidates
            .filterNot { pairKey(it.fromPersonId, it.toPersonId) in familyPairs }
            .filterNot { candidate ->
                val dismissal = dismissedKeys[
                    dismissalKey(
                        candidate.fromPersonId,
                        candidate.toPersonId,
                        candidate.rule.id,
                    )
                ]
                dismissal?.evidenceFingerprint == candidate.evidenceFingerprint
            }
            .groupBy { pairKey(it.fromPersonId, it.toPersonId) }
            .values
            .mapNotNull { samePair ->
                val bestSteps = samePair.minOfOrNull { it.rule.steps } ?: return@mapNotNull null
                val equivalent = samePair.filter { it.rule.steps == bestSteps }
                val primary = equivalent.minWithOrNull(
                    compareBy<InferredRelationshipCandidate> { it.rule.steps }
                        .thenBy { it.rule.priority }
                        .thenBy { it.fromPersonId }
                        .thenBy { it.toPersonId },
                ) ?: return@mapNotNull null
                val alternatives = equivalent.filter { it !== primary }
                primary.copy(
                    supportingRelationshipIds = equivalent
                        .flatMap { it.supportingRelationshipIds }
                        .toSet(),
                    secondaryLabelsForFrom = alternatives.map { it.labelForFrom },
                    secondaryLabelsForTo = alternatives.map { it.labelForTo },
                    secondaryReasons = alternatives.map { it.reason },
                )
            }
            .sortedWith(compareBy({ peopleById[it.fromPersonId]?.name.orEmpty() }, {
                peopleById[it.toPersonId]?.name.orEmpty()
            }))
    }

    private fun buildSymmetricMap(
        relationships: List<RelationshipEntity>,
    ): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        relationships.forEach { relationship ->
            result.getOrPut(relationship.fromPersonId) { mutableSetOf() } += relationship.toPersonId
            result.getOrPut(relationship.toPersonId) { mutableSetOf() } += relationship.fromPersonId
        }
        return result
    }

    private fun siblingEvidence(
        firstPersonId: String,
        secondPersonId: String,
        parentsByChild: Map<String, List<ParentChildEdge>>,
        siblingRelationships: List<RelationshipEntity>,
    ): Set<String> {
        val parentEvidence = (
            parentsByChild[firstPersonId].orEmpty() +
                parentsByChild[secondPersonId].orEmpty()
            ).map { it.relationshipId }
        val explicitSiblingEvidence = siblingRelationships.filter {
            (it.fromPersonId == firstPersonId && it.toPersonId == secondPersonId) ||
                (it.fromPersonId == secondPersonId && it.toPersonId == firstPersonId)
        }.map { it.id }
        return (parentEvidence + explicitSiblingEvidence).toSet()
    }

    private fun siblingLabel(person: PersonEntity, relative: PersonEntity): String {
        val age = relativeAge(person, relative)
        return when (person.gender) {
            Gender.MALE -> when (age) {
                RelativeAge.OLDER -> "哥哥"
                RelativeAge.YOUNGER -> "弟弟"
                RelativeAge.UNKNOWN -> "兄弟"
            }
            Gender.FEMALE -> when (age) {
                RelativeAge.OLDER -> "姐姐"
                RelativeAge.YOUNGER -> "妹妹"
                RelativeAge.UNKNOWN -> "姐妹"
            }
            Gender.UNSPECIFIED -> "兄弟姐妹"
        }
    }

    private fun grandparentLabel(person: PersonEntity, side: String): String = when (side) {
        "父系" -> when (person.gender) {
            Gender.MALE -> "爷爷"
            Gender.FEMALE -> "奶奶"
            Gender.UNSPECIFIED -> "祖父母"
        }
        "母系" -> when (person.gender) {
            Gender.MALE -> "外公"
            Gender.FEMALE -> "外婆"
            Gender.UNSPECIFIED -> "外祖父母"
        }
        else -> "祖父母"
    }

    private fun grandchildLabel(person: PersonEntity, side: String): String = when (side) {
        "父系" -> when (person.gender) {
            Gender.MALE -> "孙子"
            Gender.FEMALE -> "孙女"
            Gender.UNSPECIFIED -> "孙辈"
        }
        "母系" -> when (person.gender) {
            Gender.MALE -> "外孙"
            Gender.FEMALE -> "外孙女"
            Gender.UNSPECIFIED -> "外孙辈"
        }
        else -> "孙辈"
    }

    private fun auntUncleLabel(
        person: PersonEntity,
        parent: PersonEntity,
        side: String,
    ): String = when (person.gender) {
        Gender.MALE -> when (side) {
            "父系" -> when (relativeAge(person, parent)) {
                RelativeAge.OLDER -> "伯父"
                RelativeAge.YOUNGER -> "叔父"
                RelativeAge.UNKNOWN -> "叔伯"
            }
            "母系" -> "舅舅"
            else -> "叔伯"
        }
        Gender.FEMALE -> if (side == "父系") "姑姑" else "姨妈"
        Gender.UNSPECIFIED -> "叔伯/舅姨"
    }

    private fun nieceNephewLabel(person: PersonEntity, parent: PersonEntity): String =
        when {
            parent.gender == Gender.MALE -> when (person.gender) {
                Gender.MALE -> "侄子"
                Gender.FEMALE -> "侄女"
                Gender.UNSPECIFIED -> "侄辈"
            }
            parent.gender == Gender.FEMALE -> when (person.gender) {
                Gender.MALE -> "外甥"
                Gender.FEMALE -> "外甥女"
                Gender.UNSPECIFIED -> "外甥辈"
            }
            else -> "侄辈/外甥辈"
        }

    private fun cousinLabel(
        person: PersonEntity,
        relative: PersonEntity,
        side: String,
    ): String {
        if (side == "堂表") return "堂表亲"
        val age = relativeAge(person, relative)
        return when (person.gender) {
            Gender.MALE -> when (age) {
                RelativeAge.OLDER -> "${side}兄"
                RelativeAge.YOUNGER -> "${side}弟"
                RelativeAge.UNKNOWN -> "${side}表亲"
            }
            Gender.FEMALE -> when (age) {
                RelativeAge.OLDER -> "${side}姐"
                RelativeAge.YOUNGER -> "${side}妹"
                RelativeAge.UNKNOWN -> "${side}表亲"
            }
            Gender.UNSPECIFIED -> if (side == "堂") "堂表亲" else "表亲"
        }
    }

    private fun parentInLawLabel(
        person: PersonEntity,
        spouse: PersonEntity,
        anchor: PersonEntity,
    ): String = when {
        anchor.gender == Gender.MALE && spouse.gender == Gender.FEMALE ->
            if (person.gender == Gender.MALE) "岳父" else "岳母"
        anchor.gender == Gender.FEMALE && spouse.gender == Gender.MALE ->
            if (person.gender == Gender.MALE) "公公" else "婆婆"
        else -> "配偶的父母"
    }

    private fun childSpouseLabel(person: PersonEntity): String = when (person.gender) {
        Gender.MALE -> "女婿"
        Gender.FEMALE -> "儿媳"
        Gender.UNSPECIFIED -> "子女的配偶"
    }

    private fun stepParentLabel(person: PersonEntity): String = when (person.gender) {
        Gender.MALE -> "继父"
        Gender.FEMALE -> "继母"
        Gender.UNSPECIFIED -> "继父母"
    }

    private fun stepChildLabel(person: PersonEntity): String = when (person.gender) {
        Gender.MALE -> "继子"
        Gender.FEMALE -> "继女"
        Gender.UNSPECIFIED -> "继子女"
    }

    private fun parentLabel(person: PersonEntity): String = when (person.gender) {
        Gender.MALE -> "父亲"
        Gender.FEMALE -> "母亲"
        Gender.UNSPECIFIED -> "父母"
    }

    private fun childLabel(person: PersonEntity): String = when (person.gender) {
        Gender.MALE -> "儿子"
        Gender.FEMALE -> "女儿"
        Gender.UNSPECIFIED -> "子女"
    }

    private fun spouseSiblingLabels(
        anchor: PersonEntity,
        spouse: PersonEntity,
        relative: PersonEntity,
    ): Pair<String, String> {
        val anchorLabel = when {
            anchor.gender == Gender.MALE && spouse.gender == Gender.FEMALE ->
                when (relative.gender) {
                    Gender.MALE -> when (relativeAge(relative, spouse)) {
                        RelativeAge.OLDER -> "大舅子"
                        RelativeAge.YOUNGER -> "小舅子"
                        RelativeAge.UNKNOWN -> "配偶的兄弟"
                    }
                    Gender.FEMALE -> when (relativeAge(relative, spouse)) {
                        RelativeAge.OLDER -> "大姨子"
                        RelativeAge.YOUNGER -> "小姨子"
                        RelativeAge.UNKNOWN -> "配偶的姐妹"
                    }
                    Gender.UNSPECIFIED -> "配偶的兄弟姐妹"
                }
            anchor.gender == Gender.FEMALE && spouse.gender == Gender.MALE ->
                when (relative.gender) {
                    Gender.MALE -> when (relativeAge(relative, spouse)) {
                        RelativeAge.OLDER -> "大伯子"
                        RelativeAge.YOUNGER -> "小叔子"
                        RelativeAge.UNKNOWN -> "配偶的兄弟"
                    }
                    Gender.FEMALE -> when (relativeAge(relative, spouse)) {
                        RelativeAge.OLDER -> "大姑子"
                        RelativeAge.YOUNGER -> "小姑子"
                        RelativeAge.UNKNOWN -> "配偶的姐妹"
                    }
                    Gender.UNSPECIFIED -> "配偶的兄弟姐妹"
                }
            else -> "配偶的兄弟姐妹"
        }
        val reverseLabel = when {
            spouse.gender == Gender.FEMALE &&
                relative.gender == Gender.MALE &&
                anchor.gender == Gender.MALE ->
                when (relativeAge(spouse, relative)) {
                    RelativeAge.OLDER -> "姐夫"
                    RelativeAge.YOUNGER -> "妹夫"
                    RelativeAge.UNKNOWN -> "姐妹的配偶"
                }
            spouse.gender == Gender.FEMALE &&
                relative.gender == Gender.FEMALE &&
                anchor.gender == Gender.MALE ->
                when (relativeAge(spouse, relative)) {
                    RelativeAge.OLDER -> "姐夫"
                    RelativeAge.YOUNGER -> "妹夫"
                    RelativeAge.UNKNOWN -> "姐妹的配偶"
                }
            spouse.gender == Gender.MALE &&
                relative.gender == Gender.MALE &&
                anchor.gender == Gender.FEMALE ->
                when (relativeAge(spouse, relative)) {
                    RelativeAge.OLDER -> "嫂子"
                    RelativeAge.YOUNGER -> "弟媳"
                    RelativeAge.UNKNOWN -> "兄弟的配偶"
                }
            spouse.gender == Gender.MALE &&
                relative.gender == Gender.FEMALE &&
                anchor.gender == Gender.FEMALE ->
                when (relativeAge(spouse, relative)) {
                    RelativeAge.OLDER -> "嫂子"
                    RelativeAge.YOUNGER -> "弟媳"
                    RelativeAge.UNKNOWN -> "兄弟的配偶"
                }
            else -> "兄弟姐妹的配偶"
        }
        return anchorLabel to reverseLabel
    }

    private fun siblingSpouseLabels(
        anchor: PersonEntity,
        sibling: PersonEntity,
        relative: PersonEntity,
    ): Pair<String, String> {
        val anchorLabel = when {
            sibling.gender == Gender.MALE && relative.gender == Gender.FEMALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "嫂子"
                    RelativeAge.YOUNGER -> "弟媳"
                    RelativeAge.UNKNOWN -> "兄弟的配偶"
                }
            sibling.gender == Gender.FEMALE && relative.gender == Gender.MALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "姐夫"
                    RelativeAge.YOUNGER -> "妹夫"
                    RelativeAge.UNKNOWN -> "姐妹的配偶"
                }
            else -> "兄弟姐妹的配偶"
        }
        val reverseLabel = when {
            sibling.gender == Gender.MALE && anchor.gender == Gender.MALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "小叔子"
                    RelativeAge.YOUNGER -> "大伯子"
                    RelativeAge.UNKNOWN -> "配偶的兄弟"
                }
            sibling.gender == Gender.MALE && anchor.gender == Gender.FEMALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "小姑子"
                    RelativeAge.YOUNGER -> "大姑子"
                    RelativeAge.UNKNOWN -> "配偶的姐妹"
                }
            sibling.gender == Gender.FEMALE && anchor.gender == Gender.FEMALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "小姨子"
                    RelativeAge.YOUNGER -> "大姨子"
                    RelativeAge.UNKNOWN -> "配偶的姐妹"
                }
            sibling.gender == Gender.FEMALE && anchor.gender == Gender.MALE ->
                when (relativeAge(sibling, anchor)) {
                    RelativeAge.OLDER -> "小舅子"
                    RelativeAge.YOUNGER -> "大舅子"
                    RelativeAge.UNKNOWN -> "配偶的兄弟"
                }
            else -> "配偶的兄弟姐妹"
        }
        return anchorLabel to reverseLabel
    }

    private fun parentSide(
        parent: PersonEntity,
        edge: ParentChildEdge?,
    ): String = when (effectiveGender(parent, edge?.parentRoleGender)) {
        Gender.MALE -> "父系"
        Gender.FEMALE -> "母系"
        Gender.UNSPECIFIED -> "未知"
    }

    private fun cousinSide(
        parent: PersonEntity,
        parentEdge: ParentChildEdge?,
        auntOrUncle: PersonEntity,
    ): String {
        val parentGender = effectiveGender(parent, parentEdge?.parentRoleGender)
        return if (parentGender == Gender.MALE && auntOrUncle.gender == Gender.MALE) {
            "堂"
        } else if (parentGender == Gender.MALE && auntOrUncle.gender == Gender.FEMALE) {
            "表"
        } else if (parentGender == Gender.FEMALE) {
            "表"
        } else {
            "堂表"
        }
    }

    private fun effectiveGender(
        person: PersonEntity,
        roleGender: Gender?,
    ): Gender = if (person.gender != Gender.UNSPECIFIED) {
        person.gender
    } else {
        roleGender ?: Gender.UNSPECIFIED
    }

    private fun relativeAge(first: PersonEntity, second: PersonEntity): RelativeAge {
        val firstDate = parseBirthday(first.birthday) ?: return RelativeAge.UNKNOWN
        val secondDate = parseBirthday(second.birthday) ?: return RelativeAge.UNKNOWN
        return when {
            firstDate.isBefore(secondDate) -> RelativeAge.OLDER
            firstDate.isAfter(secondDate) -> RelativeAge.YOUNGER
            else -> RelativeAge.UNKNOWN
        }
    }

    private fun parseBirthday(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private fun pairKey(first: String, second: String): String {
        val pair = listOf(first, second).sorted()
        return pair[0] + "\u0000" + pair[1]
    }

    private fun dismissalKey(from: String, to: String, ruleId: String): String =
        "$ruleId\u0000$from\u0000$to"

}
