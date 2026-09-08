package com.jrprofessor.sketchly.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.data.util.networkStatusFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sketchRepository: SketchlyRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** UID of the signed-in user — used to determine sketch direction (sent vs received). */
    val currentUserId: String? get() = authRepository.currentUserId

    /**
     * `true` from cold start until the first non-null emission from Room arrives.
     * Drives the skeleton loading state (UIUX §7) so the empty-state illustration
     * never flashes briefly before real data.
     */
    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading

    val inboxSketches: StateFlow<List<Sketch>> = sketchRepository.getInboxSketches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSketches: StateFlow<List<Sketch>> = sketchRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadCount: StateFlow<Int> = sketchRepository.getUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Emits `true` when the device has a validated internet connection.
     * Drives the persistent offline banner in [DashboardScreen] (UIUX §7).
     */
    val isOnline: StateFlow<Boolean> = networkStatusFlow(appContext)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        // Clear loading state once Room emits any value (first DB read is instant)
        viewModelScope.launch {
            recentSketches.collect {
                _isInitialLoading.value = false
            }
        }

        viewModelScope.launch {
            authRepository.authState.collect { user ->
                if (user != null) {
                    sketchRepository.startListeningToInbox(user.uid, viewModelScope)
                } else {
                    sketchRepository.stopListeningToInbox()
                }
            }
        }
    }

    fun markAsRead(sketchId: String) {
        viewModelScope.launch {
            sketchRepository.markAsRead(sketchId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sketchRepository.stopListeningToInbox()
    }
}