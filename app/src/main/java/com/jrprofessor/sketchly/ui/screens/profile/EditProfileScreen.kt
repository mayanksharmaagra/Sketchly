package com.jrprofessor.sketchly.ui.screens.profile

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jrprofessor.sketchly.ui.theme.*
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Edit Profile")
@Composable
private fun EditProfileScreenPreview() {
    SketchlyTheme {
        EditProfileContent(
            uiState       = EditProfileUiState(
                isLoading   = false,
                displayName = "Alex Rivera",
                username    = "alex_rivera",
                phoneNumber = "+1 •••• 4821",
                email       = "",
                avatarUrl   = null,
            ),
            onBack             = {},
            onSave             = {},
            onDisplayNameChange = {},
            onPickFromGallery  = {},
            onPickFromCamera   = {},
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen entry-point (Hilt-wired)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pop back automatically on successful save
    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            viewModel.onSaveEventConsumed()
            onBack()
        }
    }

    // ── Camera URI helper ─────────────────────────────────────────────────────
    // We need a content URI ahead of time so the camera can write into it.
    val cameraImageUri = remember {
        val file = File(context.cacheDir, "sketchly_avatar_capture.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    // ── Activity result launchers ─────────────────────────────────────────────

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImagePicked(it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) viewModel.onImagePicked(cameraImageUri)
    }

    // ── Permission launcher for camera (required on API < 29 for external files) ──
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(cameraImageUri)
    }

    EditProfileContent(
        uiState            = uiState,
        onBack             = onBack,
        onSave             = { viewModel.onSave() },
        onDisplayNameChange = { viewModel.onDisplayNameChange(it) },
        onPickFromGallery  = { galleryLauncher.launch("image/*") },
        onPickFromCamera   = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // No CAMERA permission required for API 29+ with FileProvider
                cameraLauncher.launch(cameraImageUri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stateless content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditProfileContent(
    uiState: EditProfileUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFromCamera: () -> Unit,
) {
    var showImagePickerSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top Bar ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text = "Edit Profile",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = AppNameColor,
                )

                // Top-right "Save" text button
                TextButton(
                    onClick = onSave,
                    enabled = !uiState.isSaving && !uiState.isLoading,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = ButtonGold,
                        )
                    } else {
                        Text(
                            text = "Save",
                            color = ButtonGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Avatar ────────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.BottomEnd) {
                // Avatar circle — shows image if available, else initials
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(AppNameColor.copy(alpha = 0.15f))
                        .border(2.dp, TextEditorBorderColor, CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showImagePickerSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val imageSource = uiState.pendingImageUri ?: uiState.avatarUrl
                    if (imageSource != null) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        if (uiState.isUploadingImage) {
                            CircularProgressIndicator(
                                color = ButtonGold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Text(
                                text = uiState.initials,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppNameColor,
                            )
                        }
                    }
                }

                // Camera badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(ButtonGold)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showImagePickerSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "Change photo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to change photo",
                fontSize = 12.sp,
                color = TextMuted,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Form fields ───────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {

                // Full Name
                ProfileField(label = "FULL NAME") {
                    OutlinedTextField(
                        value = uiState.displayName,
                        onValueChange = onDisplayNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlinedTextFieldColors(),
                        textStyle = LocalTextStyle.current.copy(
                            color = AppNameColor,
                            fontSize = 16.sp,
                        ),
                        isError = uiState.errorMessage != null &&
                                uiState.displayName.isBlank(),
                    )
                }

                // Username (read-only)
                ProfileField(
                    label = "USERNAME",
                    hint = "Username cannot be changed after signup (V2 feature)",
                ) {
                    OutlinedTextField(
                        value = "@${uiState.username}",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlinedTextFieldColors(disabled = true),
                        textStyle = LocalTextStyle.current.copy(
                            color = TextMuted,
                            fontSize = 16.sp,
                        ),
                    )
                }

                // Phone number (read-only + Verified chip)
                ProfileField(
                    label = "PHONE NUMBER",
                    hint = "Phone number cannot be changed",
                ) {
                    val maskedPhone = uiState.phoneNumber.let { phone ->
                        if (phone.length >= 5) {
                            val country = phone.take(2)
                            val last4   = phone.takeLast(4)
                            "$country •••• $last4"
                        } else phone
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = maskedPhone,
                            onValueChange = {},
                            modifier = Modifier.weight(1f),
                            readOnly = true,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = outlinedTextFieldColors(disabled = true),
                            textStyle = LocalTextStyle.current.copy(
                                color = TextMuted,
                                fontSize = 16.sp,
                            ),
                        )
                        // Verified badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TextEditorBgColor,
                            border = ButtonDefaults.outlinedButtonBorder,
                        ) {
                            Text(
                                text = "Verified ✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppNameColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // Email — V2, read-only stub
                ProfileField(
                    label = null,
                    labelSuffix = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "EMAIL ADDRESS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted,
                                letterSpacing = 1.2.sp,
                            )
                            // V2 chip
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ButtonGold,
                            ) {
                                Text(
                                    text = "V2",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    },
                    hint = "Add email in V2 to enable email login & search",
                ) {
                    OutlinedTextField(
                        value = uiState.email.ifBlank { "Not added yet" },
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = outlinedTextFieldColors(disabled = true),
                        textStyle = LocalTextStyle.current.copy(
                            color = if (uiState.email.isBlank()) TextMuted.copy(alpha = 0.6f)
                                    else TextMuted,
                            fontSize = 16.sp,
                        ),
                        trailingIcon = {
                            if (uiState.email.isBlank()) {
                                Text(
                                    text = "+ Add email",
                                    color = ButtonGold,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Save Changes button ───────────────────────────────────────────
            Button(
                onClick = onSave,
                enabled = !uiState.isSaving && !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonGold,
                    disabledContainerColor = ButtonGold.copy(alpha = 0.5f),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = "Save Changes",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            // Error message
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = Color(0xFFBA1A1A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        // ── Loading overlay ───────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(BgColor),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = ButtonGold)
            }
        }
    }

    // ── Image picker bottom sheet ─────────────────────────────────────────────
    if (showImagePickerSheet) {
        ImagePickerSheet(
            onDismiss       = { showImagePickerSheet = false },
            onPickGallery   = { showImagePickerSheet = false; onPickFromGallery() },
            onPickCamera    = { showImagePickerSheet = false; onPickFromCamera() },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Image picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePickerSheet(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = TextEditorBgColor,
        scrimColor       = Color.Black.copy(alpha = 0.4f),
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = "Change Profile Photo",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = AppNameColor,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Gallery option
            SheetOption(
                icon    = Icons.Outlined.Image,
                label   = "Choose from Gallery",
                onClick = onPickGallery,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Camera option
            SheetOption(
                icon    = Icons.Outlined.CameraAlt,
                label   = "Take Photo",
                onClick = onPickCamera,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cancel
            TextButton(
                onClick  = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgColor)
            .border(1.dp, TextEditorBorderColor, RoundedCornerShape(16.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AppNameColor, modifier = Modifier.size(22.dp))
        Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = AppNameColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileField(
    label: String?,
    hint: String? = null,
    labelSuffix: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (labelSuffix != null) {
            labelSuffix()
        } else if (label != null) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.2.sp,
            )
        }
        content()
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 11.sp,
                color = TextMuted.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun outlinedTextFieldColors(disabled: Boolean = false): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = ButtonGold,
        unfocusedBorderColor = TextEditorBorderColor,
        disabledBorderColor  = TextEditorBorderColor.copy(alpha = if (disabled) 0.5f else 1f),
        focusedContainerColor   = TextEditorBgColor,
        unfocusedContainerColor = TextEditorBgColor,
        disabledContainerColor  = if (disabled) BgColor else TextEditorBgColor,
        cursorColor             = ButtonGold,
    )
