package com.relationship.graph.data.inference

import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.Gender
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity

/**
 * 保存关系前的辈分校验：纯本地、同步执行，量级为全图一次 BFS。
 *
 * 拦截三类必然破坏家谱结构的关系：
 * 1. 辈分环（parent-child 成环，含自指）；
 * 2. 直系祖先后代之间结为配偶；
 * 3. 与既有关系推出的代际矛盾（非环但辈分对不上，如“姑姑的父亲是侄女”）。
 *
 * 一、二类给出明确路径；三类给出两条矛盾路径链。任何一类命中都应阻止保存。
 */
object KinshipValidator {

    enum class ConflictType {
        CYCLE,
        ANCESTOR_MARRIAGE,
        GENERATION_MISMATCH,
    }

    data class KinshipConflict(
        val type: ConflictType,
        val message: String,
        /** 用于展示的人名链，如 [张三, 李四, 王五]，相邻两人之间为一代约束。 */
        val pathNames: List<String>,
    )

    data class Result(val conflicts: List<KinshipConflict>) {
        val ok: Boolean get() = conflicts.isEmpty()
    }

    fun validate(
        existingRelationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        peopleById: Map<String, PersonEntity>,
        candidate: RelationshipEntity,
    ): Result {
        val typeById = relationTypes.associateBy { it.id }
        val candidateType = typeById[candidate.relationTypeId]
        // 编辑场景：排除被修改关系自身的旧边，避免自己和自己判环。
        val edges = buildEdges(
            existingRelationships.filterNot { it.id == candidate.id },
            typeById,
        )
        val conflicts = mutableListOf<KinshipConflict>()

        when (RelationshipSemantics.kind(candidateType)) {
            FamilyRelationKind.PARENT_CHILD -> {
                RelationshipSemantics.parentChildEdge(candidate, candidateType)?.let { edge ->
                    if (edge.parentPersonId == edge.childPersonId) {
                        conflicts += KinshipConflict(
                            type = ConflictType.CYCLE,
                            message = "不能把一个人设为自己的晚辈。",
                            pathNames = listOf(edge.parentPersonId),
                        )
                    } else {
                        // 成环条件：child 已是 parent 的祖先（parent 是 child 的晚辈）。
                        findAncestry(edges, edge.parentPersonId, edge.childPersonId)
                            ?.let { (chain, _) ->
                                conflicts += KinshipConflict(
                                    type = ConflictType.CYCLE,
                                    message = "会形成辈分循环：「${chain.joinToString(" → ")}」" +
                                        "每人都是前一位的晚辈，最后一位又回指第一位。",
                                    pathNames = chain,
                                )
                            }
                    }
                }
            }
            FamilyRelationKind.SPOUSE -> {
                findAncestry(edges, candidate.fromPersonId, candidate.toPersonId)
                    ?.let { (chain, generations) ->
                        conflicts += KinshipConflict(
                            type = ConflictType.ANCESTOR_MARRIAGE,
                            message = "「${nameOf(peopleById, candidate.toPersonId)}」是" +
                                "「${nameOf(peopleById, candidate.fromPersonId)}」的" +
                                "${ancestorTitle(peopleById[candidate.toPersonId], generations)}" +
                                "（${chain.joinToString(" → ")}），不能结为配偶。",
                            pathNames = chain,
                        )
                        return Result(conflicts)
                    }
                findAncestry(edges, candidate.toPersonId, candidate.fromPersonId)
                    ?.let { (chain, generations) ->
                        conflicts += KinshipConflict(
                            type = ConflictType.ANCESTOR_MARRIAGE,
                            message = "「${nameOf(peopleById, candidate.fromPersonId)}」是" +
                                "「${nameOf(peopleById, candidate.toPersonId)}」的" +
                                "${ancestorTitle(peopleById[candidate.fromPersonId], generations)}" +
                                "（${chain.joinToString(" → ")}），不能结为配偶。",
                            pathNames = chain,
                        )
                        return Result(conflicts)
                    }
            }
            else -> Unit
        }

        checkGenerationConsistency(edges, candidate, candidateType, peopleById)
            ?.let { conflicts += it }
        return Result(conflicts)
    }

    // ===== 数据结构 =====

    /** 语义化边：toPerson 的代际 = fromPerson 的代际 + generationDelta。 */
    private data class KinshipEdge(
        val fromPersonId: String,
        val toPersonId: String,
        val generationDelta: Int,
        val label: String,
    )

    private fun buildEdges(
        relationships: List<RelationshipEntity>,
        typeById: Map<String, RelationTypeEntity>,
    ): List<KinshipEdge> = relationships.mapNotNull { relationship ->
        val type = typeById[relationship.relationTypeId] ?: return@mapNotNull null
        when (RelationshipSemantics.kind(type)) {
            FamilyRelationKind.PARENT_CHILD -> {
                val edge = RelationshipSemantics.parentChildEdge(relationship, type)
                    ?: return@mapNotNull null
                KinshipEdge(
                    fromPersonId = edge.parentPersonId,
                    toPersonId = edge.childPersonId,
                    generationDelta = 1,
                    label = "晚辈",
                )
            }
            FamilyRelationKind.SPOUSE,
            FamilyRelationKind.SIBLING,
            FamilyRelationKind.SIBLING_IN_LAW,
            FamilyRelationKind.COUSIN,
            -> KinshipEdge(
                fromPersonId = relationship.fromPersonId,
                toPersonId = relationship.toPersonId,
                generationDelta = 0,
                label = type.name,
            )
            FamilyRelationKind.AUNT_UNCLE,
            FamilyRelationKind.AUNT_UNCLE_IN_LAW,
            -> KinshipEdge(
                fromPersonId = relationship.fromPersonId,
                toPersonId = relationship.toPersonId,
                generationDelta = 1,
                label = "晚辈",
            )
            FamilyRelationKind.OTHER -> null
        }
    }

    private fun findAncestry(
        edges: List<KinshipEdge>,
        startId: String,
        targetId: String,
    ): Pair<List<String>, Int>? {
        if (startId == targetId) return listOf(startId) to 0
        val parentsByChild = edges
            .filter { it.generationDelta == 1 }
            .groupBy({ it.toPersonId }, { it.fromPersonId })
        val queue = ArrayDeque<Pair<String, List<String>>>()
        queue.add(startId to listOf(startId))
        val visited = mutableSetOf(startId)
        while (queue.isNotEmpty()) {
            val (current, chain) = queue.removeFirst()
            for (parent in parentsByChild[current].orEmpty()) {
                val nextChain = chain + parent
                if (parent == targetId) return nextChain to (nextChain.size - 1)
                if (visited.add(parent)) queue.add(parent to nextChain)
            }
        }
        return null
    }

    /**
     * 代际一致性：把候选关系临时并入全图，从每个连通分量做 BFS。
     * 同一人被推出两个不同代际值时，用两条路径链还原矛盾。
     */
    private fun checkGenerationConsistency(
        edges: List<KinshipEdge>,
        candidate: RelationshipEntity,
        candidateType: RelationTypeEntity?,
        peopleById: Map<String, PersonEntity>,
    ): KinshipConflict? {
        val typeById: Map<String, RelationTypeEntity> =
            candidateType?.let { mapOf(candidate.relationTypeId to it) } ?: emptyMap()
        val allEdges = edges + buildEdges(listOf(candidate), typeById)
        val adjacency = mutableMapOf<String, MutableList<Pair<String, KinshipEdge>>>()
        fun addEdge(edge: KinshipEdge) {
            adjacency.getOrPut(edge.fromPersonId) { mutableListOf() } += edge.toPersonId to edge
            adjacency.getOrPut(edge.toPersonId) { mutableListOf() } += edge.fromPersonId to edge
        }
        allEdges.forEach(::addEdge)

        val generationByPerson = mutableMapOf<String, Int>()
        val pathByPerson = mutableMapOf<String, List<String>>() // 种子 → 该人 的人名 id 链

        for (seed in adjacency.keys.sorted()) {
            if (seed in generationByPerson) continue
            generationByPerson[seed] = 0
            pathByPerson[seed] = listOf(seed)
            val queue = ArrayDeque<String>()
            queue.add(seed)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val currentGeneration = generationByPerson.getValue(current)
                for ((neighborId, edge) in adjacency[current].orEmpty()) {
                    val delta = if (edge.toPersonId == neighborId) edge.generationDelta
                    else -edge.generationDelta
                    val expected = currentGeneration + delta
                    val existing = generationByPerson[neighborId]
                    if (existing == null) {
                        generationByPerson[neighborId] = expected
                        pathByPerson[neighborId] = pathByPerson.getValue(current) + neighborId
                        queue.add(neighborId)
                    } else if (existing != expected) {
                        val chainA = pathByPerson.getValue(current)
                        val chainB = pathByPerson.getValue(neighborId)
                        val nameOf: (String) -> String = { nameOf(peopleById, it) ?: "未知" }
                        return KinshipConflict(
                            type = ConflictType.GENERATION_MISMATCH,
                            message = "与现有辈分矛盾：路径「${chainA.joinToString(" → ")}」" +
                                "与路径「${chainB.joinToString(" → ")}」对" +
                                "「${nameOf(neighborId)}」推出的辈分相差 " +
                                "${kotlin.math.abs(existing - expected)} 代。",
                            pathNames = chainA + chainB.drop(1),
                        )
                    }
                }
            }
        }
        return null
    }

    private fun nameOf(peopleById: Map<String, PersonEntity>, personId: String): String? =
        peopleById[personId]?.let { it.name.ifBlank { "未命名" } }

    /** 性别称谓（预留：环链展示用）。 */
    private fun ancestorTitle(person: PersonEntity?, generations: Int): String {
        if (person == null) return "直系长辈"
        return when (generations) {
            1 -> when (person.gender) {
                Gender.MALE -> "父亲"
                Gender.FEMALE -> "母亲"
                Gender.UNSPECIFIED -> "父/母"
            }
            2 -> when (person.gender) {
                Gender.MALE -> "祖父"
                Gender.FEMALE -> "祖母"
                Gender.UNSPECIFIED -> "祖辈"
            }
            else -> "祖先"
        }
    }
}
