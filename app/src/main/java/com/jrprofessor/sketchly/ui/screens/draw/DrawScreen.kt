package com.jrprofessor.sketchly.ui.screens.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.ui.components.PenToolbar
import com.jrprofessor.sketchly.ui.components.RecipientPickerSheet
import com.jrprofessor.sketchly.ui.theme.CanvasCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    onNavigateToCircle: () -> Unit = {},
    drawViewModel: DrawViewModel = hiltViewModel(),
) {
    val uiState by drawViewModel.uiState.collectAsStateWithLifecycle()
    val contacts by drawViewModel.contacts.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.sendSuccessMessage) {
        uiState.sendSuccessMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            // ── Canvas card (fills available space) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Surface(
                    shape = CanvasCardShape,
                    color = PaperIvory,
                    shadowElevation = 4.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ── Drawing canvas ──
                    DrawingCanvas(
                        strokes = uiState.strokes,
                        currentStroke = uiState.currentStroke,
                        onStrokeStart = { point -> drawViewModel.onStrokeStart(point) },
                        onStrokeMove = { point -> drawViewModel.onStrokeMove(point) },
                        onStrokeEnd = { drawViewModel.onStrokeEnd() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // ── Send button (top-right, overlays the canvas) ──
                if (uiState.canSend) {
                    Button(
                        onClick = { drawViewModel.openRecipientPicker() },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp,
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "Send",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send Sketch",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            // ── Pen toolbar (bottom, floating) ──
            PenToolbar(
                colors = PEN_COLORS,
                selectedColor = uiState.selectedColor,
                onColorSelected = { drawViewModel.selectColor(it) },
                thickness = uiState.selectedThickness,
                onThicknessChanged = { drawViewModel.selectThickness(it) },
                canUndo = uiState.strokes.isNotEmpty(),
                onUndo = { drawViewModel.undo() },
                onClear = { drawViewModel.clear() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Recipient Picker Bottom Sheet ──
        if (uiState.isRecipientPickerOpen) {
            RecipientPickerSheet(
                sheetState = sheetState,
                contacts = contacts,
                selectedContactIds = uiState.selectedContactIds,
                onContactToggle = { drawViewModel.toggleContactSelection(it) },
                onSendConfirmed = { drawViewModel.sendSketch() },
                onDismiss = { drawViewModel.closeRecipientPicker() },
                onAddNewContactClick = {
                    drawViewModel.closeRecipientPicker()
                    onNavigateToCircle()
                },
            )
        }

        // ── Snackbar Host ──
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }
}

/**
 * The actual Canvas composable that renders strokes and captures touch input.
 * Uses normalized coordinates (0f..1f) so drawings are resolution-independent.
 */
@Composable
private fun DrawingCanvas(
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    onStrokeStart: (DrawPoint) -> Unit,
    onStrokeMove: (DrawPoint) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val normalized = DrawPoint(
                            x = offset.x / size.width,
                            y = offset.y / size.height,
                        )
                        onStrokeStart(normalized)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val normalized = DrawPoint(
                            x = change.position.x / size.width,
                            y = change.position.y / size.height,
                        )
                        onStrokeMove(normalized)
                    },
                    onDragEnd = { onStrokeEnd() },
                    onDragCancel = { onStrokeEnd() },
                )
            },
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Draw all completed strokes
        strokes.forEach { stroke ->
            drawStrokePath(stroke, canvasWidth, canvasHeight, density.density)
        }

        // Draw the in-progress stroke
        currentStroke?.let { stroke ->
            drawStrokePath(stroke, canvasWidth, canvasHeight, density.density)
        }
    }
}

/**
 * Renders a single stroke as a smooth path on the Canvas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    stroke: Stroke,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
) {
    if (stroke.points.size < 2) return

    val path = Path()
    val firstPoint = stroke.points.first()
    path.moveTo(firstPoint.x * canvasWidth, firstPoint.y * canvasHeight)

    // Use quadratic bezier curves between midpoints for smooth rendering
    for (i in 1 until stroke.points.size) {
        val prev = stroke.points[i - 1]
        val current = stroke.points[i]
        val midX = ((prev.x + current.x) / 2f) * canvasWidth
        val midY = ((prev.y + current.y) / 2f) * canvasHeight
        path.quadraticTo(
            prev.x * canvasWidth,
            prev.y * canvasHeight,
            midX,
            midY,
        )
    }

    // Final segment to the last point
    val lastPoint = stroke.points.last()
    path.lineTo(lastPoint.x * canvasWidth, lastPoint.y * canvasHeight)

    val strokeColor = try {
        hexToColor(stroke.colorHex)
    } catch (_: Exception) {
        Color.Black
    }

    drawPath(
        path = path,
        color = strokeColor,
        style = DrawStroke(
            width = stroke.widthDp * density,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
