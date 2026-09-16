package com.jrprofessor.sketchly.ui.screens.started

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the correct startup destination on every cold launch.
 *
 * The problem it solves:
 * A user can verify OTP (creating a Firebase Auth session) and then close the
 * app before completing ProfileSetupScreen. On the next launch, Firebase Auth
 * still considers them authenticated, but their Firestore profile has no
 * username set. Without this ViewModel, MainActivity would send them straight
 * to DashboardScreen with an incomplete profile.
 *
 * Resolution logic:
 *  - No Firebase user        → [Screen.GetStarted]
 *  - User exists, no username → [Screen.ProfileSetup]  (incomplete profile)
 *  - User exists, has username → [Screen.Dashboard]    (fully onboarded)
 *
 * [startupDestination] is `null` while the async Firestore check is in-flight.
 * The UI should show a splash/loading state until it emits a non-null value.
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _startupDestination = MutableStateFlow<String?>(null)

    /**
     * Emits `null` while the profile check is in-flight, then emits one of:
     * - [Screen.GetStarted.route]   — no authenticated user
     * - [Screen.ProfileSetup.route] — authenticated but profile incomplete
     * - [Screen.Dashboard.route]    — authenticated and profile complete
     */
    val startupDestination: StateFlow<String?> = _startupDestination.asStateFlow()

    init {
        resolveStartupDestination()
    }

    private fun resolveStartupDestination() {
        viewModelScope.launch {
            val firebaseUser = authRepository.currentUser

            if (firebaseUser == null) {
                // Not signed in at all — start from scratch
                _startupDestination.value = Screen.GetStarted.route
                return@launch
            }

            try {
                // syncUserProfile returns true when username is blank (profile incomplete)
                val needsProfileSetup = authRepository.syncUserProfile(firebaseUser)
                _startupDestination.value = if (needsProfileSetup) {
                    Log.d("StartupViewModel", "User ${firebaseUser.uid} has incomplete profile → routing to ProfileSetup")
                    Screen.ProfileSetup.route
                } else {
                    Log.d("StartupViewModel", "User ${firebaseUser.uid} has complete profile → routing to Dashboard")
                    Screen.Dashboard.route
                }
            } catch (e: Exception) {
                // Firestore check failed (e.g. offline). Fall back to Dashboard so the
                // user isn't stuck. They can always sign out from Settings if needed.
                Log.e("StartupViewModel", "Profile check failed, falling back to Dashboard", e)
                _startupDestination.value = Screen.Dashboard.route
            }
        }
    }
}
