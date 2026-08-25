package com.jrprofessor.sketchly.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted Sketch.
 * Strokes are stored as a JSON string to keep the schema simple
 * (under Firestore's 1MB limit, this is always small).
 */
@Entity(tableName = "sketches")
data class SketchlyEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val senderDisplayName: String = "",
    val recipientIds: String, // JSON array of strings
    val strokesJson: String, // JSON array of Stroke objects
    val backgroundColor: String,
    val createdAt: Long,
    val isRead: Boolean = false,
    val isSent: Boolean = false,
    val isDraft: Boolean = false,
)

