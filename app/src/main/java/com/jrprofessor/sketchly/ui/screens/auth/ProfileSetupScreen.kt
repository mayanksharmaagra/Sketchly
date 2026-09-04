package com.jrprofessor.sketchly.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted

// ─────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ProfileSetupPreviewIdle() {
    SketchlyTheme {
        ProfileSetupContent(
            uiState = AuthUiState(displayName = "", username = ""),
            onDisplayNameChanged = {},
            onUsernameChanged = {},
            onContinue = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ProfileSetupPreviewAvailable() {
    SketchlyTheme {
        ProfileSetupContent(
            uiState = AuthUiState(
                displayName = "Alex Rivera",
                username = "alex_r",
                usernameStatus = UsernameStatus.AVAILABLE,
            ),
            onDisplayNameChanged = {},
            onUsernameChanged = {},
            onContinue = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ProfileSetupPreviewTaken() {
    SketchlyTheme {
        ProfileSetupContent(
            uiState = AuthUiState(
                displayName = "Alex Rivera",
                username = "sketchly",
                usernameStatus = UsernameStatus.TAKEN,
            ),
            onDisplayNameChanged = {},
            onUsernameChanged = {},
            onContinue = {},
        )
    }
}

// ─────────────────────────────────────────────
// Screen entry — hooked to shared AuthViewModel
// ─────────────────────────────────────────────

/**
 * Shown to new phone-auth users after OTP verification.
 * Collects Full Name + unique Username before navigating to ContactPermissionScreen.
 *
 * Flow: Phone OTP verified (new user) → ProfileSetupScreen → ContactPermissionScreen
 *
 * SRS FR-1.3: new users must enter Full Name and unique Username before proceeding.
 * SRS FR-1.4: username 3–20 chars, alphanumeric + underscore, stored lowercase.
 */
@Composable
fun ProfileSetupScreen(
    onContinue: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileSetupContent(
        uiState = uiState,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onContinue = { viewModel.saveUserProfile(onContinue) },
    )
}

// ─────────────────────────────────────────────
// Stateless content (previewable)
// ─────────────────────────────────────────────

@Composable
fun ProfileSetupContent(
    uiState: AuthUiState,
    onDisplayNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Staggered entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Continue button enabled only when name is filled + username is available
    val canContinue = uiState.displayName.trim().isNotBlank() &&
        uiState.usernameStatus == UsernameStatus.AVAILABLE &&
        !uiState.isLoading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // ── Header ───────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Set up your profile",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppNameColor,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "How friends will find and recognise you on Sketchly.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 24.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Full Name Field ───────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FieldLabel(text = "Full Name")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.displayName,
                        onValueChange = onDisplayNameChanged,
                        placeholder = {
                            Text(
                                "e.g. Alex Rivera",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextMuted,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
                        colors = sketchlyTextFieldColors(),
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Username Field ────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FieldLabel(text = "Username")
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChanged,
                        placeholder = {
                            Text(
                                "e.g. alex_r",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextMuted,
                            )
                        },
                        prefix = {
                            Text(
                                text = "@",
                                style = MaterialTheme.typography.bodyLarge,
                                color = ButtonGold,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        trailingIcon = {
                            UsernameStatusIcon(status = uiState.usernameStatus)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (canContinue) onContinue()
                            },
                        ),
                        isError = uiState.usernameStatus == UsernameStatus.TAKEN ||
                            uiState.usernameStatus == UsernameStatus.INVALID,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextColor),
                        colors = sketchlyTextFieldColors(
                            errorBorderColor = MaterialTheme.colorScheme.error,
                        ),
                        singleLine = true,
                    )

                    // Inline validation hint
                    Spacer(modifier = Modifier.height(6.dp))
                    UsernameHintText(status = uiState.usernameStatus, username = uiState.username)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Global error ─────────────────────────────────
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Continue Button ───────────────────────────────
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onContinue()
                },
                enabled = canContinue,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonGold,
                    disabledContainerColor = ButtonGold.copy(alpha = 0.38f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Footer note ───────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextMuted)) { append("Username cannot be changed in V1. ") }
                    withStyle(SpanStyle(color = TextMuted.copy(alpha = 0.6f))) {
                        append("Choose wisely!")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    )
}

/**
 * Trailing icon shown inside the username text field.
 * Reflects current [UsernameStatus] with animated feedback.
 */
@Composable
private fun UsernameStatusIcon(status: UsernameStatus) {
    val alpha by animateFloatAsState(
        targetValue = when (status) {
            UsernameStatus.IDLE -> 0f
            else -> 1f
        },
        label = "iconAlpha",
    )
    when (status) {
        UsernameStatus.CHECKING -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = ButtonGold,
            strokeWidth = 2.dp,
        )
        UsernameStatus.AVAILABLE -> Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = "Username available",
            tint = Color(0xFF4CAF50).copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        UsernameStatus.TAKEN, UsernameStatus.INVALID -> Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = "Username unavailable",
            tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        else -> {}
    }
}

/**
 * Inline hint text shown below the username field.
 */
@Composable
private fun UsernameHintText(status: UsernameStatus, username: String) {
    val (text, color) = when (status) {
        UsernameStatus.IDLE -> Pair(
            "3–20 characters. Letters, numbers, and _ only.",
            TextMuted.copy(alpha = 0.7f),
        )
        UsernameStatus.CHECKING -> Pair("Checking availability…", TextMuted)
        UsernameStatus.AVAILABLE -> Pair("@$username is available!", Color(0xFF4CAF50))
        UsernameStatus.TAKEN -> Pair("@$username is already taken.", MaterialTheme.colorScheme.error)
        UsernameStatus.INVALID -> Pair(
            "Letters, numbers, and underscores only (3–20 chars).",
            MaterialTheme.colorScheme.error,
        )
    }
    AnimatedVisibility(visible = text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/**
 * Shared OutlinedTextField color scheme used throughout auth screens.
 */
@Composable
private fun sketchlyTextFieldColors(
    errorBorderColor: Color = MaterialTheme.colorScheme.error,
) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ButtonGold,
    unfocusedBorderColor = TextEditorBorderColor,
    focusedContainerColor = TextEditorBgColor,
    unfocusedContainerColor = TextEditorBgColor,
    errorBorderColor = errorBorderColor,
    errorContainerColor = TextEditorBgColor,
    cursorColor = MaterialTheme.colorScheme.primary,
)
