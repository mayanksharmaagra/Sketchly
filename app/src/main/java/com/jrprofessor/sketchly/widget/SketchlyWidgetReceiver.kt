package com.jrprofessor.sketchly.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetProvider that wires [SketchlyWidget] to the Android App Widget system.
 * Registered in AndroidManifest.xml with the APPWIDGET_UPDATE intent-filter and
 * meta-data pointing to res/xml/sketchly_widget_info.xml.
 *
 * Diagnostic notes (for QA / widget debugging):
 *  - [onEnabled] fires when the FIRST instance of this widget is placed on the home screen.
 *    If you never see this log line, the widget was never pinned \u2014 which explains why
 *    GlanceAppWidgetManager.getGlanceIds() returns an empty list in WidgetUpdateWorker.
 *  - [onUpdate] fires for every periodic or system-requested refresh.
 *    Because updatePeriodMillis=0 in sketchly_widget_info.xml, this only fires on boot
 *    / provider reinstall, NOT on every FCM push (WorkManager handles those).
 *  - If the widget provider metadata (sketchly_widget_info.xml) changes while an old
 *    instance is still pinned, Android may deliver onUpdate with stale IDs before the
 *    new GlanceAppWidget class is fully resolved \u2014 the log below makes this visible.
 */
class SketchlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SketchlyWidget()

    /**
     * Called when the first widget instance is added to the home screen.
     * Useful to confirm during QA that the widget was actually pinned.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "[WidgetReceiver] onEnabled \u2014 first widget instance placed on home screen")
    }

    /**
     * Called by the system for periodic / boot-triggered updates.
     * (updatePeriodMillis=0 \u2192 this fires on boot/reinstall only, NOT on FCM pushes.)
     * Logs the widget IDs being updated so stale/duplicate instances are visible.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(
            TAG,
            "[WidgetReceiver] onUpdate \u2014 systemWidgetIds=${appWidgetIds.toList()} count=${appWidgetIds.size}",
        )
    }

    companion object {
        private const val TAG = "SketchlyWidgetReceiver"
    }
}
