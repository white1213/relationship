package com.relationship.graph.data.inference

import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.InferenceDismissalEntity
import com.relationship.graph.data.local.InferenceRelationTypeIds
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import java.time.LocalDate

enum class InferenceConfidence {
    HIGH,
    MEDIUM_HIGH,
    MEDIUM,
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
) {
    val evidenceFingerprint: String
        get() = supportingRelationshipIds.sorted().joinToString(",")

    fun labelFor(personId: String): String = when (personId) {
        fromPersonId -> labelForFrom
        toPersonId -> labelForTo
        else -> ""
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
            typeById[it.relationTypeId]?.category == RelationCategory.FAMILY
        }
        val parentChildRelationships = familyRelationships.filter {
            isParentChild(typeById[it.relationTypeId])
        }
        val spouseRelationships = familyRelationships.filter {
            isSpouse(typeById[it.relationTypeId])
        }
        val siblingRelationships = familyRelationships.filter {
            isSibling(typeById[it.relationTypeId])
        }
        val parentsByChild = parentChildRelationships.groupBy { it.toPersonId }
        val childrenByParent = parentChildRelationships.groupBy { it.fromPersonId }
        val spousesByPerson = buildSymmetricMap(spouseRelationships)
        val explicitSiblingsByPerson = buildSymmetricMap(siblingRelationships)
        val familyPairs = familyRelationships.map { pairKey(it.fromPersonId, it.toPersonId) }.toSet()
        val dismissedKeys = dismissals.associateBy {
            dismissalKey(it.fromPersonId, it.toPersonId, it.ruleId)
        }

        fun parents(personId: String): List<PersonEntity> =
            parentsByChild[personId].orEmpty().mapNotNull { peopleById[it.fromPersonId] }

        fun children(personId: String): List<PersonEntity> =
            childrenByParent[personId].orEmpty().mapNotNull { peopleById[it.toPersonId] }

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
                parents(parent.id).forEach { grandparent ->
                    val side = parentSide(parent)
                    addDirected(
                        from = grandparent,
                        to = anchor,
                        rule = InferenceRule.GRANDPARENT,
                        typeId = InferenceRelationTypeIds.GRANDPARENT,
                        fromLabel = grandparentLabel(grandparent, side),
                        toLabel = grandchildLabel(anchor, side),
                        reason = "${parent.name}的父母",
                        evidence = listOfNotNull(
                            parentsByChild[anchor.id]?.firstOrNull { it.fromPersonId == parent.id }?.id,
                            parentsByChild[parent.id]?.firstOrNull {
                                it.fromPersonId == grandparent.id
                            }?.id,
                        ),
                    )
                }

                siblings(parent.id).forEach { auntOrUncle ->
                    if (auntOrUncle.id != anchor.id && auntOrUncle.id !in parents(anchor.id).map { it.id }) {
                        val side = parentSide(parent)
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
                                    ?.firstOrNull { it.fromPersonId == parent.id }
                                    ?.id,
                            ),
                        )

                        children(auntOrUncle.id).forEach { cousin ->
                            if (cousin.id != anchor.id) {
                                val cousinSide = if (parent.gender == Gender.FEMALE) "表" else "堂"
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
                                            ?.firstOrNull { it.fromPersonId == parent.id }
                                            ?.id,
                                    ) + childrenByParent[auntOrUncle.id].orEmpty().map { it.id },
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
                                it.fromPersonId == parentInLaw.id
                            }?.id,
                        ),
                    )
                }
                children(spouse.id).forEach { child ->
                    if (child.id !in children(anchor.id).map { it.id }) {
                        addDirected(
                            from = anchor,
                            to = child,
                            rule = InferenceRule.STEP_PARENT,
                            typeId = InferenceRelationTypeIds.STEP_PARENT,
                            fromLabel = stepParentLabel(anchor),
                            toLabel = stepChildLabel(child),
                            reason = "${spouse.name}的子女",
                            evidence = listOfNotNull(
                                spousesByPerson[anchor.id]?.firstOrNull { it == spouse.id },
                                childrenByParent[spouse.id]?.firstOrNull {
                                    it.toPersonId == child.id
                                }?.id,
                            ),
                        )
                    }
                }

                siblings(spouse.id).forEach { spouseSibling ->
                    addSymmetric(
                        first = anchor,
                        second = spouseSibling,
                        rule = InferenceRule.SIBLING_IN_LAW,
                        typeId = InferenceRelationTypeIds.SIBLING_IN_LAW,
                        firstLabel = "配偶的兄弟姐妹",
                        secondLabel = "兄弟姐妹的配偶",
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
                                it.toPersonId == child.id
                            }?.id,
                            spousesByPerson[child.id]?.firstOrNull { it == childSpouse.id },
                        ),
                    )
                }
            }

            siblings(anchor.id).forEach { sibling ->
                spouses(sibling.id).forEach { siblingSpouse ->
                    addSymmetric(
                        first = anchor,
                        second = siblingSpouse,
                        rule = InferenceRule.SIBLING_IN_LAW,
                        typeId = InferenceRelationTypeIds.SIBLING_IN_LAW,
                        firstLabel = "兄弟姐妹的配偶",
                        secondLabel = "配偶的兄弟姐妹",
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
                            typeId = InferenceRelationTypeIds.STEP_PARENT,
                            fromLabel = stepParentLabel(stepParent),
                            toLabel = stepChildLabel(anchor),
                            reason = "${parent.name}的配偶",
                            evidence = listOfNotNull(
                                parentsByChild[anchor.id]?.firstOrNull {
                                    it.fromPersonId == parent.id
                                }?.id,
                                spousesByPerson[parent.id]?.firstOrNull {
                                    it == stepParent.id
                                },
                            ),
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
                samePair.minWithOrNull(
                    compareBy<InferredRelationshipCandidate> { it.rule.steps }
                        .thenBy { it.rule.priority }
                        .thenBy { it.fromPersonId }
                        .thenBy { it.toPersonId },
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
        parentsByChild: Map<String, List<RelationshipEntity>>,
        siblingRelationships: List<RelationshipEntity>,
    ): Set<String> = (
        parentsByChild[firstPersonId].orEmpty() +
            parentsByChild[secondPersonId].orEmpty() +
            siblingRelationships.filter {
                (it.fromPersonId == firstPersonId && it.toPersonId == secondPersonId) ||
                    (it.fromPersonId == secondPersonId && it.toPersonId == firstPersonId)
            }
        ).map { it.id }.toSet()

    private fun siblingLabel(person: PersonEntity, relative: PersonEntity): String {
        val older = isOlderThan(person, relative)
        return when (person.gender) {
            Gender.MALE -> if (older) "哥哥" else "弟弟"
            Gender.FEMALE -> if (older) "姐姐" else "妹妹"
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
            "父系" -> if (isOlderThan(person, parent)) "伯父" else "叔父"
            "母系" -> "舅舅"
            else -> "叔伯"
        }
        Gender.FEMALE -> if (side == "父系") "姑母" else "姨母"
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
        val older = isOlderThan(person, relative)
        return when (person.gender) {
            Gender.MALE -> if (older) "${side}兄" else "${side}弟"
            Gender.FEMALE -> if (older) "${side}姐" else "${side}妹"
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

    private fun parentSide(parent: PersonEntity): String = when (parent.gender) {
        Gender.MALE -> "父系"
        Gender.FEMALE -> "母系"
        Gender.UNSPECIFIED -> "未知"
    }

    private fun isOlderThan(first: PersonEntity, second: PersonEntity): Boolean {
        val firstDate = parseBirthday(first.birthday) ?: return false
        val secondDate = parseBirthday(second.birthday) ?: return false
        return firstDate.isBefore(secondDate)
    }

    private fun parseBirthday(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private fun pairKey(first: String, second: String): String {
        val pair = listOf(first, second).sorted()
        return pair[0] + "\u0000" + pair[1]
    }

    private fun dismissalKey(from: String, to: String, ruleId: String): String =
        "$ruleId\u0000$from\u0000$to"

    private fun isParentChild(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_parent_child"

    private fun isSpouse(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_spouse"

    private fun isSibling(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_sibling"
}
