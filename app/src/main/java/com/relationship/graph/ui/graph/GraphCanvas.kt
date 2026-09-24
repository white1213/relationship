package com.relationship.graph.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.relationship.graph.data.local.RelationCategory
import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.local.GraphPositionEntity
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelationshipEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

private enum class NodeCategory {
    FAMILY,
    SOCIAL,
    MIXED,
    NONE,
}

@Composable
fun GraphCanvas(
    people: List<PersonEntity>,
    edgeGroups: List<GraphEdgeGroup>,
    mode: GraphMode,
    myPersonId: String?,
    graphPositions: List<GraphPositionEntity>,
    highlightedPersonIds: Set<String>?,
    highlightedEdgeKeys: Set<String>?,
    selectedPersonId: String?,
    onPersonSelected: (String) -> Unit,
    onBackgroundClick: () -> Unit,
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
    val nodeCategoryByPerson = remember(relationships, relationTypes) {
        val categoryByType = relationTypes.associate { it.id to it.category }
        val familyPeople = mutableSetOf<String>()
        val socialPeople = mutableSetOf<String>()
        relationships.forEach { relationship ->
            when (categoryByType[relationship.relationTypeId]) {
                RelationCategory.FAMILY -> {
                    familyPeople += relationship.fromPersonId
                    familyPeople += relationship.toPersonId
                }
                RelationCategory.SOCIAL -> {
                    socialPeople += relationship.fromPersonId
                    socialPeople += relationship.toPersonId
                }
                else -> Unit
            }
        }
        people.associate { person ->
            person.id to when {
                person.id in familyPeople && person.id in socialPeople -> NodeCategory.MIXED
                person.id in familyPeople -> NodeCategory.FAMILY
                person.id in socialPeople -> NodeCategory.SOCIAL
                else -> NodeCategory.NONE
            }
        }
    }
    val selectedRelationshipIds = remember(selectedPersonId, relationships) {
        selectedPersonId?.let { personId ->
            relationships
                .filter { it.fromPersonId == personId || it.toPersonId == personId }
                .map { it.id }
                .toSet()
        }.orEmpty()
    }
    val avatarImages = rememberAvatarImages(people)
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
                            activeNodeId != null -> onPersonSelected(activeNodeId!!)
                            activeEdgeGroup != null -> onEdgeAction(activeEdgeGroup)
                            else -> onBackgroundClick()
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
        val parentColor = Color(0xFF3F7FDD)
        val familyColor = Color(0xFF3F7FDD)
        val socialColor = Color(0xFF2D8C7F)
        val spouseColor = Color(0xFFD35F78)
        val siblingColor = Color(0xFF5A8FD6)
        val socialRouteColor = Color(0xFF7A8796)
        val neutralColor = Color(0xFF8090A5)
        val muted = Color(0xFFB7C6D9)
        val labelText = Color(0xFF52647B)
        val center = Offset(size.width / 2f, size.height / 2f)
        val occupiedLabels = mutableListOf<Rect>()

        withTransform({
            translate(center.x + viewport.pan.x, center.y + viewport.pan.y)
            scale(viewport.zoom, viewport.zoom, pivot = Offset.Zero)
        }) {
            layout.routes.forEach { route ->
                val isHighlighted = highlightedEdgeKeys == null ||
                    route.relationshipIds.any { relationshipToGroup[it]?.key in highlightedEdgeKeys }
                val routeColor = when (route.style) {
                    GraphRouteStyle.PARENT_CHILD -> parentColor
                    GraphRouteStyle.SPOUSE -> spouseColor
                    GraphRouteStyle.SIBLING -> siblingColor
                    GraphRouteStyle.SOCIAL -> socialRouteColor
                }.copy(alpha = if (isHighlighted) 0.82f else 0.2f)
                val strokeWidth = if (isHighlighted) 2.8f else 1.6f
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
            }

            people.forEach { person ->
                val position = nodePositions[person.id] ?: return@forEach
                val isHighlighted = highlightedPersonIds == null || person.id in highlightedPersonIds
                val isDragged = draggedNodeId == person.id
                val isSelected = person.id == selectedPersonId
                val isMyPerson = person.id == myPersonId
                val category = nodeCategoryByPerson[person.id] ?: NodeCategory.NONE
                val radius = if (isDragged) 31f else 28f
                val categoryColor = when (category) {
                    NodeCategory.FAMILY -> familyColor
                    NodeCategory.SOCIAL -> socialColor
                    NodeCategory.MIXED -> familyColor
                    NodeCategory.NONE -> neutralColor
                }
                val fill = when {
                    !isHighlighted -> muted.copy(alpha = 0.3f)
                    isDragged -> Color(0xFF275EAD)
                    isMyPerson -> Color(0xFF244D86)
                    else -> categoryColor
                }
                if (isDragged || isMyPerson || isSelected) {
                    drawCircle(
                        color = categoryColor.copy(alpha = if (isSelected) 0.24f else 0.16f),
                        radius = radius + if (isSelected) 10f else 8f,
                        center = position,
                    )
                }
                drawCircle(color = Color.White, radius = radius + 2.5f, center = position)
                val avatar = avatarImages[person.id]
                if (avatar != null) {
                    val avatarRect = Rect(
                        left = position.x - radius,
                        top = position.y - radius,
                        right = position.x + radius,
                        bottom = position.y + radius,
                    )
                    clipPath(Path().apply { addOval(avatarRect) }) {
                        drawImage(
                            image = avatar,
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (position.x - radius).roundToInt(),
                                (position.y - radius).roundToInt(),
                            ),
                            dstSize = IntSize(
                                (radius * 2f).roundToInt(),
                                (radius * 2f).roundToInt(),
                            ),
                        )
                    }
                    if (!isHighlighted) {
                        drawCircle(color = muted.copy(alpha = 0.58f), radius = radius, center = position)
                    }
                } else {
                    drawCircle(color = fill, radius = radius, center = position)
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

                when (category) {
                    NodeCategory.MIXED -> {
                        drawArc(
                            color = familyColor,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(position.x - radius, position.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 4f),
                        )
                        drawArc(
                            color = socialColor,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(position.x - radius, position.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 4f),
                        )
                    }
                    NodeCategory.FAMILY,
                    NodeCategory.SOCIAL,
                    NodeCategory.NONE,
                    -> drawCircle(
                        color = Color.White.copy(alpha = if (isHighlighted) 0.26f else 0.07f),
                        radius = radius,
                        center = position,
                        style = Stroke(width = if (isMyPerson || isSelected) 2.6f else 1.2f),
                    )
                }

                if (isMyPerson) {
                    val badgeCenter = position + Offset(radius * 0.78f, radius * 0.78f)
                    drawCircle(color = Color.White, radius = 11f, center = badgeCenter)
                    drawCircle(color = Color(0xFFF0B84A), radius = 9f, center = badgeCenter)
                    val badge = textMeasurer.measure(
                        AnnotatedString("我"),
                        style = TextStyle(
                            color = Color(0xFF513600),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    drawText(
                        textLayoutResult = badge,
                        topLeft = Offset(
                            badgeCenter.x - badge.size.width / 2f,
                            badgeCenter.y - badge.size.height / 2f,
                        ),
                    )
                }
            }
        }

        val screenPositions = nodePositions.mapValues { (_, point) ->
            center + viewport.pan + point * viewport.zoom
        }
        val prioritisedPeople = people.sortedWith(
            compareByDescending<PersonEntity> {
                it.id == selectedPersonId || it.id == myPersonId
            }.thenBy { it.name },
        )
        prioritisedPeople.forEach { person ->
            val isMyPerson = person.id == myPersonId
            val isSelected = person.id == selectedPersonId
            val showName = isMyPerson || isSelected || viewport.zoom >= NAME_REVEAL_ZOOM
            if (!showName) return@forEach
            val position = screenPositions[person.id] ?: return@forEach
            val measuredName = textMeasurer.measure(
                AnnotatedString(person.name),
                style = TextStyle(
                    color = labelText,
                    fontSize = 11.sp,
                    fontWeight = if (isMyPerson || isSelected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
            val paddingX = 6f
            val paddingY = 3f
            val labelWidth = measuredName.size.width + paddingX * 2
            val labelHeight = measuredName.size.height + paddingY * 2
            val preferredLeft = position.x - labelWidth / 2f
            val left = preferredLeft.coerceIn(4f, max(4f, size.width - labelWidth - 4f))
            val top = position.y + 31f * viewport.zoom
            val rect = Rect(left, top, left + labelWidth, top + labelHeight)
            val hasCollision = occupiedLabels.any { it.overlaps(rect) }
            if (hasCollision && !isMyPerson && !isSelected) return@forEach
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = Size(labelWidth, labelHeight),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawText(
                textLayoutResult = measuredName,
                topLeft = Offset(left + paddingX, top + paddingY),
            )
            occupiedLabels += rect

            if (isSelected || viewport.zoom >= DETAIL_REVEAL_ZOOM) {
                val relationshipCount = relationships.count {
                    it.fromPersonId == person.id || it.toPersonId == person.id
                }
                val measuredCount = textMeasurer.measure(
                    AnnotatedString("$relationshipCount 条关系"),
                    style = TextStyle(
                        color = Color(0xFF60748D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val countLeft = (left + labelWidth - measuredCount.size.width - 8f)
                    .coerceAtLeast(4f)
                val countTop = top + labelHeight + 1f
                drawText(
                    textLayoutResult = measuredCount,
                    topLeft = Offset(countLeft, countTop),
                )
                occupiedLabels += Rect(
                    countLeft,
                    countTop,
                    countLeft + measuredCount.size.width,
                    countTop + measuredCount.size.height,
                )
            }
        }

        layout.routes
            .sortedByDescending { route ->
                route.relationshipIds.any { it in selectedRelationshipIds }
            }
            .forEach { route ->
                val isHighlighted = highlightedEdgeKeys == null ||
                    route.relationshipIds.any { relationshipToGroup[it]?.key in highlightedEdgeKeys }
                val isDirectlySelected = route.relationshipIds.any { it in selectedRelationshipIds }
                if (!isHighlighted || (!isDirectlySelected && viewport.zoom < LABEL_REVEAL_ZOOM)) {
                    return@forEach
                }
                val label = route.relationshipIds
                    .mapNotNull { relationshipToGroup[it] }
                    .flatMap { it.relationTypes }
                    .distinctBy { it.id }
                    .joinToString("/") { it.name }
                if (label.isBlank()) return@forEach
                val point = center + viewport.pan + route.labelPoint.toOffset() * viewport.zoom
                val measured = textMeasurer.measure(
                    AnnotatedString(label),
                    style = TextStyle(
                        color = labelText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                val paddingX = 7f
                val paddingY = 4f
                val labelWidth = measured.size.width + paddingX * 2
                val labelHeight = measured.size.height + paddingY * 2
                val left = (point.x - labelWidth / 2f)
                    .coerceIn(4f, max(4f, size.width - labelWidth - 4f))
                val top = (point.y - labelHeight / 2f)
                    .coerceIn(4f, max(4f, size.height - labelHeight - 4f))
                val rect = Rect(left, top, left + labelWidth, top + labelHeight)
                if (occupiedLabels.any { it.overlaps(rect) } && !isDirectlySelected) return@forEach
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(left, top),
                    size = Size(labelWidth, labelHeight),
                    cornerRadius = CornerRadius(10f, 10f),
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(left + paddingX, top + paddingY),
                )
                occupiedLabels += rect
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

private const val NAME_REVEAL_ZOOM = 0.55f
private const val DETAIL_REVEAL_ZOOM = 0.9f
private const val LABEL_REVEAL_ZOOM = 1.15f
