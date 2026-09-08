package com.jrprofessor.sketchly.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.SenderInfo
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.repository.HistoryFilter
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

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
class HistoryViewModel @Inject constructor(
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