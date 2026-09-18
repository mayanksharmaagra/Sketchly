package com.jrprofessor.sketchly.ui.screens.widget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddWidgetUiState(
    val strokes: List<Stroke> = emptyList(),
    val backgroundColor: String = "#F4F1DE",
    val isLoading: Boolean = true,
)

@HiltViewModel
class AddWidgetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sketchlyRepository: SketchlyRepository,
) : ViewModel() {

    private val sketchId: String = checkNotNull(savedStateHandle["sketchId"])

    private val _uiState = MutableStateFlow(AddWidgetUiState())
    val uiState: StateFlow<AddWidgetUiState> = _uiState.asStateFlow()

    init {
        loadSketch()
    }

    private fun loadSketch() {
        viewModelScope.launch {
            val sketch = sketchlyRepository.getSketchById(sketchId)
            _uiState.value = AddWidgetUiState(
                strokes = sketch?.strokes ?: emptyList(),
                backgroundColor = sketch?.backgroundColor ?: "#F4F1DE",
                isLoading = false,
            )
        }
    }

    fun scheduleWidgetUpdate() {
        sketchlyRepository.scheduleWidgetUpdate(sketchId)
    }
}
