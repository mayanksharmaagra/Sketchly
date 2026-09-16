package com.jrprofessor.sketchly.ui.screens.contacts

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.R
import com.jrprofessor.sketchly.data.model.ConnectionStatus
import com.jrprofessor.sketchly.data.model.ContactSource
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme

// ============================================================
// Colors matching the Sketchly palette
// ============================================================
private val Paper = Color(0xFFEFE8D6)
private val PaperCard = Color(0xFFF7F2E4)
private val Ink = Color(0xFF34293F)
private val Gold = Color(0xFFC99A3C)
private val Rust = Color(0xFFB4562F)
private val Forest = Color(0xFF5C7A5C)
private val Border = Color(0xFFD9CEAF)
private val TextMuted = Color(0xFF8A7F6C)

// ============================================================
// Contact Sync Screen
// ============================================================

@Composable
fun ContactPermissionScreen(
    onSyncComplete: () -> Unit,  // Navigate to Tutorial/First Drawing
    onSkip: () -> Unit,          // Navigate to Tutorial/First Drawing (skipped)
    viewModel: ContactSyncViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Permission launcher — launched when ViewModel moves to RequestingPermission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("ContactPermissionScreen", "permissionLauncher result: isGranted=$isGranted")
        if (isGranted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // React to state changes
    LaunchedEffect(uiState) {
        Log.d("ContactPermissionScreen", "LaunchedEffect: uiState changed to $uiState")
        when (uiState) {
            is ContactSyncUiState.RequestingPermission -> {
                Log.d("ContactPermissionScreen", "Launching system READ_CONTACTS permission request")
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            is ContactSyncUiState.Skipped -> {
                Log.d("ContactPermissionScreen", "Navigating via onSkip()")
                onSkip()
            }
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "contact_sync_state"
        ) { state ->
            when (state) {
                is ContactSyncUiState.Idle,
                is ContactSyncUiState.RequestingPermission -> {
                    IdleState(
                        onSyncTapped = {
                            Log.d("ContactPermissionScreen", "Sync Contacts button tapped")
                            viewModel.onSyncButtonTapped()
                        },
                        onSkipTapped = {
                            Log.d("ContactPermissionScreen", "Skip for now tapped from Idle")
                            viewModel.onSkipTapped()
                        }
                    )
                }

                is ContactSyncUiState.Syncing -> {
                    Log.d("ContactPermissionScreen", "Rendering SyncingState")
                    SyncingState()
                }

                is ContactSyncUiState.Success -> {
                    Log.d("ContactPermissionScreen", "Rendering SuccessState: matched=${state.matchedCount}, contacts=${state.contacts.size}")
                    SuccessState(
                        matchedCount = state.matchedCount,
                        contacts = state.contacts,
                        onContinue = {
                            Log.d("ContactPermissionScreen", "Continue button tapped from SuccessState")
                            onSyncComplete()
                        }
                    )
                }

                is ContactSyncUiState.PermissionDenied -> {
                    Log.w("ContactPermissionScreen", "Rendering PermissionDeniedState")
                    PermissionDeniedState(
                        onSkipTapped = {
                            Log.d("ContactPermissionScreen", "Skip tapped from PermissionDeniedState")
                            viewModel.onSkipTapped()
                        }
                    )
                }

                is ContactSyncUiState.Error -> {
                    Log.e("ContactPermissionScreen", "Rendering ErrorState: ${state.message}")
                    ErrorState(
                        message = state.message,
                        onRetry = {
                            Log.d("ContactPermissionScreen", "Retry tapped from ErrorState")
                            viewModel.onRetryTapped()
                        },
                        onSkip = {
                            Log.d("ContactPermissionScreen", "Skip tapped from ErrorState")
                            viewModel.onSkipTapped()
                        }
                    )
                }

                is ContactSyncUiState.Skipped -> {
                    // Empty — LaunchedEffect handles navigation immediately
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ============================================================
// State composables
// ============================================================

@Composable
private fun IdleState(
    onSyncTapped: () -> Unit,
    onSkipTapped: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(PaperCard, CircleShape)
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_users),
                contentDescription = null,
                tint = AppNameColor,
                modifier = Modifier.size(42.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Find your people",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Sync your contacts to see who's already on Sketchly. " +
                    "We'll never store your contacts — only secure hashes are used for matching.",
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Primary CTA
        Button(
            onClick = onSyncTapped,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Text(
                text = "Sync Contacts",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Privacy note
        Text(
            text = "🔒 Phone numbers are hashed on your device. Only secure hashes are shared — your raw contacts never leave your phone.",
            fontSize = 11.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperCard, RoundedCornerShape(12.dp))
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Skip
        TextButton(onClick = onSkipTapped) {
            Text(
                text = "Skip for now",
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SyncingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Finding your friends…",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This only takes a second",
            fontSize = 13.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun SuccessState(
    matchedCount: Int,
    contacts: List<SketchlyContact>,
    onContinue: () -> Unit,
) {
    if (contacts.isNotEmpty()) {
        // Non-empty: list at top, header + button at bottom
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(contacts) { contact ->
                    ContactRow(contact = contact)
                }
            }

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$matchedCount friend${if (matchedCount > 1) "s" else ""} on Sketchly!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Send them a follow request to start exchanging Scribbles.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(
                    text = "Continue",
                    color = Color(0xFFF2E8CE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    } else {
        // Empty: everything centered vertically
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(PaperCard, CircleShape)
                    .border(1.dp, Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_users),
                    contentDescription = null,
                    tint = AppNameColor,
                    modifier = Modifier.size(42.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No friends found yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Invite friends to join Sketchly — they'll show up here once they sign up.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(
                    text = "Continue",
                    color = Color(0xFFF2E8CE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ContactRow(contact: SketchlyContact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperCard, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        val avatarColors = listOf(
            Color(0xFFB4562F), Color(0xFF5C87A0),
            Color(0xFF5C7A5C), Color(0xFFC99A3C)
        )
        val avatarColor = avatarColors[contact.displayName.length % avatarColors.size]
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.displayName.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Name + username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Ink
            )
            Text(
                text = "@${contact.username}",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        // Follow button
        OutlinedButton(
            onClick = { /* handled by FollowRequestViewModel */ },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Gold),
            border = androidx.compose.foundation.BorderStroke(1.dp, Gold),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("Follow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionDeniedState(
    onSkipTapped: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(PaperCard, CircleShape)
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🔒", fontSize = 40.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Contacts access not granted",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "You can still find friends by searching their username. " +
                    "You can also enable contact sync later from Settings.",
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onSkipTapped,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            Text(
                text = "Continue without contacts",
                color = Color(0xFFF2E8CE),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(PaperCard, CircleShape)
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("\u26a0\ufe0f", fontSize = 36.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Sync failed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
        ) {
            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = TextMuted, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================
// @Preview composables — one per UI state
// ============================================================

private val previewContacts = listOf(
    SketchlyContact(
        userId = "uid_1",
        displayName = "Sakshi Arora",
        username = "sakshi_a",
        avatarUrl = null,
        phoneLastFour = "4821",
        source = ContactSource.CONTACT_SYNC,
        connectionStatus = ConnectionStatus.SUGGESTED,
    ),
    SketchlyContact(
        userId = "uid_2",
        displayName = "Rahul Kumar",
        username = "rahul_k",
        avatarUrl = null,
        phoneLastFour = "9203",
        source = ContactSource.CONTACT_SYNC,
        connectionStatus = ConnectionStatus.SUGGESTED,
    ),
    SketchlyContact(
        userId = "uid_3",
        displayName = "Priya Mehta",
        username = "priya_m",
        avatarUrl = null,
        phoneLastFour = "7745",
        source = ContactSource.CONTACT_SYNC,
        connectionStatus = ConnectionStatus.SUGGESTED,
    ),
)

@Preview(name = "Idle", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewIdleState() {
    SketchlyTheme {
        IdleState(
            onSyncTapped = {},
            onSkipTapped = {},
        )
    }
}

@Preview(name = "Syncing", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewSyncingState() {
    SketchlyTheme {
        SyncingState()
    }
}

@Preview(name = "Success — 3 friends", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewSuccessState() {
    SketchlyTheme {
        SuccessState(
            matchedCount = previewContacts.size,
            contacts = previewContacts,
            onContinue = {},
        )
    }
}

@Preview(name = "Success — No matches", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewSuccessEmptyState() {
    SketchlyTheme {
        SuccessState(
            matchedCount = 0,
            contacts = emptyList(),
            onContinue = {},
        )
    }
}

@Preview(name = "Permission Denied", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewPermissionDeniedState() {
    SketchlyTheme {
        PermissionDeniedState(
            onSkipTapped = {},
        )
    }
}

@Preview(name = "Error — Rate limit", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewErrorState() {
    SketchlyTheme {
        ErrorState(
            message = "Contact sync limit reached. Try again in 24 hours.",
            onRetry = {},
            onSkip = {},
        )
    }
}

@Preview(name = "Contact Row", showBackground = true, backgroundColor = 0xFFEFE8D6)
@Composable
private fun PreviewContactRow() {
    SketchlyTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ContactRow(contact = previewContacts.first())
        }
    }
}