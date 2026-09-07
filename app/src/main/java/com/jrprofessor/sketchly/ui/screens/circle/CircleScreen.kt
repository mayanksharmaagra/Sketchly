package com.jrprofessor.sketchly.ui.screens.circle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.jrprofessor.sketchly.data.local.ContactEntity
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
// Avatar colour palette
// ─────────────────────────────────────────────────────────────────────────────
private val AVATAR_COLORS = listOf(
    Color(0xFF3D405B),
    Color(0xFF70A18A),
    Color(0xFFE07A5F),
    Color(0xFFBF4E6D),
    Color(0xFF4A6FA5),
    Color(0xFF8B6914),
    Color(0xFF6B4E71),
    Color(0xFF2A9D8F),
)

private fun avatarColorFor(name: String): Color =
    AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]

// ─────────────────────────────────────────────────────────────────────────────
// Circle Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CircleScreen(
    viewModel: CircleViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    CircleScreenContent(
        contacts = contacts,
        uiState = uiState,
        onDeleteContact = viewModel::deleteContact,
        onOpenAddDialog = viewModel::openAddDialog,
        onCloseAddDialog = viewModel::closeAddDialog,
        onNameChanged = viewModel::onNameChanged,
        onEmailOrPhoneChanged = viewModel::onEmailOrPhoneChanged,
        onAddContact = viewModel::addContact,
    )
}

@Composable
fun CircleScreenContent(
    contacts: List<ContactEntity>,
    uiState: CircleUiState,
    onDeleteContact: (ContactEntity) -> Unit,
    onOpenAddDialog: () -> Unit,
    onCloseAddDialog: () -> Unit,
    onNameChanged: (String) -> Unit,
    onEmailOrPhoneChanged: (String) -> Unit,
    onAddContact: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            CircleHeader(contactCount = contacts.size)

            // ── Search bar ───────────────────────────────────────────────────
            if (contacts.isNotEmpty()) {
                CircleSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── List / Empty state ───────────────────────────────────────────
            if (contacts.isEmpty()) {
                CircleEmptyState(
                    onAddClick = onOpenAddDialog,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No contacts match\n\"$searchQuery\"",
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
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        CircleContactRow(
                            contact = contact,
                            onDelete = { onDeleteContact(contact) },
                        )
                        HorizontalDivider(
                            color = TextEditorBorderColor.copy(alpha = 0.45f),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                        )
                    }
                }
            }
        }

        // ── FAB — Add Friend (V1: HIDDEN — manual contact add removed) ─────
        // Box(
        //     modifier = Modifier
        //         .align(Alignment.BottomEnd)
        //         .navigationBarsPadding()
        //         .padding(24.dp),
        // ) {
        //     AddFab(onClick = onOpenAddDialog)
        // }

        // ── Add Friend bottom sheet (V1: HIDDEN) ──────────────────────────
        // AnimatedVisibility(
        //     visible = uiState.isAddDialogOpen,
        //     enter = fadeIn() + slideInVertically(
        //         spring(Spring.DampingRatioMediumBouncy),
        //         initialOffsetY = { it },
        //     ),
        //     exit = fadeOut() + slideOutVertically { it },
        // ) {
        //     AddFriendSheet(
        //         name = uiState.newContactName,
        //         onNameChange = onNameChanged,
        //         emailOrPhone = uiState.newContactEmailOrPhone,
        //         onEmailOrPhoneChange = onEmailOrPhoneChanged,
        //         isLoading = uiState.isLoading,
        //         errorMessage = uiState.errorMessage,
        //         onConfirm = onAddContact,
        //         onDismiss = onCloseAddDialog,
        //     )
        // }
    }
}

// ── Previews ──

@Preview(name = "Circle — Empty", showBackground = true)
@Composable
private fun CircleScreenEmptyPreview() {
    SketchlyTheme {
        CircleScreenContent(
            contacts = emptyList(),
            uiState = CircleUiState(),
            onDeleteContact = {},
            onOpenAddDialog = {},
            onCloseAddDialog = {},
            onNameChanged = {},
            onEmailOrPhoneChanged = {},
            onAddContact = {},
        )
    }
}

// V1: CircleScreenWithContactsPreview hidden — uses ContactEntity (Room type hidden)
// @Preview(name = "Circle — With Contacts", showBackground = true)
// @Composable
// private fun CircleScreenWithContactsPreview() {
//     val fakeContacts = listOf(
//         ContactEntity(id = "1", displayName = "Alice Wonderland", email = "alice@example.com",
//             phoneNumber = "", isOnSketchly = true, avatarUrl = "", source = "manual",
//             createdAt = System.currentTimeMillis(), userId = ""),
//         ContactEntity(id = "2", displayName = "Bob Builder", email = "", phoneNumber = "+1 555 000",
//             isOnSketchly = false, avatarUrl = "", source = "manual",
//             createdAt = System.currentTimeMillis(), userId = ""),
//     )
//     SketchlyTheme {
//         CircleScreenContent(contacts = fakeContacts, uiState = CircleUiState(),
//             onDeleteContact = {}, onOpenAddDialog = {}, onCloseAddDialog = {},
//             onNameChanged = {}, onEmailOrPhoneChanged = {}, onAddContact = {})
//     }
// }


// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleHeader(contactCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Your Circle",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
            ),
            color = AppNameColor,
        )
        Text(
            text = when (contactCount) {
                0 -> "No friends yet"
                1 -> "1 friend"
                else -> "$contactCount friends"
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextMuted,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleSearchBar(
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
                    "Search your circle...",
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
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
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
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleEmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            // Illustration — nested circles
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(AppNameColor.copy(alpha = 0.07f)),
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AppNameColor.copy(alpha = 0.10f)),
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AppNameColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = AppNameColor.copy(alpha = 0.70f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your Circle is empty",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                ),
                color = AppNameColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Add friends to start sending\nhand-drawn Scribbles.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                shape = PillShape,
                color = ButtonGold,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onAddClick() },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add First Friend",
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

// ─────────────────────────────────────────────────────────────────────────────
// Contact Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CircleContactRow(
    contact: ContactEntity,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val avatarColor = avatarColorFor(contact.displayName)
    val initials = contact.displayName
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
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

        // Name + detail
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    color = AppNameColor,
                )
                if (contact.isOnSketchly) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = PillShape,
                        color = ButtonGold.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "✓ Sketchly",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = ButtonGold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            val detail = contact.email.ifBlank { contact.phoneNumber }
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )
            }
        }

        // Delete — tap to confirm
        if (!showDeleteConfirm) {
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            // Inline confirm
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Remove?",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Yes",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFE07A5F),
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onDelete()
                            showDeleteConfirm = false
                        }
                        .padding(4.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "No",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AppNameColor,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showDeleteConfirm = false }
                        .padding(4.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(AppNameColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add Friend",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Friend — bottom sheet style panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddFriendSheet(
    name: String,
    onNameChange: (String) -> Unit,
    emailOrPhone: String,
    onEmailOrPhoneChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppNameColor.copy(alpha = 0.40f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                focusManager.clearFocus()
                onDismiss()
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = PaperIvory,
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { /* consume clicks to prevent dismiss */ }
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(TextEditorBorderColor.copy(alpha = 0.5f))
                        .align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add a Friend",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                        ),
                        color = AppNameColor,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Enter their name and email or phone to connect.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = TextMuted,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name field
                SheetTextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Friend's full name",
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Email/phone field
                SheetTextField(
                    value = emailOrPhone,
                    onValueChange = onEmailOrPhoneChange,
                    placeholder = "Email or phone number",
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Email,
                    onDone = {
                        focusManager.clearFocus()
                        if (!isLoading) onConfirm()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Error
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE07A5F),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Add button
                Surface(
                    shape = PillShape,
                    color = if (name.isNotBlank()) ButtonGold else ButtonGold.copy(alpha = 0.4f),
                    shadowElevation = if (name.isNotBlank()) 4.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(PillShape)
                        .clickable(
                            enabled = name.isNotBlank() && !isLoading,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            focusManager.clearFocus()
                            onConfirm()
                        },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                text = "Add to Circle",
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

// ─────────────────────────────────────────────────────────────────────────────
// Shared text field for the sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TextEditorBgColor,
        shadowElevation = 0.dp,
        modifier = modifier.border(
            width = 1.dp,
            color = if (value.isNotEmpty()) AppNameColor.copy(alpha = 0.35f)
            else TextEditorBorderColor.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
        ),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = TextMuted.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onDone?.invoke() },
            ),
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
