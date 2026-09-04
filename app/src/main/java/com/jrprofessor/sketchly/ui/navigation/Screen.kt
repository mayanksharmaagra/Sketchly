package com.jrprofessor.sketchly.ui.navigation

/**
 * Sealed hierarchy of all app screens / routes.
 */
sealed class Screen(val route: String) {
    /** Get Started / Splash Screen */
    data object GetStarted : Screen("getStarted")

    /** Auth — phone number entry + OTP verification (V1: phone only) */
    data object Auth : Screen("auth")

    /**
     * Profile Setup — Full Name + Username selection.
     * Shown ONLY to new users immediately after OTP verification.
     * Returning users (existing username in Firestore) skip this screen.
     * SRS FR-1.3, FR-1.4.
     */
    data object ProfileSetup : Screen("profile_setup")

    /**
     * Contact permission gate — shown once after new account profile is saved.
     * Allows user to grant READ_CONTACTS for hash-based contact sync.
     * SRS FR-2.1.
     */
    data object ContactPermission : Screen("contact_permission")

    /** Dashboard — main home screen with recent activity, greeting, and bottom bar */
    data object Dashboard : Screen("dashboard")

    /** Inbox — received Sketches feed */
    data object Inbox : Screen("inbox")

    /** Draw — the core creation canvas */
    data object Draw : Screen("draw")

    /** Circle — contacts / friends list */
    data object Circle : Screen("circle")

    /** Settings — app preferences */
    data object Settings : Screen("settings")

    /** Archive — all past Sketches (legacy, kept for deep-link compat) */
    data object Archive : Screen("archive")

    /** History — dedicated full-screen history route wired to bottom-nav History tab */
    data object History : Screen("history")

    /** Sketchly Viewer — fullscreen view of a single Sketch */
    data object Viewer : Screen("viewer/{sketchId}") {
        fun createRoute(sketchId: String) = "viewer/$sketchId"
    }

    /** Recipient Picker — bottom sheet to select recipients before sending */
    data object RecipientPicker : Screen("recipient_picker")

    /** Send To — full-screen recipient picker */
    data object SendTo : Screen("send_to")

    /** Add Widget — prompt to pin Sketchly widget to home screen */
    data object AddWidget : Screen("add_widget")

    /** Profile — user's profile card with stats */
    data object Profile : Screen("profile")

    /** Edit Profile — edit display name and avatar */
    data object EditProfile : Screen("edit_profile")

    /** Screen Preview — developer tool showing all app screens as thumbnails */
    data object ScreenPreview : Screen("screen_preview")
}
