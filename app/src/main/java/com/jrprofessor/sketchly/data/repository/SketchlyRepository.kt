package com.jrprofessor.sketchly.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jrprofessor.sketchly.data.local.SketchlyDao
import com.jrprofessor.sketchly.data.local.SketchlyEntity
import com.jrprofessor.sketchly.data.local.SenderInfo
import com.jrprofessor.sketchly.data.model.Reaction
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.worker.SendSketchlyWorker
import com.jrprofessor.sketchly.data.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Filter options for the History/Archive screen (SRS FR-8.2) */
sealed class HistoryFilter {
    data object All : HistoryFilter()
    data object Sent : HistoryFilter()
    data object Received : HistoryFilter()
    data class ByContact(val senderId: String) : HistoryFilter()
}

@Singleton
class SketchlyRepository @Inject constructor(
    private val sketchDao: SketchlyDao,
    private val firestore: FirebaseFirestore,
    private val workManager: WorkManager,
) {
    private val gson = Gson()
    private val strokeListType = object : TypeToken<List<Stroke>>() {}.type
    private val recipientListType = object : TypeToken<List<String>>() {}.type

    private var inboxListenerRegistration: ListenerRegistration? = null
    private var reactionListenerRegistration: ListenerRegistration? = null

    companion object {
        const val PAGE_SIZE = 20
    }

    // ── Local Room Flows ──

    fun getInboxSketches(): Flow<List<Sketch>> {
        return sketchDao.getInboxSketches().map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getAllHistory(): Flow<List<Sketch>> {
        return sketchDao.getAllSketches().map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getUnreadCount(): Flow<Int> = sketchDao.getUnreadCount()

    suspend fun getSketchById(id: String): Sketch? {
        val local = sketchDao.getById(id)
        if (local != null) return entityToDomain(local)

        // Try fetching remote if not in local Room
        return try {
            val doc = firestore.collection("scribbles").document(id).get().await()
            if (doc.exists()) {
                val entity = docToEntity(doc.data ?: emptyMap(), id)
                sketchDao.insert(entity)
                entityToDomain(entity)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ── Paginated History (SRS FR-8.3) ──

    /**
     * Returns one page of history items from Room, based on filter.
     * No full-dataset load — offset pagination keeps memory bounded.
     */
    suspend fun getPagedHistory(
        filter: HistoryFilter,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): List<Sketch> {
        val offset = page * pageSize
        val entities = when (filter) {
            is HistoryFilter.All -> sketchDao.getAllSketchesPage(pageSize, offset)
            is HistoryFilter.Sent -> sketchDao.getSentSketchesPage(pageSize, offset)
            is HistoryFilter.Received -> sketchDao.getReceivedSketchesPage(pageSize, offset)
            is HistoryFilter.ByContact -> sketchDao.getSketchesBySenderPage(filter.senderId, pageSize, offset)
        }
        return entities.map { entityToDomain(it) }
    }

    /** Returns unique senders from local Room for History filter chips */
    suspend fun getDistinctSenders(): List<SenderInfo> = sketchDao.getDistinctSenders()

    // ── Send Flow (Architecture §4 & SRS FR-4) ──

    suspend fun sendSketch(
        senderId: String,
        senderDisplayName: String,
        recipientIds: List<String>,
        strokes: List<Stroke>,
        backgroundColor: String = "#F4F1DE",
    ): Result<Sketch> {
        val sketchId = UUID.randomUUID().toString()
        val strokesJson = gson.toJson(strokes, strokeListType)
        val recipientsJson = gson.toJson(recipientIds, recipientListType)
        val now = System.currentTimeMillis()

        val entity = SketchlyEntity(
            id = sketchId,
            senderId = senderId,
            senderDisplayName = senderDisplayName,
            recipientIds = recipientsJson,
            strokesJson = strokesJson,
            backgroundColor = backgroundColor,
            createdAt = now,
            isRead = true,
            isSent = true,
            isDraft = false,
        )

        // 1. Optimistic local write to Room immediately (<200ms)
        sketchDao.insert(entity)

        // 2. Clear any active draft
        clearDraft()

        // 3. Asynchronously write to Firestore
        val domainSketch = entityToDomain(entity)
        try {
            val firestoreData = hashMapOf(
                "id" to sketchId,
                "senderId" to senderId,
                "senderDisplayName" to senderDisplayName,
                "recipientIds" to recipientIds,
                "strokesJson" to strokesJson,
                "backgroundColor" to backgroundColor,
                "createdAt" to now,
            )
            firestore.collection("scribbles").document(sketchId).set(firestoreData).await()
            // M3 (Architecture §4 step 6): sender's own widget refreshes immediately after send
            scheduleWidgetUpdate(sketchId)
            return Result.success(domainSketch)
        } catch (e: Exception) {
            // If remote write fails, schedule retry via WorkManager (FR-4.4)
            scheduleSendRetry(sketchId)
            return Result.success(domainSketch) // Still optimistic success locally
        }
    }

    private fun scheduleWidgetUpdate(sketchId: String) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(workDataOf(WidgetUpdateWorker.KEY_SKETCH_ID to sketchId))
            .build()
        workManager.enqueue(request)
    }

    private fun scheduleSendRetry(sketchId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val sendWorkRequest = OneTimeWorkRequestBuilder<SendSketchlyWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setInputData(workDataOf(SendSketchlyWorker.KEY_SKETCH_ID to sketchId))
            .build()

        workManager.enqueue(sendWorkRequest)
    }

    // ── Receive Flow (Firestore real-time sync to Room) ──

    fun startListeningToInbox(userId: String, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        stopListeningToInbox()
        if (userId.isBlank()) return

        inboxListenerRegistration = firestore.collection("scribbles")
            .whereArrayContains("recipientIds", userId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                scope.launch {
                    for (docChange in snapshots.documentChanges) {
                        val data = docChange.document.data
                        val id = docChange.document.id
                        val entity = docToEntity(data, id)

                        val existing = sketchDao.getById(id)
                        if (existing == null) {
                            sketchDao.insert(entity.copy(isSent = false, isRead = false))
                        }
                    }
                }
            }
    }

    fun stopListeningToInbox() {
        inboxListenerRegistration?.remove()
        inboxListenerRegistration = null
    }

    suspend fun markAsRead(id: String) {
        sketchDao.markAsRead(id)
    }

    // ── Reactions — Live Listener (SRS FR-7.3) ──

    /**
     * Starts a real-time Firestore listener for reactions on a given Sketch.
     * Calls [onUpdate] every time the reaction set changes.
     * Call [stopListeningToReactions] when the Viewer is destroyed.
     */
    fun listenToReactions(
        sketchId: String,
        scope: CoroutineScope,
        onUpdate: (List<Reaction>) -> Unit,
    ) {
        stopListeningToReactions()
        if (sketchId.isBlank()) return

        reactionListenerRegistration = firestore.collection("reactions")
            .whereEqualTo("scribbleId", sketchId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                scope.launch {
                    val reactions = snapshots.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        val sid = data["scribbleId"] as? String ?: data["sketchId"] as? String ?: ""
                        Reaction(
                            sketchId = sid,
                            userId = data["userId"] as? String ?: "",
                            userName = data["userName"] as? String ?: "",
                            emoji = data["emoji"] as? String ?: "",
                            createdAt = (data["createdAt"] as? Number)?.toLong()
                                ?: System.currentTimeMillis(),
                        )
                    }
                    onUpdate(reactions)
                }
            }
    }

    fun stopListeningToReactions() {
        reactionListenerRegistration?.remove()
        reactionListenerRegistration = null
    }

    // ── Reactions Write (SRS FR-7) ──

    suspend fun sendReaction(sketchId: String, userId: String, userName: String, emoji: String) {
        val reactionData = hashMapOf(
            "scribbleId" to sketchId,
            "sketchId" to sketchId,
            "userId" to userId,
            "userName" to userName,
            "emoji" to emoji,
            "createdAt" to System.currentTimeMillis(),
        )
        try {
            firestore.collection("reactions")
                .document("${sketchId}_${userId}")
                .set(reactionData, SetOptions.merge())
                .await()
        } catch (_: Exception) {}
    }

    // ── Draft Persistence (FR-3.5) ──

    suspend fun saveDraft(strokes: List<Stroke>, backgroundColor: String = "#F4F1DE") {
        if (strokes.isEmpty()) {
            clearDraft()
            return
        }
        val entity = SketchlyEntity(
            id = "current_draft",
            senderId = "",
            recipientIds = "[]",
            strokesJson = gson.toJson(strokes, strokeListType),
            backgroundColor = backgroundColor,
            createdAt = System.currentTimeMillis(),
            isDraft = true,
        )
        sketchDao.insert(entity)
    }

    suspend fun getDraft(): List<Stroke> {
        val draftEntity = sketchDao.getDraft() ?: return emptyList()
        return try {
            gson.fromJson(draftEntity.strokesJson, strokeListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearDraft() {
        val draft = sketchDao.getDraft()
        if (draft != null) {
            sketchDao.delete(draft)
        }
    }

    // ── Entity / Domain mapping helpers ──

    private fun entityToDomain(entity: SketchlyEntity): Sketch {
        val strokes: List<Stroke> = try {
            gson.fromJson(entity.strokesJson, strokeListType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val recipients: List<String> = try {
            gson.fromJson(entity.recipientIds, recipientListType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return Sketch(
            id = entity.id,
            senderId = entity.senderId,
            senderDisplayName = entity.senderDisplayName,
            recipientIds = recipients,
            strokes = strokes,
            backgroundColor = entity.backgroundColor,
            createdAt = entity.createdAt,
            isRead = entity.isRead,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun docToEntity(data: Map<String, Any>, id: String): SketchlyEntity {
        val senderId = data["senderId"] as? String ?: ""
        val senderDisplayName = data["senderDisplayName"] as? String ?: ""
        val recipientIds = data["recipientIds"] as? List<String> ?: emptyList()
        val strokesJson = data["strokesJson"] as? String ?: "[]"
        val backgroundColor = data["backgroundColor"] as? String ?: "#F4F1DE"
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

        return SketchlyEntity(
            id = id,
            senderId = senderId,
            senderDisplayName = senderDisplayName,
            recipientIds = gson.toJson(recipientIds, recipientListType),
            strokesJson = strokesJson,
            backgroundColor = backgroundColor,
            createdAt = createdAt,
            isRead = false,
            isSent = false,
            isDraft = false,
        )
    }
}
