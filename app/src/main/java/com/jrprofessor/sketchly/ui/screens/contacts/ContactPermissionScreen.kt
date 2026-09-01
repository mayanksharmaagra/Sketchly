package com.jrprofessor.sketchly.ui.screens.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.R
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted

// ─────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ContactPermissionScreenPreview() {
    SketchlyTheme {
        ContactPermissionContent(
            onSyncContacts = {},
            onSkip = {},
        )
    }
}

// ─────────────────────────────────────────────
// Entry composable — handles runtime permission
// ─────────────────────────────────────────────

/**
 * Full-screen contact permission gate shown once after a new account is created.
 *
 * Flow:
 *  1. Show custom Sketchly rationale UI (warm cream, matching brand).
 *  2. "Sync Contacts" → launch Android READ_CONTACTS permission dialog.
 *  3. Regardless of grant/deny result, call [onContinue] — permission result
 *     can be queried later in CircleScreen if needed.
 *  4. "Skip for now" → call [onContinue] immediately, no permission requested.
 */
@Composable
fun ContactPermissionScreen(
    onContinue: () -> Unit,
) {
    // Android 13+ requires READ_CONTACTS; same launcher works for all supported APIs (29+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Proceed regardless of grant/deny — CircleScreen handles the denied case gracefully
        onContinue()
    }

    ContactPermissionContent(
        onSyncContacts = {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        },
        onSkip = onContinue,
    )
}

// ─────────────────────────────────────────────
// Pure UI (stateless, previewable)
// ─────────────────────────────────────────────

@Composable
fun ContactPermissionContent(
    onSyncContacts: () -> Unit,
    onSkip: () -> Unit,
) {
    // Staggered entrance animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Subtle icon pulse on first load
    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconScale",
    )

    // Icon container elevation-like shadow via border animation
    val iconBorderWidth by animateDpAsState(
        targetValue = if (visible) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 600),
        label = "iconBorder",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 36.dp, vertical = 48.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // ── Icon circle ───────────────────────────────
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(TextEditorBgColor)
                    .border(
                        width = iconBorderWidth,
                        color = TextEditorBorderColor.copy(alpha = 0.5f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_users),
                    contentDescription = null,
                    tint = AppNameColor,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Headline ─────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(
                    tween(400),
                    initialOffsetY = { it / 3 },
                ),
            ) {
                Text(
                    text = "Find your people",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                    ),
                    color = AppNameColor,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Body copy ─────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 80)) + slideInVertically(
                    tween(500, delayMillis = 80),
                    initialOffsetY = { it / 3 },
                ),
            ) {
                Text(
                    text = "Sync your contacts to see who's already on Sketchly. We'll never message anyone without your say.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Normal,
                        lineHeight = 26.sp,
                    ),
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            // ── Primary CTA — triggers Android permission ─
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 160)) + slideInVertically(
                    tween(500, delayMillis = 160),
                    initialOffsetY = { it / 2 },
                ),
            ) {
                Button(
                    onClick = onSyncContacts,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonGold,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(
                        text = "Sync Contacts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Skip link ─────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 220)),
            ) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = "Skip for now",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        color = AppNameColor,
                    )
                }
            }
        }
    }
}
