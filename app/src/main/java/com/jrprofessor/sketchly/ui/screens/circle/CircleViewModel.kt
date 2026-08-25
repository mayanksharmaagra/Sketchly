package com.jrprofessor.sketchly.ui.screens.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
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

data class CircleUiState(
    val isAddDialogOpen: Boolean = false,
    val newContactName: String = "",
    val newContactEmailOrPhone: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CircleViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CircleUiState())
    val uiState: StateFlow<CircleUiState> = _uiState.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                contactRepository.getContacts(user.uid)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openAddDialog() {
        _uiState.update {
            it.copy(
                isAddDialogOpen = true,
                newContactName = "",
                newContactEmailOrPhone = "",
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false) }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(newContactName = name, errorMessage = null) }
    }

    fun onEmailOrPhoneChanged(input: String) {
        _uiState.update { it.copy(newContactEmailOrPhone = input, errorMessage = null) }
    }

    fun addContact() {
        val state = _uiState.value
        val name = state.newContactName.trim()
        val emailOrPhone = state.newContactEmailOrPhone.trim()

        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter a name for your contact.") }
            return
        }

        val currentUserId = authRepository.currentUserId
        if (currentUserId == null) {
            _uiState.update { it.copy(errorMessage = "You must be signed in to add contacts.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val isEmail = emailOrPhone.contains("@")
            val email = if (isEmail) emailOrPhone else ""
            val phone = if (!isEmail && emailOrPhone.isNotBlank()) emailOrPhone else ""

            val result = contactRepository.addContact(
                userId = currentUserId,
                displayName = name,
                email = email,
                phone = phone
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAddDialogOpen = false,
                            successMessage = "Contact added!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Failed to add contact."
                        )
                    }
                }
            )
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            contactRepository.deleteContact(contact)
        }
    }
}
