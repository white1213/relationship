package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class LayoutPoint(
    val x: Float,
    val y: Float,
) {
    operator fun plus(other: LayoutPoint) = LayoutPoint(x + other.x, y + other.y)
    operator fun minus(other: LayoutPoint) = LayoutPoint(x - other.x, y - other.y)
    operator fun times(value: Float) = LayoutPoint(x * value, y * value)

    companion object {
        val Zero = LayoutPoint(0f, 0f)
    }
}

enum class GraphRouteStyle {
    PARENT_CHILD,
    SPOUSE,
    SIBLING,
    SOCIAL,
}

data class RouteSegment(
    val start: LayoutPoint,
    val end: LayoutPoint,
)

data class RoutedRelationship(
    val groupKey: String,
    val relationshipIds: List<String>,
    val style: GraphRouteStyle,
    val segments: List<RouteSegment>,
) {
    val labelPoint: LayoutPoint
        get() {
            val longest = segments.maxByOrNull { (it.end - it.start).length() } ?: return LayoutPoint.Zero
            return (longest.start + longest.end) * 0.5f
        }
}

data class GraphLayoutResult(
    val positions: Map<String, LayoutPoint>,
    val routes: List<RoutedRelationship>,
    val generationByPerson: Map<String, Int>,
    val conflicts: Int,
)

object GraphLayoutEngine {
    private const val GENERATION_GAP = 190f
    private const val NODE_GAP = 132f
    private const val SPOUSE_GAP = 112f
    private const val NODE_RADIUS = 28f

    fun layout(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        relationTypes: List<RelationTypeEntity>,
        mode: GraphMode,
        myPersonId: String?,
        pinnedPositions: Map<String, LayoutPoint>,
    ): GraphLayoutResult {
        if (people.isEmpty()) {
            return GraphLayoutResult(emptyMap(), emptyList(), emptyMap(), 0)
        }
        val typeById = relationTypes.associateBy { it.id }
        val relevantRelationships = relationships.filter { relationship ->
            val type = typeById[relationship.relationTypeId] ?: return@filter false
            when (mode) {
                GraphMode.FAMILY -> type.category == RelationCategory.FAMILY
                GraphMode.SOCIAL -> type.category == RelationCategory.SOCIAL
                GraphMode.ALL -> true
            }
        }
        return when (mode) {
            GraphMode.FAMILY -> layoutFamily(
                people = people,
                relationships = relevantRelationships,
                typeById = typeById,
                myPersonId = myPersonId,
                pinnedPositions = pinnedPositions,
            )
            GraphMode.SOCIAL -> layoutSocial(
                people = people,
                relationships = relevantRelationships,
                myPersonId = myPersonId,
                pinnedPositions = pinnedPositions,
            )
            GraphMode.ALL -> layoutAll(
                people = people,
                relationships = relevantRelationships,
                typeById = typeById,
                myPersonId = myPersonId,
                pinnedPositions = pinnedPositions,
            )
        }
    }

    private fun layoutFamily(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        typeById: Map<String, RelationTypeEntity>,
        myPersonId: String?,
        pinnedPositions: Map<String, LayoutPoint>,
    ): GraphLayoutResult {
        if (people.isEmpty()) return GraphLayoutResult(emptyMap(), emptyList(), emptyMap(), 0)

        val personIds = people.map { it.id }
        val sameGeneration = DisjointSet(personIds)
        val spouseUnits = DisjointSet(personIds)
        val parentChildEdges = mutableListOf<Pair<String, String>>()
        val spouseEdges = mutableListOf<RelationshipEntity>()
        val siblingEdges = mutableListOf<RelationshipEntity>()

        relationships.forEach { relationship ->
            val type = typeById[relationship.relationTypeId] ?: return@forEach
            when {
                isParentChild(type) -> {
                    parentChildEdges += relationship.fromPersonId to relationship.toPersonId
                }
                isSpouse(type) -> {
                    spouseEdges += relationship
                    sameGeneration.union(relationship.fromPersonId, relationship.toPersonId)
                    spouseUnits.union(relationship.fromPersonId, relationship.toPersonId)
                }
                isSibling(type) -> {
                    siblingEdges += relationship
                    sameGeneration.union(relationship.fromPersonId, relationship.toPersonId)
                }
            }
        }

        val constraints = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        parentChildEdges.forEach { (parentId, childId) ->
            val parentRoot = sameGeneration.find(parentId)
            val childRoot = sameGeneration.find(childId)
            constraints.getOrPut(parentRoot) { mutableListOf() } += childRoot to 1
            constraints.getOrPut(childRoot) { mutableListOf() } += parentRoot to -1
        }

        val roots = personIds.map(sameGeneration::find).distinct()
        val generationByRoot = mutableMapOf<String, Int>()
        var conflicts = 0
        val queue = ArrayDeque<String>()
        val startRoot = myPersonId?.let(sameGeneration::find)?.takeIf { it in roots } ?: roots.first()
        generationByRoot[startRoot] = 0
        queue.add(startRoot)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentGeneration = generationByRoot.getValue(current)
            constraints[current].orEmpty().forEach { (neighbor, difference) ->
                val expected = currentGeneration + difference
                val existing = generationByRoot[neighbor]
                when {
                    existing == null -> {
                        generationByRoot[neighbor] = expected
                        queue.add(neighbor)
                    }
                    existing != expected -> conflicts++
                }
            }
        }
        roots.filterNot(generationByRoot::containsKey).forEach { generationByRoot[it] = 0 }
        val generationByPerson = personIds.associateWith { personId ->
            generationByRoot.getValue(sameGeneration.find(personId))
        }

        val unitByPerson = personIds.associateWith(spouseUnits::find)
        val membersByUnit = personIds.groupBy(unitByPerson::getValue)
            .mapValues { (_, members) -> members.sortedBy { people.first { person -> person.id == it }.name } }
        val generationByUnit = membersByUnit.mapValues { (_, members) ->
            members.map { generationByPerson.getValue(it) }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: 0
        }
        val parentUnitsByUnit = mutableMapOf<String, MutableSet<String>>()
        val childUnitsByUnit = mutableMapOf<String, MutableSet<String>>()
        parentChildEdges.forEach { (parentId, childId) ->
            val parentUnit = unitByPerson.getValue(parentId)
            val childUnit = unitByPerson.getValue(childId)
            if (parentUnit != childUnit) {
                parentUnitsByUnit.getOrPut(childUnit) { mutableSetOf() } += parentUnit
                childUnitsByUnit.getOrPut(parentUnit) { mutableSetOf() } += childUnit
            }
        }

        val orderedUnitsByGeneration = mutableMapOf<Int, List<String>>()
        membersByUnit.keys.groupBy { generationByUnit.getValue(it) }.forEach { (generation, units) ->
            orderedUnitsByGeneration[generation] = units.sorted()
        }
        repeat(4) {
            orderedUnitsByGeneration.keys.sorted().forEach { generation ->
                val previousOrder = orderedUnitsByGeneration[generation - 1].orEmpty()
                val previousIndex = previousOrder.withIndex().associate { it.value to it.index.toFloat() }
                val currentOrder = orderedUnitsByGeneration[generation].orEmpty()
                val currentIndex = currentOrder.withIndex().associate { it.value to it.index.toFloat() }
                orderedUnitsByGeneration[generation] = currentOrder.sortedWith(
                    compareBy(
                        {
                            parentUnitsByUnit[it].orEmpty()
                                .mapNotNull(previousIndex::get)
                                .averageOrFallback(currentIndex[it] ?: 0f)
                        },
                        { it },
                    ),
                )
            }
            orderedUnitsByGeneration.keys.sortedDescending().forEach { generation ->
                val nextOrder = orderedUnitsByGeneration[generation + 1].orEmpty()
                val nextIndex = nextOrder.withIndex().associate { it.value to it.index.toFloat() }
                val currentOrder = orderedUnitsByGeneration[generation].orEmpty()
                val currentIndex = currentOrder.withIndex().associate { it.value to it.index.toFloat() }
                orderedUnitsByGeneration[generation] = currentOrder.sortedWith(
                    compareBy(
                        {
                            childUnitsByUnit[it].orEmpty()
                                .mapNotNull(nextIndex::get)
                                .averageOrFallback(currentIndex[it] ?: 0f)
                        },
                        { it },
                    ),
                )
            }
        }

        val positions = mutableMapOf<String, LayoutPoint>()
        orderedUnitsByGeneration.toSortedMap().forEach { (generation, units) ->
            val unitWidths = units.associateWith { unitId ->
                val members = membersByUnit.getValue(unitId)
                max(0f, members.size * NODE_GAP + max(0, members.size - 1) * (SPOUSE_GAP - NODE_GAP))
            }
            val totalWidth = unitWidths.values.sum() + max(0, units.size - 1) * NODE_GAP
            var cursor = -totalWidth / 2f
            units.forEach { unitId ->
                val members = membersByUnit.getValue(unitId)
                val width = unitWidths.getValue(unitId)
                val center = cursor + width / 2f
                members.forEachIndexed { index, personId ->
                    val offset = (index - (members.size - 1) / 2f) * SPOUSE_GAP
                    positions[personId] = LayoutPoint(
                        x = center + offset,
                        y = generation * GENERATION_GAP,
                    )
                }
                cursor += width + NODE_GAP
            }
        }

        val anchor = myPersonId?.let(positions::get)
        if (anchor != null) {
            positions.replaceAll { _, point -> point - anchor }
        }
        pinnedPositions.forEach { (personId, point) ->
            if (personId in positions) positions[personId] = point
        }

        val routes = buildFamilyRoutes(
            relationships = relationships,
            typeById = typeById,
            positions = positions,
            spouseEdges = spouseEdges,
            siblingEdges = siblingEdges,
            parentChildEdges = parentChildEdges,
            unitByPerson = unitByPerson,
        )
        return GraphLayoutResult(
            positions = positions,
            routes = routes,
            generationByPerson = generationByPerson,
            conflicts = conflicts,
        )
    }

    private fun buildFamilyRoutes(
        relationships: List<RelationshipEntity>,
        typeById: Map<String, RelationTypeEntity>,
        positions: Map<String, LayoutPoint>,
        spouseEdges: List<RelationshipEntity>,
        siblingEdges: List<RelationshipEntity>,
        parentChildEdges: List<Pair<String, String>>,
        unitByPerson: Map<String, String>,
    ): List<RoutedRelationship> {
        val routes = mutableListOf<RoutedRelationship>()
        routes += buildSimpleRoutes(spouseEdges, positions, GraphRouteStyle.SPOUSE)
        routes += buildSimpleRoutes(siblingEdges, positions, GraphRouteStyle.SIBLING)

        val relationshipByPair = relationships
            .filter { isParentChild(typeById[it.relationTypeId]) }
            .associateBy { it.fromPersonId to it.toPersonId }
        parentChildEdges
            .groupBy { (parentId, childId) ->
                childId to (unitByPerson[parentId] ?: parentId)
            }
            .forEach { (key, edges) ->
                val childId = key.first
                val childPoint = positions[childId] ?: return@forEach
                val parentPoints = edges.mapNotNull { positions[it.first] }
                if (parentPoints.isEmpty()) return@forEach
                val parentPoint = LayoutPoint(
                    x = parentPoints.map { it.x }.average().toFloat(),
                    y = parentPoints.map { it.y }.average().toFloat(),
                )
                val busY = parentPoint.y + GENERATION_GAP * 0.44f
                val childEntry = LayoutPoint(childPoint.x, childPoint.y - NODE_RADIUS - 8f)
                val segments = if (abs(parentPoint.x - childPoint.x) < 1f) {
                    listOf(RouteSegment(parentPoint, childEntry))
                } else {
                    listOf(
                        RouteSegment(parentPoint, LayoutPoint(parentPoint.x, busY)),
                        RouteSegment(LayoutPoint(parentPoint.x, busY), LayoutPoint(childPoint.x, busY)),
                        RouteSegment(LayoutPoint(childPoint.x, busY), childEntry),
                    )
                }
                val relationshipIds = edges.mapNotNull { (parentId, child) ->
                    relationshipByPair[parentId to child]?.id
                }
                routes += RoutedRelationship(
                    groupKey = pairKey(parentPoint, childPoint),
                    relationshipIds = relationshipIds,
                    style = GraphRouteStyle.PARENT_CHILD,
                    segments = segments,
                )
            }
        return routes
    }

    private fun layoutSocial(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        myPersonId: String?,
        pinnedPositions: Map<String, LayoutPoint>,
    ): GraphLayoutResult {
        val peopleById = people.associateBy { it.id }
        val adjacency = buildAdjacency(people.map { it.id }, relationships, bidirectionally = true)
        val startId = myPersonId?.takeIf(peopleById::containsKey)
            ?: people.maxByOrNull { adjacency[it.id].orEmpty().size }?.id
            ?: people.first().id
        val positions = mutableMapOf<String, LayoutPoint>()
        val generations = mutableMapOf<String, Int>()
        val visited = mutableSetOf<String>()
        val componentStarts = mutableListOf(startId)
        componentStarts += people.map { it.id }.filterNot { it == startId }.sorted()
        var componentIndex = 0
        componentStarts.forEach { componentStart ->
            if (componentStart in visited) return@forEach
            val componentCenter = if (componentIndex == 0) {
                LayoutPoint.Zero
            } else {
                LayoutPoint(componentIndex * 520f, 0f)
            }
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(componentStart to 0)
            visited += componentStart
            generations[componentStart] = 0
            positions[componentStart] = componentCenter
            val levels = mutableMapOf<Int, MutableList<String>>()
            levels.getOrPut(0) { mutableListOf() } += componentStart
            while (queue.isNotEmpty()) {
                val (current, depth) = queue.removeFirst()
                adjacency[current].orEmpty().sorted().forEach { next ->
                    if (visited.add(next)) {
                        generations[next] = depth + 1
                        levels.getOrPut(depth + 1) { mutableListOf() } += next
                        queue.add(next to (depth + 1))
                    }
                }
            }
            levels.filterKeys { it > 0 }.toSortedMap().forEach { (depth, level) ->
                val radius = 110f + depth * 128f
                level.sorted().forEachIndexed { index, personId ->
                    val angle = -PI / 2.0 + 2.0 * PI * index / level.size
                    positions[personId] = componentCenter + LayoutPoint(
                        x = cos(angle).toFloat() * radius,
                        y = sin(angle).toFloat() * radius,
                    )
                }
            }
            componentIndex++
        }
        pinnedPositions.forEach { (personId, point) ->
            if (personId in positions) positions[personId] = point
        }
        return GraphLayoutResult(
            positions = positions,
            routes = buildSimpleRoutes(relationships, positions, GraphRouteStyle.SOCIAL),
            generationByPerson = generations,
            conflicts = 0,
        )
    }

    private fun layoutAll(
        people: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        typeById: Map<String, RelationTypeEntity>,
        myPersonId: String?,
        pinnedPositions: Map<String, LayoutPoint>,
    ): GraphLayoutResult {
        val familyRelationships = relationships.filter {
            typeById[it.relationTypeId]?.category == RelationCategory.FAMILY
        }
        val familyIds = (familyRelationships.flatMap { listOf(it.fromPersonId, it.toPersonId) } +
            listOfNotNull(myPersonId)).toSet()
        val familyPeople = people.filter { it.id in familyIds }
        val base = if (familyPeople.isNotEmpty()) {
            layoutFamily(
                people = familyPeople,
                relationships = familyRelationships,
                typeById = typeById,
                myPersonId = myPersonId,
                pinnedPositions = pinnedPositions.filterKeys { it in familyIds },
            )
        } else {
            GraphLayoutResult(emptyMap(), emptyList(), emptyMap(), 0)
        }
        val positions = base.positions.toMutableMap()
        val remaining = people.filter { it.id !in positions }
        val socialAdjacency = buildAdjacency(
            people.map { it.id },
            relationships.filter {
                typeById[it.relationTypeId]?.category == RelationCategory.SOCIAL
            },
            bidirectionally = true,
        )
        remaining.forEachIndexed { index, person ->
            val anchor = socialAdjacency[person.id].orEmpty()
                .firstNotNullOfOrNull(positions::get)
            if (anchor != null) {
                val angle = stableAngle(person.id)
                val ring = 120f + (index % 3) * 42f
                positions[person.id] = anchor + LayoutPoint(
                    x = (cos(angle) * ring).toFloat(),
                    y = (sin(angle) * ring).toFloat(),
                )
            } else {
                positions[person.id] = LayoutPoint(-480f, index * 120f)
            }
        }
        pinnedPositions.forEach { (personId, point) ->
            if (personId in positions) positions[personId] = point
        }
        val socialRoutes = buildSimpleRoutes(
            relationships.filter {
                typeById[it.relationTypeId]?.category != RelationCategory.FAMILY
            },
            positions,
            GraphRouteStyle.SOCIAL,
        )
        return GraphLayoutResult(
            positions = positions,
            routes = base.routes + socialRoutes,
            generationByPerson = base.generationByPerson,
            conflicts = base.conflicts,
        )
    }

    private fun buildAdjacency(
        personIds: List<String>,
        relationships: List<RelationshipEntity>,
        bidirectionally: Boolean,
    ): Map<String, Set<String>> {
        val adjacency = personIds.associateWith { mutableSetOf<String>() }.toMutableMap()
        relationships.forEach { relationship ->
            adjacency[relationship.fromPersonId]?.add(relationship.toPersonId)
            if (bidirectionally) {
                adjacency[relationship.toPersonId]?.add(relationship.fromPersonId)
            }
        }
        return adjacency
    }

    private fun buildSimpleRoutes(
        relationships: List<RelationshipEntity>,
        positions: Map<String, LayoutPoint>,
        style: GraphRouteStyle,
    ): List<RoutedRelationship> = relationships
        .groupBy { pairKey(it.fromPersonId, it.toPersonId) }
        .mapNotNull { (groupKey, relationshipsForPair) ->
            val first = relationshipsForPair.first()
            val start = positions[first.fromPersonId] ?: return@mapNotNull null
            val end = positions[first.toPersonId] ?: return@mapNotNull null
            RoutedRelationship(
                groupKey = groupKey,
                relationshipIds = relationshipsForPair.map { it.id },
                style = style,
                segments = listOf(RouteSegment(start, end)),
            )
        }

    private fun isParentChild(type: RelationTypeEntity?): Boolean =
        type?.id == "preset_parent_child" ||
            (
                type?.category == RelationCategory.FAMILY &&
                    type.direction == RelationDirection.DIRECTED &&
                    !type.inverseName.isNullOrBlank()
                )

    private fun isSpouse(type: RelationTypeEntity?): Boolean = type?.id == "preset_spouse"

    private fun isSibling(type: RelationTypeEntity?): Boolean = type?.id == "preset_sibling"

    private fun pairKey(firstPersonId: String, secondPersonId: String): String {
        val pair = listOf(firstPersonId, secondPersonId).sorted()
        return pair[0] + "\u0000" + pair[1]
    }

    private fun pairKey(first: LayoutPoint, second: LayoutPoint): String {
        val firstValue = "${first.x}:${first.y}"
        val secondValue = "${second.x}:${second.y}"
        return listOf(firstValue, secondValue).sorted().joinToString("\u0000")
    }

    private fun stableAngle(value: String): Double {
        val normalized = (value.hashCode().toLong() and 0xffffffffL)
        return normalized / 0xffffffff.toDouble() * 2.0 * PI
    }
}

private class DisjointSet(values: List<String>) {
    private val parent = values.associateWith { it }.toMutableMap()
    private val rank = values.associateWith { 0 }.toMutableMap()

    fun find(value: String): String {
        val current = parent.getValue(value)
        if (current == value) return value
        val root = find(current)
        parent[value] = root
        return root
    }

    fun union(first: String, second: String) {
        val firstRoot = find(first)
        val secondRoot = find(second)
        if (firstRoot == secondRoot) return
        when {
            rank.getValue(firstRoot) < rank.getValue(secondRoot) -> parent[firstRoot] = secondRoot
            rank.getValue(firstRoot) > rank.getValue(secondRoot) -> parent[secondRoot] = firstRoot
            else -> {
                parent[secondRoot] = firstRoot
                rank[firstRoot] = rank.getValue(firstRoot) + 1
            }
        }
    }
}

private fun LayoutPoint.length(): Float =
    kotlin.math.sqrt(x * x + y * y)

private fun Collection<Float>.averageOrFallback(fallback: Float): Float =
    if (isEmpty()) fallback else average().toFloat()
