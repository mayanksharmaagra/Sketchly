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
 */
@HiltWorker
class WidgetUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SketchlyRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val sketchId = inputData.getString(KEY_SKETCH_ID)
        Log.d(TAG, "doWork() start — sketchId=$sketchId attempt=$runAttemptCount")

        if (sketchId == null) {
            Log.e(TAG, "doWork() aborted — no sketchId in input data")
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Sketch (Room-first, Firestore fallback)
                val sketch = repository.getSketchById(sketchId)
                if (sketch == null) {
                    Log.e(TAG, "doWork() — sketch $sketchId not found in Room or Firestore, retrying")
                    return@withContext if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                Log.d(TAG, "doWork() — sketch fetched: sender=${sketch.senderId}, strokes=${sketch.strokes.size}")

                // 2. Render strokes to bitmap
                val bitmap = renderStrokesToBitmap(sketch.strokes, sketch.backgroundColor)
                Log.d(TAG, "doWork() — bitmap rendered ${bitmap.width}x${bitmap.height}")

                // 3. Write bitmap to local file
                val bitmapFile = File(context.filesDir, WIDGET_BITMAP_FILENAME)
                FileOutputStream(bitmapFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                Log.d(TAG, "doWork() — bitmap saved to ${bitmapFile.absolutePath}")

                // 4. Resolve sender display name — senderDisplayName is stored on the
                //    scribble doc at send time, so it is available here without an
                //    extra Firestore lookup.
                val senderName = sketch.senderDisplayName.ifBlank { "Someone" }

                // 5. Update Glance widget state via PreferencesGlanceStateDefinition
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(SketchlyWidget::class.java)
                Log.d(TAG, "doWork() — glanceIds count=${glanceIds.size}")

                if (glanceIds.isEmpty()) {
                    Log.w(TAG, "doWork() — no widget placed on home screen, nothing to update")
                    return@withContext Result.success() // not an error — widget simply not pinned
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
                    Log.d(TAG, "doWork() — widget updated glanceId=$glanceId sender=$senderName")
                }

                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "doWork() — unexpected error (attempt=$runAttemptCount)", e)
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
        private const val BITMAP_SIZE_PX = 512
    }
}
