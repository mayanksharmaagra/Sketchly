package com.jrprofessor.sketchly.ui.screens.draw

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.colorToHex
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.ui.theme.DeepCharcoal
import com.jrprofessor.sketchly.ui.theme.InkDefault
import com.jrprofessor.sketchly.ui.theme.Primary
import com.jrprofessor.sketchly.ui.theme.Tertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val PEN_COLORS: List<Color> = listOf(
    InkDefault,                 // Near-black
    DeepCharcoal,               // Blue-charcoal
    Primary,                    // Terracotta
    Tertiary,                   // Sage green
    Color(0xFF8B6914),          // Ochre/brown
    Color(0xFFCBC0B4),          // Warm grey / light pencil
    Color(0xFF4A6FA5),          // Denim blue
    Color(0xFFBF4E6D),          // Rose
)

val PEN_THICKNESSES: List<Float> = listOf(2f, 4f, 8f, 14f, 22f)

data class DrawUiState(
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val selectedColor: Color = InkDefault,
    val selectedThickness: Float = 4f,
    val canSend: Boolean = false,
    val isRecipientPickerOpen: Boolean = false,
    val selectedContactIds: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val sendSuccessMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
    private val sketchRepository: SketchlyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawUiState())
    val uiState: StateFlow<DrawUiState> = _uiState.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                contactRepository.getContacts(user.uid)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Restore draft if available (SRS FR-3.5)
        viewModelScope.launch {
            val draft = sketchRepository.getDraft()
            if (draft.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        strokes = draft,
                        canSend = true
                    )
                }
            }
        }
    }

    // ── Stroke capture ──

    fun onStrokeStart(point: DrawPoint) {
        val newStroke = Stroke(
            points = listOf(point),
            colorHex = colorToHex(_uiState.value.selectedColor),
            widthDp = _uiState.value.selectedThickness,
        )
        _uiState.update { it.copy(currentStroke = newStroke) }
    }

    fun onStrokeMove(point: DrawPoint) {
        _uiState.update { state ->
            val current = state.currentStroke ?: return@update state
            state.copy(
                currentStroke = current.copy(
                    points = current.points + point,
                ),
            )
        }
    }

    fun onStrokeEnd() {
        _uiState.update { state ->
            val finished = state.currentStroke ?: return@update state
            val updatedStrokes = if (finished.points.size >= 2) {
                state.strokes + finished
            } else {
                state.strokes
            }
            state.copy(
                strokes = updatedStrokes,
                currentStroke = null,
                canSend = updatedStrokes.isNotEmpty(),
            )
        }
        // Persist draft to Room
        viewModelScope.launch {
            sketchRepository.saveDraft(_uiState.value.strokes)
        }
    }

    // ── Pen controls ──

    fun selectColor(color: Color) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun selectThickness(thickness: Float) {
        _uiState.update { it.copy(selectedThickness = thickness) }
    }

    // ── Undo / Clear ──

    fun undo() {
        _uiState.update { state ->
            val updated = state.strokes.dropLast(1)
            state.copy(
                strokes = updated,
                canSend = updated.isNotEmpty(),
            )
        }
        viewModelScope.launch {
            sketchRepository.saveDraft(_uiState.value.strokes)
        }
    }

    fun clear() {
        _uiState.update {
            it.copy(
                strokes = emptyList(),
                currentStroke = null,
                canSend = false,
            )
        }
        viewModelScope.launch {
            sketchRepository.clearDraft()
        }
    }

    // ── Recipient Picker & Send Flow ──

    fun openRecipientPicker() {
        _uiState.update {
            it.copy(
                isRecipientPickerOpen = true,
                sendSuccessMessage = null,
                errorMessage = null,
            )
        }
    }

    fun closeRecipientPicker() {
        _uiState.update { it.copy(isRecipientPickerOpen = false) }
    }

    fun toggleContactSelection(contactId: String) {
        _uiState.update { state ->
            val updated = if (state.selectedContactIds.contains(contactId)) {
                state.selectedContactIds - contactId
            } else {
                state.selectedContactIds + contactId
            }
            state.copy(selectedContactIds = updated)
        }
    }

    fun sendSketch(onSent: () -> Unit = {}) {
        val state = _uiState.value
        val senderId = authRepository.currentUserId
        if (senderId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Please sign in to send Sketches.") }
            return
        }

        if (state.selectedContactIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least 1 recipient.") }
            return
        }

        if (state.strokes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Canvas is empty.") }
            return
        }

        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val recipientList = state.selectedContactIds.toList()
            // Resolve current user's display name for recipient Inbox display
            val senderDisplayName = authRepository.getUserProfile(senderId)?.displayName ?: ""
            val result = sketchRepository.sendSketch(
                senderId = senderId,
                senderDisplayName = senderDisplayName,
                recipientIds = recipientList,
                strokes = state.strokes,
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            isRecipientPickerOpen = false,
                            strokes = emptyList(),
                            currentStroke = null,
                            canSend = false,
                            selectedContactIds = emptySet(),
                            sendSuccessMessage = "Sketch sent!",
                        )
                    }
                    onSent()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = err.localizedMessage ?: "Failed to send."
                        )
                    }
                }
            )
        }
    }
}
