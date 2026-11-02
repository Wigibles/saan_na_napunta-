package com.saannapunta.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Green600,
    onPrimary = SurfaceColor,
    primaryContainer = Green100,
    onPrimaryContainer = Green700,
    secondary = Green700,
    onSecondary = SurfaceColor,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceColor,
    onSurface = Ink,
    surfaceVariant = Green50,
    onSurfaceVariant = InkMuted,
    outline = LineBorder,
    error = StatusOver,
    onError = SurfaceColor
)

val SaanNaPuntaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), // Bank logo tile
    small = RoundedCornerShape(14.dp),      // Buttons & inputs
    medium = RoundedCornerShape(20.dp),     // Standard cards & sheets
    large = RoundedCornerShape(28.dp),      // Hero balance card
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SaanNaPuntaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = SaanNaPuntaTypography,
        shapes = SaanNaPuntaShapes,
        content = content
    )
}
