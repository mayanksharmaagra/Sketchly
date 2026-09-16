package com.jrprofessor.sketchly.ui.screens.circle

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.data.model.ConnectionStatus
import com.jrprofessor.sketchly.data.model.SketchlyContact
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.utils.FeatureFlags
import kotlinx.coroutines.delay
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Avatar colour palette
// ─────────────────────────────────────────────────────────────────────────────

private val AVATAR_COLORS = listOf(
    Color(0xFFB5534A),
    Color(0xFF4A7B6F),
    Color(0xFF4A7090),
    Color(0xFF8B6914),
    Color(0xFF6B4E71),
    Color(0xFF3D405B),
    Color(0xFF2A9D8F),
    Color(0xFFE07A5F),
)

private fun avatarColorFor(name: String): Color =
    AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]

private fun initialsFor(name: String): String =
    name.split(" ").take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FriendsListScreen(
    viewModel: CircleViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suggested by viewModel.suggestedContacts.collectAsStateWithLifecycle()
    val requests by viewModel.incomingRequests.collectAsStateWithLifecycle()
    val connected by viewModel.connectedContacts.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // Auto-dismiss sync toast after 3 s
    LaunchedEffect(uiState.syncMessage) {
        if (uiState.syncMessage != null) {
            delay(3000)
            viewModel.clearSyncMessage()
        }
    }

    if (FeatureFlags.HIDE_SUGGESTED_TAB) {
        // ── V1: unified single-view (no tabs) ────────────────────────────────
        UnifiedCircleScreen(
            uiState        = uiState,
            connected      = connected,
            onBack         = onBack,
            onSearchChanged = viewModel::onSearchQueryChanged,
            onSyncContacts = viewModel::syncContacts,
        )
    } else {
        // ── V2: original tab-based layout ────────────────────────────────────
        CircleScreenContent(
            uiState       = uiState,
            suggested     = suggested,
            requests      = requests,
            connected     = connected,
            searchResults = searchResults,
            onBack        = onBack,
            onTabSelected = viewModel::selectTab,
            onSearchChanged = viewModel::onSearchQueryChanged,
            onFollow      = viewModel::sendConnectionRequest,
            onAccept      = viewModel::acceptRequest,
            onDecline     = viewModel::declineRequest,
            onSyncContacts = viewModel::syncContacts,
            pendingUserIds = uiState.pendingUserIds,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// V1 — Unified single-view (HIDE_SUGGESTED_TAB = true)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnifiedCircleScreen(
    uiState: CircleUiState,
    connected: List<SketchlyContact>,
    onBack: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSyncContacts: () -> Unit,
) {
    val query = uiState.searchQuery.trim()
    val isSearchMode = query.length >= 2

    // Filter connected list by search query
    val displayList = if (isSearchMode)
        connected.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.username.contains(query, ignoreCase = true)
        }
    else connected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            SketchlyTopBar(title = "Your Circle", onBack = onBack)

            // Search bar — filters connections by name
            CircleSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChanged,
                isSearching = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {

                // ── Sync Contacts card ────────────────────────────────────────
                item(key = "sync_header") { SectionHeader(text = "SYNC CONTACTS") }
                item(key = "sync_card") {
                    SyncContactsCard(
                        isSyncing   = uiState.isSyncing,
                        syncMessage = uiState.syncMessage,
                        onSync      = onSyncContacts,
                        modifier    = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                item(key = "connections_spacer") { Spacer(modifier = Modifier.height(20.dp)) }

                // ── Connected users list ──────────────────────────────────────
                item(key = "connections_header") {
                    SectionHeader(
                        text = if (isSearchMode) "MATCHES  ·  ${displayList.size}"
                               else "YOUR CONNECTIONS  ·  ${connected.size}",
                    )
                }

                if (displayList.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp, bottom = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isSearchMode) "🔍" else "✏️",
                                    fontSize = 40.sp,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isSearchMode)
                                        "No connections match \"$query\""
                                    else
                                        "No connections yet",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = AppNameColor,
                                    textAlign = TextAlign.Center,
                                )
                                if (!isSearchMode) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Send someone a scribble —\na connection forms automatically.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMuted,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(displayList, key = { it.userId }) { contact ->
                        ConnectedContactRow(contact = contact)
                        RowDivider()
                    }
                }
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Screen content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleScreenContent(
    uiState: CircleUiState,
    suggested: List<SketchlyContact>,
    requests: List<SketchlyContact>,
    connected: List<SketchlyContact>,
    searchResults: List<SketchlyContact>,
    onBack: () -> Unit,
    onTabSelected: (CircleTab) -> Unit,
    onSearchChanged: (String) -> Unit,
    onFollow: (SketchlyContact) -> Unit,
    onAccept: (SketchlyContact) -> Unit,
    onDecline: (SketchlyContact) -> Unit,
    onSyncContacts: () -> Unit,
    pendingUserIds: Set<String> = emptySet(),
) {
    // Search mode: active when user typed >= 2 chars
    val isSearchMode = uiState.searchQuery.trim().length >= 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar

            SketchlyTopBar(
                title = "Your Friend",
                onBack = onBack,
            )

            // Search bar (shows spinner while Firestore search is in flight)
            CircleSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChanged,
                isSearching = uiState.isSearching,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            // Tab row (always visible, search works on the selected tab)
            CircleTabRow(
                selectedTab = uiState.selectedTab,
                requestCount = requests.size,
                onTabSelected = onTabSelected,
            )

            // Tab body
            when (uiState.selectedTab) {
                CircleTab.SUGGESTED -> SuggestedTab(
                    // In search mode show Firestore results; otherwise local sync list
                    contacts = if (isSearchMode) searchResults else suggested,
                    searchQuery = uiState.searchQuery,
                    isSyncing = uiState.isSyncing,
                    syncMessage = uiState.syncMessage,
                    isSearchMode = isSearchMode,
                    isSearching = uiState.isSearching,
                    onFollow = onFollow,
                    onSyncContacts = onSyncContacts,
                    pendingUserIds = pendingUserIds,
                )
                CircleTab.REQUESTS -> RequestsTab(
                    contacts = if (isSearchMode)
                        requests.filter {
                            it.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                                    it.username.contains(uiState.searchQuery, ignoreCase = true)
                        }
                    else requests,
                    onAccept = onAccept,
                    onDecline = onDecline,
                )
                CircleTab.CONNECTED -> ConnectedTab(
                    // In search mode filter local connected list by query
                    contacts = if (isSearchMode)
                        connected.filter {
                            it.displayName.contains(uiState.searchQuery, ignoreCase = true) ||
                                    it.username.contains(uiState.searchQuery, ignoreCase = true)
                        }
                    else connected,
                    searchQuery = uiState.searchQuery,
                    isSearchMode = isSearchMode,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleTabRow(
    selectedTab: CircleTab,
    requestCount: Int,
    onTabSelected: (CircleTab) -> Unit,
) {
    // Visible tabs: REQUESTS is only shown when the feature flag is on.
    // CircleTab.REQUESTS, RequestsTab composable, and all Accept/Decline buttons
    // remain fully in the codebase — only this iteration list changes.
    val visibleTabs = if (FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
        CircleTab.entries
    } else {
        CircleTab.entries.filter { it != CircleTab.REQUESTS }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        visibleTabs.forEach { tab ->
            val isSelected = tab == selectedTab
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) AppNameColor else TextMuted,
                animationSpec = tween(200),
                label = "tabColor",
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onTabSelected(tab) }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text(
                        text = when (tab) {
                            CircleTab.SUGGESTED -> "Suggested"
                            CircleTab.REQUESTS  -> "Requests"
                            CircleTab.CONNECTED -> "Connected"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                        ),
                        color = labelColor,
                    )
                    // Badge on Requests tab (only visible when flag is on)
                    if (tab == CircleTab.REQUESTS && requestCount > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD9534F)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = requestCount.coerceAtMost(99).toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = Color.White,
                            )
                        }
                    }
                }
                // Underline indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(if (isSelected) ButtonGold else Color.Transparent),
                )
            }
        }
    }
    // Full-width divider under tab row
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(TextEditorBorderColor.copy(alpha = 0.5f)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Suggested tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestedTab(
    contacts: List<SketchlyContact>,
    searchQuery: String,
    isSyncing: Boolean,
    syncMessage: String?,
    onFollow: (SketchlyContact) -> Unit,
    onSyncContacts: () -> Unit,
    isSearchMode: Boolean = false,
    isSearching: Boolean = false,
    pendingUserIds: Set<String> = emptySet(),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        if (isSearchMode) {
            // ── Search mode: show Firestore username search results ─────────────
            when {
                isSearching -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = ButtonGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                contacts.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔍", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No users found for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try the exact username.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                else -> {
                    item { SectionHeader(text = "SEARCH RESULTS") }
                    items(contacts, key = { it.userId }) { contact ->
                        SuggestedContactRow(
                            contact = contact,
                            isPendingOverride = contact.userId in pendingUserIds,
                            onFollow = { onFollow(contact) },
                        )
                    }
                }
            }
        } else {
            // ── Normal mode: contact-sync list ────────────────────────────────
            if (contacts.isNotEmpty()) {
                item { SectionHeader(text = "PEOPLE YOU MAY KNOW") }
                items(contacts, key = { it.userId }) { contact ->
                    SuggestedContactRow(
                        contact = contact,
                        isPendingOverride = contact.userId in pendingUserIds,
                        onFollow = { onFollow(contact) },
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Sync Contacts card always shown in normal mode
            item { SectionHeader(text = "SYNC CONTACTS") }
            item {
                SyncContactsCard(
                    isSyncing = isSyncing,
                    syncMessage = syncMessage,
                    onSync = onSyncContacts,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Empty state
            if (contacts.isEmpty() && syncMessage == null && !isSyncing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sync your contacts to find\nfriends on Sketchly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Requests tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestsTab(
    contacts: List<SketchlyContact>,
    onAccept: (SketchlyContact) -> Unit,
    onDecline: (SketchlyContact) -> Unit,
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\u2709\uFE0F", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No pending requests",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AppNameColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "When someone follows you,\nthey'll appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { SectionHeader(text = "FOLLOW REQUESTS") }
            items(contacts, key = { it.userId }) { contact ->
                RequestContactRow(
                    contact = contact,
                    onAccept = { onAccept(contact) },
                    onDecline = { onDecline(contact) },
                )
                RowDivider()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Connected tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectedTab(
    contacts: List<SketchlyContact>,
    searchQuery: String,
    isSearchMode: Boolean = false,
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83E\uDD1D", fontSize = 42.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isSearchMode) "No connections match \"$searchQuery\""
                    else "No connections yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AppNameColor,
                    textAlign = TextAlign.Center,
                )
                if (!isSearchMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Accept follow requests to start\nsending Scribbles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                SectionHeader(
                    text = if (isSearchMode) "MATCHES  \u00B7  ${contacts.size}"
                    else "YOUR CONNECTIONS  \u00B7  ${contacts.size}"
                )
            }
            items(contacts, key = { it.userId }) { contact ->
                ConnectedContactRow(contact = contact)
                RowDivider()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row — Suggested contact (with Follow / Pending button)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestedContactRow(
    contact: SketchlyContact,
    onFollow: () -> Unit,
    isPendingOverride: Boolean = false,
) {
    val isPending = isPendingOverride || contact.connectionStatus == ConnectionStatus.PENDING_SENT
    val isConnected = contact.connectionStatus == ConnectionStatus.CONNECTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = contact.displayName, size = 52)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = AppNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                if (contact.username.isNotBlank()) append("@${contact.username}")
                if (contact.source == com.jrprofessor.sketchly.data.model.ContactSource.CONTACT_SYNC) {
                    append(" \u00B7 In your contacts")
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))

        // Button: Follow / Pending / Connected
        // [FEATURE FLAGGED]
        // When ENABLE_CONNECTION_REQUESTS = true  → shows Follow / Pending / Connected buttons.
        // When ENABLE_CONNECTION_REQUESTS = false → connections are created automatically on
        //   first scribble (Cloud Function auto-connect path). The Follow button is hidden;
        //   only the "✓ Connected" chip is shown for users who are already auto-connected.
        if (com.jrprofessor.sketchly.utils.FeatureFlags.ENABLE_CONNECTION_REQUESTS) {
            when {
                isConnected -> Surface(
                    shape = PillShape,
                    color = ButtonGold.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "\u2713 Connected",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = ButtonGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                else -> Surface(
                    shape = PillShape,
                    color = if (isPending) ButtonGold.copy(alpha = 0.35f) else ButtonGold,
                    shadowElevation = if (isPending) 0.dp else 2.dp,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable(
                            enabled = !isPending,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onFollow() },
                ) {
                    Text(
                        text = if (isPending) "Pending \u2713" else "Follow",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
        } else if (isConnected) {
            // Auto-connect mode: only show the Connected chip, no Follow button
            Surface(
                shape = PillShape,
                color = ButtonGold.copy(alpha = 0.12f),
            ) {
                Text(
                    text = "\u2713 Connected",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    ),
                    color = ButtonGold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row — Incoming request (Accept / Decline)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestContactRow(
    contact: SketchlyContact,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = contact.displayName, size = 52)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = AppNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.username.isNotBlank()) {
                Text(
                    text = "@${contact.username}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
            Text(
                text = "Wants to follow you",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextMuted.copy(alpha = 0.7f),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onAccept,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ButtonGold.copy(alpha = 0.15f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Accept",
                tint = ButtonGold,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
            onClick = onDecline,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(TextMuted.copy(alpha = 0.1f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Decline",
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row — Connected contact (no action)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConnectedContactRow(contact: SketchlyContact) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = contact.displayName, size = 52)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = AppNameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contact.username.isNotBlank()) {
                Text(
                    text = "@${contact.username}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
        }
        Surface(
            shape = PillShape,
            color = ButtonGold.copy(alpha = 0.12f),
        ) {
            Text(
                text = "\u2713 Connected",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                ),
                color = ButtonGold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sync Contacts card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SyncContactsCard(
    isSyncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TextEditorBgColor,
        shadowElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = TextEditorBorderColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "Find more friends",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = AppNameColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sync contacts to see everyone from your phone who\u2019s on Sketchly.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = TextMuted,
            )
            if (syncMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = syncMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = ButtonGold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = PillShape,
                color = ButtonGold,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(PillShape)
                    .clickable(
                        enabled = !isSyncing,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSync() },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isSyncing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Syncing\u2026",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = Color.White,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync Contacts",
                                style = MaterialTheme.typography.titleSmall.copy(
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = TextEditorBgColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.border(
            width = 1.dp,
            color = TextEditorBorderColor.copy(alpha = 0.8f),
            shape = RoundedCornerShape(50),
        ),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search by @username",
                    color = TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                when {
                    isSearching -> CircularProgressIndicator(
                        color = ButtonGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = AppNameColor,
                focusedTextColor = AppNameColor,
                unfocusedTextColor = AppNameColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactAvatar(name: String, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(avatarColorFor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsFor(name),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.29f).sp,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
        ),
        color = TextMuted.copy(alpha = 0.75f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 80.dp, end = 16.dp)
            .height(0.8.dp)
            .background(TextEditorBorderColor.copy(alpha = 0.5f)),
    )
}
