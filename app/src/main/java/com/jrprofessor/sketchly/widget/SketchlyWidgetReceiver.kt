package com.jrprofessor.sketchly.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * AppWidgetProvider that wires [SketchlyWidget] to the Android App Widget system.
 * Registered in AndroidManifest.xml with the APPWIDGET_UPDATE intent-filter and
 * meta-data pointing to res/xml/sketchly_widget_info.xml.
 */
class SketchlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SketchlyWidget()
}
