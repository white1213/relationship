package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.preferences.GraphDisplayMode

fun isRouteVisible(
    style: GraphRouteStyle,
    graphMode: GraphMode,
    displayMode: GraphDisplayMode,
    touchesSelectedPerson: Boolean,
): Boolean {
    if (displayMode == GraphDisplayMode.FULL) return true
    val isBackbone = style == GraphRouteStyle.PARENT_CHILD ||
        style == GraphRouteStyle.SPOUSE
    return when (graphMode) {
        GraphMode.FAMILY -> isBackbone ||
            (
                style in setOf(
                    GraphRouteStyle.SIBLING,
                    GraphRouteStyle.CONFIRMED_INFERENCE,
                ) && touchesSelectedPerson
                )
        GraphMode.SOCIAL -> style == GraphRouteStyle.SOCIAL && touchesSelectedPerson
        GraphMode.ALL -> isBackbone ||
            (
                style in setOf(
                    GraphRouteStyle.SIBLING,
                    GraphRouteStyle.SOCIAL,
                    GraphRouteStyle.CONFIRMED_INFERENCE,
                ) && touchesSelectedPerson
                )
    }
}
