package com.jrprofessor.sketchly.ui.screens.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ContactSyncVM"

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
    fun onSyncButtonTapped() {
        Log.d(TAG, "onSyncButtonTapped: moving to RequestingPermission")
        _uiState.value = ContactSyncUiState.RequestingPermission
    }

    // Called by composable AFTER permission is granted by the user
    fun onPermissionGranted() {
        Log.d(TAG, "onPermissionGranted: permission granted, starting sync")
        viewModelScope.launch {
            _uiState.value = ContactSyncUiState.Syncing
            Log.d(TAG, "onPermissionGranted: state set to Syncing, calling syncContacts()")

            val result = contactRepository.syncContacts()

            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "onPermissionGranted: syncContacts SUCCESS, count=$count — fetching suggested contacts")
                    // Read back from Firestore to show the list
                    val contacts = contactRepository.getSuggestedContacts().first()
                    Log.d(TAG, "onPermissionGranted: getSuggestedContacts returned ${contacts.size} contacts")
                    _uiState.value = ContactSyncUiState.Success(
                        matchedCount = count,
                        contacts = contacts
                    )
                    Log.d(TAG, "onPermissionGranted: state set to Success")
                },
                onFailure = { error ->
                    val isRateLimit = error is FirebaseFunctionsException &&
                            (error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
                             error.message?.contains("limit reached", ignoreCase = true) == true)

                    if (isRateLimit) {
                        Log.w(TAG, "onPermissionGranted: syncContacts rate limited — ${error.message}")
                    } else {
                        Log.e(TAG, "onPermissionGranted: syncContacts FAILED — ${error.javaClass.simpleName}: ${error.message}", error)
                    }

                    val message = if (isRateLimit) {
                        "Contact sync limit reached. Try again in 24 hours."
                    } else {
                        error.message ?: "Something went wrong. Please try again."
                    }

                    _uiState.value = ContactSyncUiState.Error(message = message)
                }
            )
        }
    }

    // Called by composable when user explicitly denies the permission
    fun onPermissionDenied() {
        Log.d(TAG, "onPermissionDenied: permission denied by user")
        _uiState.value = ContactSyncUiState.PermissionDenied
    }

    // Called when user taps "Skip for now"
    fun onSkipTapped() {
        Log.d(TAG, "onSkipTapped: user skipped contact sync")
        _uiState.value = ContactSyncUiState.Skipped
    }

    // Called when user taps "Try Again" on the error state
    fun onRetryTapped() {
        Log.d(TAG, "onRetryTapped: resetting to Idle for retry")
        _uiState.value = ContactSyncUiState.Idle
    }
}
