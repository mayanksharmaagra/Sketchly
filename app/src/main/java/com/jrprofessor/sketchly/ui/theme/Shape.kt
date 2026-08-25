package com.jrprofessor.sketchly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SketchlyShapes = Shapes(
    // Small — chips, tags, washi-tape elements
    small = RoundedCornerShape(8.dp),

    // Medium — cards, containers, note cards
    medium = RoundedCornerShape(24.dp),

    // Large — bottom sheets, dialogs
    large = RoundedCornerShape(32.dp),

    // Extra-large — full-screen sheets
    extraLarge = RoundedCornerShape(48.dp),
)

// ── Custom shape constants for components ──

/** Note card with a slight "wobble" — non-uniform corners for organic feel */
val NoteCardShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 28.dp,
    bottomStart = 22.dp,
    bottomEnd = 26.dp
)

/** Pill shape for primary buttons */
val PillShape = RoundedCornerShape(percent = 50)

/** Rounded FAB shape — organic blob */
val FabShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 24.dp,
    bottomStart = 26.dp,
    bottomEnd = 30.dp
)

/** Bottom nav dock — floating rounded bar */
val BottomNavShape = RoundedCornerShape(28.dp)

/** Search bar / input field */
val SearchBarShape = RoundedCornerShape(20.dp)

/** Canvas card — the main drawing surface */
val CanvasCardShape = RoundedCornerShape(20.dp)
