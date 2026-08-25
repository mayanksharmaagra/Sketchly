package com.jrprofessor.sketchly.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.jrprofessor.sketchly.data.local.SketchlyDatabase
import com.jrprofessor.sketchly.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val db: SketchlyDatabase,
) {

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

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw IllegalStateException("User creation failed")
            val userDoc = User(
                id = user.uid,
                email = email,
                displayName = displayName.ifBlank { email.substringBefore("@") },
                createdAt = System.currentTimeMillis()
            )
            firestore.collection("users").document(user.uid).set(userDoc).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUserProfile(firebaseUser: FirebaseUser) {
        val docRef = firestore.collection("users").document(firebaseUser.uid)
        val doc = docRef.get().await()
        if (!doc.exists()) {
            val newUser = User(
                id = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                phoneNumber = firebaseUser.phoneNumber.orEmpty(),
                displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "Sketchly User",
                createdAt = System.currentTimeMillis()
            )
            docRef.set(newUser, SetOptions.merge()).await()
        }
        // M3: register FCM token so Cloud Function can fan-out push to this device
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

    suspend fun signOut() {
        auth.signOut()
        // SRS FR-1.4: On logout, clear cached Sketch and contact data from local database
        db.clearAllTables()
    }
}
