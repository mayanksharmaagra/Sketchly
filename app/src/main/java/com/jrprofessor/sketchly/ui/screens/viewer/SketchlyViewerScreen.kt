package com.jrprofessor.sketchly.ui.screens.viewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jrprofessor.sketchly.data.model.Reaction
import com.jrprofessor.sketchly.data.model.Sketch
import com.jrprofessor.sketchly.data.model.Stroke
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.ui.components.ViewerSkeleton
import com.jrprofessor.sketchly.ui.theme.CanvasCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

val EMOJI_REACTIONS = listOf("❤️", "😂", "👏", "🔥", "😮", "✨")

data class ViewerUiState(
    val sketch: Sketch? = null,
    val isLoading: Boolean = true,
    val selectedEmoji: String? = null,
    val reactionSentConfirmation: Boolean = false,
    /** Live reactions from Firestore listener (SRS FR-7.3) */
    val reactions: List<Reaction> = emptyList(),
    /** Whether the stroke-by-stroke replay animation has started */
    val replayStarted: Boolean = false,
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sketchRepository: SketchlyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    val currentUserId: String? get() = authRepository.currentUserId

    fun loadSketch(sketchId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sketch = sketchRepository.getSketchById(sketchId)
            if (sketch != null) {
                sketchRepository.markAsRead(sketchId)
            }
            _uiState.update {
                it.copy(
                    sketch = sketch,
                    isLoading = false,
                    replayStarted = true,
                )
            }
            // Start live Firestore reaction listener (SRS FR-7.3)
            sketchRepository.listenToReactions(
                sketchId = sketchId,
                scope = viewModelScope,
                onUpdate = { reactions ->
                    _uiState.update { it.copy(reactions = reactions) }
                },
            )
        }
    }

    fun sendReaction(emoji: String) {
        val sketchId = _uiState.value.sketch?.id ?: return
        val currentUserId = authRepository.currentUserId ?: return
        val currentUserName = authRepository.currentUser?.displayName ?: ""

        _uiState.update {
            it.copy(
                selectedEmoji = emoji,
                reactionSentConfirmation = true,
            )
        }

        viewModelScope.launch {
            sketchRepository.sendReaction(sketchId, currentUserId, currentUserName, emoji)
            // Reset confirmation flash after a short delay
            delay(1200)
            _uiState.update { it.copy(reactionSentConfirmation = false) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sketchRepository.stopListeningToReactions()
    }
}

@Composable
fun SketchlyViewerScreen(
    sketchId: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    LaunchedEffect(sketchId) {
        viewModel.loadSketch(sketchId)
    }

    ViewerScreenContent(
        uiState = uiState,
        isSender = uiState.sketch?.senderId == viewModel.currentUserId,
        onBack = onBack,
        onReact = viewModel::sendReaction,
    )
}

@Composable
fun ViewerScreenContent(
    uiState: ViewerUiState,
    isSender: Boolean,
    onBack: () -> Unit,
    onReact: (String) -> Unit,
) {
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back button with TalkBack label (UIUX §9)
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Back" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Column(modifier = Modifier.padding(start = 8.dp)) {
                val senderName = uiState.sketch?.senderDisplayName?.takeIf { it.isNotBlank() }
                Text(
                    text = if (senderName != null) "from $senderName" else "Sketch Note",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                val createdAt = uiState.sketch?.createdAt ?: System.currentTimeMillis()
                val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(createdAt))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Full Canvas Card ──
        Surface(
            shape = CanvasCardShape,
            color = PaperIvory,
            shadowElevation = 6.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (uiState.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Paper-texture skeleton (UIUX §7) replaces generic spinner
                    ViewerSkeleton()
                }
            } else {
                val allStrokes = uiState.sketch?.strokes ?: emptyList()
                StrokeReplayCanvas(
                    allStrokes = allStrokes,
                    replayStarted = uiState.replayStarted,
                    density = density.density,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Reactions received summary (shown to sender only, SRS FR-7.3) ──
        val reactions = uiState.reactions
        if (isSender && reactions.isNotEmpty()) {
            val grouped = reactions.groupBy { it.emoji }
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Reactions: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    grouped.forEach { (emoji, list) ->
                        Text(
                            text = "$emoji ${list.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        // ── Emoji Reaction Bar (SRS FR-7) ──
        Surface(
            shape = PillShape,
            color = PaperIvory,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EMOJI_REACTIONS.forEach { emoji ->
                    val isSelected = uiState.selectedEmoji == emoji
                    // Bounce scale animation on selection (UIUX §12)
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.35f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "emoji_scale_$emoji",
                    )
                    // 48dp touch target (UIUX §9 — minimum interactive component size)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .clickable(onClickLabel = "React with $emoji") { onReact(emoji) }
                            .semantics { contentDescription = "React with $emoji" },
                        contentAlignment = Alignment.Center,
                    ) {
                        // AnimatedContent for the emoji to pop in when selected (UIUX §12)
                        AnimatedContent(
                            targetState = isSelected,
                            transitionSpec = {
                                (fadeIn(tween(150)) + scaleIn(
                                    spring(Spring.DampingRatioMediumBouncy),
                                    initialScale = 0.7f,
                                )) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100)))
                            },
                            label = "emoji_anim_$emoji",
                        ) { selected ->
                            Text(
                                text = emoji,
                                fontSize = if (selected) 26.sp else 22.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Previews ──

@Preview(name = "Viewer — Loading", showBackground = true)
@Composable
private fun ViewerScreenLoadingPreview() {
    SketchlyTheme {
        ViewerScreenContent(
            uiState = ViewerUiState(isLoading = true),
            isSender = false,
            onBack = {},
            onReact = {},
        )
    }
}

@Preview(name = "Viewer — Loaded", showBackground = true)
@Composable
private fun ViewerScreenLoadedPreview() {
    val fakeSketch = Sketch(
        id = "preview",
        senderId = "uid_me",
        senderDisplayName = "Alice",
        recipientIds = emptyList(),
        strokes = emptyList(),
        createdAt = System.currentTimeMillis(),
        isRead = true,
    )
    SketchlyTheme {
        ViewerScreenContent(
            uiState = ViewerUiState(sketch = fakeSketch, isLoading = false, replayStarted = true),
            isSender = false,
            onBack = {},
            onReact = {},
        )
    }
}

// ── Stroke-by-stroke Replay Canvas (UIUX §12, capped at 1.5s, skippable) ──

@Composable
private fun StrokeReplayCanvas(
    allStrokes: List<Stroke>,
    replayStarted: Boolean,
    density: Float,
) {
    // Number of strokes currently visible during replay
    var visibleCount by remember(allStrokes) { mutableIntStateOf(0) }
    var replayComplete by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }

    // Drive replay: distribute all strokes evenly over 1.5s (UIUX §12)
    LaunchedEffect(replayStarted, allStrokes) {
        if (!replayStarted || allStrokes.isEmpty()) {
            visibleCount = allStrokes.size
            replayComplete = true
            return@LaunchedEffect
        }
        if (skipped) return@LaunchedEffect

        val totalMs = 1_500L
        val delayPerStroke = if (allStrokes.size > 1) totalMs / allStrokes.size else 0L
        visibleCount = 0
        for (i in allStrokes.indices) {
            if (skipped) break
            visibleCount = i + 1
            if (delayPerStroke > 0L) delay(delayPerStroke)
        }
        visibleCount = allStrokes.size
        replayComplete = true
    }

    val displayStrokes = if (replayComplete || skipped) allStrokes else allStrokes.take(visibleCount)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Tap to skip replay (UIUX §12)
            .clickable(enabled = !replayComplete && !skipped) {
                skipped = true
                visibleCount = allStrokes.size
            },
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        displayStrokes.forEach { stroke ->
            if (stroke.points.size >= 2) {
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
        }
    }
}
