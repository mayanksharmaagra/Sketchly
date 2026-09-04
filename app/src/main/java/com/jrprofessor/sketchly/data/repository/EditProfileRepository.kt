package com.jrprofessor.sketchly.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.jrprofessor.sketchly.data.model.UserProfile
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EditProfileRepository
 *
 * Single source of truth for profile editing operations:
 *  1. Load current user profile from Firestore
 *  2. Upload avatar image to Firebase Storage (avatars/{uid}/avatar.jpg)
 *  3. Update Firestore `users/{uid}` with new displayName / avatarUrl
 *  4. Update Firebase Auth profile (displayName + photoUri)
 *
 * Raw phone numbers are never read or stored here.
 */
@Singleton
class EditProfileRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {

    private val currentUid: String
        get() = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

    // ── 1. Load current profile ───────────────────────────────────────────────

    suspend fun loadProfile(): Result<UserProfile> = try {
        val uid = currentUid
        val doc = firestore.collection("users").document(uid).get().await()
        val fireUser = auth.currentUser!!
        Result.success(
            UserProfile(
                uid         = uid,
                displayName = doc.getString("displayName")
                    ?: fireUser.displayName
                    ?: "",
                username    = doc.getString("username") ?: "",
                avatarUrl   = doc.getString("avatarUrl")
                    ?: fireUser.photoUrl?.toString(),
                phoneNumber = fireUser.phoneNumber ?: "",
                email       = doc.getString("email") ?: fireUser.email ?: "",
            )
        )
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── 2. Upload avatar image ────────────────────────────────────────────────

    /**
     * Uploads [imageUri] to `avatars/{uid}/avatar.jpg` in Firebase Storage
     * and returns the public download URL.
     *
     * Always overwrites the previous avatar so only one copy is ever stored.
     */
    suspend fun uploadAvatar(imageUri: Uri): Result<String> = try {
        val uid = currentUid
        val ref = storage.reference.child("avatars/$uid/avatar.jpg")

        ref.putFile(imageUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Result.success(downloadUrl)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── 3. Save profile ───────────────────────────────────────────────────────

    /**
     * Updates Firestore and Firebase Auth in parallel:
     *  - Firestore `users/{uid}`: displayName, avatarUrl (if changed)
     *  - Firebase Auth profile: displayName, photoUri (if changed)
     *
     * @param displayName New display name (trimmed, non-blank)
     * @param avatarUrl   New avatar download URL, or null if unchanged
     */
    suspend fun saveProfile(
        displayName: String,
        avatarUrl: String?,
    ): Result<Unit> = try {
        val uid = currentUid
        val trimmedName = displayName.trim()
        require(trimmedName.isNotBlank()) { "Display name cannot be blank" }

        // Build Firestore update map
        val update = mutableMapOf<String, Any>("displayName" to trimmedName)
        if (avatarUrl != null) update["avatarUrl"] = avatarUrl

        // Update Firestore
        firestore.collection("users").document(uid)
            .update(update)
            .await()

        // Update Firebase Auth profile
        val profileBuilder = UserProfileChangeRequest.Builder()
            .setDisplayName(trimmedName)
        if (avatarUrl != null) {
            profileBuilder.setPhotoUri(Uri.parse(avatarUrl))
        }
        auth.currentUser!!.updateProfile(profileBuilder.build()).await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// ── Domain model for profile data ─────────────────────────────────────────────

data class UserProfile(
    val uid: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val phoneNumber: String,
    val email: String,
)
