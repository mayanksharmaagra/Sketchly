package com.jrprofessor.sketchly.ui.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.SenderInfo
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.repository.HistoryFilter
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.theme.NoteCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Date group label logic (SRS FR-8.1) ──

private fun dateGroupLabel(createdAt: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.clone() as Calendar
    cal.timeInMillis = createdAt

    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekAgoCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }

    return when {
        cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        cal.timeInMillis > weekAgoCal.timeInMillis -> "This Week"
        else -> "Earlier"
    }
}

// ── ViewModel ──

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val sketchRepository: SketchlyRepository,
) : ViewModel() {

    private val pageSize = SketchlyRepository.PAGE_SIZE

    private val _filter = MutableStateFlow<HistoryFilter>(HistoryFilter.All)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    private val _sketches = MutableStateFlow<List<Sketch>>(emptyList())
    private var _currentPage = 0
    private var _hasMore = true
    private var _isLoadingMore = false

    /** Sketches grouped by date label for sticky-header display */
    val groupedSketches: StateFlow<Map<String, List<Sketch>>> =
        _sketches.combine(_filter) { list, _ ->
            list.distinctBy { it.id }
                .groupBy { dateGroupLabel(it.createdAt) }
                .let { grouped ->
                    val order = listOf("Today", "Yesterday", "This Week", "Earlier")
                    linkedMapOf<String, List<Sketch>>().apply {
                        order.forEach { label ->
                            grouped[label]?.let { put(label, it) }
                        }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    /** Unique senders from local cache — used for contact filter chips */
    private val _senders = MutableStateFlow<List<SenderInfo>>(emptyList())
    val senders: StateFlow<List<SenderInfo>> = _senders.asStateFlow()

    init {
        loadFirstPage()
        loadSenders()
    }

    fun setFilter(filter: HistoryFilter) {
        _filter.update { filter }
        _currentPage = 0
        _hasMore = true
        _sketches.update { emptyList() }
        loadFirstPage()
    }

    fun loadMore() {
        if (!_hasMore || _isLoadingMore) return
        _isLoadingMore = true
        viewModelScope.launch {
            val page = sketchRepository.getPagedHistory(_filter.value, _currentPage, pageSize)
            if (page.isEmpty()) {
                _hasMore = false
            } else {
                _sketches.update { current -> (current + page).distinctBy { it.id } }
                _currentPage++
                if (page.size < pageSize) _hasMore = false
            }
            _isLoadingMore = false
        }
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            val page = sketchRepository.getPagedHistory(_filter.value, 0, pageSize)
            _sketches.update { page.distinctBy { it.id } }
            _currentPage = 1
            _hasMore = page.size >= pageSize
        }
    }

    private fun loadSenders() {
        viewModelScope.launch {
            _senders.update { sketchRepository.getDistinctSenders() }
        }
    }
}

// ── Composable ──

/**
 * History / Archive screen — date-grouped view with filter chips and pagination.
 * SRS FR-8: date grouping, contact filter, pagination (no full-dataset in memory).
 */
@Composable
fun ArchiveScreen(
    onSketchTap: (String) -> Unit = {},
    viewModel: ArchiveViewModel = hiltViewModel(),
) {
    val grouped by viewModel.groupedSketches.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val senders by viewModel.senders.collectAsStateWithLifecycle()

    ArchiveScreenContent(
        grouped = grouped,
        filter = filter,
        senders = senders,
        onSketchTap = onSketchTap,
        onFilterChange = viewModel::setFilter,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun ArchiveScreenContent(
    grouped: Map<String, List<Sketch>>,
    filter: HistoryFilter,
    senders: List<SenderInfo>,
    onSketchTap: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // Detect scroll-to-bottom to trigger pagination (SRS FR-8.3)
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) onLoadMore()
    }

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
                    text = "History",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${grouped.values.sumOf { it.size }} notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✦",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // ── Filter Chips Row (SRS FR-8.2) ──
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            // Built-in filters
            item {
                HistoryFilterChip(
                    label = "All",
                    selected = filter == HistoryFilter.All,
                    onClick = { onFilterChange(HistoryFilter.All) },
                )
            }
            item {
                HistoryFilterChip(
                    label = "Sent",
                    selected = filter == HistoryFilter.Sent,
                    onClick = { onFilterChange(HistoryFilter.Sent) },
                )
            }
            item {
                HistoryFilterChip(
                    label = "Received",
                    selected = filter == HistoryFilter.Received,
                    onClick = { onFilterChange(HistoryFilter.Received) },
                )
            }
            // One chip per unique sender contact (derived from local Room cache)
            items(senders) { sender ->
                val displayName = sender.senderDisplayName.takeIf { it.isNotBlank() }
                    ?: sender.senderId.take(6)
                HistoryFilterChip(
                    label = displayName,
                    selected = filter is HistoryFilter.ByContact &&
                        (filter as HistoryFilter.ByContact).senderId == sender.senderId,
                    onClick = { onFilterChange(HistoryFilter.ByContact(sender.senderId)) },
                )
            }
        }

        // ── Content ──
        if (grouped.isEmpty()) {
            HistoryEmptyState()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                grouped.forEach { (dateLabel, sketches) ->
                    // Sticky date section header
                    stickyHeader(key = "header_$dateLabel") {
                        DateSectionHeader(label = dateLabel)
                    }

                    // 2-column grid inside LazyColumn using manual row chunking (avoids nested LazyGrid)
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
                                ArchiveGridItem(
                                    sketch = sketch,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSketchTap(sketch.id) },
                                )
                            }
                            // Fill empty slot if odd count
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Previews ──

@Preview(name = "Archive — Empty", showBackground = true)
@Composable
private fun ArchiveScreenEmptyPreview() {
    SketchlyTheme {
        ArchiveScreenContent(
            grouped = emptyMap(),
            filter = HistoryFilter.All,
            senders = emptyList(),
            onSketchTap = {},
            onFilterChange = {},
            onLoadMore = {},
        )
    }
}

@Preview(name = "Archive — With Items", showBackground = true)
@Composable
private fun ArchiveScreenPopulatedPreview() {
    val fakeSketches = listOf(
        Sketch(id = "1", senderId = "uid1", senderDisplayName = "Alice", recipientIds = emptyList(), strokes = emptyList(), createdAt = System.currentTimeMillis(), isRead = true),
        Sketch(id = "2", senderId = "uid2", senderDisplayName = "Bob",   recipientIds = emptyList(), strokes = emptyList(), createdAt = System.currentTimeMillis(), isRead = false),
    )
    SketchlyTheme {
        ArchiveScreenContent(
            grouped = mapOf("Today" to fakeSketches),
            filter = HistoryFilter.All,
            senders = listOf(SenderInfo(senderId = "uid1", senderDisplayName = "Alice")),
            onSketchTap = {},
            onFilterChange = {},
            onLoadMore = {},
        )
    }
}


@Composable
private fun DateSectionHeader(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HistoryFilterChip(
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
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        ),
        shape = MaterialTheme.shapes.extraLarge, // Washi-tape pill shape (DESIGN.md)
    )
}

@Composable
private fun ArchiveGridItem(
    sketch: Sketch,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val senderLabel = sketch.senderDisplayName.takeIf { it.isNotBlank() } ?: "Unknown sender"
    val dateLabel = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(sketch.createdAt))
    val cardDesc = "Sketch from $senderLabel on $dateLabel"

    Surface(
        shape = NoteCardShape,
        color = PaperIvory,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open Sketch from $senderLabel") { onClick() }
            .semantics { contentDescription = cardDesc },
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SketchlyThumbnail(
                strokes = sketch.strokes,
                size = 130.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            val senderName = sketch.senderDisplayName.takeIf { it.isNotBlank() }
            if (senderName != null) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            val dateFormatted = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(sketch.createdAt))
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
                        text = "📋",
                        style = MaterialTheme.typography.displayLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Nothing here yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your sent and received Sketches\nwill be collected here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
