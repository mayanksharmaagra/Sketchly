package com.jrprofessor.sketchly.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jrprofessor.sketchly.MainActivity
import com.jrprofessor.sketchly.data.worker.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

/**
 * Firebase Cloud Messaging receiver for Sketch.
 *
 * Handles two FCM message types:
 *  1. [MSG_TYPE_NEW_SKETCH] — incoming Sketch data push.
 *     Enqueues a [WidgetUpdateWorker] to fetch + render the widget bitmap.
 *  2. [MSG_TYPE_REACTION] — reaction notification push (M4 `onReactionCreate`).
 *     Posts a visible system-tray notification on the [CHANNEL_REACTIONS] channel.
 *
 * [onNewToken] — writes the refreshed FCM token to Firestore so Cloud Functions
 * can address this device by its current token.
 *
 * Registered in AndroidManifest.xml with intent-filter for
 * `com.google.firebase.MESSAGING_EVENT`.
 */
class SketchlyMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        // Ensure both notification channels exist before any message arrives (M5)
        createNotificationChannels()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"] ?: return

        Log.d(TAG, "FCM received: type=$type data=$data")

        when (type) {
            MSG_TYPE_NEW_SKETCH -> {
                val sketchId = data["sketchId"] ?: return
                enqueueWidgetUpdate(sketchId)
            }

            // M4: Reaction notification from `onReactionCreate` Cloud Function (SRS FR-7.3)
            MSG_TYPE_REACTION -> {
                val sketchId = data["sketchId"] ?: return
                val emoji = data["emoji"] ?: "❤️"
                val reactorId = data["reactorId"] ?: "Someone"
                postReactionNotification(sketchId, emoji, reactorId)
            }

            else -> Log.w(TAG, "Unknown FCM message type: $type")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        saveFcmToken(token)
    }

    // ── Private helpers ──

    /**
     * Creates the notification channels required by the app on Android 8.0+ (API 26+).
     * Safe to call multiple times — the OS is idempotent for existing channel IDs.
     *
     * Channels:
     *  - [CHANNEL_REACTIONS] HIGH importance — reaction nudges (M5: fulfils Cloud Function
     *    `channelId: "reactions"` payload expectation from `onReactionCreate`)
     *  - [CHANNEL_SKETCHES] DEFAULT importance — new Sketch system notifications
     */
    private fun createNotificationChannels() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reactionsChannel = NotificationChannel(
            CHANNEL_REACTIONS,
            "Reactions",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications when someone reacts to your Sketch"
        }

        val sketchesChannel = NotificationChannel(
            CHANNEL_SKETCHES,
            "New Sketches",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifications when you receive a new Sketch"
        }

        notificationManager.createNotificationChannel(reactionsChannel)
        notificationManager.createNotificationChannel(sketchesChannel)
    }

    /**
     * Posts a visible system-tray notification for a reaction (SRS FR-7.3).
     * Tapping the notification deep-links into the Sketch viewer.
     */
    private fun postReactionNotification(sketchId: String, emoji: String, reactorId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to_sketch", sketchId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            sketchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_REACTIONS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New reaction $emoji")
            .setContentText("Someone reacted $emoji to your Sketch")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(sketchId.hashCode(), notification)
        Log.d(TAG, "Reaction notification posted for sketch $sketchId, emoji=$emoji")
    }

    /**
     * Enqueues a [WidgetUpdateWorker] with the incoming [sketchId].
     * Uses CONNECTED network constraint so the fetch is reliable.
     * Short initial delay (1s) avoids racing the Firestore write propagation.
     */
    private fun enqueueWidgetUpdate(sketchId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf(WidgetUpdateWorker.KEY_SKETCH_ID to sketchId))
            .build()

        WorkManager.getInstance(applicationContext).enqueue(request)
        Log.d(TAG, "WidgetUpdateWorker enqueued for sketch $sketchId")
    }

    /**
     * Persists the refreshed FCM token to Firestore under `users/{uid}/fcmToken`
     * so the Cloud Function can send targeted push messages to this device.
     */
    private fun saveFcmToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save refreshed FCM token", e)
            }
    }

    companion object {
        private const val TAG = "SketchlyFCM"
        const val MSG_TYPE_NEW_SKETCH = "new_sketch"
        const val MSG_TYPE_REACTION = "reaction"
        const val CHANNEL_REACTIONS = "reactions"   // Must match onReactionCreate CF channelId
        const val CHANNEL_SKETCHES = "sketches"
    }
}
