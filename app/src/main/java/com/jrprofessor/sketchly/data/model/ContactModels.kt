package com.jrprofessor.sketchly.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jrprofessor.sketchly.data.local.ContactEntity

// ============================================================
// Domain model — what the UI sees
// ============================================================

data class SketchlyContact(
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarUrl: String?,
    val phoneLastFour: String?,      // "4821" — display only, never full number
    val source: ContactSource,
    val connectionStatus: ConnectionStatus,
)

enum class ContactSource {
    CONTACT_SYNC,        // found via phone hash matching
    SEARCH,              // found via username search
    INVITE,              // connected via invite link
    CONNECTION_REQUEST   // incoming/outgoing connection request (triggered by Scribble send)
}

enum class ConnectionStatus {
    SUGGESTED,          // contact sync matched, no request sent yet
    PENDING_SENT,       // connection request sent by us, not yet accepted
    PENDING_RECEIVED,   // they sent us a connection request
    CONNECTED           // mutually accepted — can exchange Scribbles
}

// ============================================================
// Room entity — persisted locally (dead code in V1 — not registered in @Database)
// ============================================================

@Entity(tableName = "sketchly_contacts")
data class SketchlyContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "phone_last_four") val phoneLastFour: String?,
    @ColumnInfo(name = "source") val source: String,               // ContactSource.name
    @ColumnInfo(name = "connection_status") val connectionStatus: String, // ConnectionStatus.name
    @ColumnInfo(name = "synced_at") val syncedAt: Long = System.currentTimeMillis(),
)

// ============================================================
// Mappers
// ============================================================

fun SketchlyContactEntity.toDomain() = SketchlyContact(
    userId = userId,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl,
    phoneLastFour = phoneLastFour,
    source = ContactSource.valueOf(source),
    connectionStatus = ConnectionStatus.valueOf(connectionStatus),
)

fun SketchlyContact.toEntity() = SketchlyContactEntity(
    userId = userId,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl,
    phoneLastFour = phoneLastFour,
    source = source.name,
    connectionStatus = connectionStatus.name,
)

fun SketchlyContact.toContactEntity(currentUid: String = ""): ContactEntity =
    ContactEntity(
        id = userId,
        userId = currentUid,
        contactUserId = userId,
        displayName = displayName,
        phoneNumber = if (!phoneLastFour.isNullOrBlank()) "•••• $phoneLastFour" else "",
        email = if (username.isNotBlank()) "@$username" else "",
        avatarUrl = avatarUrl ?: "",
        source = source.name,
        isOnSketchly = true,
    )
