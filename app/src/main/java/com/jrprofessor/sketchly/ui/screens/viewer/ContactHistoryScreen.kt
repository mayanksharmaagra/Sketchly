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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.ui.theme.ToolBarBgColor
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
    viewModel: ContactHistoryViewModel = hiltViewModel(),
) {
    val contactDisplayName by viewModel.contactDisplayName.collectAsStateWithLifecycle()
    val totalCount         by viewModel.totalCount.collectAsStateWithLifecycle()
    val sinceLabel         by viewModel.sinceLabel.collectAsStateWithLifecycle()
    val groupedSketches    by viewModel.groupedSketches.collectAsStateWithLifecycle()
    val isLoading          by viewModel.isLoading.collectAsStateWithLifecycle()

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
                    totalCount         = totalCount,
                    sinceLabel         = sinceLabel,
                    heroSketch         = groupedSketches.values.flatten().firstOrNull(),
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactHeroHeader(
    contactDisplayName: String,
    totalCount: Int,
    sinceLabel: String,
    heroSketch: Sketch?,
) {
    val avatarColor = historyAvatarColor(contactDisplayName)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 20.dp),
    ) {
        // Large circular avatar — uses most recent sketch thumbnail
        Surface(
            shape           = CircleShape,
            color           = avatarColor.copy(alpha = 0.12f),
            shadowElevation = 4.dp,
            modifier        = Modifier
                .size(110.dp)
                .border(2.dp, avatarColor.copy(alpha = 0.25f), CircleShape),
        ) {
            if (heroSketch != null) {
                SketchlyThumbnail(
                    strokes = heroSketch.strokes,
                    size    = 110.dp,
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.fillMaxSize(),
                ) {
                    Text(
                        text  = contactDisplayName
                            .split(" ").take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                            .joinToString("").ifEmpty { "?" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
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
