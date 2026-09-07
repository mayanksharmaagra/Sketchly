package com.jrprofessor.sketchly.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.functions.FirebaseFunctionsException
import com.jrprofessor.sketchly.data.repository.ContactRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "ContactSyncWorker"

/**
 * WorkManager CoroutineWorker responsible for periodic contact sync (every 24 h).
 * Reads device contacts, hashes them on-device, calls matchContactsByHash Cloud Function,
 * and updates Firestore suggestedContacts.
 */
@HiltWorker
class ContactSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val contactRepository: ContactRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "ContactSyncWorker starting background contact sync...")
        return try {
            val result = contactRepository.syncContacts()
            result.fold(
                onSuccess = { matchedCount ->
                    Log.d(TAG, "ContactSyncWorker sync succeeded: $matchedCount contacts matched")
                    Result.success()
                },
                onFailure = { error ->
                    if (isRateLimitException(error)) {
                        Log.w(TAG, "ContactSyncWorker sync skipped due to rate limit: ${error.message}")
                        Result.failure()
                    } else {
                        Log.e(TAG, "ContactSyncWorker sync failed: ${error.message}", error)
                        if (runAttemptCount < 3) Result.retry() else Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            if (isRateLimitException(e)) {
                Log.w(TAG, "ContactSyncWorker sync skipped due to rate limit: ${e.message}")
                Result.failure()
            } else {
                Log.e(TAG, "ContactSyncWorker exception: ${e.message}", e)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    private fun isRateLimitException(e: Throwable): Boolean {
        return e is FirebaseFunctionsException &&
                (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ||
                 e.message?.contains("limit reached", ignoreCase = true) == true)
    }

    companion object {
        const val WORK_NAME = "periodic_contact_sync_work"
    }
}
