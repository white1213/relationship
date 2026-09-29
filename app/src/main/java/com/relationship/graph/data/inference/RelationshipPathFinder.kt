package com.relationship.graph.data.inference

import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import com.relationship.graph.ui.relationshipLabelForPerson

/**
 * 任意两人之间的关系路径查询：纯本地图算法（加权 Dijkstra + 有界枚举）。
 * 权重让直系血亲/配偶边优先于旁系与社交边，用于在多路径中挑出最“像亲戚”的走向。
 */
object RelationshipPathFinder {

    data class PathHop(
        val fromPersonId: String,
        val toPersonId: String,
        /** from 视角对 to 的称谓，如「父亲」。 */
        val label: String,
    )

    data class PathResult(
        val personIds: List<String>,
        val hops: List<PathHop>,
        val cost: Float,
    )

    fun find(
        anchorPersonId: String,
        targetPersonId: String,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        people: List<PersonEntity>,
        ageOrders: List<com.relationship.graph.data.local.RelativeAgeOrderEntity>,
        maxPaths: Int = 5,
        maxHops: Int = 6,
    ): List<PathResult> {
        if (anchorPersonId == targetPersonId) return emptyList()
        val typeById = relationTypes.associateBy { it.id }
        val peopleById = people.associateBy { it.id }
        val resolver = RelativeAgeResolver(peopleById = peopleById, ageOrders = ageOrders)

        data class Edge(
            val from: String,
            val to: String,
            val weight: Float,
            val relationship: RelationshipEntity,
            val type: RelationTypeEntity,
        )

        val adjacency = mutableMapOf<String, MutableList<Edge>>()
        relationships.forEach { relationship ->
            val type = typeById[relationship.relationTypeId] ?: return@forEach
            val weight = when (RelationshipSemantics.kind(type)) {
                FamilyRelationKind.PARENT_CHILD -> 1.0f
                FamilyRelationKind.SPOUSE -> 1.2f
                FamilyRelationKind.SIBLING -> 1.6f
                FamilyRelationKind.AUNT_UNCLE,
                FamilyRelationKind.AUNT_UNCLE_IN_LAW,
                -> 2.0f
                FamilyRelationKind.COUSIN,
                FamilyRelationKind.SIBLING_IN_LAW,
                -> 2.2f
                FamilyRelationKind.OTHER -> 3.0f
            }
            val edge = Edge(relationship.fromPersonId, relationship.toPersonId, weight, relationship, type)
            adjacency.getOrPut(edge.from) { mutableListOf() } += edge
            adjacency.getOrPut(edge.to) { mutableListOf() } +=
                edge.copy(from = edge.to, to = edge.from)
        }

        // Dijkstra 求最优代价，作为枚举上界。
        data class Node(val id: String, val cost: Float)

        val best = mutableMapOf<String, Float>()
        val queue = java.util.PriorityQueue(compareBy<Node> { it.cost })
        queue.add(Node(anchorPersonId, 0f))
        best[anchorPersonId] = 0f
        var bestCost = Float.MAX_VALUE
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (current.cost > (best[current.id] ?: Float.MAX_VALUE)) continue
            if (current.id == targetPersonId) {
                bestCost = current.cost
                break
            }
            for (edge in adjacency[current.id].orEmpty()) {
                val nextCost = current.cost + edge.weight
                if (nextCost < best.getOrDefault(edge.to, Float.MAX_VALUE)) {
                    best[edge.to] = nextCost
                    queue.add(Node(edge.to, nextCost))
                }
            }
        }
        if (bestCost == Float.MAX_VALUE) return emptyList()

        // 有界 DFS 枚举：代价不超过最优×1.6 的简单路径，按代价排序取前 N。
        val costLimit = bestCost * 1.6f + 0.5f
        val results = mutableListOf<PathResult>()
        var budget = 200_000 // 访问预算，防止超大连通图枚举过久
        val pathNodes = ArrayDeque<String>()
        val pathEdges = ArrayDeque<Edge>()
        val visited = mutableSetOf<String>()

        fun dfs(id: String, cost: Float) {
            if (results.size >= maxPaths || budget <= 0) return
            budget--
            pathNodes.addLast(id)
            visited.add(id)
            if (id == targetPersonId && pathEdges.isNotEmpty()) {
                val nodes = pathNodes.toList()
                results.add(
                    PathResult(
                        personIds = nodes,
                        hops = pathEdges.mapIndexed { index, edge ->
                            val travelledFrom = nodes[index]
                            val travelledTo = nodes[index + 1]
                            PathHop(
                                fromPersonId = travelledFrom,
                                toPersonId = travelledTo,
                                label = relationshipLabelForPerson(
                                    relationship = edge.relationship,
                                    type = edge.type,
                                    personId = travelledFrom,
                                    otherPerson = peopleById[travelledTo],
                                    people = people,
                                    ageOrders = ageOrders,
                                    resolver = resolver,
                                ),
                            )
                        },
                        cost = cost,
                    ),
                )
            } else if (cost < costLimit && pathEdges.size < maxHops) {
                for (edge in adjacency[id].orEmpty().sortedBy { it.weight }) {
                    if (edge.to in visited) continue
                    pathEdges.addLast(edge)
                    dfs(edge.to, cost + edge.weight)
                    pathEdges.removeLast()
                    if (results.size >= maxPaths || budget <= 0) break
                }
            }
            pathNodes.removeLast()
            visited.remove(id)
        }

        dfs(anchorPersonId, 0f)
        return results.sortedBy { it.cost }
    }
}
