package com.relationship.graph.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationDirection
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class GraphEdgeGroup(
    val key: String,
    val firstPersonId: String,
    val secondPersonId: String,
    val relationships: List<RelationshipEntity>,
    val relationTypes: List<RelationTypeEntity>,
)

@Composable
fun GraphCanvas(
    people: List<PersonEntity>,
    edgeGroups: List<GraphEdgeGroup>,
    highlightedPersonIds: Set<String>?,
    highlightedEdgeKeys: Set<String>?,
    onPersonClick: (String) -> Unit,
    onEdgeAction: (GraphEdgeGroup) -> Unit,
    onPositionsCommitted: (Map<String, Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positions = remember { mutableStateMapOf<String, Offset>() }
    val viewportPan = remember { mutableStateOf(Offset.Zero) }
    var viewportZoom by remember { mutableFloatStateOf(0.75f) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(people.map { it.id }, edgeGroups.map { it.key }) {
        val missing = people.filter { it.id !in positions }
        if (positions.isEmpty() || missing.isNotEmpty()) {
            val initial = if (positions.isEmpty() && people.any { it.positionInitialized }) {
                people.associate { it.id to Offset(it.graphX, it.graphY) }
            } else {
                positions.toMap()
            }
            val layout = computeGraphLayout(
                people = people,
                edgeGroups = edgeGroups,
                existing = initial,
            )
            positions.putAll(layout)
            onPositionsCommitted(
                layout.mapValues { (_, point) -> point.x to point.y },
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(people.map { it.id }, edgeGroups.map { it.key }) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis
                    var activeNodeId = hitTestNode(
                        click = down.position,
                        positions = positions,
                        viewportPan = viewportPan.value,
                        zoom = viewportZoom,
                        canvasSize = size,
                    )
                    val activeEdge = if (activeNodeId == null) {
                        hitTestEdge(
                            click = down.position,
                            edgeGroups = edgeGroups,
                            positions = positions,
                            viewportPan = viewportPan.value,
                            zoom = viewportZoom,
                            canvasSize = size,
                        )
                    } else {
                        null
                    }
                    var moved = false
                    var longPressHandled = false
                    var lastCentroid = down.position
                    var lastDistance = 0f
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            activeNodeId = null
                            moved = true
                            val first = pressed[0].position
                            val second = pressed[1].position
                            val centroid = (first + second) / 2f
                            val distance = (first - second).getDistance()
                            val oldZoom = viewportZoom
                            if (lastDistance > 0f && distance > 0f) {
                                val newZoom = (oldZoom * distance / lastDistance).coerceIn(0.25f, 3f)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val relative = centroid - center - viewportPan.value
                                viewportPan.value = centroid - center - relative * (newZoom / oldZoom)
                                viewportZoom = newZoom
                            }
                            viewportPan.value += centroid - lastCentroid
                            lastCentroid = centroid
                            lastDistance = distance
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            if (delta.getDistance() > 0.5f) moved = true

                            if (activeNodeId != null) {
                                val current = positions[activeNodeId] ?: Offset.Zero
                                positions[activeNodeId!!] = current + delta / viewportZoom
                                draggedNodeId = activeNodeId
                            } else {
                                viewportPan.value += delta
                            }

                            if (activeEdge != null &&
                                !moved &&
                                !longPressHandled &&
                                change.uptimeMillis - startTime >= 500L
                            ) {
                                longPressHandled = true
                                onEdgeAction(activeEdge)
                            }
                            change.consume()
                        }
                    }

                    if (!moved && !longPressHandled) {
                        when {
                            activeNodeId != null -> onPersonClick(activeNodeId!!)
                            activeEdge != null -> onEdgeAction(activeEdge)
                        }
                    }
                    draggedNodeId?.let { id ->
                        positions[id]?.let { point ->
                            onPositionsCommitted(mapOf(id to (point.x to point.y)))
                        }
                    }
                    draggedNodeId = null
                }
            },
    ) {
        val panelColor = Color(0xFFD6E2F0)
        val primary = Color(0xFF3F7FDD)
        val muted = Color(0xFFB7C6D9)
        val labelText = Color(0xFF52647B)
        val center = Offset(size.width / 2f, size.height / 2f)

        withTransform({
            translate(center.x + viewportPan.value.x, center.y + viewportPan.value.y)
            scale(viewportZoom, viewportZoom, pivot = Offset.Zero)
        }) {
            edgeGroups.forEach { group ->
                val start = positions[group.firstPersonId] ?: return@forEach
                val end = positions[group.secondPersonId] ?: return@forEach
                val isHighlighted = highlightedEdgeKeys == null || group.key in highlightedEdgeKeys
                val edgeColor = if (isHighlighted) primary.copy(alpha = 0.72f) else muted.copy(alpha = 0.22f)
                drawLine(
                    color = edgeColor,
                    start = start,
                    end = end,
                    strokeWidth = if (isHighlighted) 3.2f else 2f,
                )

                val hasDirected = group.relationTypes.any {
                    it.direction == RelationDirection.DIRECTED
                }
                if (hasDirected) {
                    drawArrowHead(start = start, end = end, color = edgeColor)
                }

                val label = group.relationTypes.map { it.name }.distinct().joinToString("/")
                if (label.isNotBlank() && isHighlighted) {
                    val measured = textMeasurer.measure(
                        AnnotatedString(label),
                        style = TextStyle(
                            color = labelText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    val midpoint = (start + end) / 2f
                    val paddingX = 7f
                    val paddingY = 4f
                    val topLeft = Offset(
                        midpoint.x - measured.size.width / 2f - paddingX,
                        midpoint.y - measured.size.height / 2f - paddingY,
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.94f),
                        topLeft = topLeft,
                        size = Size(
                            measured.size.width + paddingX * 2,
                            measured.size.height + paddingY * 2,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                    )
                    drawText(measured, topLeft = topLeft + Offset(paddingX, paddingY))
                }
            }

            people.forEach { person ->
                val position = positions[person.id] ?: return@forEach
                val isHighlighted = highlightedPersonIds == null || person.id in highlightedPersonIds
                val isDragged = draggedNodeId == person.id
                val radius = if (isDragged) 31f else 28f
                val fill = when {
                    !isHighlighted -> muted.copy(alpha = 0.32f)
                    isDragged -> Color(0xFF275EAD)
                    else -> primary
                }
                if (isDragged) {
                    drawCircle(
                        color = primary.copy(alpha = 0.16f),
                        radius = radius + 8f,
                        center = position,
                    )
                }
                drawCircle(color = Color.White, radius = radius + 2.5f, center = position)
                drawCircle(color = fill, radius = radius, center = position)
                drawCircle(
                    color = Color.White.copy(alpha = if (isHighlighted) 0.18f else 0.06f),
                    radius = radius,
                    center = position,
                    style = Stroke(width = 1.2f),
                )

                val initial = person.name.trim().take(1).ifBlank { "?" }
                val measured = textMeasurer.measure(
                    AnnotatedString(initial),
                    style = TextStyle(
                        color = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        position.x - measured.size.width / 2f,
                        position.y - measured.size.height / 2f,
                    ),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    start: Offset,
    end: Offset,
    color: Color,
) {
    val direction = end - start
    val distance = direction.getDistance()
    if (distance < 1f) return
    val unit = direction / distance
    val tip = end - unit * 31f
    val perpendicular = Offset(-unit.y, unit.x)
    val base = tip - unit * 11f
    drawLine(color, base + perpendicular * 6f, tip, strokeWidth = 3f)
    drawLine(color, base - perpendicular * 6f, tip, strokeWidth = 3f)
}

private fun hitTestNode(
    click: Offset,
    positions: Map<String, Offset>,
    viewportPan: Offset,
    zoom: Float,
    canvasSize: IntSize,
): String? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val world = (click - center - viewportPan) / zoom
    return positions.minByOrNull { (_, point) -> (point - world).getDistance() }
        ?.takeIf { (_, point) -> (point - world).getDistance() <= 36f }
        ?.key
}

private fun hitTestEdge(
    click: Offset,
    edgeGroups: List<GraphEdgeGroup>,
    positions: Map<String, Offset>,
    viewportPan: Offset,
    zoom: Float,
    canvasSize: IntSize,
): GraphEdgeGroup? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val world = (click - center - viewportPan) / zoom
    return edgeGroups.minByOrNull { group ->
        val start = positions[group.firstPersonId] ?: return@minByOrNull Float.MAX_VALUE
        val end = positions[group.secondPersonId] ?: return@minByOrNull Float.MAX_VALUE
        distanceToSegment(world, start, end)
    }?.takeIf { group ->
        val start = positions[group.firstPersonId] ?: return@takeIf false
        val end = positions[group.secondPersonId] ?: return@takeIf false
        distanceToSegment(world, start, end) <= max(14f, 20f / zoom)
    }
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val segment = end - start
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared == 0f) return (point - start).getDistance()
    val projection = (
        (point.x - start.x) * segment.x + (point.y - start.y) * segment.y
        ) / lengthSquared
    val clamped = projection.coerceIn(0f, 1f)
    val nearest = start + segment * clamped
    return (point - nearest).getDistance()
}

internal fun computeGraphLayout(
    people: List<PersonEntity>,
    edgeGroups: List<GraphEdgeGroup>,
    existing: Map<String, Offset>,
): Map<String, Offset> {
    if (people.isEmpty()) return emptyMap()

    val hasSavedLayout = people.count(PersonEntity::positionInitialized) >= max(1, people.size / 2)
    val result = existing.toMutableMap()
    if (!hasSavedLayout) {
        val radius = min(420f, max(150f, people.size * 11f))
        people.forEachIndexed { index, person ->
            val angle = (2.0 * PI * index / people.size).toFloat()
            result[person.id] = Offset(cos(angle) * radius, sin(angle) * radius)
        }
        simulateForces(people, edgeGroups, result)
    } else {
        people.filter { it.id !in result }.forEachIndexed { index, person ->
            val related = edgeGroups.firstOrNull {
                it.firstPersonId == person.id || it.secondPersonId == person.id
            }
            val neighborId = when (related?.firstPersonId) {
                person.id -> related?.secondPersonId
                else -> related?.firstPersonId
            }
            val neighbor = neighborId?.let(result::get)
            val angle = index * 1.7f
            result[person.id] = neighbor?.plus(Offset(cos(angle) * 130f, sin(angle) * 130f))
                ?: Offset(cos(angle) * 260f, sin(angle) * 260f)
        }
    }
    return result
}

private fun simulateForces(
    people: List<PersonEntity>,
    edgeGroups: List<GraphEdgeGroup>,
    positions: MutableMap<String, Offset>,
) {
    val count = people.size
    if (count <= 1) {
        people.firstOrNull()?.let { positions[it.id] = Offset.Zero }
        return
    }
    val area = 900f * 900f
    val idealDistance = kotlin.math.sqrt(area / count)
    val displacement = people.associate { it.id to Offset.Zero }.toMutableMap()

    repeat(140) { iteration ->
        people.forEach { person ->
            var force = Offset.Zero
            val point = positions[person.id] ?: Offset.Zero
            people.forEach { other ->
                if (other.id == person.id) return@forEach
                val otherPoint = positions[other.id] ?: Offset.Zero
                val delta = point - otherPoint
                val distance = max(1f, delta.getDistance())
                force += delta / distance * (idealDistance * idealDistance / distance)
            }
            edgeGroups.forEach { edge ->
                val otherId = when (person.id) {
                    edge.firstPersonId -> edge.secondPersonId
                    edge.secondPersonId -> edge.firstPersonId
                    else -> null
                }
                if (otherId != null) {
                    val otherPoint = positions[otherId] ?: Offset.Zero
                    val delta = point - otherPoint
                    val distance = max(1f, delta.getDistance())
                    force -= delta / distance * (distance * distance / idealDistance)
                }
            }
            displacement[person.id] = force
        }

        val temperature = idealDistance * (1f - iteration / 140f) * 0.08f
        people.forEach { person ->
            val point = positions[person.id] ?: Offset.Zero
            val force = displacement[person.id] ?: Offset.Zero
            val length = max(1f, force.getDistance())
            val step = force / length * min(length, temperature)
            val next = point + step
            positions[person.id] = Offset(
                next.x.coerceIn(-650f, 650f),
                next.y.coerceIn(-650f, 650f),
            )
        }
    }
}

fun buildEdgeGroups(
    relationships: List<RelationshipEntity>,
    relationTypes: List<RelationTypeEntity>,
): List<GraphEdgeGroup> {
    val typeById = relationTypes.associateBy { it.id }
    return relationships
        .groupBy { relationship ->
            val pair = listOf(relationship.fromPersonId, relationship.toPersonId).sorted()
            pair[0] + "\u0000" + pair[1]
        }
        .map { (key, groupedRelationships) ->
            val pair = key.split("\u0000")
            GraphEdgeGroup(
                key = key,
                firstPersonId = pair[0],
                secondPersonId = pair[1],
                relationships = groupedRelationships,
                relationTypes = groupedRelationships.mapNotNull { typeById[it.relationTypeId] }.distinct(),
            )
        }
}
