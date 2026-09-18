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

    /** Draw — the core creation canvas */
    data object Draw : Screen("draw")

    /** Circle — contacts / friends list */
    data object FriendsList : Screen("friendsList")

    /** Settings — app preferences */
    data object Settings : Screen("settings")

    /** History — dedicated full-screen history route wired to bottom-nav History tab */
    data object History : Screen("history")

    /** Sketchly Viewer — fullscreen view of a single Sketch */
    data object Viewer : Screen("viewer/{sketchId}") {
        fun createRoute(sketchId: String) = "viewer/$sketchId"
    }

    /** Send To — full-screen recipient picker */
    data object SendTo : Screen("send_to")

    /** Add Widget — prompt to pin Sketchly widget to home screen, previewing the sent scribble */
    data object AddWidget : Screen("add_widget/{sketchId}") {
        fun createRoute(sketchId: String) = "add_widget/$sketchId"
    }

    /** Profile — user's profile card with stats */
    data object Profile : Screen("profile")

    /** Edit Profile — edit display name and avatar */
    data object EditProfile : Screen("edit_profile")

    /** Contact History — all scribbles exchanged with a specific contact */
    data object ContactHistory : Screen("contact_history/{contactId}") {
        fun createRoute(contactId: String) = "contact_history/$contactId"
    }

    /** Blocked Users — list of blocked users with Unblock action (Settings → Privacy) */
    data object BlockedUsers : Screen("blocked_users")
}
