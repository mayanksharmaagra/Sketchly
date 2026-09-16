package com.jrprofessor.sketchly.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// ─────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────

/**
 * Username check result for live validation feedback in ProfileSetupScreen.
 */
enum class UsernameStatus {
    IDLE,       // not yet checked (empty or too short)
    CHECKING,   // Firestore query in-flight
    AVAILABLE,  // unique — user can proceed
    TAKEN,      // already in use — show inline error
    INVALID,    // fails format validation
}

data class AuthUiState(
    // ── Auth mode ───────────────────────────
    /** V1: always true (phone only). Email toggle is hidden in V1 UI but logic kept for V2. */
    val isSignUp: Boolean = true,
    val isPhoneMode: Boolean = true,

    // ── Input fields ─────────────────────────
    /** V2 / hidden in V1 UI — kept for email auth logic */
    val email: String = "",
    /** V2 / hidden in V1 UI — kept for email auth logic */
    val password: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+91",

    // ── Profile setup step (username) ────────
    /**
     * Username chosen in ProfileSetupScreen.
     * Must be 3–20 chars, alphanumeric + underscore, stored lowercase.
     * SRS FR-1.4.
     */
    val username: String = "",
    val usernameStatus: UsernameStatus = UsernameStatus.IDLE,

    // ── Routing ──────────────────────────────
    /**
     * Set to true after phone OTP verify when Firestore shows no username yet.
     * NavGraph uses this to route to ProfileSetupScreen (new) vs Draw (returning).
     */
    val isNewUser: Boolean = false,

    // ── OTP state ───────────────────────────
    val otpCode: String = "",
    val otpDigits: List<String> = List(6) { "" },
    val isOtpSent: Boolean = false,
    val verificationId: String? = null,   // Firebase phone verificationId
    val resendCountdown: Int = 0,

    // ── Name-entry step (kept for internal state transitions) ─────────
    /**
     * Kept for compatibility with OTP flow transitions.
     * In V1, profile setup is a separate screen (ProfileSetupScreen), not
     * an inline step in AuthContent.
     */
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
    private var usernameCheckJob: Job? = null
    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()


    init {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
        fetchFcmToken()
    }

    // ── Mode toggles ──────────────────────────────
    // NOTE: toggleAuthMode and onSignUpChanged are preserved for V2 email auth.
    // The V1 UI does not render the toggle button, but the logic stays.

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

    /**
     * Called by the SMS User Consent receiver when Android auto-reads the OTP SMS.
     * Fills all 6 digits in one shot, then immediately triggers verification.
     * [onNewUser] / [onReturningUser] are the same nav callbacks as [verifyPhoneOtp].
     */
    fun onOtpAutoFilled(
        code: String,
        onNewUser: () -> Unit,
        onReturningUser: () -> Unit,
    ) {
        if (code.length != 6 || !code.all { it.isDigit() }) return
        val digits = code.map { it.toString() }
        _uiState.update { it.copy(otpDigits = digits, errorMessage = null) }
        // Give Compose one frame to render the filled boxes, then verify
        viewModelScope.launch {
            delay(150L)
            verifyPhoneOtp(onNewUser = onNewUser, onReturningUser = onReturningUser)
        }
    }

    // ── Username handling (ProfileSetupScreen) ────

    private val usernameRegex = Regex("^[a-zA-Z0-9_]{3,20}$")

    /**
     * Called whenever the username field changes.
     * Debounces uniqueness checks (600ms) to avoid hammering Firestore.
     * SRS FR-1.4: 3–20 chars, alphanumeric + underscore, case-insensitive.
     */
    fun onUsernameChanged(raw: String) {
        // Strip leading @ if user types it
        val cleaned = raw.removePrefix("@").take(20)
        _uiState.update {
            it.copy(
                username = cleaned,
                usernameStatus = when {
                    cleaned.length < 3 -> UsernameStatus.IDLE
                    !usernameRegex.matches(cleaned) -> UsernameStatus.INVALID
                    else -> UsernameStatus.CHECKING
                },
                errorMessage = null,
            )
        }

        usernameCheckJob?.cancel()
        if (cleaned.length >= 3 && usernameRegex.matches(cleaned)) {
            usernameCheckJob = viewModelScope.launch {
                delay(600L)
                checkUsernameAvailability(cleaned)
            }
        }
    }

    private suspend fun checkUsernameAvailability(username: String) {
        val available = authRepository.checkUsernameAvailable(username)
        _uiState.update {
            it.copy(
                usernameStatus = if (available) UsernameStatus.AVAILABLE else UsernameStatus.TAKEN,
            )
        }
    }

    // ── Profile Setup (after OTP) ─────────────────

    /**
     * Saves the completed profile (displayName + username + phone hash) to Firestore.
     * Called from ProfileSetupScreen on "Continue" tap.
     * SRS FR-1.3, FR-1.4, FR-2.2.
     */
    fun saveUserProfile(onSuccess: () -> Unit) {
        val state = _uiState.value
        val name = state.displayName.trim()
        val username = state.username.trim()

        // Validate locally before network call
        if (name.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter your full name.") }
            return
        }
        if (username.length < 3 || !usernameRegex.matches(username)) {
            _uiState.update { it.copy(errorMessage = "Username must be 3–20 characters (letters, numbers, underscore).") }
            return
        }
        if (state.usernameStatus != UsernameStatus.AVAILABLE) {
            _uiState.update { it.copy(errorMessage = "Please choose a unique username.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val uid = authRepository.currentUserId
                if (uid != null) {
                    val fullPhone = "${state.countryCode}${state.phoneNumber}"
                    authRepository.saveUserProfile(
                        uid = uid,
                        displayName = name,
                        username = username,
                        rawPhoneE164 = fullPhone.takeIf { state.isPhoneMode && state.phoneNumber.isNotBlank() }.orEmpty(),
                        fcmToken=fcmToken.value.orEmpty()
                    )
                }
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to save profile.",
                    )
                }
            }
        }
    }

    /**
     * Legacy: saves only display name.
     * Kept for backward compatibility. Prefer [saveUserProfile] for new phone users.
     */
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
                    val fullPhone = "${state.countryCode}${state.phoneNumber}".takeIf {
                        state.isPhoneMode && state.phoneNumber.isNotBlank()
                    }.orEmpty()
                    authRepository.updateDisplayName(uid, name, fullPhone)
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

    // ── Email Auth (V2 — not exposed in V1 UI) ─────

    /**
     * For Sign In: email + password → direct sign-in, no OTP needed.
     * For Sign Up: creates account → then triggers email OTP verification.
     * (V2 scope — logic preserved, not called from V1 UI)
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
    fun sendPhoneOtp(activity: Activity, onOtpSent: () -> Unit = {}) {
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
                onOtpSent()
            },
            onError = { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error)
                }
            },
        )
    }

    /**
     * Step 2 — verify the entered phone OTP.
     * On success: if new user → navigates to ProfileSetupScreen.
     *             if returning user → navigates directly to Draw/Inbox.
     * The NavGraph reads [AuthUiState.isNewUser] to decide routing.
     */
    fun verifyPhoneOtp(onNewUser: () -> Unit, onReturningUser: () -> Unit) {
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
                rawPhoneE164 = "${state.countryCode}${state.phoneNumber}",
            )
            result.fold(
                onSuccess = { (firebaseUser, isNewUser) ->
                    _uiState.update { it.copy(isLoading = false, isNewUser = isNewUser) }

                    if (!isNewUser) {
                        // Returning user → update FCM token in Firestore immediately.
                        // New users get their token saved inside saveUserProfile() instead.
                        val token = _fcmToken.value.orEmpty()
                        if (token.isNotBlank()) {
                            authRepository.updateFcmToken(
                                uid = firebaseUser.uid,
                                fcmToken = token,
                            )
                        }
                        onReturningUser()
                    } else {
                        onNewUser()
                    }
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

    // ── Unified verify dispatcher ─────────────────
    // NOTE: verifyOtp is kept for callers that don't need new/returning distinction.

    fun verifyOtp(onSuccess: () -> Unit) {
        if (_uiState.value.isPhoneMode) {
            verifyPhoneOtp(
                onNewUser = onSuccess,
                onReturningUser = onSuccess,
            )
        } else {
            verifyEmailOtp(onSuccess)
        }
    }

    // ── Email OTP ─────────────────────────────────
    // NOTE: Full email OTP logic preserved for V2.

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

    // ── Resend ────────────────────────────────────

    fun resendOtp(activity: Activity? = null, onOtpSent: () -> Unit = {}) {
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
        usernameCheckJob?.cancel()
    }

    fun fetchFcmToken() {
        viewModelScope.launch {
            try {
                val token = suspendCoroutine<String> { continuation ->
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
                _fcmToken.value = token

                // Save to Firestore so server can send push to this device.
                // This runs on every app start to ensure the token is current,
                // which handles the case where FCM refreshed the token silently.
                Log.d("AuthViewModel", "fetchFcmToken: $token")
                authRepository.saveFcmToken(token)

            } catch (e: Exception) {
                _fcmToken.value = null
            }
        }
    }
}
