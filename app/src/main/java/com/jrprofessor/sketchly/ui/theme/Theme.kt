package com.jrprofessor.sketchly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val SketchlyLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    surfaceVariant = SurfaceVariant,
    surfaceTint = SurfaceTint,
    outline = Outline,
    outlineVariant = OutlineVariant,
    background = Background,
    onBackground = OnBackground,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

@Composable
fun SketchlyTheme(
    // Sketch has a single warm-toned light theme — no dark mode for MVP
    // (dark mode would conflict with the paper/notebook aesthetic)
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides SketchlySpacing(),
    ) {
        MaterialTheme(
            colorScheme = SketchlyLightColorScheme,
            typography = SketchlyTypography,
            shapes = SketchlyShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `SketchlyTheme.spacing.lg` */
object SketchlyTheme {
    val spacing: SketchlySpacing
        @Composable get() = LocalSpacing.current
}