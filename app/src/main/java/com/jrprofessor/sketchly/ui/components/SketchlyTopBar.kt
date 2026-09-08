package com.jrprofessor.sketchly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.ui.theme.ToolBarBgColor

// ─────────────────────────────────────────────────────────────────────────────
// SketchlyTopBar — common top bar used by every screen with a back arrow.
//
// Layout (matching Image references):
//   [ ← back ]   [ centred title + optional subtitle ]   [ optional right slot ]
//
// Background: ToolBarBgColor — also covers the status bar area via
// .background(ToolBarBgColor) applied before .statusBarsPadding().
// ─────────────────────────────────────────────────────────────────────────────
 val TOP_BAR_HEIGHT = 56.dp

/**
 * @param title        Centred title text shown in the bar.
 * @param onBack       Called when the back arrow is tapped. Pass null to hide the arrow.
 * @param modifier     Applied to the outer container.
 * @param subtitle     Optional subtitle text (e.g. "Sent at 10:30 AM").
 * @param isItalic     Whether title is italicized (default true for brand style).
 * @param rightSlot    Optional composable anchored to the end of the bar (e.g. Save / ⋮).
 */
@Composable
fun SketchlyTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isItalic: Boolean = true,
    rightSlot: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ToolBarBgColor)
            .statusBarsPadding()
            .height(TOP_BAR_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        // ── Back arrow (left) ────────────────────────────────────────────────
        if (onBack != null) {
            IconButton(
                onClick  = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint               = AppNameColor,
                )
            }
        }

        // ── Centred content (title + optional subtitle) ───────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp), // keeps text clear of both icon slots
        ) {
            Text(
                text      = title,
                style     = if (subtitle == null) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight  = FontWeight.Bold,
                        fontStyle   = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        fontSize    = 20.sp,
                        lineHeight  = 24.sp,
                    )
                } else {
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight  = FontWeight.Bold,
                        fontStyle   = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                        fontSize    = 17.sp,
                        lineHeight  = 20.sp,
                    )
                },
                color     = AppNameColor,
                textAlign = TextAlign.Center,
                maxLines  = if (subtitle == null) 2 else 1,
                overflow  = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text      = subtitle,
                    style     = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color     = TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Right slot (optional) ────────────────────────────────────────────
        if (rightSlot != null) {
            Box(
                modifier         = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                rightSlot()
            }
        }
    }
}
