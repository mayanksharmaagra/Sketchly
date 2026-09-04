package com.jrprofessor.sketchly.ui.screens.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.ui.components.InboxSkeletonList
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.theme.NoteCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(
    onSketchTap: (String) -> Unit = {},
    onDrawTap: () -> Unit = {},
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val sketches by viewModel.inboxSketches.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val isInitialLoading by viewModel.isInitialLoading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    InboxScreenContent(
        sketches = sketches,
        unreadCount = unreadCount,
        isInitialLoading = isInitialLoading,
        isOnline = isOnline,
        onSketchTap = { id ->
            viewModel.markAsRead(id)
            onSketchTap(id)
        },
        onDrawTap = onDrawTap,
    )
}

@Composable
fun InboxScreenContent(
    sketches: List<Sketch>,
    unreadCount: Int,
    isInitialLoading: Boolean,
    isOnline: Boolean,
    onSketchTap: (String) -> Unit,
    onDrawTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Sketchly",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount new doodle${if (unreadCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Avatar — accessible label for TalkBack (UIUX §9)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .semantics { contentDescription = "Profile" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // ── Offline banner (UIUX §7) ──
        // Slides in/out automatically on network transitions.
        AnimatedVisibility(
            visible = !isOnline,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .semantics { contentDescription = "You are offline. Changes will sync when reconnected." },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null, // banner itself has the description
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "You're offline — changes will sync when reconnected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // ── Content ──
        when {
            // Skeleton during cold-start initial load (UIUX §7 — no empty-state flash)
            isInitialLoading -> {
                InboxSkeletonList()
            }

            sketches.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Surface(
                            shape = NoteCardShape,
                            color = PaperIvory,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(160.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Text(
                                    text = "✉️",
                                    style = MaterialTheme.typography.displayLarge,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "No Sketches yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Draw one to get things started!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDrawTap,
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Draw,
                                contentDescription = null, // button label covers this
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = "Draw a Sketch",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sketches, key = { it.id }) { sketch ->
                        InboxSketchCard(
                            sketch = sketch,
                            onClick = { onSketchTap(sketch.id) },
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ──

@Preview(name = "Inbox — Empty", showBackground = true)
@Composable
private fun InboxScreenEmptyPreview() {
    SketchlyTheme {
        InboxScreenContent(
            sketches = emptyList(),
            unreadCount = 0,
            isInitialLoading = false,
            isOnline = true,
            onSketchTap = {},
            onDrawTap = {},
        )
    }
}

@Preview(name = "Inbox — Loading", showBackground = true)
@Composable
private fun InboxScreenLoadingPreview() {
    SketchlyTheme {
        InboxScreenContent(
            sketches = emptyList(),
            unreadCount = 0,
            isInitialLoading = true,
            isOnline = true,
            onSketchTap = {},
            onDrawTap = {},
        )
    }
}

@Preview(name = "Inbox — Offline Banner", showBackground = true)
@Composable
private fun InboxScreenOfflinePreview() {
    SketchlyTheme {
        InboxScreenContent(
            sketches = emptyList(),
            unreadCount = 0,
            isInitialLoading = false,
            isOnline = false,
            onSketchTap = {},
            onDrawTap = {},
        )
    }
}

@Composable
private fun InboxSketchCard(
    sketch: Sketch,
    onClick: () -> Unit,
) {
    val senderName = sketch.senderDisplayName.takeIf { it.isNotBlank() } ?: "A Sketch"
    val readState = if (!sketch.isRead) "Unread" else "Read"
    // Semantic description for TalkBack (UIUX §9) — announces sender, read state, and date
    val formattedTime = SimpleDateFormat("h:mm a · MMM d", Locale.getDefault())
        .format(Date(sketch.createdAt))
    val cardDescription = "$readState Sketch from $senderName, received $formattedTime"

    Surface(
        shape = NoteCardShape,
        color = PaperIvory,
        shadowElevation = if (!sketch.isRead) 4.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open Sketch from $senderName") { onClick() }
            .semantics { contentDescription = cardDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail preview
            SketchlyThumbnail(
                strokes = sketch.strokes,
                size = 72.dp,
                modifier = Modifier.semantics {
                    contentDescription = "Sketch thumbnail preview"
                },
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (!sketch.isRead) FontWeight.Bold else FontWeight.Medium,
                    )

                    // Unread indicator — color + shape (not color alone, UIUX §9)
                    if (!sketch.isRead) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .semantics { contentDescription = "Unread" },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${sketch.strokes.size} strokes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
