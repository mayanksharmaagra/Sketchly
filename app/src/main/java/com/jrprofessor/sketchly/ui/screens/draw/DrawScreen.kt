package com.jrprofessor.sketchly.ui.screens.draw

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted

// ── Eraser sentinel ──────────────────────────────────────────────────────────
private val EraserColor = Color(0x00000000)
private const val ERASER_HEX = "ERASER"

@Composable
fun DrawScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToSendTo: () -> Unit = {},
    drawViewModel: DrawViewModel = hiltViewModel(),
) {
    val uiState by drawViewModel.uiState.collectAsStateWithLifecycle()
    val hasDrawnBefore by drawViewModel.hasDrawnBefore.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var isEraserActive by remember { mutableStateOf(false) }
    var lastPenColor by remember { mutableStateOf(uiState.selectedColor) }

    LaunchedEffect(uiState.selectedColor) {
        if (uiState.selectedColor != EraserColor) {
            lastPenColor = uiState.selectedColor
            isEraserActive = false
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    DrawScreenContent(
        uiState = uiState,
        hasDrawnBefore = hasDrawnBefore,
        isEraserActive = isEraserActive,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToSendTo = onNavigateToSendTo,
        onEraserToggle = { isEraserActive = it },
        onColorSelected = { color ->
            isEraserActive = false
            drawViewModel.selectColor(color)
        },
        onStrokeStart = drawViewModel::onStrokeStart,
        onStrokeMove = drawViewModel::onStrokeMove,
        onStrokeEnd = drawViewModel::onStrokeEnd,
        onUndo = drawViewModel::undo,
    )
}

@Composable
fun DrawScreenContent(
    uiState: DrawUiState,
    hasDrawnBefore: Boolean = false,
    isEraserActive: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToSendTo: () -> Unit,
    onEraserToggle: (Boolean) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeStart: (DrawPoint) -> Unit,
    onStrokeMove: (DrawPoint) -> Unit,
    onStrokeEnd: () -> Unit,
    onUndo: () -> Unit,
) {
    val topBarTitle = if (hasDrawnBefore) "New Scribble" else "Draw your first Scribble"
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            SketchlyTopBar(
                title  = topBarTitle,
                onBack = onNavigateBack,
            )

            // ── Paper canvas area ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                PaperCanvas(
                    strokes = uiState.strokes,
                    currentStroke = uiState.currentStroke,
                    isEraserActive = isEraserActive,
                    onStrokeStart = onStrokeStart,
                    onStrokeMove = onStrokeMove,
                    onStrokeEnd = onStrokeEnd,
                    modifier = Modifier.fillMaxSize(),
                )

                // Hint chip — visible only when canvas is blank
                if (uiState.strokes.isEmpty() && uiState.currentStroke == null) {
                    HintChip(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                    )
                }
            }

            // ── Bottom toolbar + Next ─────────────────────────────────────────
            DrawBottomSection(
                colors = PEN_COLORS,
                selectedColor = if (isEraserActive) EraserColor else uiState.selectedColor,
                onColorSelected = onColorSelected,
                isEraserActive = isEraserActive,
                canUndo = uiState.strokes.isNotEmpty(),
                onUndo = onUndo,
                canSend = uiState.canSend,
                onNext = onNavigateToSendTo,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }

        // ── Snackbar (errors only) ────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
        )
    }
}

// ── Previews ──

@Preview(name = "Draw — Empty Canvas", showBackground = true)
@Composable
private fun DrawScreenEmptyPreview() {
    SketchlyTheme {
        DrawScreenContent(
            uiState = DrawUiState(),
            isEraserActive = false,
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNavigateToSendTo = {},
            onEraserToggle = {},
            onColorSelected = {},
            onStrokeStart = {},
            onStrokeMove = {},
            onStrokeEnd = {},
            onUndo = {},
        )
    }
}

@Preview(name = "Draw — Can Send", showBackground = true)
@Composable
private fun DrawScreenCanSendPreview() {
    SketchlyTheme {
        DrawScreenContent(
            uiState = DrawUiState(canSend = true),
            isEraserActive = false,
            snackbarHostState = SnackbarHostState(),
            onNavigateBack = {},
            onNavigateToSendTo = {},
            onEraserToggle = {},
            onColorSelected = {},
            onStrokeStart = {},
            onStrokeMove = {},
            onStrokeEnd = {},
            onUndo = {},
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawTopBar is replaced by the common SketchlyTopBar. Kept as a no-op to
// avoid unused-import warnings during the transition. Delete once all
// references are confirmed removed.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Hint Chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HintChip(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = PaperIvory,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                text = "♀",
                fontSize = 13.sp,
                color = AppNameColor.copy(alpha = 0.65f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Try drawing something small",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = AppNameColor.copy(alpha = 0.72f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paper Canvas — lined ruled paper + stroke rendering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaperCanvas(
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    isEraserActive: Boolean,
    onStrokeStart: (DrawPoint) -> Unit,
    onStrokeMove: (DrawPoint) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperIvory,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        modifier = modifier.shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(20.dp),
            ambientColor = AppNameColor.copy(alpha = 0.08f),
            spotColor = AppNameColor.copy(alpha = 0.10f),
        ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isEraserActive) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onStrokeStart(
                                DrawPoint(
                                    x = offset.x / size.width,
                                    y = offset.y / size.height,
                                )
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onStrokeMove(
                                DrawPoint(
                                    x = change.position.x / size.width,
                                    y = change.position.y / size.height,
                                )
                            )
                        },
                        onDragEnd = { onStrokeEnd() },
                        onDragCancel = { onStrokeEnd() },
                    )
                },
        ) {
            val w = size.width
            val h = size.height

            // Ruled horizontal lines
            val lineSpacingPx = with(density) { 28.dp.toPx() }
            val lineColor = TextEditorBorderColor.copy(alpha = 0.40f)
            var lineY = lineSpacingPx * 2f
            while (lineY < h) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, lineY),
                    end = Offset(w, lineY),
                    strokeWidth = with(density) { 0.7.dp.toPx() },
                )
                lineY += lineSpacingPx
            }

            // Render committed strokes
            strokes.forEach { stroke -> drawStrokePath(stroke, w, h, density.density) }
            // Render in-progress stroke
            currentStroke?.let { drawStrokePath(it, w, h, density.density) }
        }
    }
}

/**
 * Draws a single [Stroke] as a smooth quadratic-bezier path.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    stroke: Stroke,
    canvasWidth: Float,
    canvasHeight: Float,
    density: Float,
) {
    if (stroke.points.size < 2) return

    val path = Path()
    val first = stroke.points.first()
    path.moveTo(first.x * canvasWidth, first.y * canvasHeight)

    for (i in 1 until stroke.points.size) {
        val prev = stroke.points[i - 1]
        val curr = stroke.points[i]
        val midX = ((prev.x + curr.x) / 2f) * canvasWidth
        val midY = ((prev.y + curr.y) / 2f) * canvasHeight
        path.quadraticTo(
            prev.x * canvasWidth, prev.y * canvasHeight,
            midX, midY,
        )
    }
    val last = stroke.points.last()
    path.lineTo(last.x * canvasWidth, last.y * canvasHeight)

    val strokeColor = runCatching { hexToColor(stroke.colorHex) }.getOrDefault(Color.Black)

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

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Section — pill toolbar + Next button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawBottomSection(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    isEraserActive: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canSend: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Color + undo pill
        Surface(
            shape = RoundedCornerShape(50),
            color = TextEditorBgColor,
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                // 4 color swatches
                colors.take(4).forEachIndexed { index, color ->
                    ColorDot(
                        color = color,
                        isSelected = !isEraserActive && color == selectedColor,
                        onClick = { onColorSelected(color) },
                    )
                    if (index < 3) Spacer(modifier = Modifier.width(2.dp))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(TextEditorBorderColor.copy(alpha = 0.65f)),
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Undo icon button
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) AppNameColor else TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // Next → button
        Surface(
            shape = RoundedCornerShape(50),
            color = if (canSend) ButtonGold else ButtonGold.copy(alpha = 0.4f),
            shadowElevation = if (canSend) 6.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable(
                    enabled = canSend,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onNext() },
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp),
            ) {
                Text(
                    text = "Next  →",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Color Dot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColorDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.25f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "colorDotScale",
    )
    val dotSize by animateDpAsState(
        targetValue = if (isSelected) 34.dp else 28.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "colorDotSize",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .scale(scale)
                .clip(CircleShape)
                .background(color, CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.5.dp,
                            color = AppNameColor.copy(alpha = 0.45f),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    }
                ),
        )
    }
}
