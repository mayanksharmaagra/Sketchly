package com.jrprofessor.sketchly.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jrprofessor.sketchly.data.local.ContactEntity
import com.jrprofessor.sketchly.ui.components.SketchlyTopBar
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Avatar colour palette — warm tones matching the app palette
// ─────────────────────────────────────────────────────────────────────────────
private val AVATAR_COLORS = listOf(
    Color(0xFF3D405B), // Deep charcoal
    Color(0xFF70A18A), // Sage green
    Color(0xFFE07A5F), // Terracotta
    Color(0xFFBF4E6D), // Rose
    Color(0xFF4A6FA5), // Denim blue
    Color(0xFF8B6914), // Ochre
    Color(0xFF6B4E71), // Plum
    Color(0xFF2A9D8F), // Teal
)

private fun avatarColorFor(name: String): Color =
    AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]

// ─────────────────────────────────────────────────────────────────────────────
// SendToScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SendToScreen(
    contacts: List<ContactEntity>,
    selectedContactIds: Set<String>,
    isSending: Boolean,
    showSentDialog: Boolean,
    sentToNames: List<String>,
    onContactToggle: (String) -> Unit,
    onSendConfirmed: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,   // "Continue" after sent dialog → AddWidget
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.email.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    val selectedCount = selectedContactIds.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            SendToTopBar(onBack = onBack)

            // ── Search field ─────────────────────────────────────────────────
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Contact list ─────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (contacts.isEmpty())
                            "No contacts yet.\nInvite friends to join Sketchly!"
                        else
                            "No contacts match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isSelected = contact.id in selectedContactIds,
                            onToggle = { onContactToggle(contact.id) },
                        )
                        HorizontalDivider(
                            color = TextEditorBorderColor.copy(alpha = 0.5f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(start = 76.dp),
                        )
                    }
                }
            }

            // ── Send button ──────────────────────────────────────────────────
            SendButton(
                selectedCount = selectedCount,
                isSending = isSending,
                onClick = onSendConfirmed,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }

        // ── Sent confirmation overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = showSentDialog,
            enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
            exit = fadeOut() + scaleOut(),
        ) {
            SentConfirmationDialog(
                recipientNames = sentToNames,
                onContinue = onContinue,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SendToTopBar(onBack: () -> Unit) {
    SketchlyTopBar(
        title  = "Send to",
        onBack = onBack,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = TextEditorBgColor,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    "Search contacts...",
                    color = TextMuted.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
// Contact Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(
    contact: ContactEntity,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onToggle() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar circle
        val avatarColor = avatarColorFor(contact.displayName)
        val initials = contact.displayName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Name + username/phone
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = AppNameColor,
            )
            val sub = contact.email.ifBlank { contact.phoneNumber }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
        }

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ButtonGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, TextEditorBorderColor, CircleShape),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Send Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SendButton(
    selectedCount: Int,
    isSending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = selectedCount > 0 && !isSending
    val label = when {
        isSending -> "Sending…"
        selectedCount == 0 -> "Select someone to send to"
        else -> "Send to $selectedCount  →"
    }

    Surface(
        shape = PillShape,
        color = if (enabled) AppNameColor else AppNameColor.copy(alpha = 0.35f),
        shadowElevation = if (enabled) 6.dp else 0.dp,
        modifier = modifier
            .height(56.dp)
            .clip(PillShape)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sent Confirmation — dimmed overlay with centred card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SentConfirmationDialog(
    recipientNames: List<String>,
    onContinue: () -> Unit,
) {
    val heading = when {
        recipientNames.isEmpty() -> "Scribble sent!"
        recipientNames.size == 1 -> "Sent to ${recipientNames.first()}!"
        else -> "Sent to ${recipientNames.first()} & ${recipientNames.size - 1} more!"
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppNameColor.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = PaperIvory,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .padding(vertical = 8.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                ) {
                    // Green check circle
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF70A18A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Heading
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                        ),
                        color = AppNameColor,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtitle
                    Text(
                        text = "Your Scribble is on its way.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Continue button
                    Surface(
                        shape = PillShape,
                        color = ButtonGold,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(PillShape)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { onContinue() },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Continue",
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
}

// ── Previews ──

@Preview(name = "SendTo — Empty Contacts", showBackground = true)
@Composable
private fun SendToScreenEmptyPreview() {
    SketchlyTheme {
        SendToScreen(
            contacts = emptyList(),
            selectedContactIds = emptySet(),
            isSending = false,
            showSentDialog = false,
            sentToNames = emptyList(),
            onContactToggle = {},
            onSendConfirmed = {},
            onBack = {},
            onContinue = {},
        )
    }
}

@Preview(name = "SendTo — With Selection", showBackground = true)
@Composable
private fun SendToScreenWithSelectionPreview() {
    val contacts = listOf(
        ContactEntity(
            id = "1",
            displayName = "Alice Wonderland",
            email = "alice@example.com",
            phoneNumber = "",
            isOnSketchly = true,
            avatarUrl = "",
            source = "manual",
            createdAt = System.currentTimeMillis(),
            userId = "",
        ),
        ContactEntity(
            id = "2",
            displayName = "Bob Builder",
            email = "",
            phoneNumber = "+1 555 000",
            isOnSketchly = false,
            avatarUrl = "",
            source = "manual",
            createdAt = System.currentTimeMillis(),
            userId = ""
        ),
    )
    SketchlyTheme {
        SendToScreen(
            contacts = contacts,
            selectedContactIds = setOf("1"),
            isSending = false,
            showSentDialog = false,
            sentToNames = emptyList(),
            onContactToggle = {},
            onSendConfirmed = {},
            onBack = {},
            onContinue = {},
        )
    }
}
