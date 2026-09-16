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
import com.jrprofessor.sketchly.R
import com.jrprofessor.sketchly.data.worker.WidgetUpdateWorker
import com.jrprofessor.sketchly.utils.FeatureFlags
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
            MSG_TYPE_NEW_SCRIBBLE -> {
                // Always active — standard scribble notification (V1 auto-connect path)
                val scribbleId = data["scribbleId"] ?: return
                val senderId = data["senderId"] ?: "Someone"
                enqueueWidgetUpdate(scribbleId)
                postNewScribbleNotification(scribbleId, senderId)
            }

            // M4: Reaction notification from `onReactionCreate` Cloud Function (SRS FR-7.3)
            MSG_TYPE_REACTION -> {
                val sketchId = data["sketchId"] ?: return
                val emoji = data["emoji"] ?: "❤️"
                val reactorId = data["reactorId"] ?: "Someone"
                postReactionNotification(sketchId, emoji, reactorId)
            }

            // ── [FEATURE FLAGGED — V2] Connection-request notifications ───────────────
            // The Cloud Function (Phase 2) already won't send these types while
            // ENABLE_CONNECTION_REQUESTS = false, but we guard here too so the client
            // stays consistent if a stale FCM message ever arrives.
            //
            // postConnectionRequestNotification() and postConnectionAcceptedNotification()
            // are fully preserved below — DO NOT delete them.
            // Re-enable by setting FeatureFlags.ENABLE_CONNECTION_REQUESTS = true.
            // ─────────────────────────────────────────────────────────────────────────

            // Connection request from onConnectionRequestCreate Cloud Function
            MSG_TYPE_CONNECTION_REQUEST -> {
                if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
                    val fromUserId = data["fromUserId"] ?: return
                    val requestId = data["requestId"] ?: ""
                    val scribbleId = data["scribbleId"] ?: ""
                    postConnectionRequestNotification(fromUserId, requestId, scribbleId)
                } else {
                    Log.d(TAG, "MSG_TYPE_CONNECTION_REQUEST received but ENABLE_CONNECTION_REQUESTS=false — ignoring.")
                }
            }

            // Connection accepted from onConnectionRequestAccept Cloud Function
            MSG_TYPE_CONNECTION_ACCEPTED -> {
                if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
                    // Navigate user to Friends/Connected tab — for now just post a notification
                    val toUserId = data["toUserId"] ?: return
                    postConnectionAcceptedNotification(toUserId)
                } else {
                    Log.d(TAG, "MSG_TYPE_CONNECTION_ACCEPTED received but ENABLE_CONNECTION_REQUESTS=false — ignoring.")
                }
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
     *  - [CHANNEL_SOCIAL]   HIGH importance — connection-request / accepted notifications
     *    (required by `onConnectionRequestCreate` and `onConnectionRequestAccept` CFs)
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

        // Required by onConnectionRequestCreate and onConnectionRequestAccept Cloud Functions
        val socialChannel = NotificationChannel(
            CHANNEL_SOCIAL,
            "Social",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for connection requests and new connections"
        }

        notificationManager.createNotificationChannel(reactionsChannel)
        notificationManager.createNotificationChannel(sketchesChannel)
        notificationManager.createNotificationChannel(socialChannel)
    }

    /**
     * Posts a visible system-tray notification for a new incoming scribble.
     * Tapping the notification opens the app and can be routed to the viewer.
     */
    private fun postNewScribbleNotification(scribbleId: String, senderId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to_sketch", scribbleId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            scribbleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SKETCHES)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle("New Scribble! ✏️")
            .setContentText("You received a new scribble")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(scribbleId.hashCode(), notification)
        Log.d(TAG, "New scribble notification posted for scribble $scribbleId from $senderId")
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
     * Posts a visible notification for an incoming connection request.
     * Tapping opens the app's Friends screen (Requests tab).
     */
    private fun postConnectionRequestNotification(fromUserId: String, requestId: String, scribbleId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "friends_requests")
            putExtra("from_user_id", fromUserId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New connection request")
            .setContentText("Someone wants to connect with you on Sketchly")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestId.hashCode(), notification)
        Log.d(TAG, "Connection request notification posted from $fromUserId")
    }

    /**
     * Posts a visible notification when a connection request is accepted.
     */
    private fun postConnectionAcceptedNotification(toUserId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "friends_connected")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            toUserId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_SOCIAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Connection accepted! 🎉")
            .setContentText("You're now connected on Sketchly")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(toUserId.hashCode(), notification)
        Log.d(TAG, "Connection accepted notification posted")
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
        /** Matches the `type` field sent by the `onScribbleCreate` Cloud Function */
        const val MSG_TYPE_NEW_SCRIBBLE = "new_scribble"
        const val MSG_TYPE_REACTION = "reaction"
        const val MSG_TYPE_CONNECTION_REQUEST = "connection_request"  // from onConnectionRequestCreate CF
        const val MSG_TYPE_CONNECTION_ACCEPTED = "connection_accepted" // from onConnectionRequestAccept CF
        const val CHANNEL_REACTIONS = "reactions"   // Must match onReactionCreate CF channelId
        const val CHANNEL_SKETCHES = "sketches"
        const val CHANNEL_SOCIAL = "social"         // Must match onConnectionRequestCreate/Accept CF channelId
    }
}
