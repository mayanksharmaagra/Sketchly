package com.jrprofessor.sketchly.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.widget.SketchlyWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker responsible for the widget refresh pipeline (Architecture §4, step 5).
 * Uses @HiltWorker so Hilt can inject its dependencies via HiltWorkerFactory.
 *
 * ─── MANUAL QA CHECKLIST ─────────────────────────────────────────────────────
 * Use this checklist every time the widget update pipeline is being verified:
 *
 * 1. CONFIRM widget is pinned on the TEST device (User B's device) BEFORE
 *    sending a Scribble.  The Settings screen shows a debug row:
 *    "Widget status: Pinned ✅ / Not added ❌" (BuildConfig.DEBUG only).
 *
 * 2. From a second account / device (User A), send a Scribble to User B.
 *
 * 3. Filter logcat on User B's device:
 *       adb logcat | grep WidgetUpdate
 *
 * 4. Confirm the full step sequence appears in order:
 *       [WidgetUpdate] step=start
 *       [WidgetUpdate] step=sketch_fetched
 *       [WidgetUpdate] step=bitmap_rendered
 *       [WidgetUpdate] step=bitmap_saved
 *       [WidgetUpdate] step=glance_ids_resolved   count=N (N ≥ 1 if widget is pinned)
 *       [WidgetUpdate] step=widget_updated        (once per glanceId)
 *
 * 5. If step=no_widget_pinned appears instead of step=widget_updated, the
 *    FCM → Worker pipeline is functioning correctly.  The widget simply was
 *    not added to the home screen — this is NOT a code bug.
 *
 * 6. If step=sketch_not_found appears on attempt=0, check logcat for
 *    SketchlyRepository — a "Room miss, fetching Firestore" immediately
 *    followed by "Firestore doc does not exist" on the first attempt means
 *    the 1-second initial delay is too short.  Increase to 2 seconds in
 *    enqueueWidgetUpdate() only if this is reproducible.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SketchlyRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sketchId = inputData.getString(KEY_SKETCH_ID)
        Log.d(TAG, "[WidgetUpdate] step=start sketchId=$sketchId attempt=$runAttemptCount")

        if (sketchId == null) {
            Log.e(TAG, "[WidgetUpdate] step=aborted reason=missing_sketch_id")
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Sketch (Room-first, Firestore fallback)
                val sketch = repository.getSketchById(sketchId)
                if (sketch == null) {
                    Log.e(
                        TAG,
                        "[WidgetUpdate] step=sketch_not_found sketchId=$sketchId attempt=$runAttemptCount — scheduling retry",
                    )
                    return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                Log.d(
                    TAG,
                    "[WidgetUpdate] step=sketch_fetched sender=${sketch.senderId} strokes=${sketch.strokes.size}",
                )

                // 2. Render strokes to bitmap
                val bitmap = renderStrokesToBitmap(sketch.strokes, sketch.backgroundColor)
                Log.d(TAG, "[WidgetUpdate] step=bitmap_rendered size=${bitmap.width}x${bitmap.height}")

                // 3. Write bitmap to local file
                val bitmapFile = File(context.filesDir, WIDGET_BITMAP_FILENAME)
                FileOutputStream(bitmapFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                Log.d(TAG, "[WidgetUpdate] step=bitmap_saved path=${bitmapFile.absolutePath}")

                // 4. Resolve sender display name — senderDisplayName is stored on the
                //    scribble doc at send time, so it is available here without an
                //    extra Firestore lookup.
                val senderName = sketch.senderDisplayName.ifBlank { "Someone" }

                // 5. Resolve Glance widget instances pinned on the home screen.
                //    GlanceAppWidgetManager.getGlanceIds() returns an empty list when
                //    the widget has never been pinned — this is NOT an error.
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(SketchlyWidget::class.java)
                Log.d(TAG, "[WidgetUpdate] step=glance_ids_resolved count=${glanceIds.size}")

                if (glanceIds.isEmpty()) {
                    // Widget not pinned — pipeline is healthy, nothing to render.
                    // See QA checklist at top of file.
                    Log.w(TAG, "[WidgetUpdate] step=no_widget_pinned — aborting, this is not an error")
                    return@withContext Result.success()
                }

                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            set(stringPreferencesKey(SketchlyWidget.PREF_SKETCH_ID), sketch.id)
                            set(stringPreferencesKey(SketchlyWidget.PREF_SENDER_NAME), senderName)
                            set(stringPreferencesKey(SketchlyWidget.PREF_RELATIVE_TIME), formatRelativeTime(sketch.createdAt))
                            set(stringPreferencesKey(SketchlyWidget.PREF_BITMAP_PATH), bitmapFile.absolutePath)
                        }
                    }
                    SketchlyWidget().update(context, glanceId)
                    Log.d(TAG, "[WidgetUpdate] step=widget_updated glanceId=$glanceId sender=$senderName")
                }

                Log.d(TAG, "[WidgetUpdate] step=complete sketchId=$sketchId updatedWidgets=${glanceIds.size}")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "[WidgetUpdate] step=error attempt=$runAttemptCount", e)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    /**
     * Renders a list of [Stroke]s to an off-screen [Bitmap].
     * Coordinates are normalized (0..1) and scaled to [BITMAP_SIZE_PX].
     */
    private fun renderStrokesToBitmap(strokes: List<Stroke>, backgroundColorHex: String): Bitmap {
        val size = BITMAP_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill background
        val bgColor = try {
            val composeColor = hexToColor(backgroundColorHex)
            android.graphics.Color.argb(
                (composeColor.alpha * 255).toInt(),
                (composeColor.red * 255).toInt(),
                (composeColor.green * 255).toInt(),
                (composeColor.blue * 255).toInt(),
            )
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#F4F1DE")
        }
        canvas.drawColor(bgColor)

        if (strokes.isEmpty()) return bitmap

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            val strokeComposeColor = hexToColor(stroke.colorHex)
            paint.color = android.graphics.Color.argb(
                (strokeComposeColor.alpha * 255).toInt(),
                (strokeComposeColor.red * 255).toInt(),
                (strokeComposeColor.green * 255).toInt(),
                (strokeComposeColor.blue * 255).toInt(),
            )
            paint.strokeWidth = (stroke.widthDp / 360f) * size * 1.5f

            val path = Path()
            val first: DrawPoint = stroke.points.first()
            path.moveTo(first.x * size, first.y * size)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x * size, p.y * size)
            }
            canvas.drawPath(path, paint)
        }

        return bitmap
    }


    private fun formatRelativeTime(createdAtMs: Long): String {
        val diffMs = System.currentTimeMillis() - createdAtMs
        return when {
            diffMs < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diffMs < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diffMs)}m ago"
            diffMs < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diffMs)}h ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(createdAtMs))
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateWorker"
        const val KEY_SKETCH_ID = "key_sketch_id"
        const val WIDGET_BITMAP_FILENAME = "widget_preview.png"
        //
        // BITMAP_SIZE_PX = 240  (was 512).
        // A 512×512 ARGB_8888 bitmap is 1,048,576 bytes — Android’s Binder IPC
        // limit is also ~1 MB.  With RemoteViews frame overhead on top, the call
        // to setImageViewBitmap() silently fails and the widget stays blank even
        // though WorkManager reports SUCCESS.
        // At 240×240: 240*240*4 = 230,400 bytes (~225 KB) — well within limits.
        // Do NOT increase above 350 without validating on low-memory devices.
        //
        private const val BITMAP_SIZE_PX = 240
    }
}
