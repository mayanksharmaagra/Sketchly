package com.jrprofessor.sketchly.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SketchlyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sketch: SketchlyEntity)

    @Update
    suspend fun update(sketch: SketchlyEntity)

    @Delete
    suspend fun delete(sketch: SketchlyEntity)

    /** All received sketches, newest first (Inbox) */
    @Query("SELECT * FROM sketches WHERE isSent = 0 AND isDraft = 0 ORDER BY createdAt DESC")
    fun getInboxSketches(): Flow<List<SketchlyEntity>>

    /** All sent sketches, newest first */
    @Query("SELECT * FROM sketches WHERE isSent = 1 ORDER BY createdAt DESC")
    fun getSentSketches(): Flow<List<SketchlyEntity>>

    /** All sketches (sent + received), newest first (History/Archive) */
    @Query("SELECT * FROM sketches WHERE isDraft = 0 ORDER BY createdAt DESC")
    fun getAllSketches(): Flow<List<SketchlyEntity>>

    // ── Paginated History Queries (SRS FR-8.3) ──

    /** All sketches, paginated — no full-dataset load */
    @Query("SELECT * FROM sketches WHERE isDraft = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllSketchesPage(limit: Int, offset: Int): List<SketchlyEntity>

    /** Sent sketches only, paginated */
    @Query("SELECT * FROM sketches WHERE isSent = 1 AND isDraft = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getSentSketchesPage(limit: Int, offset: Int): List<SketchlyEntity>

    /** Received sketches only, paginated */
    @Query("SELECT * FROM sketches WHERE isSent = 0 AND isDraft = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getReceivedSketchesPage(limit: Int, offset: Int): List<SketchlyEntity>

    /** Received sketches from a specific sender, paginated (contact filter) */
    @Query("SELECT * FROM sketches WHERE senderId = :senderId AND isSent = 0 AND isDraft = 0 ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getSketchesBySenderPage(senderId: String, limit: Int, offset: Int): List<SketchlyEntity>

    /** Unique sender IDs in received sketches — used for contact filter chip list */
    @Query("SELECT DISTINCT senderId, senderDisplayName FROM sketches WHERE isSent = 0 AND isDraft = 0 AND senderId != ''")
    suspend fun getDistinctSenders(): List<SenderInfo>

    /** Current in-progress draft, if any */
    @Query("SELECT * FROM sketches WHERE isDraft = 1 LIMIT 1")
    suspend fun getDraft(): SketchlyEntity?

    /** Single sketch by ID */
    @Query("SELECT * FROM sketches WHERE id = :id")
    suspend fun getById(id: String): SketchlyEntity?

    /** Mark a sketch as read */
    @Query("UPDATE sketches SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    /**
     * Hard-deletes all received sketches (isSent=0) from [senderId] from the local Room cache.
     * Called immediately after blocking a user so the inbox clears without requiring a
     * Firestore write (the client has no update permission on /scribbles).
     */
    @Query("DELETE FROM sketches WHERE senderId = :senderId AND isSent = 0")
    suspend fun deleteReceivedFrom(senderId: String)

    /** Count of unread received sketches (for badge) */
    @Query("SELECT COUNT(*) FROM sketches WHERE isRead = 0 AND isSent = 0 AND isDraft = 0")
    fun getUnreadCount(): Flow<Int>

    /** Total sent sketches (for Profile stats) */
    @Query("SELECT COUNT(*) FROM sketches WHERE isSent = 1 AND isDraft = 0")
    fun getSentCount(): Flow<Int>

    /** Total received sketches (for Profile stats) */
    @Query("SELECT COUNT(*) FROM sketches WHERE isSent = 0 AND isDraft = 0")
    fun getReceivedCount(): Flow<Int>

    /** Distinct senders (proxy for unique friends who sent a scribble) */
    @Query("SELECT COUNT(DISTINCT senderId) FROM sketches WHERE isSent = 0 AND isDraft = 0 AND senderId != ''")
    fun getUniqueSenderCount(): Flow<Int>

    /**
     * All sketches exchanged with a specific contact — both received from them
     * (isSent = 0, senderId matches) and sent to them (isSent = 1,
     * recipientIds JSON contains their uid). Newest first.
     */
    @Query("""
        SELECT * FROM sketches
        WHERE isDraft = 0
          AND (
            (isSent = 0 AND senderId = :contactId)
            OR
            (isSent = 1 AND recipientIds LIKE '%' || :contactId || '%')
          )
        ORDER BY createdAt DESC
    """)
    fun getSketchesWithContact(contactId: String): Flow<List<SketchlyEntity>>

    /** Total count of sketches exchanged with a contact (for header stat) */
    @Query("""
        SELECT COUNT(*) FROM sketches
        WHERE isDraft = 0
          AND (
            (isSent = 0 AND senderId = :contactId)
            OR
            (isSent = 1 AND recipientIds LIKE '%' || :contactId || '%')
          )
    """)
    suspend fun getContactSketchCount(contactId: String): Int
}

/** Lightweight projection for building the contact filter chip list */
data class SenderInfo(
    val senderId: String,
    val senderDisplayName: String,
)

