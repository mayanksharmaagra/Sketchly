package com.jrprofessor.sketchly.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseUser
import com.jrprofessor.sketchly.BuildConfig
import com.jrprofessor.sketchly.data.model.User
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.data.worker.ContactSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactRepository: ContactRepository,
    private val sketchlyRepository: SketchlyRepository,
    private val workManager: WorkManager,
) :
    ViewModel() {

    private val _isContactSyncEnabled = MutableStateFlow(false)
    val isContactSyncEnabled: StateFlow<Boolean> = _isContactSyncEnabled.asStateFlow()

    /** True while a manual "Sync contacts now" call is in-flight. */
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * One-shot message emitted after syncContactsNow() completes.
     * Consumed by the UI (Toast), then cleared via clearSyncMessage().
     */
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** FirebaseUser — used for uid and auth state only. displayName is always blank for phone auth. */
    val currentUser: StateFlow<FirebaseUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUser)

    /**
     * Firestore User document — the REAL source of truth for displayName, username, avatarUrl.
     * Reloaded automatically whenever auth state changes (login / logout).
     */
    val firestoreUser: StateFlow<User?> = authRepository.authState
        .mapLatest { firebaseUser ->
            firebaseUser?.uid?.let { uid -> authRepository.getUserProfile(uid) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Total sketches sent — shown in the Profile stats row. */
    val sentCount: StateFlow<Int> = sketchlyRepository.getSentCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Total sketches received — shown in the Profile stats row. */
    val receivedCount: StateFlow<Int> = sketchlyRepository.getReceivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Unique senders (proxy for friends) — shown in the Profile stats row. */
    val friendsCount: StateFlow<Int> = sketchlyRepository.getUniqueSenderCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Widget pin status (DEBUG only) ────────────────────────────────────────
    //
    // Exposes a live pin-status flag so the Settings screen can show a
    // "Widget status: Pinned ✅ / Not added ❌" row during manual QA.
    // The StateFlow is backed by GlanceAppWidgetManager and refreshed every
    // time refreshWidgetPinStatus() is called (typically on screen resume).
    // Gated on BuildConfig.DEBUG — in release builds this state is never
    // updated and the corresponding UI row is compiled out entirely.
    // ─────────────────────────────────────────────────────────────────────────
    private val _isWidgetPinned = MutableStateFlow(false)
    val isWidgetPinned: StateFlow<Boolean> = _isWidgetPinned.asStateFlow()

    /**
     * Refreshes the [isWidgetPinned] state by querying [GlanceAppWidgetManager].
     * Call this from the Settings screen's [LaunchedEffect] so the badge
     * reflects reality after the user returns from pinning the widget.
     *
     * No-op in release builds (guarded by [BuildConfig.DEBUG]).
     */
    fun refreshWidgetPinStatus() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            _isWidgetPinned.value = sketchlyRepository.isWidgetPinned()
        }
    }

    /**
     * Enables periodic contact sync every 24 hours using WorkManager
     * and triggers an immediate one-time sync task.
     */
    fun enableContactSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 1. Schedule 24-hour periodic work
        val periodicWorkRequest = PeriodicWorkRequestBuilder<ContactSyncWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ContactSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            periodicWorkRequest
        )

        // 2. Trigger immediate sync task
        val immediateWorkRequest = OneTimeWorkRequestBuilder<ContactSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(immediateWorkRequest)

        _isContactSyncEnabled.value = true
    }

    /**
     * Cancels the periodic WorkManager task for contact sync.
     */
    fun disableContactSync() {
        workManager.cancelUniqueWork(ContactSyncWorker.WORK_NAME)
        _isContactSyncEnabled.value = false
    }

    /**
     * Triggers an immediate contact sync via ContactRepository and surfaces
     * the result as a one-shot message in [syncMessage] for the UI to Toast.
     */
    fun syncContactsNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.update { true }
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
                        "Sync failed. Please check your connection."
                }
            )
            _syncMessage.update { msg }
            _isSyncing.update { false }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.update { null }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}