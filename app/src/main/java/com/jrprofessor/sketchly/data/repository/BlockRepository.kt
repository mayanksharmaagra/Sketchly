package com.jrprofessor.sketchly.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jrprofessor.sketchly.data.model.BlockedUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val BLOCK_TAG = "BlockRepository"

/**
 * Manages the block/unblock lifecycle.
 *
 * Firestore layout:
 *   blocks/{userId}/entries/{blockedUserId}   ← matches firestore.rules
 *       Fields: userId, blockedUserId, blockedDisplayName, reason?, blockedAt
 *
 * Security model:
 *   - The onScribbleCreate Cloud Function reads BOTH directions of this collection
 *     before delivering any scribble — server-enforced, cannot be bypassed by a modified client.
 *   - Client-side, ContactRepository.getBlockedIds() additionally filters blocked users
 *     from the Suggested tab on every snapshot update.
 *
 * Connection teardown on block:
 *   Deletes connections/{uid}/entries/{targetUserId} AND the reverse entry atomically.
 *   Past scribbles from the blocked sender are removed from local Room so they
 *   no longer appear in the inbox. Firestore copies are left intact for moderation.
 */
@Singleton
class BlockRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val fireAuth: FirebaseAuth,
) {

    private val currentUid: String
        get() = fireAuth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

    // ── Firestore collection path — MUST match firestore.rules ──
    // Rule: match /blocks/{blockerId}/entries/{targetId}
    //   allow read, write: if isSignedIn() && isOwner(blockerId)
    private fun blockEntriesRef(uid: String) =
        firestore.collection("blocks").document(uid).collection("entries")

    // =========================================================================
    // Block
    // =========================================================================

    /**
     * Blocks [targetUserId].
     *
     * Writes ONE document to `blocks/{uid}/entries/{targetUserId}`.
     *
     * Connection teardown (removing `connections/{uid}/entries/{targetId}`) is
     * intentionally NOT done here because `connections` has `allow write: if false`
     * in Firestore rules — including it in a batch causes PERMISSION_DENIED which
     * rolls back the ENTIRE batch, so the block entry itself never gets written.
     *
     * The Cloud Function `onBlockCreate` (Admin SDK) handles:
     *   - Deleting connections/{uid}/entries/{targetUserId}
     *   - Deleting connections/{targetUserId}/entries/{uid}
     *
     * Sender receives NO feedback that they were blocked (avoid tipping off).
     */
    suspend fun blockUser(
        targetUserId: String,
        targetDisplayName: String,
        reason: String? = null,
    ): Result<Unit> {
        return try {
            val uid = currentUid
            Log.d(BLOCK_TAG, "blockUser: START — caller=$uid  target=$targetUserId  name='$targetDisplayName'")

            // ── Step 1: Write block entry ─────────────────────────────────────
            // Path: blocks/{uid}/entries/{targetUserId}
            // Firestore rule: allow read, write: if isSignedIn() && isOwner(blockerId)
            val blockRef = blockEntriesRef(uid).document(targetUserId)
            Log.d(BLOCK_TAG, "blockUser: writing to path=${blockRef.path}")

            val blockData = mapOf(
                "userId"             to uid,
                "blockedUserId"      to targetUserId,
                "blockedDisplayName" to targetDisplayName,
                "reason"             to reason,
                "blockedAt"          to com.google.firebase.Timestamp.now(),
            )
            Log.d(BLOCK_TAG, "blockUser: block data = $blockData")

            // Single-document set — no batch, no cross-user write that could
            // trigger PERMISSION_DENIED and silently swallow the block.
            blockRef.set(blockData).await()

            Log.d(BLOCK_TAG, "blockUser: SUCCESS — $uid blocked $targetUserId. " +
                    "Connection teardown will be handled by onBlockCreate Cloud Function.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(BLOCK_TAG, "blockUser FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // Unblock
    // =========================================================================

    /**
     * Removes the block entry for [targetUserId].
     * Does NOT automatically re-create the connection — users re-connect naturally
     * when either sends a scribble (Phase 2 auto-connect path).
     */
    suspend fun unblockUser(targetUserId: String): Result<Unit> {
        return try {
            val uid = currentUid
            val ref = blockEntriesRef(uid).document(targetUserId)
            Log.d(BLOCK_TAG, "unblockUser: START — caller=$uid  target=$targetUserId  path=${ref.path}")
            ref.delete().await()
            Log.d(BLOCK_TAG, "unblockUser: SUCCESS — $uid unblocked $targetUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(BLOCK_TAG, "unblockUser FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Real-time stream of all users that the current user has blocked.
     * Used by BlockedUsersScreen to render the list + Unblock actions.
     */
    fun getBlockedUsers(): Flow<List<BlockedUser>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run {
            Log.w(BLOCK_TAG, "getBlockedUsers: no authenticated user — emitting empty list")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        Log.d(BLOCK_TAG, "getBlockedUsers: attaching snapshot listener for uid=$uid  path=blocks/$uid/entries")

        val listener = blockEntriesRef(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Do NOT close(err) — that would crash the StateFlow collector.
                    // Emit an empty list and keep the channel open so the UI shows
                    // the empty-state instead of an unhandled exception.
                    Log.e(BLOCK_TAG, "getBlockedUsers: listener error — ${err.code} ${err.message}", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val rawCount = snap?.documents?.size ?: 0
                Log.d(BLOCK_TAG, "getBlockedUsers: snapshot received — $rawCount raw docs")
                val list = snap?.documents?.mapNotNull { doc ->
                    Log.d(BLOCK_TAG, "getBlockedUsers: parsing doc id=${doc.id}  data=${doc.data}")
                    try {
                        BlockedUser(
                            userId             = doc.getString("userId") ?: uid,
                            blockedUserId      = doc.getString("blockedUserId") ?: doc.id,
                            blockedDisplayName = doc.getString("blockedDisplayName") ?: "Sketchly User",
                            reason             = doc.getString("reason"),
                            blockedAt          = doc.getTimestamp("blockedAt")
                                ?.toDate()?.time
                                ?: System.currentTimeMillis(),
                        )
                    } catch (ex: Exception) {
                        Log.e(BLOCK_TAG, "getBlockedUsers: failed to parse doc ${doc.id} — ${ex.message}")
                        null
                    }
                }?.sortedBy { it.blockedDisplayName.lowercase() } ?: emptyList()

                Log.d(BLOCK_TAG, "getBlockedUsers: emitting ${list.size} blocked users")
                trySend(list)
            }

        awaitClose {
            Log.d(BLOCK_TAG, "getBlockedUsers: listener removed for uid=$uid")
            listener.remove()
        }
    }
}
