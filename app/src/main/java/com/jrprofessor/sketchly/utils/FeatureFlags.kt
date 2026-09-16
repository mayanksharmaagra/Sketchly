package com.jrprofessor.sketchly.utils

/**
 * Central feature-flag registry for Sketchly.
 *
 * All flags are compile-time constants so that the Kotlin compiler and
 * R8/ProGuard can dead-code-eliminate the gated branches in release builds.
 *
 * HOW TO USE
 * ----------
 * Wrap any feature-gated UI / logic with:
 *
 *   if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) { … }
 *
 * To re-enable a feature for a release:
 *   1. Flip the constant to `true` here.
 *   2. Mirror the change in the Cloud Functions config (functions/index.js).
 *   3. Update `firestore.rules` if the collection has additional guards.
 *
 * NEVER delete code that is wrapped behind a flag — gate it, don't gut it.
 */
object FeatureFlags {

    // ── Connection-Request flow ────────────────────────────────────────────────
    //
    // Controls the full Accept / Decline connection-request UX:
    //   • "Requests" tab in CircleScreen
    //   • In-app connection-request dialog (send / accept / decline)
    //   • ConnectionRequest data class, RequestStatus enum
    //   • Firestore `connectionRequests` collection listener
    //   • Cloud Functions: onFollowRequestCreate, onFollowRequestAccept
    //
    // Set to `false` for V1 — the feature is preserved in code but hidden from
    // the UI.  Flip to `true` to restore the full flow in a future release.
    // ──────────────────────────────────────────────────────────────────────────
    const val ENABLE_CONNECTION_REQUESTS = false

    // ── Suggested tab (Circle screen) ─────────────────────────────────────────
    //
    // When true  → CircleScreen shows a single unified view:
    //   Sync Contacts card + auto-connected users list (no tab row).
    // When false → Original 3-tab layout (Suggested / Requests / Connected).
    //
    // V1 = true  — connections form automatically the first time either user
    //   sends a scribble (Cloud Function onScribbleCreate auto-connect path).
    //   The Suggested tab (contact-sync suggestions + Follow button) is hidden.
    //   Flip to false to restore it for V2.
    // ──────────────────────────────────────────────────────────────────────────
    const val HIDE_SUGGESTED_TAB = true
}
