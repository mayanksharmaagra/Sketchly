package com.jrprofessor.sketchly.ui.screens.draw

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.model.ContactSource
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.colorToHex
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.BlockRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.data.model.toContactEntity
import com.jrprofessor.sketchly.ui.theme.DeepCharcoal
import com.jrprofessor.sketchly.ui.theme.InkDefault
import com.jrprofessor.sketchly.ui.theme.Primary
import com.jrprofessor.sketchly.ui.theme.Tertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Groups the recipient-picker list into two visually distinct sections:
 *  - [yourContacts]  — CONNECTED contacts + contact-sync matches
 *  - [scribbledYou]  — empty in new model; kept for API compatibility with SendToScreen
 */
data class ContactGroups(
    val yourContacts: List<SketchlyContact> = emptyList(),
    val scribbledYou: List<SketchlyContact> = emptyList(), // V2: may surface pending-received requests
)

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
    // Send-to flow
    val selectedContactIds: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val showSentDialog: Boolean = false,
    val sentToNames: List<String> = emptyList(),
    val errorMessage: String? = null,
    /** ID of the sketch that was just sent — used by "Add to Widget" action. */
    val lastSentSketchId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrawViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
    private val sketchRepository: SketchlyRepository,
    private val blockRepository: BlockRepository,
) : ViewModel() {

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("sketchly_prefs", Context.MODE_PRIVATE)

    private val _hasDrawnBefore = MutableStateFlow(prefs.getBoolean("has_drawn_before", false))
    /** True once the user has committed at least one stroke in any session. */
    val hasDrawnBefore: StateFlow<Boolean> = _hasDrawnBefore.asStateFlow()

    private val _uiState = MutableStateFlow(DrawUiState())
    val uiState: StateFlow<DrawUiState> = _uiState.asStateFlow()

    /**
     * Merged contact groups for the recipient picker.
     *
     * Merge rules (new connection-request model):
     *  - [yourContacts] = connected contacts + suggested (contact-sync matches), deduplicated.
     *    Only CONNECTED users can actually receive Scribbles; suggested are shown so the sender
     *    knows who they're connecting with automatically on send.
     *  - [scribbledYou] = empty in V1 of this model (reverseConnections removed).
     */
    /**
     * Real-time stream of blocked user IDs for the current user.
     * Used as a second-layer filter on top of the ContactRepository-level block filter,
     * guarding against any cached Firestore snapshot that fires before the Cloud Function
     * removes the connection document.
     */
    private val blockedIds: StateFlow<Set<String>> =
        blockRepository.getBlockedUsers()
            .map { list -> list.map { it.blockedUserId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val contactGroups: StateFlow<ContactGroups> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                combine(
                    contactRepository.getSuggestedContacts(),
                    contactRepository.getConnectedContacts(),
                    blockedIds,
                ) { suggested, connected, blocked ->
                    val all = (connected + suggested)
                        .distinctBy { it.userId }
                        .filter { it.userId !in blocked }
                    ContactGroups(yourContacts = all)
                }
            } else {
                flowOf(ContactGroups())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactGroups())

    /**
     * Flat [ContactEntity] list for backward-compatible selection UX.
     * Derived from [contactGroups].
     */
    val contacts: StateFlow<List<ContactEntity>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                combine(
                    contactRepository.getSuggestedContacts(),
                    contactRepository.getConnectedContacts(),
                    blockedIds,
                ) { suggested, connected, blocked ->
                    (connected + suggested)
                        .distinctBy { it.userId }
                        .filter { it.userId !in blocked }
                        .map { it.toContactEntity(user.uid) }
                }
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
            if (!_hasDrawnBefore.value) {
                val sent = sketchRepository.getSentSketches().firstOrNull()
                if (!sent.isNullOrEmpty()) {
                    _hasDrawnBefore.value = true
                    prefs.edit().putBoolean("has_drawn_before", true).apply()
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

    // ── Send Flow ──

    fun dismissSentDialog() {
        _uiState.update {
            it.copy(
                showSentDialog = false,
                sentToNames = emptyList(),
                selectedContactIds = emptySet(),
            )
        }
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
                onSuccess = { sketch ->
                    // Resolve names for the confirmation dialog
                    val names = contacts.value
                        .filter { it.id in state.selectedContactIds }
                        .map { it.displayName }
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            strokes = emptyList(),
                            currentStroke = null,
                            canSend = false,
                            showSentDialog = true,
                            sentToNames = names,
                            lastSentSketchId = sketch.id,
                        )
                    }
                    _hasDrawnBefore.value = true
                    prefs.edit().putBoolean("has_drawn_before", true).apply()
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

    /**
     * Schedules a WidgetUpdateWorker for [sketchId] so the home screen widget
     * immediately shows that scribble. Called from the "Add to Widget" action
     * in [SendToScreen]'s confirmation dialog.
     */
    fun scheduleWidgetUpdate(sketchId: String) {
        sketchRepository.scheduleWidgetUpdate(sketchId)
    }
}
