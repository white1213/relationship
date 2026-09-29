package com.relationship.graph.ui.graph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.relationship.graph.data.FamilyRelationKind
import com.relationship.graph.data.RelationshipSemantics
import com.relationship.graph.data.inference.RelativeAgeResolver
import com.relationship.graph.data.local.PersonEntity
import com.relationship.graph.data.local.RelationTypeEntity
import com.relationship.graph.data.local.RelativeAgeOrderEntity
import com.relationship.graph.ui.relationshipLabelForPerson
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图谱静态图导出（PNG 位图 / SVG 矢量）。
 * 与屏幕画布各自绘制，但共用同一套布局与配色语义；导出视角为整图自适应、全部标签可见。
 */
object GraphImageExporter {

    data class Input(
        val people: List<PersonEntity>,
        val routes: List<RoutedRelationship>,
        val positions: Map<String, LayoutPoint>,
        val relationTypes: List<RelationTypeEntity>,
        val ageOrders: List<RelativeAgeOrderEntity>,
        val relationshipById: Map<String, com.relationship.graph.data.local.RelationshipEntity>,
        val avatars: Map<String, Bitmap>,
        val myPersonId: String?,
    ) {
        internal val peopleById: Map<String, PersonEntity> by lazy { people.associateBy { it.id } }
        internal val ageResolver: RelativeAgeResolver by lazy {
            RelativeAgeResolver(peopleById = peopleById, ageOrders = ageOrders)
        }
    }

    private data class Bounds(val minX: Float, val minY: Float, val width: Float, val height: Float)

    private const val PADDING = 90f
    private const val NODE_RADIUS = 30f

    suspend fun exportPng(context: Context, input: Input): File = withContext(Dispatchers.Default) {
        val bounds = boundsOf(input)
        val scale = computeScale(bounds)
        val width = ((bounds.width + PADDING * 2) * scale).toInt().coerceIn(320, 4096)
        val height = ((bounds.height + PADDING * 2) * scale).toInt().coerceIn(320, 4096)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.scale(scale, scale)
        canvas.translate(PADDING - bounds.minX, PADDING - bounds.minY)
        drawModel(canvas, input, textScale = scale)
        val file = File(exportDir(context), "relationship-graph-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        file
    }

    suspend fun exportSvg(context: Context, input: Input): File = withContext(Dispatchers.Default) {
        val bounds = boundsOf(input)
        val width = bounds.width + PADDING * 2
        val height = bounds.height + PADDING * 2
        val svg = StringBuilder()
        svg.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${width.round1()}" """ +
                """height="${height.round1()}" viewBox="0 0 ${width.round1()} ${height.round1()}">""",
        )
        svg.append("""<rect width="100%" height="100%" fill="white"/>""")
        val translate = "translate(${(PADDING - bounds.minX).round1()} ${(PADDING - bounds.minY).round1()})"
        svg.append("""<g transform="$translate">""")
        appendSvgRoutes(svg, input)
        appendSvgNodes(svg, input)
        svg.append("</g></svg>")
        val file = File(exportDir(context), "relationship-graph-${System.currentTimeMillis()}.svg")
        file.writeText(svg.toString(), Charsets.UTF_8)
        file
    }

    fun shareUri(context: Context, file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun exportDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    // ===== 路由标签（与屏上称谓逻辑一致） =====

    private fun routeLabel(route: RoutedRelationship, input: Input): String {
        val typeById = input.relationTypes.associateBy { it.id }
        return route.relationshipIds
            .mapNotNull { input.relationshipById[it] }
            .flatMap { relationship ->
                val type = typeById[relationship.relationTypeId]
                    ?: return@flatMap emptyList()
                val from = input.peopleById[relationship.fromPersonId]
                val to = input.peopleById[relationship.toPersonId]
                listOfNotNull(
                    from?.let {
                        relationshipLabelForPerson(
                            relationship = relationship,
                            type = type,
                            personId = it.id,
                            otherPerson = to,
                            people = input.people,
                            ageOrders = input.ageOrders,
                            resolver = input.ageResolver,
                        )
                    },
                    to?.let {
                        relationshipLabelForPerson(
                            relationship = relationship,
                            type = type,
                            personId = it.id,
                            otherPerson = from,
                            people = input.people,
                            ageOrders = input.ageOrders,
                            resolver = input.ageResolver,
                        )
                    },
                ).distinct()
            }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("/")
    }

    // ===== 几何 =====

    private fun boundsOf(input: Input): Bounds {
        if (input.positions.isEmpty()) return Bounds(0f, 0f, 400f, 400f)
        val minX = input.positions.values.minOf { it.x } - NODE_RADIUS
        val maxX = input.positions.values.maxOf { it.x } + NODE_RADIUS
        val minY = input.positions.values.minOf { it.y } - NODE_RADIUS
        val maxY = input.positions.values.maxOf { it.y } + NODE_RADIUS
        return Bounds(minX, minY, maxX - minX, maxY - minY)
    }

    private fun computeScale(bounds: Bounds): Float {
        val targetWidth = 2000f
        val targetHeight = 2400f
        val byWidth = targetWidth / (bounds.width + PADDING * 2)
        val byHeight = targetHeight / (bounds.height + PADDING * 2)
        return (minOf(byWidth, byHeight).coerceIn(1f, 2.5f)).round1()
    }

    // ===== 配色 =====

    private fun routeColor(style: GraphRouteStyle): Int = when (style) {
        GraphRouteStyle.PARENT_CHILD -> 0xFF3F7FDD.toInt()
        GraphRouteStyle.SPOUSE -> 0xFFD35F78.toInt()
        GraphRouteStyle.SIBLING -> 0xFF5A8FD6.toInt()
        GraphRouteStyle.SOCIAL -> 0xFF7A8796.toInt()
        GraphRouteStyle.CONFIRMED_INFERENCE -> 0xFF79A6D2.toInt()
    }

    private fun nodeColor(person: PersonEntity, myPersonId: String?): Int = when {
        person.id == myPersonId -> 0xFF244D86.toInt()
        person.deceased -> 0xFF8C99A8.toInt()
        else -> 0xFF3F7FDD.toInt()
    }

    // ===== PNG 绘制 =====

    private fun drawModel(canvas: Canvas, input: Input, textScale: Float) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        input.routes.forEach { route ->
            linePaint.color = routeColor(route.style)
            linePaint.strokeWidth = 2.6f
            linePaint.pathEffect = when (route.style) {
                GraphRouteStyle.SIBLING, GraphRouteStyle.SOCIAL ->
                    DashPathEffect(floatArrayOf(8f, 6f), 0f)
                else -> null
            }
            route.segments.forEach { segment ->
                canvas.drawLine(segment.start.x, segment.start.y, segment.end.x, segment.end.y, linePaint)
            }
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        input.routes.forEach { route ->
            val label = routeLabel(route, input)
            if (label.isBlank()) return@forEach
            val point = route.labelPoint
            textPaint.textSize = 22f
            textPaint.textAlign = Paint.Align.CENTER
            val textWidth = textPaint.measureText(label)
            canvas.drawRoundRect(
                android.graphics.RectF(
                    point.x - textWidth / 2f - 8f,
                    point.y - 16f,
                    point.x + textWidth / 2f + 8f,
                    point.y + 16f,
                ),
                10f, 10f, bgPaint,
            )
            textPaint.color = 0xFF52647B.toInt()
            canvas.drawText(label, point.x, point.y + 7f, textPaint)
        }

        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 2.5f
        }
        input.people.sortedBy { it.name }.forEach { person ->
            val position = input.positions[person.id] ?: return@forEach
            nodePaint.style = Paint.Style.FILL
            nodePaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(position.x, position.y, NODE_RADIUS + 2.5f, nodePaint)
            val avatar = input.avatars[person.id]
            if (avatar != null) {
                val size = NODE_RADIUS * 2f
                nodePaint.shader = null
                val clipped = Bitmap.createBitmap(
                    avatar.width, avatar.height, Bitmap.Config.ARGB_8888,
                )
                val clipCanvas = Canvas(clipped)
                clipCanvas.drawOval(
                    0f, 0f, avatar.width.toFloat(), avatar.height.toFloat(),
                    Paint().apply { isAntiAlias = true },
                )
                clipCanvas.drawBitmap(avatar, null, android.graphics.RectF(0f, 0f, avatar.width.toFloat(), avatar.height.toFloat()), null)
                canvas.drawBitmap(clipped, null, android.graphics.RectF(
                    position.x - NODE_RADIUS, position.y - NODE_RADIUS,
                    position.x + NODE_RADIUS, position.y + NODE_RADIUS,
                ), nodePaint.apply { isFilterBitmap = true })
                clipped.recycle()
                if (person.deceased) {
                    nodePaint.color = 0x735B6570
                    canvas.drawCircle(position.x, position.y, NODE_RADIUS, nodePaint)
                }
            } else {
                nodePaint.color = nodeColor(person, input.myPersonId)
                canvas.drawCircle(position.x, position.y, NODE_RADIUS, nodePaint)
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = 30f
                textPaint.textAlign = Paint.Align.CENTER
                val initial = person.name.trim().take(1).ifBlank { "?" }
                canvas.drawText(initial, position.x, position.y + textPaint.textSize / 3f, textPaint)
            }
            ringPaint.style = Paint.Style.STROKE
            canvas.drawCircle(position.x, position.y, NODE_RADIUS, ringPaint)
            if (person.deceased) {
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = 18f
                val badgeX = position.x + NODE_RADIUS * 0.78f
                val badgeY = position.y - NODE_RADIUS * 0.78f
                nodePaint.style = Paint.Style.FILL
                nodePaint.color = 0xFF5B6570.toInt()
                canvas.drawCircle(badgeX, badgeY, 14f, nodePaint)
                canvas.drawText("故", badgeX, badgeY + 6f, textPaint)
            }
            textPaint.color = 0xFF52647B.toInt()
            textPaint.textSize = 24f * textScale.coerceAtMost(1.5f)
            canvas.drawText(person.name, position.x, position.y + NODE_RADIUS + 24f, textPaint)
        }
    }

    // ===== SVG 绘制 =====

    private fun appendSvgRoutes(svg: StringBuilder, input: Input) {
        input.routes.forEach { route ->
            val color = "#%06X".format(routeColor(route.style) and 0xFFFFFF)
            val dash = when (route.style) {
                GraphRouteStyle.SIBLING, GraphRouteStyle.SOCIAL -> """ stroke-dasharray="8 6""""
                else -> ""
            }
            route.segments.forEach { segment ->
                svg.append(
                    """<line x1="${segment.start.x.round1()}" y1="${segment.start.y.round1()}" """ +
                        """x2="${segment.end.x.round1()}" y2="${segment.end.y.round1()}" """ +
                        """stroke="$color" stroke-width="2.6"$dash/>""",
                )
            }
            val label = routeLabel(route, input)
            if (label.isNotBlank()) {
                val point = route.labelPoint
                val labelWidth = label.length * 22f * 0.95f
                svg.append(
                    """<rect x="${(point.x - labelWidth / 2 - 8).round1()}" y="${(point.y - 16).round1()}" """ +
                        """width="${(labelWidth + 16).round1()}" height="32" rx="10" """ +
                        """fill="white" fill-opacity="0.95"/>""",
                )
                svg.append(
                    """<text x="${point.x.round1()}" y="${(point.y + 7).round1()}" """ +
                        """text-anchor="middle" font-size="22" fill="#52647B" """ +
                        """font-family="sans-serif">${label.escapeXml()}</text>""",
                )
            }
        }
    }

    private fun appendSvgNodes(svg: StringBuilder, input: Input) {
        input.people.sortedBy { it.name }.forEach { person ->
            val position = input.positions[person.id] ?: return@forEach
            val avatar = input.avatars[person.id]
            if (avatar != null) {
                val clipId = "clip-${person.id}"
                svg.append("""<clipPath id="$clipId"><circle cx="${position.x.round1()}" cy="${position.y.round1()}" r="$NODE_RADIUS"/></clipPath>""")
                svg.append(
                    """<image x="${(position.x - NODE_RADIUS).round1()}" """ +
                        """y="${(position.y - NODE_RADIUS).round1()}" """ +
                        """width="${NODE_RADIUS * 2}" height="${NODE_RADIUS * 2}" """ +
                        """clip-path="url(#$clipId)" """ +
                        """href="data:image/png;base64,${encodeAvatarPng(avatar)}"/>""",
                )
                if (person.deceased) {
                    svg.append(
                        """<circle cx="${position.x.round1()}" cy="${position.y.round1()}" r="$NODE_RADIUS" """ +
                            """fill="#5B6570" fill-opacity="0.45"/>""",
                    )
                }
            } else {
                svg.append(
                    """<circle cx="${position.x.round1()}" cy="${position.y.round1()}" r="$NODE_RADIUS" """ +
                        """fill="#%06X"/>""".format(nodeColor(person, input.myPersonId) and 0xFFFFFF),
                )
                svg.append(
                    """<text x="${position.x.round1()}" y="${(position.y + 10).round1()}" """ +
                        """text-anchor="middle" font-size="30" fill="white" """ +
                        """font-family="sans-serif">${person.name.take(1).ifBlank { "?" }.escapeXml()}</text>""",
                )
            }
            svg.append(
                """<circle cx="${position.x.round1()}" cy="${position.y.round1()}" r="$NODE_RADIUS" """ +
                    """fill="none" stroke="white" stroke-width="2.5"/>""",
            )
            if (person.deceased) {
                val bx = position.x + NODE_RADIUS * 0.78f
                val by = position.y - NODE_RADIUS * 0.78f
                svg.append("""<circle cx="${bx.round1()}" cy="${by.round1()}" r="14" fill="#5B6570"/>""")
                svg.append(
                    """<text x="${bx.round1()}" y="${(by + 5).round1()}" text-anchor="middle" """ +
                        """font-size="15" fill="white" font-family="sans-serif">故</text>""",
                )
            }
            svg.append(
                """<text x="${position.x.round1()}" y="${(position.y + NODE_RADIUS + 26).round1()}" """ +
                    """text-anchor="middle" font-size="24" fill="#52647B" """ +
                    """font-family="sans-serif">${person.name.escapeXml()}</text>""",
            )
        }
    }

    private fun encodeAvatarPng(avatar: Bitmap): String {
        val output = java.io.ByteArrayOutputStream()
        avatar.compress(Bitmap.CompressFormat.PNG, 90, output)
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun Float.round1(): Float = (this * 10).toInt() / 10f

    private fun String.escapeXml(): String =
        replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
