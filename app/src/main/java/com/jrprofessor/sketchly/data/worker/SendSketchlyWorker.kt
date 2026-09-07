package com.jrprofessor.sketchly.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jrprofessor.sketchly.data.local.SketchlyDao
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.model.Stroke
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

/**
 * Background WorkManager worker to retry failed Sketch sends with exponential backoff.
 * Matches SRS FR-4.4 (retry up to 5 times on network failure).
 *
 * Uses @HiltWorker so Hilt can inject its dependencies via HiltWorkerFactory.
 */
@HiltWorker
class SendSketchlyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sketchDao: SketchlyDao,
    private val firestore: FirebaseFirestore,
) : CoroutineWorker(context, workerParams) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val sketchId = inputData.getString(KEY_SKETCH_ID) ?: return Result.failure()

        val entity = sketchDao.getById(sketchId) ?: return Result.failure()

        return try {
            val strokeListType = object : TypeToken<List<Stroke>>() {}.type
            val recipientListType = object : TypeToken<List<String>>() {}.type
            val recipients: List<String> = gson.fromJson(entity.recipientIds, recipientListType) ?: emptyList()

            val firestoreData = hashMapOf(
                "id" to entity.id,
                "senderId" to entity.senderId,
                "recipientIds" to recipients,
                "strokesJson" to entity.strokesJson,
                "backgroundColor" to entity.backgroundColor,
                "createdAt" to entity.createdAt,
            )

            firestore.collection("scribbles").document(entity.id).set(firestoreData).await()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_SKETCH_ID = "key_sketch_id"
    }
}
