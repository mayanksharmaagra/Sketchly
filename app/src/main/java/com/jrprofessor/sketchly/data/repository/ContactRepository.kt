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
import com.jrprofessor.sketchly.utils.FeatureFlags
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

    /** Sub-collection that lists everyone uid is connected to. */
    private fun connectionEntriesRef(uid: String) =
        firestore.collection("connections").document(uid).collection("entries")

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
     * Search Firestore users by username prefix.
     * Resolves connection status relative to the current user.
     * Returns empty list if query is blank.
     */
    suspend fun searchByUsername(query: String): List<SketchlyContact> {
        if (query.isBlank()) return emptyList()
        val uid = fireAuth.currentUser?.uid ?: return emptyList()
        val lowerQuery = query.trim().lowercase()
        return try {
            // Firestore prefix query on the indexed `username` field (all usernames are stored lowercase)
            val snapshot = firestore.collection("users")
                .orderBy("username")
                .startAt(lowerQuery)
                .endAt(lowerQuery + "\uf8ff")
                .whereEqualTo("isSearchable", true)
                .limit(20)
                .get()
                .await()

            // Exclude self; then resolve connection status for each result.
            //
            // [FEATURE FLAGGED — V2]
            // getPendingOutgoingIds() queries connectionRequests and is only called
            // when ENABLE_CONNECTION_REQUESTS = true to avoid unnecessary Firestore reads.
            // The call site and the helper are both fully preserved — flip the flag to re-enable.
            val connected = getConnectedIds(uid)
            val pending = if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
                getPendingOutgoingIds(uid)
            } else {
                emptySet() // no pending concept while flag is off
            }

            snapshot.documents
                .filter { it.id != uid }
                .mapNotNull { doc ->
                    val username = doc.getString("username") ?: return@mapNotNull null
                    val displayName = doc.getString("displayName") ?: username
                    val status = when {
                        doc.id in connected -> ConnectionStatus.CONNECTED
                        doc.id in pending   -> ConnectionStatus.PENDING_SENT
                        else                -> ConnectionStatus.SUGGESTED
                    }
                    SketchlyContact(
                        userId = doc.id,
                        displayName = displayName,
                        username = username,
                        avatarUrl = doc.getString("avatarUrl"),
                        phoneLastFour = null,
                        source = ContactSource.SEARCH,
                        connectionStatus = status,
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "searchByUsername failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Returns the set of user IDs to whom the current user has sent a pending connection request.
     *
     * [FEATURE FLAGGED — V2] Only called when ENABLE_CONNECTION_REQUESTS = true.
     * Preserved in full so flipping the flag immediately restores pending-state behaviour.
     */
    private suspend fun getPendingOutgoingIds(uid: String): Set<String> {
        return try {
            firestore.collection("connectionRequests")
                .whereEqualTo("fromUserId", uid)
                .whereEqualTo("status", "pending")
                .get().await()
                .documents.mapNotNull { it.getString("toUserId") }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /** Returns the set of user IDs that are confirmed connections of uid. */
    private suspend fun getConnectedIds(uid: String): Set<String> {
        return try {
            connectionEntriesRef(uid).get().await()
                .documents.map { it.id }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    /**
     * Returns the set of user IDs that the current user has blocked (or who have blocked them).
     * Used by getSuggestedContacts() to exclude blocked users from the Suggested list.
     * Both directions are checked: a user blocked by me OR a user who blocked me is excluded.
     */
    private suspend fun getBlockedIds(uid: String): Set<String> {
        // We only read the current user's OWN block list here.
        //
        // Why we don't check the reverse direction ("who blocked me") on the client:
        //   The collectionGroup("entries") query needed for that check collides with
        //   connections/{userId}/entries/{otherUserId}, whose Firestore rule requires
        //   isOwner(userId).  Any read of another user's connections entries fails with
        //   PERMISSION_DENIED, making the collection-group query unreliable/broken.
        //
        // The "blocked me" direction is enforced server-side:
        //   • onScribbleCreate Cloud Function already gates FCM fan-out behind the block check.
        //   • Suggested-contact lists are populated by matchContactsByHash, which also filters.
        //   So filtering "blocked me" contacts out of the suggested list is best-effort UI polish,
        //   not a security boundary — skipping it on the client is safe.
        return try {
            firestore
                .collection("blocks").document(uid).collection("entries")
                .get().await()
                .documents.map { it.id }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "getBlockedIds: failed — ${e.message}")
            emptySet()
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
     *
     * Filtering applied to every emission (flag-aware):
     *   - Always excluded: already connected, self, blocked (either direction)
     *   - Excluded only when ENABLE_CONNECTION_REQUESTS = true:
     *       users with an outstanding outgoing connection request (PENDING_SENT)
     *       — no pending concept exists in V1 auto-connect flow.
     *
     * getPendingOutgoingIds() and getBlockedIds() are both fully preserved;
     * they are simply skipped (returning emptySet) while their respective
     * features are inactive, costing zero Firestore reads.
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
                val raw = snap?.documents
                    ?.mapNotNull { docToContact(it) }
                    ?: emptyList()

                // Fetch exclusion sets on IO, then filter and emit.
                // `this@callbackFlow` is the ProducerScope — its launch() is
                // bound to the flow's lifecycle so it is cancelled automatically
                // when the collector cancels (unlike GlobalScope).
                this@callbackFlow.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val connectedIds = getConnectedIds(uid)
                    val blockedIds   = getBlockedIds(uid)
                    val pendingIds   = if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
                        getPendingOutgoingIds(uid)
                    } else {
                        emptySet() // no pending concept while flag is off
                    }

                    val filtered = raw
                        .filter { contact ->
                            contact.userId != uid &&          // self guard
                            contact.userId !in connectedIds && // already connected
                            contact.userId !in blockedIds &&   // blocked (either direction)
                            contact.userId !in pendingIds      // pending sent (V2 only)
                        }
                        .sortedBy { it.displayName.lowercase() }

                    Log.d(TAG, "getSuggestedContacts: ${raw.size} raw → ${filtered.size} after filtering")
                    trySend(filtered)
                }
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
     * Real-time stream of accepted connections from the sub-collection
     *   connections/{uid}/entries/{otherUserId}
     * Written exclusively by onConnectionRequestAccept Cloud Function.
     * Used by: Recipient Picker (DrawViewModel) and the Connected tab.
     */
    fun getConnectedContacts(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run {
            Log.w(TAG, "getConnectedContacts: currentUser is null — sending emptyList()")
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        Log.d(TAG, "getConnectedContacts: registering listener on connections/$uid/entries")

        val listener = connectionEntriesRef(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "getConnectedContacts: listener error — ${err.message}", err)
                    close(err)
                    return@addSnapshotListener
                }
                val raw = snap?.documents
                    ?.mapNotNull { doc ->
                        val otherId = doc.getString("connectedUserId") ?: doc.id
                        try {
                            SketchlyContact(
                                userId           = otherId,
                                displayName      = doc.getString("displayName") ?: "Sketchly User",
                                username         = doc.getString("username") ?: "",
                                avatarUrl        = doc.getString("avatarUrl"),
                                phoneLastFour    = null,
                                source           = ContactSource.valueOf(
                                    doc.getString("source") ?: ContactSource.CONTACT_SYNC.name
                                ),
                                connectionStatus = ConnectionStatus.CONNECTED,
                            )
                        } catch (_: Exception) { null }
                    }
                    ?: emptyList()

                // Filter blocked users on IO — same pattern as getSuggestedContacts().
                // Covers the race window between the client-side block write and the
                // onBlockCreate Cloud Function removing the connection document.
                this@callbackFlow.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val blockedIds = getBlockedIds(uid)
                    val filtered = raw
                        .filter { contact -> contact.userId !in blockedIds }
                        .sortedBy { it.displayName.lowercase() }
                    Log.d(TAG, "getConnectedContacts: ${raw.size} raw → ${filtered.size} after block filter")
                    trySend(filtered)
                }
            }

        awaitClose {
            Log.d(TAG, "getConnectedContacts: listener removed")
            listener.remove()
        }
    }

    /**
     * One-shot check: returns true if the current user is connected to [otherUserId].
     * Used by ViewerViewModel to decide whether to show the "Connect?" banner.
     */
    suspend fun isConnectedTo(otherUserId: String): Boolean {
        val uid = fireAuth.currentUser?.uid ?: return false
        return try {
            connectionEntriesRef(uid).document(otherUserId).get().await().exists()
        } catch (e: Exception) {
            Log.w(TAG, "isConnectedTo: failed — ${e.message}")
            false
        }
    }

    // ============================================================
    // 3b. INCOMING CONNECTION REQUESTS — Requests tab
    // ============================================================

    /**
     * Real-time stream of pending incoming connection requests
     * (where toUserId == current user).
     * Used by: Friends screen Requests tab + badge count.
     */
    fun getIncomingConnectionRequests(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore
            .collection("connectionRequests")
            .whereEqualTo("toUserId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val fromUserId = doc.getString("fromUserId") ?: return@mapNotNull null
                    SketchlyContact(
                        userId           = fromUserId,
                        displayName      = doc.getString("fromDisplayName") ?: "Sketchly User",
                        username         = doc.getString("fromUsername") ?: "",
                        avatarUrl        = doc.getString("fromAvatarUrl"),
                        phoneLastFour    = null,
                        source           = ContactSource.CONNECTION_REQUEST,
                        connectionStatus = ConnectionStatus.PENDING_RECEIVED,
                    )
                } ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    // ============================================================
    // 4. SEND CONNECTION REQUEST
    // ============================================================

    /**
     * Creates a connection request from the current user to [toUser].
     * requestId = "{fromUid}_{toUid}" prevents duplicate requests naturally.
     * Cloud Function onConnectionRequestCreate pushes FCM notification to recipient.
     */
    suspend fun sendConnectionRequest(toUser: SketchlyContact): Result<Unit> {
        return try {
            val uid = currentUid
            val requestId = "${uid}_${toUser.userId}"
            val ref = firestore.collection("connectionRequests").document(requestId)

            // Duplicate check — if request already exists just treat as success
            // (button stays "Pending ✓" — do NOT revert optimistic state)
            if (ref.get().await().exists()) {
                Log.d(TAG, "sendConnectionRequest: doc $requestId already exists — treating as success")
                return Result.success(Unit)
            }

            // Fetch sender profile from Firestore for the correct display name
            val senderDoc = firestore.collection("users").document(uid).get().await()
            val senderName = senderDoc.getString("displayName") ?: fireAuth.currentUser?.displayName ?: ""
            val senderUsername = senderDoc.getString("username") ?: ""
            val senderAvatarUrl = senderDoc.getString("avatarUrl") ?: fireAuth.currentUser?.photoUrl?.toString()

            ref.set(mapOf(
                "id"              to requestId,
                "fromUserId"      to uid,
                "fromDisplayName" to senderName,
                "fromAvatarUrl"   to senderAvatarUrl,
                "fromUsername"    to senderUsername,
                "toUserId"        to toUser.userId,
                "scribbleId"      to "",
                "status"          to "pending",
                "createdAt"       to com.google.firebase.Timestamp.now(),
                "lastDeclinedAt"  to null,
            )).await()

            // Optimistically update the local suggested contact status
            updateSuggestedStatus(toUser.userId, ConnectionStatus.PENDING_SENT)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendConnectionRequest failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ============================================================
    // 5. ACCEPT / DECLINE / CANCEL
    // ============================================================

    /**
     * Accepts an incoming connection request.
     * Cloud Function onConnectionRequestAccept creates symmetric
     * connections/{uid}/entries/{other} docs and notifies the sender.
     */
    suspend fun acceptConnectionRequest(fromUserId: String): Result<Unit> {
        return try {
            firestore.collection("connectionRequests")
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
     * Silently declines an incoming connection request.
     * Sets status=declined + stamps lastDeclinedAt (cooldown enforcement in Cloud Function).
     * Sender is NOT notified (avoids awkwardness per UX spec).
     */
    suspend fun declineConnectionRequest(fromUserId: String): Result<Unit> {
        return try {
            firestore.collection("connectionRequests")
                .document("${fromUserId}_${currentUid}")
                .update(mapOf(
                    "status"          to "declined",
                    "lastDeclinedAt"  to com.google.firebase.Timestamp.now(),
                    "updatedAt"       to com.google.firebase.Timestamp.now(),
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cancels an outgoing pending connection request.
     * Deletes the doc + reverts local suggested contact status to SUGGESTED.
     */
    suspend fun cancelConnectionRequest(toUserId: String): Result<Unit> {
        return try {
            firestore.collection("connectionRequests")
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
    // 6. OUTGOING REQUEST STATUS STREAM
    // ============================================================

    /**
     * Real-time stream of outgoing pending connection requests sent by the
     * current user. Used by the Suggested tab to show "Pending" chips.
     */
    fun getOutgoingConnectionRequests(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore
            .collection("connectionRequests")
            .whereEqualTo("fromUserId", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    val toUserId = doc.getString("toUserId") ?: return@mapNotNull null
                    SketchlyContact(
                        userId           = toUserId,
                        displayName      = "", // display name not stored on outgoing side
                        username         = "",
                        avatarUrl        = null,
                        phoneLastFour    = null,
                        source           = ContactSource.CONNECTION_REQUEST,
                        connectionStatus = ConnectionStatus.PENDING_SENT,
                    )
                } ?: emptyList()
                trySend(list)
            }

        awaitClose { listener.remove() }
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
        // connections sub-collection docs use "connectedUserId"; suggestedContacts use "userId".
        val userId = doc.getString("connectedUserId") ?: doc.getString("userId") ?: doc.id
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
