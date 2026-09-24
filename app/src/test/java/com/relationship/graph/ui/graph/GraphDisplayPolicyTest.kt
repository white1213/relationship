package com.relationship.graph.ui.graph

import com.relationship.graph.data.local.GraphMode
import com.relationship.graph.data.preferences.GraphDisplayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphDisplayPolicyTest {
    @Test
    fun simpleFamilyModeAlwaysKeepsMainBackbone() {
        assertTrue(
            isRouteVisible(
                GraphRouteStyle.PARENT_CHILD,
                GraphMode.FAMILY,
                GraphDisplayMode.SIMPLE,
                touchesSelectedPerson = false,
            ),
        )
        assertTrue(
            isRouteVisible(
                GraphRouteStyle.SPOUSE,
                GraphMode.FAMILY,
                GraphDisplayMode.SIMPLE,
                touchesSelectedPerson = false,
            ),
        )
    }

    @Test
    fun simpleFamilyModeOnlyExpandsSiblingsForSelectedPerson() {
        assertFalse(
            isRouteVisible(
                GraphRouteStyle.SIBLING,
                GraphMode.FAMILY,
                GraphDisplayMode.SIMPLE,
                touchesSelectedPerson = false,
            ),
        )
        assertTrue(
            isRouteVisible(
                GraphRouteStyle.SIBLING,
                GraphMode.FAMILY,
                GraphDisplayMode.SIMPLE,
                touchesSelectedPerson = true,
            ),
        )
    }

    @Test
    fun fullModeRestoresEveryRoute() {
        assertTrue(
            isRouteVisible(
                GraphRouteStyle.SOCIAL,
                GraphMode.FAMILY,
                GraphDisplayMode.FULL,
                touchesSelectedPerson = false,
            ),
        )
    }
}
