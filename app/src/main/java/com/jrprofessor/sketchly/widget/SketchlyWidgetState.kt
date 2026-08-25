package com.jrprofessor.sketchly.widget

/**
 * State for [SketchlyWidget].
 * The widget renderer switches on this sealed class to decide which layout to draw.
 */
sealed class SketchlyWidgetState {

    /**
     * A new/unread Sketch is available to display.
     * [bitmapFilePath] points to the locally-cached PNG rendered by [WidgetUpdateWorker].
     */
    data class HasSketch(
        val sketchId: String,
        val senderName: String,
        val relativeTime: String,
        val bitmapFilePath: String,
    ) : SketchlyWidgetState()

    /**
     * No new Scribbles — show a calm "all caught up" empty state.
     * Per UI/UX §4.6: never a blank/dead widget.
     */
    data object Empty : SketchlyWidgetState()

    /**
     * Widget is first loading or refreshing — show last-known content if available.
     */
    data object Loading : SketchlyWidgetState()
}
