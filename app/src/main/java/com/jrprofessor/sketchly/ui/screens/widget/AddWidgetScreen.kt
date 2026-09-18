package com.jrprofessor.sketchly.ui.screens.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.hexToColor
import com.jrprofessor.sketchly.ui.components.SketchlyThumbnail
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.widget.SketchlyWidgetReceiver
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetScreen(
    onAddWidget: () -> Unit,
    onSkip: () -> Unit,
    viewModel: AddWidgetViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Bottom sheet — open automatically after a short delay so the user sees the preview first
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            delay(400) // brief moment to see the sketch before sheet slides up
            showSheet = true
        }
    }

    // Resolve background color from hex
    val bgColor = remember(uiState.backgroundColor) {
        runCatching { hexToColor(uiState.backgroundColor) }.getOrDefault(PaperIvory)
    }

    // ── Main screen — the scribble fills the whole screen ──────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        // ── Header label ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Add to Widget",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                ),
                color = AppNameColor,
            )
            Text(
                text = "Preview how it'll look on your home screen",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = TextMuted,
            )
        }

        // ── Widget card preview — centred in the remaining space ────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 280.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer phone-like card with shadow
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = AppNameColor.copy(alpha = 0.12f),
                        spotColor = AppNameColor.copy(alpha = 0.20f),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(bgColor)
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                if (uiState.isLoading) {
                    // Skeleton placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        bgColor.copy(alpha = 0.6f),
                                        bgColor,
                                    ),
                                ),
                            ),
                    )
                } else {
                    // Real sketch rendered with the same canvas used in Viewer/Thumbnail
                    SketchlyThumbnail(
                        strokes = uiState.strokes,
                        backgroundColor = bgColor,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // Subtle "WIDGET PREVIEW" stamp badge at top-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppNameColor.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Widgets,
                                contentDescription = null,
                                tint = AppNameColor.copy(alpha = 0.55f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "WIDGET PREVIEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = AppNameColor.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheet ────────────────────────────────────────────────────────
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { /* prevent accidental dismissal — user must tap a button */ },
            sheetState = sheetState,
            containerColor = BgColor,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted.copy(alpha = 0.4f)) },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Widget icon badge
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(ButtonGold.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Widgets,
                        contentDescription = null,
                        tint = ButtonGold,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Add Scribble to\nHome Screen",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 30.sp,
                    ),
                    color = AppNameColor,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "See notes from friends instantly\nwithout opening the app.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                // ── Set as Widget button ─────────────────────────────────
                Surface(
                    shape = PillShape,
                    color = ButtonGold,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(PillShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            // 1. Schedule the worker so the widget state is written
                            viewModel.scheduleWidgetUpdate()
                            // 2. Request pin (Android 8+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val awm = AppWidgetManager.getInstance(context)
                                val provider = ComponentName(context, SketchlyWidgetReceiver::class.java)
                                if (awm.isRequestPinAppWidgetSupported) {
                                    awm.requestPinAppWidget(provider, null, null)
                                }
                            }
                            onAddWidget()
                        },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Set as Widget 📌",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Maybe later ──────────────────────────────────────────
                Text(
                    text = "Maybe later",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = TextMuted,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSkip() }
                        .padding(vertical = 8.dp, horizontal = 20.dp),
                )
            }
        }
    }
}
