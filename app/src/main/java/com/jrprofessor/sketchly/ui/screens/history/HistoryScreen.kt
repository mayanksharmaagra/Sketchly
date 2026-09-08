package com.jrprofessor.sketchly.ui.screens.history

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.local.SenderInfo
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.data.repository.HistoryFilter
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.components.TOP_BAR_HEIGHT
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.ui.theme.ToolBarBgColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// History Screen (dedicated full-screen route — bottom-bar History tab)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen History screen — date-grouped 2-column grid of all past Sketches.
 * Reuses [HistoryViewModel] for pagination, filtering, and sender loading.
 */
@Composable
fun HistoryScreen(
    onSketchTap: (String) -> Unit = {},
    onProfileTap: () -> Unit = {},
    onSettingsTap: () -> Unit = {},
    onDrawTap: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val grouped by viewModel.groupedSketches.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val senders by viewModel.senders.collectAsStateWithLifecycle()

    HistoryContent(
        grouped = grouped,
        filter = filter,
        senders = senders,
        onSketchTap = onSketchTap,
        onFilterChange = viewModel::setFilter,
        onLoadMore = viewModel::loadMore,
        onProfileTap = onProfileTap,
        onSettingsTap = onSettingsTap,
        onDrawTap = onDrawTap,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryContent(
    grouped: Map<String, List<Sketch>>,
    filter: HistoryFilter,
    senders: List<SenderInfo>,
    onSketchTap: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onLoadMore: () -> Unit,
    onProfileTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onDrawTap: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Trigger pagination near bottom (SRS FR-8.3)
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(isAtBottom) { if (isAtBottom) onLoadMore() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        HistoryTopBar(onProfileTap = onProfileTap, onSettingsTap = onSettingsTap)

        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            item {
                HistoryChip(
                    label = "All",
                    selected = filter == HistoryFilter.All,
                    onClick = { onFilterChange(HistoryFilter.All) },
                )
            }
            item {
                HistoryChip(
                    label = "Sent",
                    selected = filter == HistoryFilter.Sent,
                    onClick = { onFilterChange(HistoryFilter.Sent) },
                )
            }
            item {
                HistoryChip(
                    label = "Received",
                    selected = filter == HistoryFilter.Received,
                    onClick = { onFilterChange(HistoryFilter.Received) },
                )
            }
            items(senders) { sender ->
                val name = sender.senderDisplayName.takeIf { it.isNotBlank() }
                    ?: sender.senderId.take(6)
                HistoryChip(
                    label = name,
                    selected = filter is HistoryFilter.ByContact &&
                            (filter as HistoryFilter.ByContact).senderId == sender.senderId,
                    onClick = { onFilterChange(HistoryFilter.ByContact(sender.senderId)) },
                )
            }
        }

        // Body
        if (grouped.isEmpty()) {
            HistoryEmptyState(onDrawTap = onDrawTap)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 100.dp,
                ),
            ) {
                grouped.forEach { (dateLabel, sketches) ->
                    stickyHeader(key = "header_$dateLabel") {
                        HistoryDateHeader(label = dateLabel)
                    }
                    val rows = sketches.chunked(2)
                    itemsIndexed(
                        rows,
                        key = { rowIndex, row -> "row_${dateLabel}_${rowIndex}_${row.joinToString("_") { it.id }}" }
                    ) { _, rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowItems.forEach { sketch ->
                                HistoryGridCard(
                                    sketch = sketch,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSketchTap(sketch.id) },
                                )
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTopBar(
    onProfileTap: () -> Unit,
    onSettingsTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ToolBarBgColor)
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Profile avatar circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AppNameColor.copy(alpha = 0.10f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onProfileTap() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✦", fontSize = 16.sp, color = AppNameColor)
        }

        // Screen title
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
            ),
            color = AppNameColor,
        )

        // Settings gear
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(AppNameColor.copy(alpha = 0.08f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onSettingsTap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = AppNameColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date section sticky header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryDateHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            color = AppNameColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppNameColor,
            selectedLabelColor = Color.White,
            containerColor = PaperIvory,
            labelColor = TextMuted,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = TextEditorBorderColor,
            selectedBorderColor = Color.Transparent,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryGridCard(
    sketch: Sketch,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val senderLabel = sketch.senderDisplayName.takeIf { it.isNotBlank() } ?: "Unknown"
    val dateLabel = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(sketch.createdAt))
    val cardBg = try {
        hexToColor(sketch.backgroundColor)
    } catch (_: Exception) {
        PaperIvory
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = PaperIvory,
        border = BorderStroke(3.dp, ToolBarBgColor),
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClickLabel = "Open sketch from $senderLabel") { onClick() }
            .semantics { contentDescription = "Sketch from $senderLabel on $dateLabel" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(0.8.dp, Color(0x40000000), RoundedCornerShape(16.dp))
                .background(cardBg),
            contentAlignment = Alignment.Center,
        ) {
            SketchlyThumbnail(
                strokes = sketch.strokes,
                backgroundColor = cardBg,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state — mirrors Dashboard EmptyActivityState design
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryEmptyState(onDrawTap: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperIvory,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🕐", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No history yet",
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose Previews — no ViewModel / Hilt required
// ─────────────────────────────────────────────────────────────────────────────

private fun fakeSketches(): List<Sketch> {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    return listOf(
        Sketch(
            id = "1",
            senderId = "uid_alice",
            senderDisplayName = "Alice",
            strokes = listOf(
                com.jrprofessor.sketchly.data.model.Stroke(
                    points = listOf(
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.2f, 0.3f),
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.5f, 0.6f),
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.8f, 0.4f),
                    ),
                ),
            ),
            createdAt = now,
        ),
        Sketch(
            id = "2",
            senderId = "uid_bob",
            senderDisplayName = "Bob",
            strokes = listOf(
                com.jrprofessor.sketchly.data.model.Stroke(
                    points = listOf(
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.1f, 0.8f),
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.4f, 0.2f),
                        com.jrprofessor.sketchly.data.model.DrawPoint(0.9f, 0.7f),
                    ),
                ),
            ),
            createdAt = now,
        ),
        Sketch(
            id = "3",
            senderId = "uid_carol",
            senderDisplayName = "Carol",
            strokes = emptyList(),
            createdAt = now - day,
        ),
        Sketch(
            id = "4",
            senderId = "uid_alice",
            senderDisplayName = "Alice",
            strokes = emptyList(),
            createdAt = now - day,
        ),
        Sketch(
            id = "5",
            senderId = "uid_dan",
            senderDisplayName = "Dan",
            strokes = emptyList(),
            createdAt = now - 3 * day,
        ),
    )
}

private fun fakeGrouped(): Map<String, List<Sketch>> {
    val all = fakeSketches()
    return linkedMapOf(
        "Today" to all.filter { it.id in listOf("1", "2") },
        "Yesterday" to all.filter { it.id in listOf("3", "4") },
        "This Week" to all.filter { it.id == "5" },
    )
}

private fun fakeSenders() = listOf(
    com.jrprofessor.sketchly.data.local.SenderInfo(
        senderId = "uid_alice",
        senderDisplayName = "Alice"
    ),
    com.jrprofessor.sketchly.data.local.SenderInfo(senderId = "uid_bob", senderDisplayName = "Bob"),
    com.jrprofessor.sketchly.data.local.SenderInfo(
        senderId = "uid_carol",
        senderDisplayName = "Carol"
    ),
    com.jrprofessor.sketchly.data.local.SenderInfo(senderId = "uid_dan", senderDisplayName = "Dan"),
)

/** Full screen — populated with sample sketches, All filter active. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "History – Populated",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun PreviewHistoryPopulated() {
    com.jrprofessor.sketchly.ui.theme.SketchlyTheme {
        HistoryContent(
            grouped = fakeGrouped(),
            filter = HistoryFilter.All,
            senders = fakeSenders(),
            onSketchTap = {},
            onFilterChange = {},
            onLoadMore = {},
            onProfileTap = {},
            onSettingsTap = {},
            onDrawTap = {},
        )
    }
}

/** Full screen — Sent filter chip selected. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "History – Sent filter",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun PreviewHistorySentFilter() {
    com.jrprofessor.sketchly.ui.theme.SketchlyTheme {
        HistoryContent(
            grouped = linkedMapOf("Today" to fakeSketches().take(1)),
            filter = HistoryFilter.Sent,
            senders = fakeSenders(),
            onSketchTap = {},
            onFilterChange = {},
            onLoadMore = {},
            onProfileTap = {},
            onSettingsTap = {},
            onDrawTap = {},
        )
    }
}

/** Full screen — empty state, no sketches yet. */
@androidx.compose.ui.tooling.preview.Preview(
    name = "History – Empty state",
    showBackground = true,
    device = "spec:width=411dp,height=891dp",
)
@Composable
private fun PreviewHistoryEmpty() {
    com.jrprofessor.sketchly.ui.theme.SketchlyTheme {
        HistoryContent(
            grouped = emptyMap(),
            filter = HistoryFilter.All,
            senders = emptyList(),
            onSketchTap = {},
            onFilterChange = {},
            onLoadMore = {},
            onProfileTap = {},
            onSettingsTap = {},
            onDrawTap = {},
        )
    }
}