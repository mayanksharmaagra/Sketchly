package com.jrprofessor.sketchly.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
// V1: Room Contact imports hidden
// import com.jrprofessor.sketchly.data.local.ContactDao
// import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.model.ConnectionStatus
import com.jrprofessor.sketchly.data.model.ContactSource
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.model.User
import com.jrprofessor.sketchly.utils.ContactHashUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContactRepository"

@Singleton
class ContactRepository @Inject constructor(
    // V1: Room ContactDao hidden — private val contactDao: ContactDao,
    private val firestore: FirebaseFirestore,
    private val fireAuth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val context: Context,
) {

    // ============================================================
    // Helpers
    // ============================================================

    private val currentUid: String
        get() = fireAuth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

    private fun suggestedRef(uid: String) =
        firestore.collection("users").document(uid).collection("suggestedContacts")

    private fun connectionsRef(uid: String) =
        firestore.collection("connections").whereEqualTo("userAId", uid)

    // V1: These Room-based flows are hidden. Contact list is now driven by
    // getSuggestedContacts() and getConnectedContacts() (Firestore-only).
    fun getContacts(userId: String): Flow<List<Nothing>> {
        // V1 HIDDEN: return contactDao.getAllContacts(userId)
        return emptyFlow()
    }

    fun getSketchlyContacts(userId: String): Flow<List<Nothing>> {
        // V1 HIDDEN: return contactDao.getSketchlyContacts(userId)
        return emptyFlow()
    }

    fun searchContacts(userId: String, query: String): Flow<List<Nothing>> {
        // V1 HIDDEN: return contactDao.searchContacts(userId, query)
        return emptyFlow()
    }

    /**
     * Add contact manually by email or phone.
     * Looks up user in Firestore "users" collection to see if they're registered.
     */
    // V1: Manual addContact feature hidden (depends on Room write)
    // All contact discovery goes through syncContacts() + getSuggestedContacts()
    suspend fun addContact(
        userId: String,
        displayName: String,
        email: String = "",
        phone: String = "",
    ): Result<Nothing> {
        // V1 HIDDEN: Room write + Firestore lookup removed
        // contactDao.insertOrUpdate(contact)
        return Result.failure(UnsupportedOperationException("V1: addContact disabled. Use contact sync."))
    }

    // V1: deleteContact hidden (Room-based)
    suspend fun deleteContact(contact: Any) {
        // V1 HIDDEN: contactDao.delete(contact)
    }

    /**
     * Search global users in Firestore by displayName or email prefix for finding friends
     */
    suspend fun searchGlobalUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .orderBy("displayName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(10)
                .get()
                .await()
            snapshot.toObjects(User::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }


    // ============================================================
    // 1. CONTACT SYNC
    // ============================================================

    /**
     * Full contact sync flow:
     *   1. Read device contacts → hash (raw numbers never leave device)
     *   2. Call matchContactsByHash Cloud Function with hashes only
     *   3. Save matched UserProfiles to Firestore suggestedContacts
     *
     * @return Result.success(matchedCount) or Result.failure(error)
     */
    suspend fun syncContacts(): Result<Int> {
        Log.d(TAG, "syncContacts() called")
        return try {
            // ── Auth check ─────────────────────────────────────────────────────
            val user = fireAuth.currentUser
            if (user == null) {
                Log.e(TAG, "syncContacts: currentUser is NULL — user not authenticated")
                return Result.failure(IllegalStateException("User not authenticated"))
            }
            Log.d(TAG, "syncContacts: currentUser uid=${user.uid}, email=${user.email}, phone=${user.phoneNumber}")

            // Force-refresh ID token so Functions SDK always sends a fresh token.
            // This fixes UNAUTHENTICATED errors caused by stale/expired tokens.
            Log.d(TAG, "syncContacts: forcing ID token refresh...")
            val tokenResult = user.getIdToken(/* forceRefresh= */ true).await()
            Log.d(TAG, "syncContacts: ID token refreshed — expiresAt=${tokenResult.expirationTimestamp}s, uid=${tokenResult.claims["user_id"]}")

            val uid = user.uid

            // ── Step 1: Hash contacts on-device ────────────────────────────────
            Log.d(TAG, "syncContacts: reading + hashing device contacts...")
            val hashes = ContactHashUtil.getHashedPhoneNumbers(context)
            Log.d(TAG, "syncContacts: got ${hashes.size} unique hashes (raw numbers never leave device)")
            if (hashes.isEmpty()) {
                Log.d(TAG, "syncContacts: no hashes — device has no contacts with phone numbers, returning 0")
                return Result.success(0)
            }

            // ── Step 2: Call Cloud Function ────────────────────────────────────
            Log.d(TAG, "syncContacts: calling matchContactsByHash Cloud Function with ${hashes.size} hashes...")
            val matched = callMatchContactsFunction(uid, hashes)
            Log.d(TAG, "syncContacts: Cloud Function returned ${matched.size} matched contacts")

            // ── Step 3: Persist to Firestore ───────────────────────────────────
            Log.d(TAG, "syncContacts: saving ${matched.size} contacts to Firestore suggestedContacts...")
            saveToFirestore(uid, matched)
            Log.d(TAG, "syncContacts: Firestore write complete")

            Log.d(TAG, "syncContacts: SUCCESS — matched=${matched.size}")
            Result.success(matched.size)

        } catch (e: Exception) {
            if (e is FirebaseFunctionsException &&
                (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
                 e.message?.contains("limit reached", ignoreCase = true) == true)
            ) {
                Log.w(TAG, "syncContacts: rate limit reached — ${e.message}")
            } else {
                Log.e(TAG, "syncContacts: FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
            }
            Result.failure(e)
        }
    }

    private suspend fun callMatchContactsFunction(
        callerUid: String,
        hashes: List<String>,
    ): List<SketchlyContact> {
        val allMatches = mutableListOf<SketchlyContact>()
        val totalBatches = (hashes.size + 499) / 500
        Log.d(TAG, "callMatchContactsFunction: callerUid=$callerUid, totalHashes=${hashes.size}, batches=$totalBatches")

        // Chunk into 500 — Cloud Function payload limit
        hashes.chunked(500).forEachIndexed { batchIndex, batch ->
            Log.d(TAG, "callMatchContactsFunction: sending batch ${batchIndex + 1}/$totalBatches (${batch.size} hashes)")
            try {
                val result = functions
                    .getHttpsCallable("matchContactsByHash")
                    .call(mapOf("hashes" to batch))
                    .await()

                Log.d(TAG, "callMatchContactsFunction: batch ${batchIndex + 1} response received, data type=${result.data?.javaClass?.simpleName}")

                @Suppress("UNCHECKED_CAST")
                val rawList = (result.data as? Map<String, Any>)
                    ?.get("matches") as? List<Map<String, Any>>
                    ?: emptyList()

                Log.d(TAG, "callMatchContactsFunction: batch ${batchIndex + 1} — rawList size=${rawList.size}")

                rawList.forEach { map ->
                    val userId = map["uid"] as? String ?: run {
                        Log.w(TAG, "callMatchContactsFunction: skipping entry with null uid — $map")
                        return@forEach
                    }
                    if (userId == callerUid) {
                        Log.d(TAG, "callMatchContactsFunction: skipping self (uid=$userId)")
                        return@forEach
                    }
                    allMatches.add(
                        SketchlyContact(
                            userId        = userId,
                            displayName   = map["displayName"] as? String ?: "Sketchly User",
                            username      = map["username"]    as? String ?: "",
                            avatarUrl     = map["avatarUrl"]   as? String,
                            phoneLastFour = map["phoneLastFour"] as? String,
                            source        = ContactSource.CONTACT_SYNC,
                            connectionStatus = ConnectionStatus.SUGGESTED,
                        )
                    )
                    Log.d(TAG, "callMatchContactsFunction: matched user — uid=$userId, displayName=${map["displayName"]}")
                }
            } catch (e: Exception) {
                if (e is FirebaseFunctionsException &&
                    (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
                     e.message?.contains("limit reached", ignoreCase = true) == true)
                ) {
                    Log.w(TAG, "callMatchContactsFunction: batch ${batchIndex + 1} rate limit reached — ${e.message}")
                } else {
                    Log.e(TAG, "callMatchContactsFunction: batch ${batchIndex + 1} FAILED — ${e.javaClass.simpleName}: ${e.message}", e)
                }
                throw e // re-throw so syncContacts() catches and returns Result.failure
            }
        }

        val distinct = allMatches.distinctBy { it.userId }
        Log.d(TAG, "callMatchContactsFunction: final distinct matches=${distinct.size}")
        return distinct
    }

    private suspend fun saveToFirestore(uid: String, contacts: List<SketchlyContact>) {
        Log.d(TAG, "saveToFirestore: uid=$uid, contacts=${contacts.size}")
        val ref = suggestedRef(uid)

        // Clear previous sync results
        val old = ref.get().await()
        Log.d(TAG, "saveToFirestore: deleting ${old.documents.size} old suggestedContacts docs")
        val delBatch = firestore.batch()
        old.documents.forEach { delBatch.delete(it.reference) }
        if (old.documents.isNotEmpty()) delBatch.commit().await()

        if (contacts.isEmpty()) {
            Log.d(TAG, "saveToFirestore: no new contacts to write — done")
            return
        }

        // Write new results — chunk for Firestore 500-write batch limit
        contacts.chunked(500).forEachIndexed { idx, chunk ->
            Log.d(TAG, "saveToFirestore: writing chunk ${idx + 1} (${chunk.size} docs)")
            val writeBatch = firestore.batch()
            chunk.forEach { c ->
                writeBatch.set(
                    ref.document(c.userId),
                    mapOf(
                        "userId"           to c.userId,
                        "displayName"      to c.displayName,
                        "username"         to c.username,
                        "avatarUrl"        to c.avatarUrl,
                        "phoneLastFour"    to c.phoneLastFour,
                        "source"           to c.source.name,
                        "connectionStatus" to c.connectionStatus.name,
                        "syncedAt"         to com.google.firebase.Timestamp.now(),
                    )
                )
            }
            writeBatch.commit().await()
            Log.d(TAG, "saveToFirestore: chunk ${idx + 1} written successfully")
        }
        Log.d(TAG, "saveToFirestore: all done")
    }

    // ============================================================
    // 2. SUGGESTED CONTACTS — Find Friends screen
    // ============================================================

    /**
     * Real-time stream of contact sync results.
     * Used by: Find Friends / "People You May Know" section.
     */
    fun getSuggestedContacts(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run {
            Log.w(TAG, "getSuggestedContacts: currentUser is null — sending emptyList()")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        Log.d(TAG, "getSuggestedContacts: registering snapshot listener on suggestedContacts for uid=$uid")

        val listener = suggestedRef(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "getSuggestedContacts: listener error — ${err.message}", err)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { docToContact(it) }
                    ?.sortedBy { it.displayName.lowercase() }
                    ?: emptyList()
                Log.d(TAG, "getSuggestedContacts: snapshot received with ${list.size} contacts")
                trySend(list)
            }

        awaitClose {
            Log.d(TAG, "getSuggestedContacts: snapshot listener removed")
            listener.remove()
        }
    }

    // ============================================================
    // 3. CONNECTED CONTACTS — Recipient Picker
    // ============================================================

    /**
     * Real-time stream of accepted connections.
     * Used by: Recipient Picker (only connected users can receive Scribbles).
     */
    fun getConnectedContacts(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run {
            Log.w(TAG, "getConnectedContacts: currentUser is null — sending emptyList()")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        Log.d(TAG, "getConnectedContacts: registering snapshot listener on connections for uid=$uid")

        val listener = connectionsRef(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "getConnectedContacts: listener error — ${err.message}", err)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { docToContact(it) }
                    ?.sortedBy { it.displayName.lowercase() }
                    ?: emptyList()
                Log.d(TAG, "getConnectedContacts: snapshot received with ${list.size} connected contacts")
                trySend(list)
            }

        awaitClose {
            Log.d(TAG, "getConnectedContacts: snapshot listener removed")
            listener.remove()
        }
    }

    // ============================================================
    // 4. SEND FOLLOW REQUEST
    // ============================================================

    /**
     * Sends a follow request.
     * requestId = "{fromUid}_{toUid}" — prevents duplicates naturally.
     * Cloud Function onFollowRequestCreate pushes notification to recipient.
     */
    suspend fun sendFollowRequest(toUser: SketchlyContact): Result<Unit> {
        return try {
            val uid = currentUid
            val requestId = "${uid}_${toUser.userId}"
            val ref = firestore.collection("followRequests").document(requestId)

            // Duplicate check
            if (ref.get().await().exists()) {
                return Result.failure(IllegalStateException("Request already sent"))
            }

            ref.set(mapOf(
                "id"              to requestId,
                "fromUserId"      to uid,
                "fromDisplayName" to (fireAuth.currentUser?.displayName ?: ""),
                "fromAvatarUrl"   to (fireAuth.currentUser?.photoUrl?.toString()),
                "toUserId"        to toUser.userId,
                "status"          to "pending",
                "createdAt"       to com.google.firebase.Timestamp.now(),
            )).await()

            // Update suggested contact status in Firestore
            updateSuggestedStatus(toUser.userId, ConnectionStatus.PENDING_SENT)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // 5. INCOMING FOLLOW REQUESTS
    // ============================================================

    /**
     * Real-time stream of pending incoming follow requests.
     * Used by: Follow Requests screen + notification badge count.
     */
    fun getIncomingFollowRequests(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore
            .collection("followRequests")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val fromUserId = doc.getString("fromUserId") ?: return@mapNotNull null
                    SketchlyContact(
                        userId           = fromUserId,
                        displayName      = doc.getString("fromDisplayName") ?: "Sketchly User",
                        username         = "",
                        avatarUrl        = doc.getString("fromAvatarUrl"),
                        phoneLastFour    = null,
                        source           = ContactSource.SEARCH,
                        connectionStatus = ConnectionStatus.PENDING_RECEIVED,
                    )
                } ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    // ============================================================
    // 6. ACCEPT / DECLINE / CANCEL
    // ============================================================

    /**
     * Accepts incoming request → status = "accepted"
     * Cloud Function onFollowRequestAccept creates connection docs on both sides
     * and sends FCM to the original sender.
     */
    suspend fun acceptFollowRequest(fromUserId: String): Result<Unit> {
        return try {
            firestore.collection("followRequests")
                .document("${fromUserId}_${currentUid}")
                .update(mapOf(
                    "status"    to "accepted",
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Declines incoming request → status = "declined"
     * Silent decline — sender is NOT notified (per SRS).
     */
    suspend fun declineFollowRequest(fromUserId: String): Result<Unit> {
        return try {
            firestore.collection("followRequests")
                .document("${fromUserId}_${currentUid}")
                .update(mapOf(
                    "status"    to "declined",
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels outgoing pending request.
     * Deletes the request doc + reverts suggested contact status.
     */
    suspend fun cancelFollowRequest(toUserId: String): Result<Unit> {
        return try {
            firestore.collection("followRequests")
                .document("${currentUid}_${toUserId}")
                .delete()
                .await()
            updateSuggestedStatus(toUserId, ConnectionStatus.SUGGESTED)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================
    // 7. PRIVATE HELPERS
    // ============================================================

    private suspend fun updateSuggestedStatus(
        contactUserId: String,
        status: ConnectionStatus,
    ) {
        val uid = fireAuth.currentUser?.uid ?: return
        try {
            suggestedRef(uid)
                .document(contactUserId)
                .update("connectionStatus", status.name)
                .await()
        } catch (_: Exception) {
            // Doc may not exist if contact was found via search — ignore
        }
    }

    private fun docToContact(
        doc: com.google.firebase.firestore.DocumentSnapshot,
    ): SketchlyContact? {
        val userId = doc.getString("userId") ?: doc.getString("userBId") ?: doc.id
        return try {
            SketchlyContact(
                userId      = userId,
                displayName = doc.getString("displayName") ?: "Sketchly User",
                username    = doc.getString("username")    ?: "",
                avatarUrl   = doc.getString("avatarUrl"),
                phoneLastFour = doc.getString("phoneLastFour"),
                source = ContactSource.valueOf(
                    doc.getString("source") ?: ContactSource.CONTACT_SYNC.name
                ),
                connectionStatus = ConnectionStatus.valueOf(
                    doc.getString("connectionStatus") ?: ConnectionStatus.SUGGESTED.name
                ),
            )
        } catch (_: Exception) { null }
    }
}
