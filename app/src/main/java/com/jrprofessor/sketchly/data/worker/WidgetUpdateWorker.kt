package com.jrprofessor.sketchly.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
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
        val sketchId = inputData.getString(KEY_SKETCH_ID) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch Sketch (Room-first, Firestore fallback)
                val sketch = repository.getSketchById(sketchId) ?: return@withContext Result.failure()

                // 2. Render strokes to bitmap
                val bitmap = renderStrokesToBitmap(sketch.strokes, sketch.backgroundColor)

                // 3. Write bitmap to local file
                val bitmapFile = File(context.filesDir, WIDGET_BITMAP_FILENAME)
                FileOutputStream(bitmapFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }

                // 4. Resolve sender display name (best-effort — falls back to uid)
                val senderName = resolveSenderName(sketch.senderId)

                // 5. Update Glance widget state via PreferencesGlanceStateDefinition
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(SketchlyWidget::class.java)

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
                }

                Result.success()
            } catch (e: Exception) {
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

    private fun resolveSenderName(senderId: String): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return if (currentUser?.uid == senderId) {
            currentUser.displayName ?: "You"
        } else {
            "Someone"
        }
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
        const val KEY_SKETCH_ID = "key_sketch_id"
        const val WIDGET_BITMAP_FILENAME = "widget_preview.png"
        private const val BITMAP_SIZE_PX = 512
    }
}
