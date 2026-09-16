package com.jrprofessor.sketchly.ui.screens.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.ConnectionStatus
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.utils.FeatureFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CircleTab { SUGGESTED, REQUESTS, CONNECTED }

data class CircleUiState(
    val selectedTab: CircleTab = if (FeatureFlags.HIDE_SUGGESTED_TAB) CircleTab.CONNECTED
                                 else CircleTab.SUGGESTED,
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    /** Optimistic set: user IDs for which Follow was tapped but Firestore hasn't confirmed yet.
     *  Drives the "Pending" button state immediately without waiting for a Firestore snapshot. */
    val pendingUserIds: Set<String> = emptySet(),
    /** True while a Firestore username search is in-flight. */
    val isSearching: Boolean = false,
    // Legacy add-dialog state — kept for V2 (UI hidden in V1)
    val isAddDialogOpen: Boolean = false,
    val newContactName: String = "",
    val newContactEmailOrPhone: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CircleViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CircleUiState())
    val uiState: StateFlow<CircleUiState> = _uiState.asStateFlow()

    // ── Suggested contacts (contact-sync results, not yet connected) ──────────
    val suggestedContacts: StateFlow<List<SketchlyContact>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) contactRepository.getSuggestedContacts()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Incoming connection requests (Requests tab) ─────────────────────────
    // [FEATURE FLAGGED — V2]
    // When ENABLE_CONNECTION_REQUESTS = false the listener returns emptyList() immediately
    // to avoid unnecessary Firestore reads. The flow, ViewModel methods (acceptRequest,
    // declineRequest) and CircleTab.REQUESTS are all preserved — flip the flag to re-enable.
    val incomingRequests: StateFlow<List<SketchlyContact>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null && FeatureFlags.ENABLE_CONNECTION_REQUESTS)
                contactRepository.getIncomingConnectionRequests()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Outgoing pending requests (used to derive Suggested tab states) ────────
    // [FEATURE FLAGGED — V2] Same guard as incomingRequests above.
    val outgoingRequests: StateFlow<List<SketchlyContact>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null && FeatureFlags.ENABLE_CONNECTION_REQUESTS)
                contactRepository.getOutgoingConnectionRequests()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Confirmed connections (Connected tab) ─────────────────────────────
    val connectedContacts: StateFlow<List<SketchlyContact>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) contactRepository.getConnectedContacts()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Firestore username search results ─────────────────────────────────────
    // Driven by searchQuery with a 400ms debounce. Only fires when query >= 2 chars.
    private val _searchResults = MutableStateFlow<List<SketchlyContact>>(emptyList())
    val searchResults: StateFlow<List<SketchlyContact>> = _searchResults.asStateFlow()

    // ── Tab selection ─────────────────────────────────────────────────────────

    fun selectTab(tab: CircleTab) {
        // Safety net: REQUESTS tab is unreachable when the flag is off,
        // but guard here too in case of stale state or deep-link.
        if (tab == CircleTab.REQUESTS && !FeatureFlags.ENABLE_CONNECTION_REQUESTS) return
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.trim().length < 2) {
            // Clear results immediately for short/empty queries
            _searchResults.value = emptyList()
            _uiState.update { it.copy(isSearching = false) }
            return
        }
        // Debounce: launch search after 400ms of inactivity
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            kotlinx.coroutines.delay(400)
            // Re-read current query in case user kept typing during delay
            val currentQuery = _uiState.value.searchQuery.trim()
            if (currentQuery.length >= 2) {
                val results = contactRepository.searchByUsername(currentQuery)
                // Merge optimistic pending IDs into search results so button shows "Pending" correctly
                val pending = _uiState.value.pendingUserIds
                _searchResults.value = results.map { contact ->
                    if (contact.userId in pending && contact.connectionStatus == ConnectionStatus.SUGGESTED)
                        contact.copy(connectionStatus = ConnectionStatus.PENDING_SENT)
                    else contact
                }
            }
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    // ── Send connection request ──────────────────────────────────────────

    fun sendConnectionRequest(contact: SketchlyContact) {
        // 1. Optimistically flip the button to "Pending" immediately
        _uiState.update { it.copy(pendingUserIds = it.pendingUserIds + contact.userId) }
        // Also update search results list if contact is in there
        _searchResults.update { list ->
            list.map {
                if (it.userId == contact.userId)
                    it.copy(connectionStatus = ConnectionStatus.PENDING_SENT)
                else it
            }
        }
        viewModelScope.launch {
            val result = contactRepository.sendConnectionRequest(contact)
            if (result.isFailure) {
                // Revert optimistic state if the Firestore write actually failed
                _uiState.update { it.copy(pendingUserIds = it.pendingUserIds - contact.userId) }
                _searchResults.update { list ->
                    list.map {
                        if (it.userId == contact.userId)
                            it.copy(connectionStatus = ConnectionStatus.SUGGESTED)
                        else it
                    }
                }
            }
        }
    }

    // ── Cancel outgoing request ──────────────────────────────────────────

    fun cancelConnectionRequest(contact: SketchlyContact) {
        viewModelScope.launch {
            contactRepository.cancelConnectionRequest(contact.userId)
        }
    }

    // ── Accept / Decline incoming request ──────────────────────────────────

    fun acceptRequest(contact: SketchlyContact) {
        viewModelScope.launch {
            contactRepository.acceptConnectionRequest(contact.userId)
        }
    }

    fun declineRequest(contact: SketchlyContact) {
        viewModelScope.launch {
            contactRepository.declineConnectionRequest(contact.userId)
        }
    }

    // ── Sync contacts ─────────────────────────────────────────────────────────

    fun syncContacts() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = null) }
            val result = contactRepository.syncContacts()
            val msg = result.fold(
                onSuccess = { count ->
                    if (count == 0) "No new contacts found on Sketchly."
                    else "Found $count contact${if (count == 1) "" else "s"} on Sketchly!"
                },
                onFailure = { e ->
                    if (e.message?.contains("limit", ignoreCase = true) == true)
                        "Sync limit reached — try again later."
                    else
                        "Sync failed. Check your connection."
                }
            )
            _uiState.update { it.copy(isSyncing = false, syncMessage = msg) }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    // ── Legacy V1 stubs (kept for V2) ────────────────────────────────────────

    fun openAddDialog() { _uiState.update { it.copy(isAddDialogOpen = true) } }
    fun closeAddDialog() { _uiState.update { it.copy(isAddDialogOpen = false) } }
    fun onNameChanged(name: String) { _uiState.update { it.copy(newContactName = name) } }
    fun onEmailOrPhoneChanged(input: String) { _uiState.update { it.copy(newContactEmailOrPhone = input) } }
    fun addContact() { _uiState.update { it.copy(isAddDialogOpen = false) } }
    fun deleteContact(contact: Any) { /* V1 HIDDEN */ }
}
