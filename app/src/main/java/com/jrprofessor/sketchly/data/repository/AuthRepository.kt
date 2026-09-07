package com.jrprofessor.sketchly.data.repository

import android.app.Activity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.jrprofessor.sketchly.BuildConfig
// V1: Room DB hidden — import com.jrprofessor.sketchly.data.local.SketchlyDatabase
import com.jrprofessor.sketchly.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    // V1: Room DB hidden — private val db: SketchlyDatabase,
) {

    // ── Current user accessors ─────────────────────────────────────────────

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Temporarily holds the E.164 phone number between OTP verification
     * and ProfileSetupScreen so we can hash it without raw storage.
     *
     * AuthRepository is @Singleton — persists across Auth → ProfileSetup nav.
     * Cleared immediately after use in [saveUserProfile].
     * Raw phone never written to Firestore or shared prefs.
     */
    private var pendingPhoneE164: String = ""

    // ── Email / Password Auth ──────────────────────────────────────────────
    // NOTE: Email auth is kept fully functional for V2. The V1 UI simply
    //       does not expose the email sign-up/sign-in toggle. Do not delete.

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw IllegalStateException("User is null")
            syncUserProfile(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new Firebase Auth account with email + password.
     * After this succeeds, the caller should trigger [sendEmailOtp] so the user
     * can verify their email with a 6-digit code.
     * Subsequent sign-ins use [signInWithEmail] directly (no OTP needed).
     * (V2 — not exposed in V1 UI but logic is preserved.)
     */
    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw IllegalStateException("User creation failed")
            val userDoc = User(
                id = user.uid,
                email = email,
                displayName = displayName.ifBlank { email.substringBefore("@") },
                authProvider = "email",
                createdAt = System.currentTimeMillis(),
                isEmailVerified = false,
            )
            firestore.collection("users").document(user.uid).set(userDoc).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Phone OTP ──────────────────────────────────────────────────────────

    /**
     * Starts Firebase Phone Auth verification flow.
     * Calls [onCodeSent] with the verificationId on SMS dispatch.
     * Calls [onAutoVerified] if Firebase instantly verifies (test devices, etc).
     * Calls [onError] with a human-readable message on failure.
     *
     * For testing without real SMS, register test phone numbers in:
     * Firebase Console → Authentication → Sign-in method → Phone → Phone numbers for testing.
     */
    fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (user: FirebaseUser) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification / instant verification
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        result.user?.let { user ->
                            onAutoVerified(user)
                        }
                    }
                    .addOnFailureListener { e ->
                        onError(e.localizedMessage ?: "Auto-verification failed.")
                    }
            }

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                Log.e("AuthRepository", "Phone OTP verification failed", e)
                onError(e.localizedMessage ?: "Phone verification failed.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                Log.d("AuthRepository", "OTP SMS sent. verificationId=$verificationId")
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Verifies the 6-digit [smsCode] against the [verificationId] received
     * from [sendPhoneOtp]. On success, the user is signed in.
     *
     * Returns [Result] with a [Pair]: (FirebaseUser, isNewUser).
     * [isNewUser] is true if the Firestore profile does not yet have a username set —
     * which means the user needs to go through ProfileSetupScreen.
     */
    suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
        rawPhoneE164: String = "",
    ): Result<Pair<FirebaseUser, Boolean>> {
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("Phone sign-in returned null user")
            // Store phone temporarily so ProfileSetupScreen can hash it later
            if (rawPhoneE164.isNotBlank()) pendingPhoneE164 = rawPhoneE164
            val isNewUser = syncUserProfile(user)
            Result.success(Pair(user, isNewUser))
        } catch (e: Exception) {
            Log.e("AuthRepository", "verifyPhoneOtp failed", e)
            Result.failure(e)
        }
    }

    // ── Email OTP (via Cloud Function) ─────────────────────────────────────
    // NOTE: Full email OTP logic is kept for V2. Do not delete.

    /**
     * Calls the `sendEmailOtp` Firebase Cloud Function, which generates a
     * 6-digit OTP, stores a hashed copy in Firestore, and emails it to [email].
     *
     * In **debug builds** the Cloud Function call is skipped. The OTP is logged
     * to Logcat and is always `123456`.
     */
    suspend fun sendEmailOtp(email: String): Result<Unit> {
        // ── Debug bypass ───────────────────────────────────────────────────
        if (BuildConfig.DEBUG_OTP.isNotEmpty()) {
            Log.d("AuthRepository", "[DEBUG] Skipping Cloud Function. Use OTP: ${BuildConfig.DEBUG_OTP}")
            return Result.success(Unit)
        }

        // ── Release: real Cloud Function call ──────────────────────────────
        return try {
            val data = hashMapOf("email" to email)
            functions
                .getHttpsCallable("sendEmailOtp")
                .call(data)
                .await()
            Log.d("AuthRepository", "Email OTP sent to $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "sendEmailOtp failed", e)
            Result.failure(e)
        }
    }

    /**
     * Calls the `verifyEmailOtp` Firebase Cloud Function to check the entered
     * [otp] for the given [email]. On success, marks the user's Firestore
     * document as email-verified.
     *
     * In **debug builds** the Cloud Function call is skipped. Any [otp] equal
     * to [BuildConfig.DEBUG_OTP] (`123456`) is accepted directly.
     */
    suspend fun verifyEmailOtp(email: String, otp: String): Result<Unit> {
        // ── Debug bypass ───────────────────────────────────────────────────
        if (BuildConfig.DEBUG_OTP.isNotEmpty()) {
            return if (otp == BuildConfig.DEBUG_OTP) {
                try {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        firestore.collection("users").document(uid)
                            .update("isEmailVerified", true)
                            .await()
                    }
                    Log.d("AuthRepository", "[DEBUG] Email OTP accepted for $email")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(IllegalArgumentException("[DEBUG] Invalid OTP. Expected: ${BuildConfig.DEBUG_OTP}"))
            }
        }

        // ── Release: real Cloud Function call ──────────────────────────────
        return try {
            val data = hashMapOf("email" to email, "otp" to otp)
            functions
                .getHttpsCallable("verifyEmailOtp")
                .call(data)
                .await()

            // Mark email as verified in Firestore
            val uid = auth.currentUser?.uid
            if (uid != null) {
                firestore.collection("users").document(uid)
                    .update("isEmailVerified", true)
                    .await()
            }

            Log.d("AuthRepository", "Email OTP verified for $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "verifyEmailOtp failed", e)
            Result.failure(e)
        }
    }

    // ── Profile sync ───────────────────────────────────────────────────────

    /**
     * Called after phone OTP verification.
     * - If the Firestore doc does NOT exist yet → creates a minimal shell profile, returns true (new user).
     * - If the doc exists but [username] is blank → profile incomplete, returns true (needs setup).
     * - If the doc exists with a non-blank username → returning user, returns false.
     *
     * Also registers the FCM token on every login.
     *
     * @return true if the user must go through ProfileSetupScreen, false if they can go to Inbox/Draw.
     */
    suspend fun syncUserProfile(firebaseUser: FirebaseUser): Boolean {
        val docRef = firestore.collection("users").document(firebaseUser.uid)
        val doc = docRef.get().await()
        return if (!doc.exists()) {
            // Brand new user — create shell, they must complete ProfileSetup
            val shellUser = User(
                id = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                authProvider = if (firebaseUser.phoneNumber != null) "phone" else "email",
                createdAt = System.currentTimeMillis(),
            )
            docRef.set(shellUser, SetOptions.merge()).await()
            true // is new user
        } else {
            val existingUsername = doc.getString("username").orEmpty()
            existingUsername.isBlank() // true = still needs profile setup
        }
    }

    /**
     * Updates the FCM token for an existing (returning) user in Firestore.
     * Called from AuthViewModel after OTP verify succeeds for returning users.
     *
     * For new users, fcmToken is saved inside [saveUserProfile] instead.
     *
     * Non-fatal: failure is logged but does not block login.
     */
    suspend fun updateFcmToken(uid: String, fcmToken: String) {
        if (fcmToken.isBlank()) return
        try {
            firestore.collection("users").document(uid)
                .update("fcmToken", fcmToken)
                .await()
            Log.d("AuthRepository", "FCM token updated for returning user uid=$uid")
        } catch (e: Exception) {
            Log.e("AuthRepository", "updateFcmToken failed (non-fatal)", e)
        }
    }

    suspend fun getUserProfile(uid: String): User? {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ── Username ──────────────────────────────────────────────────────────

    /**
     * Checks whether [username] is not yet taken in Firestore.
     * Returns true if the username is available (no document found with that username).
     * Username comparison is case-insensitive (stored as lowercase).
     *
     * SRS FR-1.4: username must be unique, stored as lowercase.
     */
    suspend fun checkUsernameAvailable(username: String): Boolean {
        return try {
            val query = firestore.collection("users")
                .whereEqualTo("username", username.lowercase())
                .limit(1)
                .get()
                .await()
            query.isEmpty
        } catch (e: Exception) {
            Log.e("AuthRepository", "checkUsernameAvailable failed", e)
            false // Fail safe: treat as unavailable
        }
    }

    /**
     * Saves the full user profile after the ProfileSetupScreen is completed.
     * Writes displayName, username (lowercase), phoneNumberHash (SHA-256) and fcmToken to Firestore.
     *
     * [fcmToken] is fetched in AuthViewModel.init{} via fetchFcmToken() and passed here directly.
     * [registerFcmToken] in AuthRepository is NOT called during signup — this replaces it.
     *
     * SRS FR-2.2: phone number is hashed client-side before any network call.
     * SRS FR-1.4: username stored as lowercase.
     */
    suspend fun saveUserProfile(
        uid: String,
        displayName: String,
        username: String,
        rawPhoneE164: String = "",
        fcmToken: String = "",
    ) {
        // Prefer the explicitly passed phone; fall back to what was cached during OTP verify
        val effectivePhone = rawPhoneE164.ifBlank { pendingPhoneE164 }
        val phoneHash = if (effectivePhone.isNotBlank()) sha256(effectivePhone) else ""
        pendingPhoneE164 = "" // clear immediately — raw number no longer needed

        val updates = mapOf(
            "displayName" to displayName.trim(),
            "username" to username.lowercase().trim(),
            "phoneNumberHash" to phoneHash,
            "authProvider" to "phone",
            "isSearchable" to true,
            "fcmToken" to fcmToken,
        )
        firestore.collection("users").document(uid)
            .update(updates)
            .await()
        Log.d("AuthRepository", "User profile saved for uid=$uid, username=${username.lowercase()}, hashPresent=${phoneHash.isNotBlank()}, fcmPresent=${fcmToken.isNotBlank()}")
    }

    /**
     * Legacy: writes only displayName (and optionally phoneNumber) to Firestore.
     * Kept for backward compatibility. Prefer [saveUserProfile] for new users.
     */
    suspend fun updateDisplayName(
        uid: String,
        displayName: String,
        phoneNumber: String = "",
    ) {
        val updates = mutableMapOf<String, Any>("displayName" to displayName)
        if (phoneNumber.isNotBlank()) {
            updates["phoneNumber"] = phoneNumber
        }
        firestore.collection("users").document(uid)
            .update(updates)
            .await()
    }

    // ── FCM token ─────────────────────────────────────────────────────────

    /**
     * Fetches the current FCM registration token and writes it to Firestore
     * under `users/{uid}/fcmToken`. The `onScribbleCreate` Cloud Function reads
     * this field to send targeted push messages (Architecture §7).
     */
    private suspend fun registerFcmToken(uid: String) {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(uid)
                .update("fcmToken", token)
                .await()
            Log.d("AuthRepository", "FCM token registered for user $uid")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to register FCM token", e)
            // Non-fatal — token refresh via SketchlyMessagingService.onNewToken() will retry
        }
    }

    // ── Sign out ──────────────────────────────────────────────────────────

    suspend fun signOut() {
        auth.signOut()
        // V1: Room DB hidden — db.clearAllTables() skipped (no local cache in V1)
        // SRS FR-1.7: On logout, clear cached Sketch and contact data from local database
        // db.clearAllTables()
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /**
     * Returns the SHA-256 hex digest of [input].
     * Used for phone number hashing per SRS FR-2.2 / Architecture §5.
     * Raw phone numbers must NEVER be sent over the network.
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getFcmToken(onResult: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                onResult(token)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
    // UserRepository.kt
    suspend fun saveFcmToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            .await()
    }
}
