package com.jrprofessor.sketchly.ui.screens.viewer

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.ui.theme.ToolBarBgColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Contact History Screen  (Image 1 reference)
// All scribbles exchanged with a contact, grouped by week in a 2-col grid.
// ─────────────────────────────────────────────────────────────────────────────

private val HISTORY_AVATAR_COLORS = listOf(
    Color(0xFF3D405B), Color(0xFF70A18A), Color(0xFFE07A5F),
    Color(0xFFBF4E6D), Color(0xFF4A6FA5), Color(0xFF8B6914),
    Color(0xFF6B4E71), Color(0xFF2A9D8F),
)
private fun historyAvatarColor(seed: String) =
    HISTORY_AVATAR_COLORS[abs(seed.hashCode()) % HISTORY_AVATAR_COLORS.size]

@Composable
fun ContactHistoryScreen(
    contactId: String,
    onBack: () -> Unit,
    onSketchTap: (String) -> Unit,
    onBlock: () -> Unit = {},
    viewModel: ContactHistoryViewModel = hiltViewModel(),
) {
    val contactDisplayName by viewModel.contactDisplayName.collectAsStateWithLifecycle()
    val contactAvatarUrl   by viewModel.contactAvatarUrl.collectAsStateWithLifecycle()
    val totalCount         by viewModel.totalCount.collectAsStateWithLifecycle()
    val sinceLabel         by viewModel.sinceLabel.collectAsStateWithLifecycle()
    val groupedSketches    by viewModel.groupedSketches.collectAsStateWithLifecycle()
    val isLoading          by viewModel.isLoading.collectAsStateWithLifecycle()
    val showBlockDialog    by viewModel.showBlockDialog.collectAsStateWithLifecycle()

    LaunchedEffect(contactId) { viewModel.load(contactId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        SketchlyTopBar(
            title  = "Sketchly",
            onBack = onBack,
            rightSlot = {
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector        = Icons.Outlined.MoreVert,
                            contentDescription = "More options",
                            tint               = AppNameColor,
                        )
                    }
                    DropdownMenu(
                        expanded         = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text  = "Block ${contactDisplayName.ifBlank { "user" }}",
                                    color = Color(0xFFD64242),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                viewModel.requestBlock()
                            },
                        )
                    }
                }
            },
        )

        // ── Content ──────────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier       = Modifier.fillMaxSize(),
        ) {
            // Hero header
            item(key = "hero") {
                ContactHeroHeader(
                    contactDisplayName = contactDisplayName,
                    contactAvatarUrl   = contactAvatarUrl,
                    totalCount         = totalCount,
                    sinceLabel         = sinceLabel,
                )
            }

            when {
                isLoading -> item(key = "skeleton") { SkeletonGrid() }
                groupedSketches.isEmpty() -> item(key = "empty") { EmptyState() }
                else -> {
                    groupedSketches.forEach { (weekLabel, sketches) ->
                        // Section header
                        item(key = "header_$weekLabel") {
                            Text(
                                text  = weekLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight    = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    fontSize      = 11.sp,
                                ),
                                color    = TextMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
                            )
                        }

                        // 2-column grid rows
                        val rows = sketches.chunked(2)
                        items(rows, key = { row -> "row_${row.first().id}" }) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { sketch ->
                                    SketchCard(
                                        sketch   = sketch,
                                        isSent   = sketch.senderDisplayName.isBlank(),
                                        onClick  = { onSketchTap(sketch.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // Fill empty slot for odd row
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // \u2500\u2500 Block confirmation dialog \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    if (showBlockDialog) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockDialog,
            title = {
                Text(
                    text  = "Block ${contactDisplayName.ifBlank { "this user" }}?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppNameColor,
                )
            },
            text = {
                Text(
                    text  = "They won't be able to send you scribbles. " +
                            "You can unblock them anytime in Settings \u2192 Blocked Users.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.blockContact(contactId, contactDisplayName)
                            onBlock()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD64242)),
                ) {
                    Text("Block", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissBlockDialog) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactHeroHeader(
    contactDisplayName: String,
    contactAvatarUrl: String?,
    totalCount: Int,
    sinceLabel: String,
) {
    val avatarColor = historyAvatarColor(contactDisplayName)
    val initials = contactDisplayName
        .split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("").ifEmpty { "?" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 20.dp),
    ) {
        // Large circular avatar — real photo if available, initials circle fallback
        if (!contactAvatarUrl.isNullOrBlank()) {
            // Real avatar image from Firestore
            AsyncImage(
                model = contactAvatarUrl,
                contentDescription = "$contactDisplayName's avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .border(2.dp, avatarColor.copy(alpha = 0.35f), CircleShape),
            )
        } else {
            // Initials fallback
            Surface(
                shape           = CircleShape,
                color           = avatarColor.copy(alpha = 0.15f),
                shadowElevation = 4.dp,
                modifier        = Modifier
                    .size(110.dp)
                    .border(2.dp, avatarColor.copy(alpha = 0.35f), CircleShape),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text  = initials,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize   = 40.sp,
                        ),
                        color = avatarColor,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text      = contactDisplayName.ifBlank { "—" },
            style     = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize   = 26.sp,
            ),
            color     = AppNameColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val subtitle = buildString {
            append("$totalCount Scribble${if (totalCount != 1) "s" else ""} exchanged")
            if (sinceLabel.isNotBlank()) append(" since $sinceLabel")
        }
        Text(
            text      = subtitle,
            style     = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color     = TextMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(
            modifier  = Modifier.padding(horizontal = 24.dp),
            thickness = 0.8.dp,
            color     = TextEditorBorderColor.copy(alpha = 0.6f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sketch Card (2-column grid cell)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SketchCard(
    sketch: Sketch,
    isSent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayLabel = remember(sketch.createdAt) {
        SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(sketch.createdAt))
    }

    Surface(
        shape           = RoundedCornerShape(16.dp),
        color           = PaperIvory,
        shadowElevation = 3.dp,
        modifier        = modifier
            .aspectRatio(0.85f)
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ↩ sent indicator (top-left corner)
            if (isSent) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "You sent this",
                    tint               = TextMuted.copy(alpha = 0.55f),
                    modifier           = Modifier
                        .padding(8.dp)
                        .size(15.dp)
                        .align(Alignment.TopStart),
                )
            }

            // Sketch thumbnail — centred, leaving room for day chip
            Box(
                modifier        = Modifier
                    .fillMaxSize()
                    .padding(bottom = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                SketchlyThumbnail(
                    strokes = sketch.strokes,
                    size    = 160.dp,
                )
            }

            // Day label chip (bottom-right)
            Surface(
                shape    = RoundedCornerShape(8.dp),
                color    = Color.White.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text     = dayLabel,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color    = AppNameColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / Skeleton states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✏️", style = MaterialTheme.typography.displayMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text      = "No scribbles yet",
                style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color     = AppNameColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text      = "Send a doodle to get the conversation started!",
                style     = MaterialTheme.typography.bodySmall,
                color     = TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SkeletonGrid() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TextEditorBorderColor.copy(alpha = 0.3f)),
            )
        }
    }
}
