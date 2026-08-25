package com.jrprofessor.sketchly.ui.navigation

/**
 * Sealed hierarchy of all app screens / routes.
 */
sealed class Screen(val route: String) {
    /** Auth — sign-in and sign-up */
    data object Auth : Screen("auth")

    /** Inbox — received Sketches feed */
    data object Inbox : Screen("inbox")

    /** Draw — the core creation canvas */
    data object Draw : Screen("draw")

    /** Circle — contacts / friends list */
    data object Circle : Screen("circle")

    /** Settings — app preferences */
    data object Settings : Screen("settings")

    /** Archive / History — all past Sketches */
    data object Archive : Screen("archive")

    /** Sketchly Viewer — fullscreen view of a single Sketch */
    data object Viewer : Screen("viewer/{sketchId}") {
        fun createRoute(sketchId: String) = "viewer/$sketchId"
    }

    /** Recipient Picker — bottom sheet to select recipients before sending */
    data object RecipientPicker : Screen("recipient_picker")

    /** Screen Preview — developer tool showing all app screens as thumbnails */
    data object ScreenPreview : Screen("screen_preview")
}
