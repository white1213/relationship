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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.GraphPositionEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import kotlin.math.max
import kotlin.math.min

data class GraphEdgeGroup(
    val key: String,
    val firstPersonId: String,
    val secondPersonId: String,
    val relationships: List<RelationshipEntity>,
    val relationTypes: List<RelationTypeEntity>,
)

private data class Viewport(
    val pan: Offset = Offset.Zero,
    val zoom: Float = 0.8f,
)

@Composable
fun GraphCanvas(
    people: List<PersonEntity>,
    edgeGroups: List<GraphEdgeGroup>,
    mode: GraphMode,
    myPersonId: String?,
    graphPositions: List<GraphPositionEntity>,
    highlightedPersonIds: Set<String>?,
    highlightedEdgeKeys: Set<String>?,
    onPersonClick: (String) -> Unit,
    onEdgeAction: (GraphEdgeGroup) -> Unit,
    onPersonMoved: (personId: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val relationships = remember(edgeGroups) { edgeGroups.flatMap { it.relationships } }
    val relationTypes = remember(edgeGroups) {
        edgeGroups.flatMap { it.relationTypes }.distinctBy { it.id }
    }
    val pinnedPositions = remember(graphPositions, mode) {
        graphPositions
            .filter { it.mode == mode && it.isManuallyPinned }
            .associate { it.personId to LayoutPoint(it.x, it.y) }
    }
    val layout = remember(
        people,
        relationships,
        relationTypes,
        mode,
        myPersonId,
        pinnedPositions,
    ) {
        GraphLayoutEngine.layout(
            people = people,
            relationships = relationships,
            relationTypes = relationTypes,
            mode = mode,
            myPersonId = myPersonId,
            pinnedPositions = pinnedPositions,
        )
    }

    val nodePositions = remember { mutableStateMapOf<String, Offset>() }
    val relationshipToGroup = remember(edgeGroups) {
        edgeGroups.flatMap { group -> group.relationships.map { it.id to group } }.toMap()
    }
    val viewportsByMode = remember { mutableStateMapOf<GraphMode, Viewport>() }
    var viewport by remember { mutableStateOf(Viewport()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(layout, mode, canvasSize) {
        nodePositions.clear()
        layout.positions.forEach { (personId, point) ->
            nodePositions[personId] = Offset(point.x, point.y)
        }
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            viewport = viewportsByMode[mode] ?: fitViewport(layout.positions, canvasSize).also {
                viewportsByMode[mode] = it
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(people.map { it.id }, edgeGroups.map { it.key }, mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis
                    var activeNodeId = hitTestNode(
                        click = down.position,
                        positions = nodePositions,
                        viewport = viewport,
                        canvasSize = size,
                    )
                    val activeRoute = if (activeNodeId == null) {
                        hitTestRoute(
                            click = down.position,
                            routes = layout.routes,
                            viewport = viewport,
                            canvasSize = size,
                        )
                    } else {
                        null
                    }
                    val activeEdgeGroup = activeRoute
                        ?.relationshipIds
                        ?.firstOrNull()
                        ?.let(relationshipToGroup::get)
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
                            if (lastDistance > 0f && distance > 0f) {
                                val oldZoom = viewport.zoom
                                val newZoom = (oldZoom * distance / lastDistance).coerceIn(0.2f, 3f)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val relative = centroid - center - viewport.pan
                                val newPan = centroid - center - relative * (newZoom / oldZoom)
                                viewport = viewport.copy(pan = newPan, zoom = newZoom)
                            }
                            viewport = viewport.copy(pan = viewport.pan + centroid - lastCentroid)
                            viewportsByMode[mode] = viewport
                            lastCentroid = centroid
                            lastDistance = distance
                            pressed.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            val delta = change.position - change.previousPosition
                            if (delta.getDistance() > 0.5f) moved = true

                            if (activeNodeId != null) {
                                val current = nodePositions[activeNodeId] ?: Offset.Zero
                                nodePositions[activeNodeId!!] = current + delta / viewport.zoom
                                draggedNodeId = activeNodeId
                            } else {
                                viewport = viewport.copy(pan = viewport.pan + delta)
                                viewportsByMode[mode] = viewport
                            }

                            if (activeEdgeGroup != null &&
                                !moved &&
                                !longPressHandled &&
                                change.uptimeMillis - startTime >= 500L
                            ) {
                                longPressHandled = true
                                onEdgeAction(activeEdgeGroup)
                            }
                            change.consume()
                        }
                    }

                    if (!moved && !longPressHandled) {
                        when {
                            activeNodeId != null -> onPersonClick(activeNodeId!!)
                            activeEdgeGroup != null -> onEdgeAction(activeEdgeGroup)
                        }
                    }
                    draggedNodeId?.let { personId ->
                        nodePositions[personId]?.let { point ->
                            onPersonMoved(personId, point.x, point.y)
                        }
                    }
                    draggedNodeId = null
                }
            },
    ) {
        val primary = Color(0xFF3F7FDD)
        val spouse = Color(0xFFD35F78)
        val sibling = Color(0xFF5A8FD6)
        val social = Color(0xFF7A8796)
        val muted = Color(0xFFB7C6D9)
        val labelText = Color(0xFF52647B)
        val center = Offset(size.width / 2f, size.height / 2f)

        withTransform({
            translate(center.x + viewport.pan.x, center.y + viewport.pan.y)
            scale(viewport.zoom, viewport.zoom, pivot = Offset.Zero)
        }) {
            layout.routes.forEach { route ->
                val isHighlighted = highlightedEdgeKeys == null ||
                    route.relationshipIds.any { relationshipToGroup[it]?.key in highlightedEdgeKeys }
                val routeColor = when (route.style) {
                    GraphRouteStyle.PARENT_CHILD -> primary
                    GraphRouteStyle.SPOUSE -> spouse
                    GraphRouteStyle.SIBLING -> sibling
                    GraphRouteStyle.SOCIAL -> social
                }.copy(alpha = if (isHighlighted) 0.78f else 0.22f)
                val strokeWidth = if (isHighlighted) 2.8f else 1.7f
                val pathEffect = when (route.style) {
                    GraphRouteStyle.SIBLING,
                    GraphRouteStyle.SOCIAL,
                    -> PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    else -> null
                }

                route.segments.forEachIndexed { index, segment ->
                    val start = segment.start.toOffset()
                    val end = segment.end.toOffset()
                    if (route.style == GraphRouteStyle.SPOUSE) {
                        val direction = end - start
                        val distance = max(1f, direction.getDistance())
                        val perpendicular = Offset(-direction.y / distance, direction.x / distance) * 2.5f
                        drawLine(routeColor, start + perpendicular, end + perpendicular, strokeWidth)
                        drawLine(routeColor, start - perpendicular, end - perpendicular, strokeWidth)
                    } else {
                        drawLine(
                            color = routeColor,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect,
                        )
                    }
                    if (route.style == GraphRouteStyle.PARENT_CHILD && index == route.segments.lastIndex) {
                        drawArrowHead(start = start, end = end, color = routeColor)
                    }
                }

                if (isHighlighted && viewport.zoom >= LABEL_REVEAL_ZOOM && route.relationshipIds.isNotEmpty()) {
                    val label = route.relationshipIds
                        .mapNotNull { relationshipToGroup[it] }
                        .flatMap { it.relationTypes }
                        .distinctBy { it.id }
                        .joinToString("/") { it.name }
                    if (label.isNotBlank()) {
                        drawRouteLabel(
                            text = label,
                            point = route.labelPoint.toOffset(),
                            color = labelText,
                            textMeasurer = textMeasurer,
                        )
                    }
                }
            }

            people.forEach { person ->
                val position = nodePositions[person.id] ?: return@forEach
                val isHighlighted = highlightedPersonIds == null || person.id in highlightedPersonIds
                val isDragged = draggedNodeId == person.id
                val isMyPerson = person.id == myPersonId
                val radius = if (isDragged) 31f else 28f
                val fill = when {
                    !isHighlighted -> muted.copy(alpha = 0.32f)
                    isDragged -> Color(0xFF275EAD)
                    isMyPerson -> Color(0xFF244D86)
                    else -> primary
                }
                if (isDragged || isMyPerson) {
                    drawCircle(
                        color = primary.copy(alpha = if (isMyPerson) 0.2f else 0.16f),
                        radius = radius + 8f,
                        center = position,
                    )
                }
                drawCircle(color = Color.White, radius = radius + 2.5f, center = position)
                drawCircle(color = fill, radius = radius, center = position)
                drawCircle(
                    color = Color.White.copy(alpha = if (isHighlighted) 0.24f else 0.06f),
                    radius = radius,
                    center = position,
                    style = Stroke(width = if (isMyPerson) 2.6f else 1.2f),
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

private fun LayoutPoint.toOffset(): Offset = Offset(x, y)

private fun fitViewport(
    positions: Map<String, LayoutPoint>,
    canvasSize: IntSize,
): Viewport {
    if (positions.isEmpty()) return Viewport()
    val minimumX = positions.values.minOf { it.x }
    val maximumX = positions.values.maxOf { it.x }
    val minimumY = positions.values.minOf { it.y }
    val maximumY = positions.values.maxOf { it.y }
    val width = max(260f, maximumX - minimumX + 180f)
    val height = max(260f, maximumY - minimumY + 180f)
    val zoom = min(canvasSize.width / width, canvasSize.height / height).coerceIn(0.2f, 1.15f)
    val worldCenter = LayoutPoint((minimumX + maximumX) / 2f, (minimumY + maximumY) / 2f)
    return Viewport(
        pan = Offset(-worldCenter.x * zoom, -worldCenter.y * zoom),
        zoom = zoom,
    )
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRouteLabel(
    text: String,
    point: Offset,
    color: Color,
    textMeasurer: TextMeasurer,
) {
    val measured = textMeasurer.measure(
        AnnotatedString(text),
        style = TextStyle(
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    val paddingX = 7f
    val paddingY = 4f
    val topLeft = Offset(
        point.x - measured.size.width / 2f - paddingX,
        point.y - measured.size.height / 2f - paddingY,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.94f),
        topLeft = topLeft,
        size = Size(
            measured.size.width + paddingX * 2,
            measured.size.height + paddingY * 2,
        ),
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawText(measured, topLeft = topLeft + Offset(paddingX, paddingY))
}

private fun hitTestNode(
    click: Offset,
    positions: Map<String, Offset>,
    viewport: Viewport,
    canvasSize: IntSize,
): String? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val world = (click - center - viewport.pan) / viewport.zoom
    return positions.minByOrNull { (_, point) -> (point - world).getDistance() }
        ?.takeIf { (_, point) -> (point - world).getDistance() <= 36f }
        ?.key
}

private fun hitTestRoute(
    click: Offset,
    routes: List<RoutedRelationship>,
    viewport: Viewport,
    canvasSize: IntSize,
): RoutedRelationship? {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val world = (click - center - viewport.pan) / viewport.zoom
    return routes.minByOrNull { route ->
        route.segments.minOfOrNull {
            distanceToSegment(world, it.start.toOffset(), it.end.toOffset())
        } ?: Float.MAX_VALUE
    }?.takeIf { route ->
        val distance = route.segments.minOfOrNull {
            distanceToSegment(world, it.start.toOffset(), it.end.toOffset())
        } ?: Float.MAX_VALUE
        distance <= max(14f, 20f / viewport.zoom)
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

private const val LABEL_REVEAL_ZOOM = 1.15f
