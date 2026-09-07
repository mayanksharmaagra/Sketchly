package com.jrprofessor.sketchly.ui.screens.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.model.toContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    // Contacts synced via Contact Sync + connected contacts
    val contacts: StateFlow<List<ContactEntity>> = authRepository.authState
        .flatMapLatest { user ->
            if (user != null) {
                combine(
                    contactRepository.getSuggestedContacts(),
                    contactRepository.getConnectedContacts(),
                ) { suggested, connected ->
                    val allList = (suggested + connected).distinctBy { it.userId }
                    allList.map { it.toContactEntity(user.uid) }
                }
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

    // V1: addContact() hidden — manual contact add removed, use contact sync
    fun addContact() {
        // V1 HIDDEN: contactRepository.addContact()
        _uiState.update { it.copy(isAddDialogOpen = false) }
    }

    // V1: deleteContact() hidden — Room-based, no local contact list in V1
    fun deleteContact(contact: ContactEntity) {
        // V1 HIDDEN: contactRepository.deleteContact(contact)
    }
}
