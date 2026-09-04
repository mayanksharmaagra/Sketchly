package com.jrprofessor.sketchly.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.jrprofessor.sketchly.data.repository.AuthRepository
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUser)

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Screen
// ─────────────────────────────────────────────────────────────────────────────


@Preview(showBackground = true, showSystemUi = true, name = "Settings")
@Composable
private fun SettingsScreenPreview() {
    SketchlyTheme {
        SettingsScreenContent(
            displayName = "Mayank Sharma",
            showDoodlePreview = true,
            notifyNewScribbles = true,
            notifyReactions = false,
            contactSync = false,
            onShowDoodlePreviewChange = {},
            onNotifyNewScribblesChange = {},
            onNotifyReactionsChange = {},
            onContactSyncChange = {},
            onBack = {},
            onNavigateToProfile = {},
            onSignOut = {},
        )
    }
}
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToCircle: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToScreenPreview: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()

    var showDoodlePreview by remember { mutableStateOf(true) }
    var notifyNewScribbles by remember { mutableStateOf(true) }
    var notifyReactions by remember { mutableStateOf(false) }
    var contactSync by remember { mutableStateOf(false) }

    SettingsScreenContent(
        displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "",
        showDoodlePreview = showDoodlePreview,
        notifyNewScribbles = notifyNewScribbles,
        notifyReactions = notifyReactions,
        contactSync = contactSync,
        onShowDoodlePreviewChange = { showDoodlePreview = it },
        onNotifyNewScribblesChange = { notifyNewScribbles = it },
        onNotifyReactionsChange = { notifyReactions = it },
        onContactSyncChange = { contactSync = it },
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onSignOut = { viewModel.signOut(onSignedOut) },
    )
}

@Composable
private fun SettingsScreenContent(
    displayName: String,
    showDoodlePreview: Boolean,
    notifyNewScribbles: Boolean,
    notifyReactions: Boolean,
    contactSync: Boolean,
    onShowDoodlePreviewChange: (Boolean) -> Unit,
    onNotifyNewScribblesChange: (Boolean) -> Unit,
    onNotifyReactionsChange: (Boolean) -> Unit,
    onContactSyncChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    // Local toggle state — V2: persist to Firestore / DataStore
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
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
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                ),
                color = AppNameColor,
            )
        }

        HorizontalDivider(color = TextEditorBorderColor.copy(alpha = 0.5f))

        Spacer(modifier = Modifier.height(8.dp))

        // ── WIDGET section ────────────────────────────────────────────────────
        SectionLabel("WIDGET")

        SettingsToggleRow(
            title = "Show doodle preview",
            subtitle = "Display recent sketches on your home screen",
            checked = showDoodlePreview,
            onCheckedChange = onShowDoodlePreviewChange,
        )

        RowDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // ── NOTIFICATIONS section ─────────────────────────────────────────────
        SectionLabel("NOTIFICATIONS")

        SettingsToggleRow(
            title = "New Scribbles",
            subtitle = null,
            checked = notifyNewScribbles,
            onCheckedChange = onNotifyNewScribblesChange,
        )

        RowDivider()

        SettingsToggleRow(
            title = "Reactions",
            subtitle = null,
            checked = notifyReactions,
            onCheckedChange = onNotifyReactionsChange,
        )

        RowDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // ── ACCOUNT section ───────────────────────────────────────────────────
        SectionLabel("ACCOUNT")

        SettingsToggleRow(
            title = "Contact sync",
            subtitle = "Find friends on Sketchly automatically • Last synced 2m ago",
            checked = contactSync,
            onCheckedChange = onContactSyncChange,
        )

        RowDivider()

        SettingsNavRow(
            title = "Edit profile",
            onClick = onNavigateToProfile,
        )

        RowDivider()

        // Log out
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onSignOut() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Log out",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                ),
                color = Color(0xFFD64242),
            )
        }

        RowDivider()

        Spacer(modifier = Modifier.height(32.dp))

        // Version footer
        Text(
            text = "Sketchly v1.0",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = TextMuted.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable row components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            fontSize = 11.sp,
        ),
        color = ButtonGold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                ),
                color = AppNameColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = ButtonGold,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = TextEditorBorderColor,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            ),
            color = AppNameColor,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
            contentDescription = null,
            tint = TextMuted.copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = TextEditorBorderColor.copy(alpha = 0.45f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
