package com.jrprofessor.sketchly.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.jrprofessor.sketchly.data.local.ContactDao
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.data.model.ConnectionStatus
import com.jrprofessor.sketchly.data.model.ContactSource
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.data.model.User
import com.jrprofessor.sketchly.utils.ContactHashUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
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
        firestore.collection("users").document(uid).collection("connections")

    fun getContacts(userId: String): Flow<List<ContactEntity>> {
        return contactDao.getAllContacts(userId)
    }

    fun getSketchlyContacts(userId: String): Flow<List<ContactEntity>> {
        return contactDao.getSketchlyContacts(userId)
    }

    fun searchContacts(userId: String, query: String): Flow<List<ContactEntity>> {
        return contactDao.searchContacts(userId, query)
    }

    /**
     * Add contact manually by email or phone.
     * Looks up user in Firestore "users" collection to see if they're registered.
     */
    suspend fun addContact(
        userId: String,
        displayName: String,
        email: String = "",
        phone: String = "",
    ): Result<ContactEntity> {
        return try {
            // Check if there is an existing user in Firestore
            var matchedUid = ""
            var isOnSketchly = false

            if (email.isNotBlank()) {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("email", email.trim().lowercase())
                    .limit(1)
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    matchedUid = snapshot.documents.first().id
                    isOnSketchly = true
                }
            }

            if (!isOnSketchly && phone.isNotBlank()) {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("phoneNumber", phone.trim())
                    .limit(1)
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    matchedUid = snapshot.documents.first().id
                    isOnSketchly = true
                }
            }

            val contact = ContactEntity(
                id = if (matchedUid.isNotBlank()) matchedUid else UUID.randomUUID().toString(),
                userId = userId,
                contactUserId = matchedUid,
                displayName = displayName.trim(),
                email = email.trim(),
                phoneNumber = phone.trim(),
                source = "manual",
                isOnSketchly = isOnSketchly,
                createdAt = System.currentTimeMillis()
            )

            contactDao.insertOrUpdate(contact)
            Result.success(contact)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contact: ContactEntity) {
        contactDao.delete(contact)
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
        return try {
            val uid = currentUid

            // Step 1 — hash on device, raw numbers never transmitted
            val hashes = ContactHashUtil.getHashedPhoneNumbers(context)
            if (hashes.isEmpty()) return Result.success(0)

            // Step 2 — Cloud Function (Firebase attaches ID token automatically)
            val matched = callMatchContactsFunction(uid, hashes)

            // Step 3 — persist to Firestore
            saveToFirestore(uid, matched)

            Result.success(matched.size)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callMatchContactsFunction(
        callerUid: String,
        hashes: List<String>,
    ): List<SketchlyContact> {
        val allMatches = mutableListOf<SketchlyContact>()

        // Chunk into 500 — Cloud Function payload limit
        hashes.chunked(500).forEach { batch ->
            val result = functions
                .getHttpsCallable("matchContactsByHash")
                .call(mapOf("hashes" to batch))
                .await()

            @Suppress("UNCHECKED_CAST")
            val rawList = (result.data as? Map<String, Any>)
                ?.get("matches") as? List<Map<String, Any>>
                ?: emptyList()

            rawList.forEach { map ->
                val userId = map["uid"] as? String ?: return@forEach
                if (userId == callerUid) return@forEach // never include self
                allMatches.add(
                    SketchlyContact(
                        userId = userId,
                        displayName = map["displayName"] as? String ?: "Sketchly User",
                        username   = map["username"]    as? String ?: "",
                        avatarUrl  = map["avatarUrl"]   as? String,
                        phoneLastFour = map["phoneLastFour"] as? String,
                        source = ContactSource.CONTACT_SYNC,
                        connectionStatus = ConnectionStatus.SUGGESTED,
                    )
                )
            }
        }
        return allMatches.distinctBy { it.userId }
    }

    private suspend fun saveToFirestore(uid: String, contacts: List<SketchlyContact>) {
        val ref = suggestedRef(uid)

        // Clear previous sync results
        val old = ref.get().await()
        val delBatch = firestore.batch()
        old.documents.forEach { delBatch.delete(it.reference) }
        if (old.documents.isNotEmpty()) delBatch.commit().await()

        if (contacts.isEmpty()) return

        // Write new results — chunk for Firestore 500-write batch limit
        contacts.chunked(500).forEach { chunk ->
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
        }
    }

    // ============================================================
    // 2. SUGGESTED CONTACTS — Find Friends screen
    // ============================================================

    /**
     * Real-time stream of contact sync results.
     * Used by: Find Friends / "People You May Know" section.
     */
    fun getSuggestedContacts(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = suggestedRef(uid)
            .orderBy("displayName")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { docToContact(it) } ?: emptyList())
            }

        awaitClose { listener.remove() }
    }

    // ============================================================
    // 3. CONNECTED CONTACTS — Recipient Picker
    // ============================================================

    /**
     * Real-time stream of accepted connections.
     * Used by: Recipient Picker (only connected users can receive Scribbles).
     */
    fun getConnectedContacts(): Flow<List<SketchlyContact>> = callbackFlow {
        val uid = fireAuth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val listener = connectionsRef(uid)
            .orderBy("displayName")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { docToContact(it) } ?: emptyList())
            }

        awaitClose { listener.remove() }
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
            val ref = firestore.collection("follow_requests").document(requestId)

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
            .collection("follow_requests")
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
            firestore.collection("follow_requests")
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
            firestore.collection("follow_requests")
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
            firestore.collection("follow_requests")
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
        val userId = doc.getString("userId") ?: return null
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
