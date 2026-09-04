package com.jrprofessor.sketchly.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.repository.EditProfileRepository
import com.jrprofessor.sketchly.data.repository.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingImage: Boolean = false,

    // Current field values shown in the form
    val displayName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val email: String = "",

    // Avatar — null = show initials, non-null = loaded/newly picked image URL
    val avatarUrl: String? = null,

    // Newly picked local URI (pending upload); distinct from the saved remote URL
    val pendingImageUri: Uri? = null,

    // One-shot events
    val savedSuccessfully: Boolean = false,
    val errorMessage: String? = null,
) {
    val initials: String
        get() = displayName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val repository: EditProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.loadProfile().fold(
                onSuccess = { profile ->
                    _uiState.update { s ->
                        s.copy(
                            isLoading   = false,
                            displayName = profile.displayName,
                            username    = profile.username,
                            phoneNumber = profile.phoneNumber,
                            email       = profile.email,
                            avatarUrl   = profile.avatarUrl,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message)
                    }
                }
            )
        }
    }

    // ── Field edits ───────────────────────────────────────────────────────────

    fun onDisplayNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessage = null) }
    }

    // ── Image picker ──────────────────────────────────────────────────────────

    /**
     * Called when the user picks an image from gallery OR captures with camera.
     * Stores the URI locally — actual upload happens on Save.
     */
    fun onImagePicked(uri: Uri) {
        _uiState.update { it.copy(pendingImageUri = uri, errorMessage = null) }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun onSave() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Full name cannot be empty") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            // Step 1 — upload new avatar if one was picked
            var finalAvatarUrl: String? = null
            if (state.pendingImageUri != null) {
                _uiState.update { it.copy(isUploadingImage = true) }
                repository.uploadAvatar(state.pendingImageUri).fold(
                    onSuccess = { url -> finalAvatarUrl = url },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                isSaving         = false,
                                isUploadingImage = false,
                                errorMessage     = "Image upload failed: ${err.message}",
                            )
                        }
                        return@launch
                    }
                )
                _uiState.update { it.copy(isUploadingImage = false) }
            }

            // Step 2 — save display name + avatar URL to Firestore + Auth
            repository.saveProfile(
                displayName = state.displayName.trim(),
                avatarUrl   = finalAvatarUrl,
            ).fold(
                onSuccess = {
                    _uiState.update { s ->
                        s.copy(
                            isSaving          = false,
                            pendingImageUri   = null,
                            avatarUrl         = finalAvatarUrl ?: s.avatarUrl,
                            savedSuccessfully = true,
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = err.message)
                    }
                }
            )
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onSaveEventConsumed() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
