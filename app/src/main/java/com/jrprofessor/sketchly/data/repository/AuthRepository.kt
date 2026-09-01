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
import com.jrprofessor.sketchly.data.local.SketchlyDatabase
import com.jrprofessor.sketchly.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val db: SketchlyDatabase,
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

    // ── Email / Password Auth ──────────────────────────────────────────────

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
     * In **debug builds** the Firebase SMS call is skipped entirely.
     * [onCodeSent] is invoked immediately with a fixed sentinel verificationId
     * (`"DEBUG_VERIFICATION_ID"`). Use OTP `123456` to complete verification.
     */
    fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (user: FirebaseUser) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        // ── Debug bypass ───────────────────────────────────────────────────
        if (BuildConfig.DEBUG_OTP.isNotEmpty()) {
            Log.d("AuthRepository", "[DEBUG] Skipping real SMS. Use OTP: ${BuildConfig.DEBUG_OTP}")
            onCodeSent("DEBUG_VERIFICATION_ID")
            return
        }

        // ── Release: real Firebase Phone Auth ──────────────────────────────
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
     * In **debug builds**, if [verificationId] is the debug sentinel, any code
     * equal to [BuildConfig.DEBUG_OTP] (`123456`) is accepted without Firebase.
     * The user is then signed in anonymously so the rest of the app works.
     */
    suspend fun verifyPhoneOtp(
        verificationId: String,
        smsCode: String,
    ): Result<FirebaseUser> {
        // ── Debug bypass ───────────────────────────────────────────────────
        if (BuildConfig.DEBUG_OTP.isNotEmpty() && verificationId == "DEBUG_VERIFICATION_ID") {
            return if (smsCode == BuildConfig.DEBUG_OTP) {
                try {
                    val result = auth.signInAnonymously().await()
                    val user = result.user ?: throw IllegalStateException("Anonymous sign-in returned null")
                    syncUserProfile(user)
                    Log.d("AuthRepository", "[DEBUG] Phone OTP accepted. Signed in anonymously.")
                    Result.success(user)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            } else {
                Result.failure(IllegalArgumentException("[DEBUG] Invalid OTP. Expected: ${BuildConfig.DEBUG_OTP}"))
            }
        }

        // ── Release: real Firebase credential check ────────────────────────
        return try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("Phone sign-in returned null user")
            syncUserProfile(user)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("AuthRepository", "verifyPhoneOtp failed", e)
            Result.failure(e)
        }
    }

    // ── Email OTP (via Cloud Function) ─────────────────────────────────────

    /**
     * Calls the `sendEmailOtp` Firebase Cloud Function, which generates a
     * 6-digit OTP, stores a hashed copy in Firestore, and emails it to [email].
     *
     * Cloud Function: functions/index.js → sendEmailOtp(email)
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
     * Cloud Function: functions/index.js → verifyEmailOtp(email, otp)
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

    suspend fun syncUserProfile(firebaseUser: FirebaseUser) {
        val docRef = firestore.collection("users").document(firebaseUser.uid)
        val doc = docRef.get().await()
        if (!doc.exists()) {
            val newUser = User(
                id = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                displayName = firebaseUser.displayName
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: "Sketchly User",
                createdAt = System.currentTimeMillis(),
            )
            docRef.set(newUser, SetOptions.merge()).await()
        }
        registerFcmToken(firebaseUser.uid)
    }

    suspend fun getUserProfile(uid: String): User? {
        return try {
            val snapshot = firestore.collection("users").document(uid).get().await()
            snapshot.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
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

    // ── Display name ──────────────────────────────────────────────────────

    /**
     * Writes [displayName] into the `users/{uid}` Firestore document.
     * Called after phone OTP verification when the user has not yet set a name.
     */
    suspend fun updateDisplayName(uid: String, displayName: String) {
        firestore.collection("users").document(uid)
            .update("displayName", displayName)
            .await()
    }

    // ── Sign out ──────────────────────────────────────────────────────────

    suspend fun signOut() {
        auth.signOut()
        // SRS FR-1.4: On logout, clear cached Sketch and contact data from local database
        db.clearAllTables()
    }
}
