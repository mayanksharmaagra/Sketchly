package com.jrprofessor.sketchly.data.model

/**
 * Firestore UserProfile document mapped to a Kotlin data class.
 *
 * V1 auth is phone + OTP only. Email/password fields are kept in the model
 * but are NOT shown in the V1 UI — they are addable from Profile → Settings (V2).
 *
 * Fields:
 *  - [id]              Firebase UID
 *  - [displayName]     Full name chosen by the user after OTP verify
 *  - [username]        Unique handle (lowercase, 3–20 chars, alphanumeric + _)
 *  - [phoneNumberHash] SHA-256 hash of the E.164 phone number — used for contact sync matching
 *  - [avatarUrl]       Optional profile picture URL
 *  - [isSearchable]    If false, this user won't appear in username search results
 *  - [authProvider]    "phone" for V1; "email" reserved for V2
 *  - [createdAt]       Account creation timestamp (ms)
 *  - [fcmToken]        FCM registration token for push delivery
 *
 *  V2 / hidden-in-V1:
 *  - [email]           Not collected at signup; addable from Settings
 *  - [isEmailVerified] True once the user completes email OTP verification (V2)
 */
data class User(
    val id: String = "",

    // ── Identity ───────────────────────────────────────────────────────
    val displayName: String = "",
    val username: String = "",          // unique, lowercase; empty until ProfileSetupScreen
    val avatarUrl: String = "",

    // ── Contact sync ──────────────────────────────────────────────────
    /** SHA-256 hash of the normalised E.164 phone number. Never store raw number. */
    val phoneNumberHash: String = "",

    // ── Discovery ─────────────────────────────────────────────────────
    @get:com.google.firebase.firestore.PropertyName("isSearchable")
    @set:com.google.firebase.firestore.PropertyName("isSearchable")
    var isSearchable: Boolean = true,

    // ── Auth ──────────────────────────────────────────────────────────
    val authProvider: String = "phone",  // "phone" | "email" (V2)
    val createdAt: Long = System.currentTimeMillis(),

    // ── Push ──────────────────────────────────────────────────────────
    val fcmToken: String = "",

    // ── Preferences ───────────────────────────────────────────────────
    val widgetPreviewEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,

    // ── V2 / hidden in V1 UI (logic kept, not shown at signup) ────────
    val email: String = "",
    @get:com.google.firebase.firestore.PropertyName("isEmailVerified")
    @set:com.google.firebase.firestore.PropertyName("isEmailVerified")
    var isEmailVerified: Boolean = false,
)
