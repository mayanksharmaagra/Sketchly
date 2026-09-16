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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jrprofessor.sketchly.data.repository.BlockRepository
import com.jrprofessor.sketchly.data.repository.ContactRepository
import com.jrprofessor.sketchly.data.repository.SketchlyRepository
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.components.ViewerSkeleton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.CanvasCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.mutableListOf

val EMOJI_REACTIONS = listOf("❤️", "😂", "✨", "😮", "😢")

/** Connection state relative to the sketch sender. */
enum class ViewerConnectionState { UNKNOWN, NOT_CONNECTED, PENDING, CONNECTED }

data class ViewerUiState(
    val sketch: Sketch? = null,
    val isLoading: Boolean = true,
    val selectedEmoji: String? = null,
    val reactionSentConfirmation: Boolean = false,
    /** Live reactions from Firestore listener (SRS FR-7.3) */
    val reactions: List<Reaction> = emptyList(),
    /** Whether the stroke-by-stroke replay animation has started */
    val replayStarted: Boolean = false,
    /** Whether the current user is already connected to the sender */
    val connectionState: ViewerConnectionState = ViewerConnectionState.UNKNOWN,
    /** Controls the block-confirmation dialog */
    val showBlockDialog: Boolean = false,
    /** True for ~2 s after the user taps "Set as Widget" — triggers ✓ flash */
    val widgetSetConfirmation: Boolean = false,
    /**
     * Resolved display names for recipient UIDs — populated when the current user is
     * the sender so the top bar shows a real name instead of a raw Firebase UID.
     * Key = userId, Value = displayName.
     */
    val recipientDisplayNames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sketchRepository: SketchlyRepository,
    private val contactRepository: ContactRepository,
    private val blockRepository: BlockRepository,
) : ViewModel()
{

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
            // Check connection status with sender (only when viewing a received sketch)
            val senderId = sketch?.senderId
            if (senderId != null && senderId != authRepository.currentUserId) {
                val isConnected = contactRepository.isConnectedTo(senderId)
                _uiState.update {
                    it.copy(
                        connectionState = if (isConnected)
                            ViewerConnectionState.CONNECTED
                        else
                            ViewerConnectionState.NOT_CONNECTED,
                    )
                }
            } else {
                // Sender viewing own sketch — no banner needed
                _uiState.update { it.copy(connectionState = ViewerConnectionState.CONNECTED) }
            }

            // If the viewer IS the sender, resolve recipient UIDs → display names so
            // the top bar can show a real name instead of a raw Firebase UID.
            val isSender = senderId == authRepository.currentUserId
            if (isSender && sketch != null && sketch.recipientIds.isNotEmpty()) {
                try {
                    val connected = contactRepository.getConnectedContacts().first()
                    val nameMap = connected.associate { it.userId to it.displayName }
                    _uiState.update { it.copy(recipientDisplayNames = nameMap) }
                } catch (_: Exception) { /* best-effort; fall back to raw ID */ }
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

    /** Send a connection request to the sketch sender and update the local state to PENDING. */
    fun sendConnectionRequest() {
        val sketch = _uiState.value.sketch ?: return
        val senderId = sketch.senderId
        val senderName = sketch.senderDisplayName
        viewModelScope.launch {
            _uiState.update { it.copy(connectionState = ViewerConnectionState.PENDING) }
            val contact = com.jrprofessor.sketchly.data.model.SketchlyContact(
                userId = senderId,
                displayName = senderName,
                username = "",
                avatarUrl = null,
                phoneLastFour = null,
                source = com.jrprofessor.sketchly.data.model.ContactSource.CONNECTION_REQUEST,
                connectionStatus = com.jrprofessor.sketchly.data.model.ConnectionStatus.PENDING_SENT,
            )
            contactRepository.sendConnectionRequest(contact)
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

    // ── Block ─────────────────────────────────────────────────────────────────

    /** Show the block confirmation dialog. */
    fun requestBlock() {
        _uiState.update { it.copy(showBlockDialog = true) }
    }

    /** Dismiss the block confirmation dialog without taking action. */
    fun dismissBlockDialog() {
        _uiState.update { it.copy(showBlockDialog = false) }
    }

    /**
     * Blocks the sketch sender.
     * The caller (NavGraph) is responsible for navigating back after this returns
     * so the viewer screen is removed from the back-stack.
     */
    suspend fun blockSender(): Result<Unit> {
        val sketch =
            _uiState.value.sketch ?: return Result.failure(IllegalStateException("No sketch"))
        _uiState.update { it.copy(showBlockDialog = false) }
        val result = blockRepository.blockUser(
            targetUserId = sketch.senderId,
            targetDisplayName = sketch.senderDisplayName.takeIf { it.isNotBlank() }
                ?: "Sketchly User",
        )
        if (result.isSuccess) {
            // Remove blocked sender's sketches from local Room immediately
            // so they stop appearing in the Dashboard feed.
            sketchRepository.deleteReceivedSketchesFrom(sketch.senderId)
        }
        return result
    }

    /**
     * Schedules [WidgetUpdateWorker] to render the current sketch to the home-screen
     * widget, then shows a brief ✓ confirmation for ~2 seconds.
     * Works for both sender and receiver.
     */
    fun setAsWidget() {
        val sketchId = _uiState.value.sketch?.id ?: return
        sketchRepository.scheduleWidgetUpdate(sketchId)
        viewModelScope.launch {
            _uiState.update { it.copy(widgetSetConfirmation = true) }
            delay(2000)
            _uiState.update { it.copy(widgetSetConfirmation = false) }
        }
    }
}

@Composable
fun SketchlyViewerScreen(
    sketchId: String,
    onBack: () -> Unit,
    onBlock: () -> Unit = {},
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
        onConnect = viewModel::sendConnectionRequest,
        onRequestBlock = viewModel::requestBlock,
        onDismissBlockDialog = viewModel::dismissBlockDialog,
        onAddToWidget = viewModel::setAsWidget,
        onConfirmBlock = {
            viewModel.viewModelScope.launch {
                viewModel.blockSender()
                onBlock()
            }
        },
    )
}

@Composable
fun ViewerScreenContent(
    uiState: ViewerUiState,
    isSender: Boolean,
    onBack: () -> Unit,
    onReact: (String) -> Unit,
    onConnect: () -> Unit = {},
    onRequestBlock: () -> Unit = {},
    onDismissBlockDialog: () -> Unit = {},
    onConfirmBlock: () -> Unit = {},
    onAddToWidget: () -> Unit = {},
) {
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        val displayName = when {
            isSender -> {
                // Resolve first recipient UID to a real display name.
                // Falls back to the raw UID only if the contact isn't in our list.
                val firstId = uiState.sketch?.recipientIds?.firstOrNull()
                if (firstId != null) {
                    uiState.recipientDisplayNames[firstId] ?: firstId
                } else {
                    "Unknown"
                }
            }

            else -> {
                uiState.sketch?.senderDisplayName?.takeIf { it.isNotBlank() } ?: "Unknown"
            }
        }
        val createdAt = uiState.sketch?.createdAt ?: System.currentTimeMillis()
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(createdAt))

        SketchlyTopBar(
            title = displayName,
            subtitle = "Sent at $timeStr",
            onBack = onBack,
            isItalic = false,
            rightSlot = if (isSender) null else ({
                // 3-dot menu — only shown when viewing a received scribble
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More options",
                            tint = AppNameColor,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Block $displayName",
                                    color = Color(0xFFD64242),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestBlock()
                            },
                        )
                    }
                }
            }),
        )

        // ── Full Canvas Card ──────────────────────────────────────────────────
        Surface(
            shape = CanvasCardShape,
            color = PaperIvory,
            shadowElevation = 6.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (uiState.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // ── "Connect with [name]?" banner ─────────────────────────────────────
        // [FEATURE FLAGGED — ENABLE_CONNECTION_REQUESTS]
        // When false (V1 auto-connect): connection is created server-side on the
        // first scribble send. No manual Connect prompt is needed on the viewer.
        // When true (V2 request flow): banner is shown for non-connected senders.
        if (com.jrprofessor.sketchly.utils.FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
            val connState = uiState.connectionState
            val senderName =
                uiState.sketch?.senderDisplayName?.takeIf { it.isNotBlank() } ?: "this person"
            if (!isSender && connState != ViewerConnectionState.CONNECTED && connState != ViewerConnectionState.UNKNOWN) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() +
                            androidx.compose.animation.slideInVertically { it / 2 },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(Color(0xFFF5EFE0))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connect with $senderName?",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                ),
                                color = AppNameColor,
                            )
                            Text(
                                text = "Send each other Scribbles anytime.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = AppNameColor.copy(alpha = 0.6f),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            shape = PillShape,
                            color = if (connState == ViewerConnectionState.PENDING)
                                ButtonGold.copy(alpha = 0.35f)
                            else
                                ButtonGold,
                            shadowElevation = if (connState == ViewerConnectionState.PENDING) 0.dp else 2.dp,
                            modifier = Modifier
                                .clip(PillShape)
                                .clickable(
                                    enabled = connState == ViewerConnectionState.NOT_CONNECTED,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onConnect() },
                        ) {
                            Text(
                                text = if (connState == ViewerConnectionState.PENDING) "Pending" else "Connect",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── Emoji Reaction Bar (Image 2 style — 5 circular pill buttons) ──────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EMOJI_REACTIONS.forEach { emoji ->
                val isSelected = uiState.selectedEmoji == emoji
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "emoji_scale_$emoji",
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Color(0xFFF5EFE0),
                            shape = CircleShape,
                        )
                        .then(
                            if (isSelected)
                                Modifier.border(2.dp, Color(0xFFC99A3C), CircleShape)
                            else
                                Modifier
                        )
                        .clickable(onClickLabel = "React with $emoji") { onReact(emoji) }
                        .semantics { contentDescription = "React with $emoji" },
                    contentAlignment = Alignment.Center,
                ) {
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

        // ── "Set as Widget" button ────────────────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = !uiState.isLoading && uiState.sketch != null,
            enter = fadeIn(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
            ) {
                Surface(
                    shape = PillShape,
                    color = if (uiState.widgetSetConfirmation)
                        Color(0xFF70A18A)  // sage-green confirmation state
                    else
                        ButtonGold,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(PillShape)
                        .clickable(
                            enabled = !uiState.widgetSetConfirmation,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onAddToWidget() },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AnimatedContent(
                            targetState = uiState.widgetSetConfirmation,
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(
                                    spring(Spring.DampingRatioMediumBouncy),
                                    initialScale = 0.8f,
                                )) togetherWith (fadeOut(tween(150)) + scaleOut(tween(150)))
                            },
                            label = "widget_btn_label",
                        ) { confirmed ->
                            Text(
                                text = if (confirmed) "✓  Added to Widget!" else "📱  Set as Widget",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Block confirmation dialog ─────────────────────────────────────────────
    if (uiState.showBlockDialog) {
        val senderName =
            uiState.sketch?.senderDisplayName?.takeIf { it.isNotBlank() } ?: "this user"
        AlertDialog(
            onDismissRequest = onDismissBlockDialog,
            title = {
                Text(
                    text = "Block $senderName?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppNameColor,
                )
            },
            text = {
                Text(
                    text = "They won't be able to send you scribbles. " +
                            "You can unblock them anytime in Settings → Blocked Users.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmBlock,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD64242)),
                ) {
                    Text("Block", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBlockDialog) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = BgColor,
        )
    }
}

// ── Previews ──

@Preview(name = "Viewer — Loading", showBackground = true)
@Composable
private fun ViewerScreenLoadingPreview() {
    SketchlyTheme {
        ViewerScreenContent(
            uiState = ViewerUiState(
                isLoading = true,
                connectionState = ViewerConnectionState.NOT_CONNECTED,
                showBlockDialog = true
            ),
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

    val displayStrokes =
        if (replayComplete || skipped) allStrokes else allStrokes.take(visibleCount)

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
