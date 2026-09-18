package com.jrprofessor.sketchly.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jrprofessor.sketchly.MainActivity
import java.io.File

/**
 * Home Screen widget — primary passive touchpoint for Sketch (UI/UX §4.6).
 *
 * Two sizes supported (SizeMode.Responsive):
 *   - 2×2 (SMALL)  : sticky-note card, thumbnail + sender name
 *   - 4×2 (WIDE)   : same content, wider aspect, larger thumbnail
 *
 * Three states (via [SketchlyWidgetState]):
 *   - [SketchlyWidgetState.HasSketch] : shows bitmap + sender name + relative time
 *   - [SketchlyWidgetState.Empty]       : calm "all caught up" state — never blank
 *   - [SketchlyWidgetState.Loading]     : last-known bitmap (stale is fine, blank is not)
 *
 * Tap deep-links to MainActivity, which routes to ScribbleViewer for the given sketch ID.
 */
class SketchlyWidget : GlanceAppWidget() {

    companion object {
        // SharedPreferences keys written by WidgetUpdateWorker
        const val PREF_SKETCH_ID = "widget_sketch_id"
        const val PREF_SENDER_NAME = "widget_sender_name"
        const val PREF_RELATIVE_TIME = "widget_relative_time"
        const val PREF_BITMAP_PATH = "widget_bitmap_path"

        // Responsive size breakpoints
        private val SIZE_SMALL = DpSize(120.dp, 120.dp) // 2×2
        private val SIZE_WIDE = DpSize(240.dp, 120.dp)  // 4×2

        // Paper ivory background matching the app's canvas colour
        private val PaperIvory = Color(0xFFF4F1DE)
        private val InkDark = Color(0xFF2C2C2C)
        private val AccentOrange = Color(0xFFE07A5F)

        private const val TAG = "SketchlyWidget"
    }

    // Responsive mode — Glance picks the best size from the set
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_SMALL, SIZE_WIDE))

    // Use SharedPreferences as the state store (written by WidgetUpdateWorker)
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val sketchId = prefs[androidx.datastore.preferences.core.stringPreferencesKey(PREF_SKETCH_ID)]
        val senderName = prefs[androidx.datastore.preferences.core.stringPreferencesKey(PREF_SENDER_NAME)]
        val relativeTime = prefs[androidx.datastore.preferences.core.stringPreferencesKey(PREF_RELATIVE_TIME)]
        val bitmapPath = prefs[androidx.datastore.preferences.core.stringPreferencesKey(PREF_BITMAP_PATH)]

        val state: SketchlyWidgetState = when {
            sketchId != null && bitmapPath != null && File(bitmapPath).exists() ->
                SketchlyWidgetState.HasSketch(
                    sketchId = sketchId,
                    senderName = senderName ?: "Someone",
                    relativeTime = relativeTime ?: "",
                    bitmapFilePath = bitmapPath,
                )
            else -> SketchlyWidgetState.Empty
        }

        val size = LocalSize.current
        val isWide = size.width >= SIZE_WIDE.width

        when (state) {
            is SketchlyWidgetState.HasSketch -> HasSketchLayout(state, isWide)
            is SketchlyWidgetState.Empty -> EmptyLayout()
            is SketchlyWidgetState.Loading -> EmptyLayout() // Same visual as empty while loading
        }
    }

    @Composable
    private fun HasSketchLayout(state: SketchlyWidgetState.HasSketch, isWide: Boolean) {
        val bitmapFile = File(state.bitmapFilePath)
        val bitmap: Bitmap? = if (bitmapFile.exists()) {
            // Decode with a safety guard: if the file on disk is larger than expected
            // (e.g. left over from a previous install that used BITMAP_SIZE_PX=512),
            // sample down to inSampleSize=2 so the decoded bitmap stays below the
            // ~1 MB Android Binder IPC limit for setImageViewBitmap().
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(bitmapFile.absolutePath, opts)
            val rawPixels = opts.outWidth.toLong() * opts.outHeight
            val safeOpts = BitmapFactory.Options().apply {
                // Each pixel is 4 bytes (ARGB_8888).  Keep total <= 512 KB to
                // leave headroom for the rest of the RemoteViews transaction.
                inSampleSize = if (rawPixels * 4 > 524_288L) 2 else 1
            }
            BitmapFactory.decodeFile(bitmapFile.absolutePath, safeOpts).also { bmp ->
                if (bmp != null) {
                    Log.d(TAG, "[Widget] bitmap loaded ${bmp.width}×${bmp.height} "
                        + "rawBytes=${bmp.byteCount} inSampleSize=${safeOpts.inSampleSize}")
                }
            }
        } else null

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(PaperIvory))
                .clickable(
                    actionStartActivity<MainActivity>()
                ),
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                // Doodle thumbnail — fills available space above the sender name footer
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = "Sketch from ${state.senderName}",
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .padding(horizontal = 8.dp, vertical = if (isWide) 6.dp else 4.dp),
                    )
                } else {
                    // Bitmap missing (edge case) — show a placeholder
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                            .background(ColorProvider(PaperIvory)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✏️",
                            style = TextStyle(fontSize = 24.sp),
                        )
                    }
                }

                // Sender name + time footer
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(ColorProvider(PaperIvory))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Column {
                        Text(
                            text = state.senderName,
                            style = TextStyle(
                                color = ColorProvider(InkDark),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                        if (state.relativeTime.isNotBlank()) {
                            Text(
                                text = state.relativeTime,
                                style = TextStyle(
                                    color = ColorProvider(InkDark.copy(alpha = 0.6f)),
                                    fontSize = 9.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyLayout() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(PaperIvory)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = "✏️",
                    style = TextStyle(fontSize = 28.sp),
                )
                Text(
                    text = "All caught up",
                    style = TextStyle(
                        color = ColorProvider(InkDark.copy(alpha = 0.5f)),
                        fontSize = 10.sp,
                    ),
                )
            }
        }
    }
}
