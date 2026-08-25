package com.jrprofessor.sketchly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a contact.
 * Matches SRS §5 Data Requirements:
 * userId, contactUserId, displayName, phone/email, source (synced/manual/invite), status, isOnSketchly
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String, // contactUserId or unique local key
    val userId: String, // current logged-in user id
    val contactUserId: String = "", // Firebase UID if registered
    val displayName: String,
    val phoneNumber: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val source: String = "manual", // "manual", "synced", "invite"
    val isOnSketchly: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
