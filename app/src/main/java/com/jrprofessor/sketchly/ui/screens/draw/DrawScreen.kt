package com.jrprofessor.sketchly.ui.screens.draw

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.DrawPoint
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.ui.components.RecipientPickerSheet
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted

// ── Eraser sentinel — stored as a special hex in the stroke ──────────────────
private val EraserColor = Color(0x00000000) // fully transparent sentinel
private const val ERASER_HEX = "ERASER"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCircle: () -> Unit = {},
    drawViewModel: DrawViewModel = hiltViewModel(),
) {
    val uiState by drawViewModel.uiState.collectAsStateWithLifecycle()
    val contacts by drawViewModel.contacts.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Eraser toggle local state
    var isEraserActive by remember { mutableStateOf(false) }
    var lastPenColor by remember { mutableStateOf(uiState.selectedColor) }

    LaunchedEffect(uiState.selectedColor) {
        if (uiState.selectedColor != EraserColor) {
            lastPenColor = uiState.selectedColor
            isEraserActive = false
        }
    }

    LaunchedEffect(uiState.sendSuccessMessage) {
        uiState.sendSuccessMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Top Bar ────────────────────────────────────────────────────
            DrawTopBar(onNavigateBack = onNavigateBack)

            // ── Canvas ─────────────────────────────────────────────────────
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
                    eraserThickness = uiState.selectedThickness * 3f,
                    onStrokeStart = { drawViewModel.onStrokeStart(it) },
                    onStrokeMove = { drawViewModel.onStrokeMove(it) },
                    onStrokeEnd = { drawViewModel.onStrokeEnd() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── Toolbar ────────────────────────────────────────────────────
            DrawToolbar(
                colors = PEN_COLORS,
                selectedColor = if (isEraserActive) EraserColor else uiState.selectedColor,
                onColorSelected = { color ->
                    isEraserActive = false
                    drawViewModel.selectColor(color)
                },
                selectedThickness = uiState.selectedThickness,
                onThicknessSelected = { drawViewModel.selectThickness(it) },
                isEraserActive = isEraserActive,
                onEraserToggle = {
                    isEraserActive = !isEraserActive
                    if (isEraserActive) {
                        drawViewModel.selectColor(lastPenColor) // keep for undo color memory
                    }
                },
                canUndo = uiState.strokes.isNotEmpty(),
                onUndo = { drawViewModel.undo() },
                onClear = { drawViewModel.clear() },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }

        // ── Floating Send FAB ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.canSend,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(tween(150)) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 140.dp),
        ) {
            FloatingActionButton(
                onClick = { drawViewModel.openRecipientPicker() },
                containerColor = ButtonGold,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 4.dp,
                ),
                modifier = Modifier.size(60.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send Sketch",
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // ── Recipient Picker ────────────────────────────────────────────────
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

        // ── Snackbar ────────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DrawTopBar(onNavigateBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = AppNameColor,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "New Scribble",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                fontSize = 20.sp,
            ),
            color = AppNameColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Paper Canvas — lined ruled paper look
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaperCanvas(
    strokes: List<Stroke>,
    currentStroke: Stroke?,
    isEraserActive: Boolean,
    eraserThickness: Float,
    onStrokeStart: (DrawPoint) -> Unit,
    onStrokeMove: (DrawPoint) -> Unit,
    onStrokeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = PaperIvory,
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(20.dp),
            ambientColor = AppNameColor.copy(alpha = 0.08f),
            spotColor = AppNameColor.copy(alpha = 0.12f),
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

            // ── Ruled lines ───────────────────────────────────────────────
            val lineSpacingPx = with(density) { 28.dp.toPx() }
            val lineColor = TextEditorBorderColor.copy(alpha = 0.45f)
            val marginColor = Color(0xFFF4C0A0).copy(alpha = 0.35f)
            val marginX = with(density) { 48.dp.toPx() }

            // Horizontal ruled lines
            var y = lineSpacingPx * 2
            while (y < h) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = with(density) { 0.7.dp.toPx() },
                )
                y += lineSpacingPx
            }
            // Left margin line
            drawLine(
                color = marginColor,
                start = Offset(marginX, 0f),
                end = Offset(marginX, h),
                strokeWidth = with(density) { 1.2.dp.toPx() },
            )

            // ── Strokes ───────────────────────────────────────────────────
            strokes.forEach { stroke ->
                drawStrokePath(stroke, w, h, density.density)
            }
            currentStroke?.let { stroke ->
                drawStrokePath(stroke, w, h, density.density)
            }
        }
    }
}

/**
 * Renders a single stroke as a smooth quadratic bezier path.
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

// ─────────────────────────────────────────────────────────────────────────────
// Draw Toolbar — colors + pen sizes + eraser + undo/clear
// ─────────────────────────────────────────────────────────────────────────────

private val THICKNESS_OPTIONS = listOf(2f, 4f, 8f, 14f, 22f)

@Composable
private fun DrawToolbar(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    selectedThickness: Float,
    onThicknessSelected: (Float) -> Unit,
    isEraserActive: Boolean,
    onEraserToggle: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = TextEditorBgColor,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 12.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── Row 1: Color swatches ─────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    colors.forEach { color ->
                        ColorDot(
                            color = color,
                            isSelected = !isEraserActive && color == selectedColor,
                            onClick = { onColorSelected(color) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Divider ───────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(TextEditorBorderColor.copy(alpha = 0.5f)),
                )

                // ── Row 2: Pen sizes + eraser + undo + clear ──────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Pen size dots
                    THICKNESS_OPTIONS.forEach { size ->
                        PenSizeDot(
                            size = size,
                            isSelected = !isEraserActive && size == selectedThickness,
                            color = if (isEraserActive) TextMuted else selectedColor,
                            onClick = { onThicknessSelected(size) },
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(TextEditorBorderColor.copy(alpha = 0.6f)),
                    )

                    // Eraser toggle
                    EraserButton(
                        isActive = isEraserActive,
                        onClick = onEraserToggle,
                    )

                    // Undo
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) AppNameColor else TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // Clear
                    IconButton(
                        onClick = onClear,
                        enabled = canUndo,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Clear canvas",
                            tint = if (canUndo) Color(0xFFBA1A1A) else TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
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
        targetValue = if (isSelected) 30.dp else 26.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "colorDotSize",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
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
                            color = AppNameColor.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                    } else Modifier,
                ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pen Size Dot — visual dots of different sizes
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PenSizeDot(
    size: Float,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    // Map thickness float to visual dot dp: 2f→6dp, 4f→9dp, 8f→13dp, 14f→18dp, 22f→24dp
    val visualDp = (size * 0.95f + 4f).coerceIn(6f, 25f)
    val animScale by animateFloatAsState(
        targetValue = if (isSelected) 1.25f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "penSizeDotScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(visualDp.dp)
                .scale(animScale)
                .clip(CircleShape)
                .background(
                    if (isSelected) color else TextMuted.copy(alpha = 0.45f),
                    CircleShape,
                ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Eraser Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EraserButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) AppNameColor.copy(alpha = 0.12f) else Color.Transparent,
            )
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) AppNameColor.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Text(
            text = "✦",
            fontSize = if (isActive) 18.sp else 16.sp,
            color = if (isActive) AppNameColor else TextMuted.copy(alpha = 0.6f),
        )
    }
}
