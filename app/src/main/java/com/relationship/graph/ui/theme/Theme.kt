package com.relationship.graph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RelationshipColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueContainer,
    onPrimaryContainer = BluePrimaryDark,
    secondary = Color(0xFF54749B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDF9),
    onSecondaryContainer = Ink,
    background = SkyBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = BlueSoft,
    onSurfaceVariant = MutedInk,
    outline = OutlineSoft,
    error = Danger,
)

@Composable
fun RelationshipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RelationshipColorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
