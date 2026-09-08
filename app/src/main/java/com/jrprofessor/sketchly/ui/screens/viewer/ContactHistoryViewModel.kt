package com.jrprofessor.sketchly.ui.screens.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── Week-bucket label ─────────────────────────────────────────────────────────

private fun weekLabel(createdAt: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = createdAt

    val thisWeekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);     set(Calendar.MILLISECOND, 0)
    }
    val lastWeekStart = (thisWeekStart.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -1) }

    return when {
        cal.timeInMillis >= thisWeekStart.timeInMillis -> "THIS WEEK"
        cal.timeInMillis >= lastWeekStart.timeInMillis -> "LAST WEEK"
        else -> SimpleDateFormat("MMM yyyy", Locale.getDefault())
            .format(Date(createdAt)).uppercase(Locale.getDefault())
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ContactHistoryViewModel @Inject constructor(
    private val sketchRepository: SketchlyRepository,
) : ViewModel() {

    /** Live display name of the contact being viewed */
    private val _contactDisplayName = MutableStateFlow("")
    val contactDisplayName: StateFlow<String> = _contactDisplayName.asStateFlow()

    /** Total count of sketches exchanged with this contact */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** Month + year of the earliest sketch, e.g. "Jun 2026" */
    private val _sinceLabel = MutableStateFlow("")
    val sinceLabel: StateFlow<String> = _sinceLabel.asStateFlow()

    /** Sketches grouped by week bucket, ordered THIS WEEK first */
    private val _groupedSketches = MutableStateFlow<Map<String, List<Sketch>>>(emptyMap())
    val groupedSketches: StateFlow<Map<String, List<Sketch>>> = _groupedSketches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(contactId: String) {
        viewModelScope.launch {
            sketchRepository.getSketchesWithContact(contactId).collect { sketches ->
                if (sketches.isEmpty()) {
                    _groupedSketches.value = emptyMap()
                    _isLoading.value = false
                    return@collect
                }

                // Derive contact display name from received sketches (senderId == contactId)
                val name = sketches
                    .firstOrNull { it.senderId == contactId }
                    ?.senderDisplayName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Unknown"
                _contactDisplayName.value = name

                _totalCount.value = sketches.size

                val earliest = sketches.minOf { it.createdAt }
                _sinceLabel.value = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                    .format(Date(earliest))

                // Group by week bucket, ordered: THIS WEEK -> LAST WEEK -> month buckets desc
                val bucketOrder = listOf("THIS WEEK", "LAST WEEK")
                val rawGrouped = sketches.groupBy { weekLabel(it.createdAt) }
                val ordered = linkedMapOf<String, List<Sketch>>()
                bucketOrder.forEach { key -> rawGrouped[key]?.let { ordered[key] = it } }
                rawGrouped.keys
                    .filter { it !in bucketOrder }
                    .sortedDescending()
                    .forEach { key -> rawGrouped[key]?.let { ordered[key] = it } }
                _groupedSketches.value = ordered

                _isLoading.value = false
            }
        }
    }
}
