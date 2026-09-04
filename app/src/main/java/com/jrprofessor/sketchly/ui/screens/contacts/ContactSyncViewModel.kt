package com.jrprofessor.sketchly.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================
// UI State
// ============================================================

sealed interface ContactSyncUiState {
    object Idle : ContactSyncUiState              // Initial — button not tapped yet
    object RequestingPermission : ContactSyncUiState // Waiting for user to grant/deny
    object Syncing : ContactSyncUiState            // Hashing + Cloud Function in progress
    data class Success(
        val matchedCount: Int,
        val contacts: List<SketchlyContact>
    ) : ContactSyncUiState
    data class Error(val message: String) : ContactSyncUiState
    object PermissionDenied : ContactSyncUiState   // User denied READ_CONTACTS
    object Skipped : ContactSyncUiState            // User tapped "Skip for now"
}

// ============================================================
// ViewModel
// ============================================================

@HiltViewModel
class ContactSyncViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ContactSyncUiState>(ContactSyncUiState.Idle)
    val uiState: StateFlow<ContactSyncUiState> = _uiState.asStateFlow()

    // Triggered by the screen when user taps "Sync Contacts" button
    // The actual permission request happens in the composable using
    // rememberLauncherForActivityResult — this just moves state to
    // RequestingPermission so the composable knows to launch the request
    fun onSyncButtonTapped() {
        _uiState.value = ContactSyncUiState.RequestingPermission
    }

    // Called by composable AFTER permission is granted by the user
    fun onPermissionGranted() {
        viewModelScope.launch {
            _uiState.value = ContactSyncUiState.Syncing

            val result = contactRepository.syncContacts()

            result.fold(
                onSuccess = { count ->
                    // Read back from Room to show the list
                    val contacts = contactRepository.getSuggestedContacts().first()
                    _uiState.value = ContactSyncUiState.Success(
                        matchedCount = count,
                        contacts = contacts
                    )
                },
                onFailure = { error ->
                    _uiState.value = ContactSyncUiState.Error(
                        message = error.message ?: "Something went wrong. Please try again."
                    )
                }
            )
        }
    }

    // Called by composable when user explicitly denies the permission
    fun onPermissionDenied() {
        _uiState.value = ContactSyncUiState.PermissionDenied
    }

    // Called when user taps "Skip for now"
    fun onSkipTapped() {
        _uiState.value = ContactSyncUiState.Skipped
    }

    // Called when user taps "Try Again" on the error state
    fun onRetryTapped() {
        _uiState.value = ContactSyncUiState.Idle
    }
}
