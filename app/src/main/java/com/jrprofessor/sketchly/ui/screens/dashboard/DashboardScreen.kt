package com.jrprofessor.sketchly.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.screens.inbox.InboxViewModel
import com.jrprofessor.sketchly.ui.screens.settings.SettingsViewModel
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.ui.theme.ToolBarBgColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Avatar colours (same palette as Circle screen)
// ─────────────────────────────────────────────────────────────────────────────

private val AVATAR_COLORS = listOf(
    Color(0xFF3D405B), Color(0xFF70A18A), Color(0xFFE07A5F),
    Color(0xFFBF4E6D), Color(0xFF4A6FA5), Color(0xFF8B6914),
    Color(0xFF6B4E71), Color(0xFF2A9D8F),
)

private fun avatarColor(seed: String) =
    AVATAR_COLORS[abs(seed.hashCode()) % AVATAR_COLORS.size]

// ─────────────────────────────────────────────────────────────────────────────
// Greeting helper
// ─────────────────────────────────────────────────────────────────────────────

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else      -> "Good night"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard Screen
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard – Empty Inbox")
@Composable
private fun DashboardScreenPreview() {
    SketchlyTheme {
        DashboardContent(
            initials = "MS",
            firstName = "Mayank",
            sketches = emptyList(),
            unreadCount = 0,
            isInitialLoading = false,
            isOnline = true,
            onSketchTap = {},
            onDrawTap = {},
            onCircleTap = {},
            onProfileTap = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Dashboard – Offline")
@Composable
private fun DashboardScreenOfflinePreview() {
    SketchlyTheme {
        DashboardContent(
            initials = "JD",
            firstName = "Jane",
            sketches = emptyList(),
            unreadCount = 3,
            isInitialLoading = false,
            isOnline = false,
            onSketchTap = {},
            onDrawTap = {},
            onCircleTap = {},
            onProfileTap = {},
        )
    }
}

@Composable
fun DashboardScreen(
    onSketchTap: (String) -> Unit = {},
    onDrawTap: () -> Unit = {},
    onCircleTap: () -> Unit = {},
    onProfileTap: () -> Unit = {},
    inboxViewModel: InboxViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val sketches by inboxViewModel.inboxSketches.collectAsStateWithLifecycle()
    val unreadCount by inboxViewModel.unreadCount.collectAsStateWithLifecycle()
    val isInitialLoading by inboxViewModel.isInitialLoading.collectAsStateWithLifecycle()
    val isOnline by inboxViewModel.isOnline.collectAsStateWithLifecycle()
    val user by settingsViewModel.currentUser.collectAsStateWithLifecycle()

    val displayName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        ?: "Creator"

    val firstName = displayName.split(" ").firstOrNull() ?: "Creator"

    val initials = displayName
        .split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("").ifEmpty { "?" }

    DashboardContent(
        initials         = initials,
        firstName        = firstName,
        sketches         = sketches,
        unreadCount      = unreadCount,
        isInitialLoading = isInitialLoading,
        isOnline         = isOnline,
        onSketchTap      = { sketch ->
            inboxViewModel.markAsRead(sketch.id)
            onSketchTap(sketch.id)
        },
        onDrawTap        = onDrawTap,
        onCircleTap      = onCircleTap,
        onProfileTap     = onProfileTap,
    )
}

@Composable
internal fun DashboardContent(
    initials: String,
    firstName: String,
    sketches: List<Sketch>,
    unreadCount: Int,
    isInitialLoading: Boolean,
    isOnline: Boolean,
    onSketchTap: (Sketch) -> Unit,
    onDrawTap: () -> Unit,
    onCircleTap: () -> Unit,
    onProfileTap: () -> Unit,
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Top Bar ───────────────────────────────────────────────────────
            DashTopBar(
                initials = initials,
                onCircleTap = onCircleTap,
                onProfileTap = onProfileTap,
            )

            // ── Offline banner ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE07A5F).copy(alpha = 0.15f))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.WifiOff, null,
                        tint = Color(0xFFE07A5F),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        "You're offline — changes sync when reconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE07A5F),
                    )
                }
            }

            // ── Greeting ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "${greeting()}, $firstName",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                    ),
                    color = AppNameColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isInitialLoading -> "Loading your scribbles…"
                        unreadCount == 0 -> "You're all caught up ✓"
                        unreadCount == 1 -> "You have 1 new scribble waiting for you."
                        else -> "You have $unreadCount new scribbles waiting for you."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = TextMuted,
                )
            }

            // ── Activity Card ─────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PaperIvory,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Card header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "RECENT ACTIVITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                fontSize = 11.sp,
                            ),
                            color = TextMuted,
                        )
                        Icon(
                            Icons.Outlined.MoreHoriz, null,
                            tint = TextMuted.copy(alpha = 0.55f),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Card content — inbox feed
                    when {
                        isInitialLoading -> SkeletonActivityList()
                        sketches.isEmpty() -> EmptyActivityState(onDrawTap = onDrawTap)
                        else -> ActivityList(
                            sketches    = sketches,
                            onSketchTap = onSketchTap,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hint text
            Text(
                text = "tap an avatar to see all Scribbles from that person.",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.sp,
                ),
                color = TextMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashTopBar(
    initials: String,
    onCircleTap: () -> Unit,
    onProfileTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolBarBgColor)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App name
        Text(
            text = "Sketchly",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
            ),
            color = AppNameColor,
        )

        // Right icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Circle / friends icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppNameColor.copy(alpha = 0.08f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onCircleTap() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = "Your Circle",
                    tint = AppNameColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Profile avatar — initials circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppNameColor.copy(alpha = 0.13f))
                    .border(1.5.dp, AppNameColor.copy(alpha = 0.20f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onProfileTap() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    ),
                    color = AppNameColor,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity List
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivityList(
    sketches: List<Sketch>,
    onSketchTap: (Sketch) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(sketches, key = { it.id }) { sketch ->
            ActivityRow(sketch = sketch, onClick = { onSketchTap(sketch) })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 76.dp, end = 18.dp)
                    .height(0.8.dp)
                    .background(TextEditorBorderColor.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
private fun ActivityRow(
    sketch: Sketch,
    onClick: () -> Unit,
) {
    val senderName = sketch.senderDisplayName.takeIf { it.isNotBlank() } ?: "Unknown"
    val initials = senderName.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("").ifEmpty { "?" }
    val color = avatarColor(senderName)

    val timeLabel = remember(sketch.createdAt) {
        val now = System.currentTimeMillis()
        val diff = now - sketch.createdAt
        when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${diff / 60_000}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000}h ago"
            diff < 172_800_000L -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(sketch.createdAt))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar with sketch thumbnail or initials
        Box(modifier = Modifier.size(52.dp)) {
            // Sketch thumbnail as avatar
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxSize(),
            ) {
                SketchlyThumbnail(
                    strokes = sketch.strokes,
                    size = 52.dp,
                )
            }
            // Unread dot
            if (!sketch.isRead) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(ButtonGold)
                        .border(1.5.dp, PaperIvory, CircleShape)
                        .align(Alignment.TopEnd),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (!sketch.isRead) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = AppNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Sent a doodle · \"${sketch.strokes.size} stroke${if (sketch.strokes.size != 1) "s" else ""}...\"",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = TextMuted,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyActivityState(onDrawTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "✉️",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No scribbles yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AppNameColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Draw one to get things started!",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = ButtonGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onDrawTap() },
            ) {
                Text(
                    text = "✏  Draw a Scribble",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton loader
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SkeletonActivityList() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(TextEditorBorderColor.copy(alpha = 0.3f)),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .height(14.dp)
                            .fillMaxWidth(0.45f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TextEditorBorderColor.copy(alpha = 0.3f)),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(11.dp)
                            .fillMaxWidth(0.70f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TextEditorBorderColor.copy(alpha = 0.2f)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 66.dp)
                    .height(0.8.dp)
                    .background(TextEditorBorderColor.copy(alpha = 0.35f)),
            )
        }
    }
}


