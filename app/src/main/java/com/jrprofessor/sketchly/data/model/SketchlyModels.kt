package com.jrprofessor.sketchly.data.model

import androidx.compose.ui.graphics.Color
import com.jrprofessor.sketchly.ui.theme.InkDefault

/**
 * A single point captured from touch input.
 * Coordinates are normalized (0f..1f) relative to canvas dimensions
 * so strokes render correctly at any resolution.
 */
data class DrawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
)

/**
 * A continuous stroke — one finger-down to finger-up gesture.
 */
data class Stroke(
    val points: List<DrawPoint> = emptyList(),
    val colorHex: String = colorToHex(InkDefault),
    val widthDp: Float = 4f,
)

/**
 * A complete Sketch — the atomic unit of content in the app.
 * Contains all strokes drawn on the canvas + metadata.
 */
data class Sketch(
    val id: String = "",
    val senderId: String = "",
    val senderDisplayName: String = "",
    val recipientIds: List<String> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    val backgroundColor: String = "#F4F1DE", // PaperIvory
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)

/**
 * A single emoji reaction to a Sketch (SRS FR-7).
 * One reaction per user per Sketch — last write wins.
 */
data class Reaction(
    val sketchId: String = "",
    val userId: String = "",
    val userName: String = "",
    val emoji: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A block relationship: [userId] has blocked [blockedUserId].
 * Stored in Firestore at: blockedUsers/{userId}/entries/{blockedUserId}
 *
 * On block:
 *  - Both sides of the connection are deleted immediately.
 *  - Scribbles from the blocked sender are silently rejected by the
 *    onScribbleCreate Cloud Function (server-enforced, not just client-side).
 *  - Past scribbles are soft-hidden (not deleted) for potential moderation review.
 */
data class BlockedUser(
    val userId: String = "",        // who placed the block
    val blockedUserId: String = "", // who was blocked
    val blockedDisplayName: String = "", // display name at time of block (for list UI)
    val reason: String? = null,     // optional reason (e.g. "inappropriate content")
    val blockedAt: Long = System.currentTimeMillis(),
)

/** Convert a Compose Color to a hex string (#AARRGGBB) */
fun colorToHex(color: Color): String {
    val argb = color.value.toLong()
    return String.format("#%08X", argb.shr(32).toInt())
}

/** Convert a hex string to a Compose Color */
fun hexToColor(hex: String): Color {
    val sanitized = hex.removePrefix("#")
    return Color(sanitized.toLong(16))
}
