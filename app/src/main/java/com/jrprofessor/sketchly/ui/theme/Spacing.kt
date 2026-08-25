package com.jrprofessor.sketchly.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SketchlySpacing(
    /** 4dp — base unit */
    val xs: Dp = 4.dp,
    /** 8dp */
    val sm: Dp = 8.dp,
    /** 12dp */
    val md: Dp = 12.dp,
    /** 16dp — standard content padding */
    val lg: Dp = 16.dp,
    /** 20dp — gutter */
    val gutter: Dp = 20.dp,
    /** 24dp — large spacing between sections */
    val xl: Dp = 24.dp,
    /** 32dp */
    val xxl: Dp = 32.dp,
    /** 40dp — extra large spacing */
    val xxxl: Dp = 40.dp,
    /** 64dp — max spacing */
    val huge: Dp = 64.dp,
    /** 16dp — mobile screen margin (keeps "floating paper" effect) */
    val screenMargin: Dp = 16.dp,
)

val LocalSpacing = staticCompositionLocalOf { SketchlySpacing() }
