package com.jrprofessor.sketchly.ui.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.utils.getCountryPhoneCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────

data class AuthUiState(
    // ── Auth mode ───────────────────────────
    val isSignUp: Boolean = true,
    val isPhoneMode: Boolean = true,

    // ── Input fields ─────────────────────────
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+91",

    // ── OTP state ───────────────────────────
    val otpCode: String = "",
    val otpDigits: List<String> = List(6) { "" },
    val isOtpSent: Boolean = false,
    val verificationId: String? = null,   // Firebase phone verificationId
    val resendCountdown: Int = 0,

    // ── Name-entry step (phone flow only) ─────
    /** True after phone OTP is verified; shows the name-entry screen before navigating to Draw. */
    val isNameEntry: Boolean = false,

    // ── Async state ──────────────────────────
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: FirebaseUser? = null,
)

// ─────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            currentUser = authRepository.currentUser,
            countryCode = getCountryPhoneCode(),
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    // ── Mode toggles ──────────────────────────────

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isPhoneMode = !it.isPhoneMode,
                isOtpSent = false,
                otpDigits = List(6) { "" },
                errorMessage = null,
            )
        }
    }

    fun onSignUpChanged(status: Boolean) {
        _uiState.update {
            it.copy(
                isPhoneMode = false,
                isSignUp = status,
                isOtpSent = false,
                otpDigits = List(6) { "" },
                errorMessage = null,
            )
        }
    }

    // ── Field update handlers ─────────────────────

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onDisplayNameChanged(displayName: String) {
        _uiState.update { it.copy(displayName = displayName, errorMessage = null) }
    }

    fun onPhoneNumberChanged(phoneNumber: String) {
        _uiState.update { it.copy(phoneNumber = phoneNumber, errorMessage = null) }
    }

    /** Called from the OTP box row for each digit change */
    fun onOtpDigitChanged(index: Int, digit: String) {
        val updated = _uiState.value.otpDigits.toMutableList()
        updated[index] = digit
        _uiState.update { it.copy(otpDigits = updated, errorMessage = null) }
    }

    // ── Email Auth (Sign Up / Sign In without OTP) ─

    /**
     * For Sign In: email + password → direct sign-in, no OTP needed.
     * For Sign Up: creates account → then triggers email OTP verification.
     */
    fun submitEmailAuth(
        onSuccess: () -> Unit,
        onNeedsOtp: () -> Unit,   // navigate into OTP screen after sign-up
    ) {
        val state = _uiState.value
        if (!validateEmailFields(state)) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            if (state.isSignUp) {
                // Sign Up → create account → send email OTP
                val result = authRepository.signUpWithEmail(
                    email = state.email.trim(),
                    pass = state.password,
                    displayName = state.displayName.trim(),
                )
                result.fold(
                    onSuccess = {
                        // Account created; now send OTP to verify email
                        sendEmailOtp(onNeedsOtp)
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Sign up failed.",
                            )
                        }
                    },
                )
            } else {
                // Sign In → direct email + password, no OTP
                val result = authRepository.signInWithEmail(
                    email = state.email.trim(),
                    pass = state.password,
                )
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "Sign in failed.",
                            )
                        }
                    },
                )
            }
        }
    }

    // ── Phone OTP ─────────────────────────────────

    /** Step 1 — send SMS OTP */
    fun sendPhoneOtp(activity: Activity, onOtpSent: () -> Unit) {
        val state = _uiState.value
        val fullPhone = "${state.countryCode}${state.phoneNumber}"
        if (state.phoneNumber.length < 10) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 10-digit phone number.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        authRepository.sendPhoneOtp(
            phoneNumber = fullPhone,
            activity = activity,
            onCodeSent = { verificationId ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isOtpSent = true,
                        verificationId = verificationId,
                    )
                }
                startResendCountdown()
                onOtpSent()
            },
            onAutoVerified = { user ->
                // Firebase auto-verified (instant verification on some devices)
                _uiState.update { it.copy(isLoading = false, currentUser = user) }
                onOtpSent() // still navigate to OTP screen briefly before success
            },
            onError = { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error)
                }
            },
        )
    }

    /** Step 2 — verify the entered phone OTP.
     *  On success, transitions to the name-entry step instead of navigating directly.
     */
    fun verifyPhoneOtp(onSuccess: () -> Unit) {
        val state = _uiState.value
        val code = state.otpDigits.joinToString("")
        if (code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Enter all 6 digits.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.verifyPhoneOtp(
                verificationId = state.verificationId ?: "",
                smsCode = code,
            )
            result.fold(
                onSuccess = {
                    // Show name-entry screen before proceeding to the app
                    _uiState.update { it.copy(isLoading = false, isNameEntry = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Invalid OTP. Try again.",
                            otpDigits = List(6) { "" },
                        )
                    }
                },
            )
        }
    }

    /** Called from the name-entry screen. Saves the display name to Firestore and proceeds. */
    fun saveDisplayName(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.displayName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val uid = authRepository.currentUserId
                if (uid != null) {
                    authRepository.updateDisplayName(uid, name)
                }
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to save name.",
                    )
                }
            }
        }
    }

    // ── Email OTP ─────────────────────────────────

    /** Step 1 — send email OTP via Cloud Function */
    fun sendEmailOtp(onOtpSent: () -> Unit) {
        val email = _uiState.value.email.trim()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.sendEmailOtp(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOtpSent = true,
                        )
                    }
                    startResendCountdown()
                    onOtpSent()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to send OTP.",
                        )
                    }
                },
            )
        }
    }

    /** Step 2 — verify the entered email OTP via Cloud Function */
    fun verifyEmailOtp(onSuccess: () -> Unit) {
        val state = _uiState.value
        val code = state.otpDigits.joinToString("")
        if (code.length < 6) {
            _uiState.update { it.copy(errorMessage = "Enter all 6 digits.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.verifyEmailOtp(
                email = state.email.trim(),
                otp = code,
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Incorrect code. Try again.",
                            otpDigits = List(6) { "" },
                        )
                    }
                },
            )
        }
    }

    // ── Unified verify dispatcher ─────────────────

    fun verifyOtp(onSuccess: () -> Unit) {
        if (_uiState.value.isPhoneMode) {
            // Phone mode: OTP success → name-entry step (onSuccess called from saveDisplayName)
            verifyPhoneOtp(onSuccess)
        } else {
            verifyEmailOtp(onSuccess)
        }
    }

    // ── Resend ────────────────────────────────────

    fun resendOtp(activity: Activity? = null, onOtpSent: () -> Unit) {
        _uiState.update { it.copy(otpDigits = List(6) { "" }, errorMessage = null) }
        if (_uiState.value.isPhoneMode && activity != null) {
            sendPhoneOtp(activity, onOtpSent)
        } else {
            sendEmailOtp(onOtpSent)
        }
    }

    // ── Countdown ────────────────────────────────

    private fun startResendCountdown(seconds: Int = 60) {
        countdownJob?.cancel()
        _uiState.update { it.copy(resendCountdown = seconds) }
        countdownJob = viewModelScope.launch {
            repeat(seconds) {
                delay(1_000L)
                _uiState.update { it.copy(resendCountdown = it.resendCountdown - 1) }
            }
        }
    }

    // ── Validation helpers ────────────────────────

    private fun validateEmailFields(state: AuthUiState): Boolean {
        if (state.email.isBlank() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()
        ) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email address.") }
            return false
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters.") }
            return false
        }
        if (state.isSignUp && state.displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name.") }
            return false
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
