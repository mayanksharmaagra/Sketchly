package com.jrprofessor.sketchly.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jrprofessor.sketchly.ui.theme.NoteCardShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import androidx.compose.material3.Surface

/**
 * Paper-toned shimmer skeleton loader (UIUX §7).
 *
 * Colors stay in the ivory-to-warm-grey family so the shimmer feels like
 * light catching the texture of paper rather than a generic grey placeholder.
 */

// ── Shimmer Brush ──

private val SkeletonBase = Color(0xFFEDE8DF)     // Light paper ivory
private val SkeletonHighlight = Color(0xFFF7F3EE)  // Brighter ivory highlight
private val SkeletonEdge = Color(0xFFD9D3C8)       // Warm grey shadow

@Composable
fun shimmerBrush(widthPx: Float = 800f): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val translateX by transition.animateFloat(
        initialValue = -widthPx,
        targetValue = widthPx * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )

    return Brush.linearGradient(
        colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
        start = Offset(translateX, 0f),
        end = Offset(translateX + widthPx, 0f),
    )
}

// ── Inbox Skeleton — 4 card-shaped placeholders ──

/**
 * Shows 4 paper-toned card skeletons for the Inbox initial load state (UIUX §7).
 * Matches the exact dimensions of [InboxSketchCard] so there's no layout jump.
 */
@Composable
fun InboxSkeletonList() {
    val brush = shimmerBrush()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
    ) {
        repeat(4) {
            InboxSkeletonCard(brush = brush)
        }
    }
}

@Composable
private fun InboxSkeletonCard(brush: Brush) {
    Surface(
        shape = NoteCardShape,
        color = PaperIvory,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(brush, NoteCardShape),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Sender name line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(16.dp)
                        .background(brush, NoteCardShape),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Subtitle line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .height(12.dp)
                        .background(brush, NoteCardShape),
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Timestamp line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .background(brush, NoteCardShape),
                )
            }
        }
    }
}

// ── Viewer Skeleton — full canvas paper placeholder ──

/**
 * Full-canvas skeleton for the [SketchlyViewerScreen] loading state (UIUX §7).
 * Fills the entire available space with an animated shimmer over a paper-ivory surface.
 */
@Composable
fun ViewerSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush(widthPx = 1200f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush),
    )
}

// ── Generic skeleton block (reusable by History, etc.) ──

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 14.dp,
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(brush, NoteCardShape),
    )
}
