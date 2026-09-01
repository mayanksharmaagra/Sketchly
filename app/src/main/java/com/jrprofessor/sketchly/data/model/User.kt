package com.jrprofessor.sketchly.data.model

data class User(
    val id: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val widgetPreviewEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** True once the user has completed email OTP verification after sign-up. */
    val isEmailVerified: Boolean = false,
)
