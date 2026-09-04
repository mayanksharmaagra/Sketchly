package com.jrprofessor.sketchly.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import androidx.compose.ui.tooling.preview.Preview
import com.jrprofessor.sketchly.ui.screens.settings.SettingsViewModel
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Profile")
@Composable
private fun ProfileScreenPreview() {
    SketchlyTheme {
        ProfileScreenContent(
            initials = "MS",
            displayName = "Mayank Sharma",
            email = "+91 98765 43210",
            onBack = {},
            onOpenSettings = {},
            onEditProfile = {},
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()

    val displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Sketchly User"
    val email = user?.email?.takeIf { it.isNotBlank() }
        ?: user?.phoneNumber?.takeIf { it.isNotBlank() }
        ?: ""

    val initials = displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    ProfileScreenContent(
        initials = initials,
        displayName = displayName,
        email = email,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onEditProfile = onEditProfile,
    )
}

@Composable
private fun ProfileScreenContent(
    initials: String,
    displayName: String,
    email: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = AppNameColor,
                )
            }

            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                ),
                color = AppNameColor,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Avatar ────────────────────────────────────────────────────────────
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(AppNameColor.copy(alpha = 0.12f))
                    .border(1.5.dp, AppNameColor.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                    ),
                    color = AppNameColor,
                )
            }

            // Edit badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(ButtonGold)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onEditProfile() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Name & Email ──────────────────────────────────────────────────────
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            ),
            color = AppNameColor,
            textAlign = TextAlign.Center,
        )

        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ── Stats row ─────────────────────────────────────────────────────────
        HorizontalDivider(
            color = TextEditorBorderColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatItem(value = "—", label = "SENT")

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(TextEditorBorderColor.copy(alpha = 0.55f)),
            )

            StatItem(value = "—", label = "RECEIVED")

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(TextEditorBorderColor.copy(alpha = 0.55f)),
            )

            StatItem(value = "—", label = "FRIENDS")
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            color = TextEditorBorderColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))

        // ── Open Settings button ──────────────────────────────────────────────
        Surface(
            shape = PillShape,
            color = ButtonGold,
            shadowElevation = 4.dp,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .height(54.dp)
                .clip(PillShape)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onOpenSettings() },
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Open Settings",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            color = AppNameColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                fontSize = 10.sp,
            ),
            color = TextMuted,
        )
    }
}
