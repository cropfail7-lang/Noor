package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NoorDarkColorScheme = darkColorScheme(
    primary = NoorCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = NoorCyanContainer,
    onPrimaryContainer = NoorCyan,
    secondary = NoorGold,
    onSecondary = ObsidianVoid,
    secondaryContainer = NoorGoldContainer,
    onSecondaryContainer = NoorGold,
    tertiary = NoorIndigo,
    onTertiary = TextPrimary,
    tertiaryContainer = NoorIndigoContainer,
    onTertiaryContainer = NoorIndigo,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianDeep,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder.copy(alpha = 0.5f),
    error = NoorRose,
    onError = ObsidianVoid,
    errorContainer = NoorRoseContainer,
    onErrorContainer = NoorRose
)

val NoorShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NoorAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NoorDarkColorScheme,
        typography = NoorTypography,
        shapes = NoorShapes,
        content = content
    )
}
