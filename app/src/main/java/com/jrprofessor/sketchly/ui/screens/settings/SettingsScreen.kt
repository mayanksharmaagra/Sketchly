package com.jrprofessor.sketchly.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.BuildConfig
import com.jrprofessor.sketchly.data.worker.ContactSyncWorker
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import androidx.compose.runtime.LaunchedEffect
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
            contactSync = true,
            onShowDoodlePreviewChange = {},
            onNotifyNewScribblesChange = {},
            onNotifyReactionsChange = {},
            onContactSyncChange = {},
            onSyncNow = {},
            isSyncing = false,
            isWidgetPinned = true,
            onBack = {},
            onNavigateToEditProfile = {},
            onSignOut = {},
        )
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onContactSyncChange: ((Boolean) -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val firestoreUser by viewModel.firestoreUser.collectAsStateWithLifecycle()
    val isSyncEnabledInVm by viewModel.isContactSyncEnabled.collectAsStateWithLifecycle()
    val isWidgetPinned by viewModel.isWidgetPinned.collectAsStateWithLifecycle()

    // Refresh widget pin status every time this screen is shown (DEBUG builds only).
    // This ensures the badge reflects reality after the user returns from the
    // home screen where they may have just pinned/removed the widget.
    LaunchedEffect(Unit) {
        viewModel.refreshWidgetPinStatus()
    }

    var showDoodlePreview by remember { mutableStateOf(true) }
    var notifyNewScribbles by remember { mutableStateOf(true) }
    var notifyReactions by remember { mutableStateOf(false) }

    // Check if READ_CONTACTS permission is granted
    val hasPermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    var contactSync by remember { mutableStateOf(hasPermission || isSyncEnabledInVm) }

    // Permission launcher for requesting READ_CONTACTS from the system
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactSync = true
            viewModel.enableContactSync()
            onContactSyncChange?.invoke(true)
        } else {
            contactSync = false
            viewModel.disableContactSync()
            onContactSyncChange?.invoke(false)
        }
    }

    val handleContactSyncToggle: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            val isCurrentlyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            if (isCurrentlyGranted) {
                contactSync = true
                viewModel.enableContactSync()
                onContactSyncChange?.invoke(true)
            } else {
                // Ask for READ_CONTACTS permission
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        } else {
            contactSync = false
            viewModel.disableContactSync()
            onContactSyncChange?.invoke(false)
        }
    }

    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    // Show Toast whenever syncMessage is set, then clear it
    LaunchedEffect(syncMessage) {
        syncMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearSyncMessage()
        }
    }

    SettingsScreenContent(
        displayName = firestoreUser?.displayName?.takeIf { it.isNotBlank() } ?: "",
        showDoodlePreview = showDoodlePreview,
        notifyNewScribbles = notifyNewScribbles,
        notifyReactions = notifyReactions,
        contactSync = contactSync,
        isSyncing = isSyncing,
        isWidgetPinned = isWidgetPinned,
        onShowDoodlePreviewChange = { showDoodlePreview = it },
        onNotifyNewScribblesChange = { notifyNewScribbles = it },
        onNotifyReactionsChange = { notifyReactions = it },
        onContactSyncChange = handleContactSyncToggle,
        onSyncNow = { viewModel.syncContactsNow() },
        onBack = onBack,
        onNavigateToEditProfile = onNavigateToEditProfile,
        onNavigateToBlockedUsers = onNavigateToBlockedUsers,
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
    isSyncing: Boolean = false,
    isWidgetPinned: Boolean = false,
    onShowDoodlePreviewChange: (Boolean) -> Unit,
    onNotifyNewScribblesChange: (Boolean) -> Unit,
    onNotifyReactionsChange: (Boolean) -> Unit,
    onContactSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit = {},
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .navigationBarsPadding(),
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────
        SketchlyTopBar(
            title  = "Settings",
            onBack = onBack,
        )

        HorizontalDivider(color = TextEditorBorderColor.copy(alpha = 0.5f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

        // ── WIDGET section ────────────────────────────────────────────────────
        SectionLabel("WIDGET")

        SettingsToggleRow(
            title = "Show doodle preview",
            subtitle = "Display recent sketches on your home screen",
            checked = showDoodlePreview,
            onCheckedChange = onShowDoodlePreviewChange,
        )

        // ── DEBUG ONLY: Widget pin status row ────────────────────────────────────
        // Visible in debug builds only — R8 eliminates this entire branch in
        // release APKs.  Use it to confirm widget pin status during manual QA
        // without relying on logcat.
        // ─────────────────────────────────────────────────────────────────────────
        if (BuildConfig.DEBUG) {
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "🚧 DEBUG — Widget status",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = ButtonGold.copy(alpha = 0.75f),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isWidgetPinned) "Pinned ✅" else "Not added ❌",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        ),
                        color = AppNameColor,
                    )
                }
            }
        }

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
            subtitle = "Find friends on Sketchly automatically • Periodic sync every 15m",
            checked = contactSync,
            onCheckedChange = onContactSyncChange,
        )

        if (contactSync) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !isSyncing,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSyncNow() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isSyncing) "Syncing…" else "Sync contacts now",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    ),
                    color = if (isSyncing) ButtonGold.copy(alpha = 0.45f) else ButtonGold,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = "Sync now",
                    tint = if (isSyncing) ButtonGold.copy(alpha = 0.45f) else ButtonGold,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        RowDivider()

        SettingsNavRow(
            title = "Edit profile",
            onClick = onNavigateToEditProfile,
        )

        RowDivider()

        // Privacy — Block management
        SettingsNavRow(
            title = "Blocked Users",
            onClick = onNavigateToBlockedUsers,
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
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 12.dp)) {
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
