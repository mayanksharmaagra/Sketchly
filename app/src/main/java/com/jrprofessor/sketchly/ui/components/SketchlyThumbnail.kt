package com.jrprofessor.sketchly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.ui.theme.NoteCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory

/**
 * Renders a compact thumbnail preview of a Sketch's strokes.
 */
@Composable
fun SketchlyThumbnail(
    strokes: List<Stroke>,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    backgroundColor: Color = PaperIvory,
) {
    val density = LocalDensity.current

    val finalModifier = if (size != Dp.Unspecified) modifier.size(size) else modifier

    Box(
        modifier = finalModifier
            .clip(NoteCardShape)
            .background(backgroundColor),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height

            strokes.forEach { stroke ->
                if (stroke.points.size >= 2) {
                    val path = Path()
                    val first = stroke.points.first()
                    path.moveTo(first.x * canvasWidth, first.y * canvasHeight)

                    for (i in 1 until stroke.points.size) {
                        val prev = stroke.points[i - 1]
                        val curr = stroke.points[i]
                        val midX = ((prev.x + curr.x) / 2f) * canvasWidth
                        val midY = ((prev.y + curr.y) / 2f) * canvasHeight
                        path.quadraticTo(
                            prev.x * canvasWidth,
                            prev.y * canvasHeight,
                            midX,
                            midY,
                        )
                    }
                    val last = stroke.points.last()
                    path.lineTo(last.x * canvasWidth, last.y * canvasHeight)

                    val strokeColor = try {
                        hexToColor(stroke.colorHex)
                    } catch (_: Exception) {
                        Color.Black
                    }

                    // Scale width proportionally for thumbnail
                    val scaledWidth = (stroke.widthDp * (canvasWidth / 300f)).coerceAtLeast(1.5f) * density.density

                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = DrawStroke(
                            width = scaledWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}
