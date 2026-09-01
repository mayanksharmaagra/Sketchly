package com.jrprofessor.sketchly.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.jrprofessor.sketchly.utils.PhoneVisualTransformation

// ─────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun AuthScreenSignInPhonePreview() {
    SketchlyTheme {
        AuthContent(
            uiState = AuthUiState(isSignUp = false, isPhoneMode = true),
            onDisplayNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onPhoneNumberChanged = {},
            toggleAuthMode = {},
            onSignUpChanged = {},
            onSubmit = {},
            onVerifyOtp = {},
            onOtpDigitChanged = { _, _ -> },
            onResendOtp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenSignInEmailPreview() {
    SketchlyTheme {
        AuthContent(
            uiState = AuthUiState(isSignUp = false, isPhoneMode = false),
            onDisplayNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onPhoneNumberChanged = {},
            toggleAuthMode = {},
            onSignUpChanged = {},
            onSubmit = {},
            onVerifyOtp = {},
            onOtpDigitChanged = { _, _ -> },
            onResendOtp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenSignUpEmailPreview() {
    SketchlyTheme {
        AuthContent(
            uiState = AuthUiState(isSignUp = true, isPhoneMode = false),
            onDisplayNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onPhoneNumberChanged = {},
            toggleAuthMode = {},
            onSignUpChanged = {},
            onSubmit = {},
            onVerifyOtp = {},
            onOtpDigitChanged = { _, _ -> },
            onResendOtp = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenOtpPreview() {
    SketchlyTheme {
        AuthContent(
            uiState = AuthUiState(
                isPhoneMode = true,
                isOtpSent = true,
                phoneNumber = "9876543210",
                otpDigits = listOf("4", "2", "8", "1", "", ""),
            ),
            onDisplayNameChanged = {},
            onEmailChanged = {},
            onPasswordChanged = {},
            onPhoneNumberChanged = {},
            toggleAuthMode = {},
            onSignUpChanged = {},
            onSubmit = {},
            onVerifyOtp = {},
            onOtpDigitChanged = { _, _ -> },
            onResendOtp = {},
        )
    }
}

// ─────────────────────────────────────────────
// Screen (hooked to ViewModel)
// ─────────────────────────────────────────────

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    AuthContent(
        uiState = uiState,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
        toggleAuthMode = viewModel::toggleAuthMode,
        onSignUpChanged = viewModel::onSignUpChanged,
        onSubmit = {
            if (uiState.isPhoneMode) {
                // Phone mode → send SMS OTP first
                viewModel.sendPhoneOtp(activity!!) { /* OTP sent, state update shows OTP screen */ }
            } else {
                // Email mode → sign up creates account + sends email OTP; sign in goes direct
                viewModel.submitEmailAuth(
                    onSuccess = onAuthSuccess,
                    onNeedsOtp = { /* state update shows OTP screen */ },
                )
            }
        },
        onVerifyOtp = { viewModel.verifyOtp(onAuthSuccess) },
        onOtpDigitChanged = viewModel::onOtpDigitChanged,
        onResendOtp = {
            viewModel.resendOtp(activity = if (uiState.isPhoneMode) activity else null) {}
        },
        onSaveDisplayName = { viewModel.saveDisplayName(onAuthSuccess) },
    )
}

// ─────────────────────────────────────────────
// Stateless content shell
// ─────────────────────────────────────────────

@Composable
fun AuthContent(
    uiState: AuthUiState,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    toggleAuthMode: () -> Unit,
    onSignUpChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onVerifyOtp: () -> Unit,
    onOtpDigitChanged: (Int, String) -> Unit,
    onResendOtp: () -> Unit,
    onSaveDisplayName: () -> Unit = {},
) {
    // Derive a single integer key so AnimatedContent knows which step we're on
    val step = when {
        uiState.isNameEntry -> 2
        uiState.isOtpSent   -> 1
        else                -> 0
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                (slideOutHorizontally { -it } + fadeOut())
        },
        label = "authStepTransition",
    ) { currentStep ->
        when (currentStep) {
            2 -> {
                // ── Name-entry screen (after phone OTP) ──
                NameEntryScreen(
                    uiState = uiState,
                    onDisplayNameChanged = onDisplayNameChanged,
                    onContinue = onSaveDisplayName,
                )
            }
            1 -> {
                // ── OTP verification screen ──
                OtpVerificationScreen(
                    uiState = uiState,
                    onDigitChanged = onOtpDigitChanged,
                    onVerify = onVerifyOtp,
                    onResend = onResendOtp,
                )
            }
            else -> {
                // ── Auth form (phone / email) ──
                AuthFormContent(
                    uiState = uiState,
                    onDisplayNameChanged = onDisplayNameChanged,
                    onEmailChanged = onEmailChanged,
                    onPasswordChanged = onPasswordChanged,
                    onPhoneNumberChanged = onPhoneNumberChanged,
                    toggleAuthMode = toggleAuthMode,
                    onSignUpChanged = onSignUpChanged,
                    onSubmit = onSubmit,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Auth form (phone entry / email+password)
// ─────────────────────────────────────────────

@Composable
private fun AuthFormContent(
    uiState: AuthUiState,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    toggleAuthMode: () -> Unit,
    onSignUpChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.isPhoneMode) {
                        PhoneNumberView(
                            uiState.phoneNumber, uiState.countryCode, onPhoneNumberChanged
                        )
                    } else {
                        EmailView(
                            isSignUp = uiState.isSignUp,
                            email = uiState.email,
                            displayName = uiState.displayName,
                            password = uiState.password,
                            onDisplayNameChanged = onDisplayNameChanged,
                            onEmailChanged = onEmailChanged,
                            onPasswordChanged = onPasswordChanged,
                            onSignUpChanged = onSignUpChanged,
                            onSubmit = onSubmit,
                            focusManager = focusManager,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Error Message
                    if (uiState.errorMessage != null) {
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }

                    // Submit / Continue button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit()
                        },
                        enabled = !uiState.isLoading,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonGold,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Text(
                                text = when {
                                    uiState.isPhoneMode -> "Continue"
                                    uiState.isSignUp -> "Create Account"
                                    else -> "Sign In"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Toggle
                    TextButton(onClick = toggleAuthMode) {
                        Text(
                            text = if (uiState.isPhoneMode) "Use email instead"
                            else "Use phone number instead",
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = TextDecoration.Underline,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Email form (Sign Up / Sign In tabs)
// ─────────────────────────────────────────────

@Composable
fun EmailView(
    isSignUp: Boolean,
    email: String,
    displayName: String,
    password: String,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignUpChanged: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    focusManager: FocusManager,
) {
    val tabs = listOf("Sign Up", "Log In")
    var selectedIndex by remember(isSignUp) {
        if (isSignUp) mutableIntStateOf(0) else mutableIntStateOf(1)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Continue with email",
            style = MaterialTheme.typography.headlineMedium,
            color = AppNameColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Up / Log In tab switcher
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TextEditorBgColor, RoundedCornerShape(20.dp))
                .border(1.dp, TextEditorBorderColor, RoundedCornerShape(20.dp))
                .padding(4.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                val tabWidth = maxWidth / tabs.size
                val indicatorOffset by animateDpAsState(
                    targetValue = if (selectedIndex == 0) 0.dp else tabWidth,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "indicatorOffset",
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppNameColor),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (selected) Color.White else TextMuted,
                        label = "textColor",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedIndex = index
                                onSignUpChanged(index == 0)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSignUp)
                "New here? Create an account to get started."
            else
                "Welcome back — enter your email and password.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Display Name (Sign Up only)
        AnimatedVisibility(visible = isSignUp) {
            Column {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChanged,
                    placeholder = {
                        Text(
                            "Name",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        letterSpacing = 2.sp,
                        color = TextColor,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ButtonGold,
                        unfocusedBorderColor = TextEditorBorderColor,
                        focusedContainerColor = TextEditorBgColor,
                        unfocusedContainerColor = TextEditorBgColor,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            placeholder = {
                Text(
                    "Email",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 2.sp,
                color = TextColor,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ButtonGold,
                unfocusedBorderColor = TextEditorBorderColor,
                focusedContainerColor = TextEditorBgColor,
                unfocusedContainerColor = TextEditorBgColor,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            placeholder = {
                Text(
                    "Password",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                letterSpacing = 2.sp,
                color = TextColor,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ButtonGold,
                unfocusedBorderColor = TextEditorBorderColor,
                focusedContainerColor = TextEditorBgColor,
                unfocusedContainerColor = TextEditorBgColor,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
        )
    }
}

// ─────────────────────────────────────────────
// Phone number form
// ─────────────────────────────────────────────

@Composable
fun PhoneNumberView(
    phoneNumber: String,
    countryCode: String,
    onPhoneNumberChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {

        Text(
            text = "What's your number?",
            style = MaterialTheme.typography.headlineMedium,
            color = AppNameColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "We'll text you a code to verify it's you.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                    onPhoneNumberChanged(it)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            visualTransformation = PhoneVisualTransformation(
                countryCode = countryCode,
                placeholderColor = TextColor.copy(alpha = 0.5f),
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                color = TextColor,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ButtonGold,
                unfocusedBorderColor = TextEditorBorderColor,
                focusedContainerColor = TextEditorBgColor,
                unfocusedContainerColor = TextEditorBgColor,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            singleLine = true,
        )
    }
}

// ─────────────────────────────────────────────
// Name Entry Screen (shown after phone OTP)
// ─────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
fun NameEntryScreenPreview() {
    SketchlyTheme {
        NameEntryScreen(
            uiState = AuthUiState(displayName = "Alex Rivera", isNameEntry = true),
            onDisplayNameChanged = {},
            onContinue = {},
        )
    }
}

/**
 * Shown to phone-auth users immediately after OTP verification.
 * Asks for their display name before navigating to the Draw screen.
 */
@Composable
fun NameEntryScreen(
    uiState: AuthUiState,
    onDisplayNameChanged: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Title
            Text(
                text = "What's your name?",
                style = MaterialTheme.typography.headlineMedium,
                color = AppNameColor,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle
            Text(
                text = "This is how you'll appear to friends on Sketchly.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Name field — uses same style as the rest of the auth form
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = onDisplayNameChanged,
                placeholder = {
                    Text(
                        "Name",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onContinue()
                    },
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    letterSpacing = 2.sp,
                    color = TextColor,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonGold,
                    unfocusedBorderColor = TextEditorBorderColor,
                    focusedContainerColor = TextEditorBgColor,
                    unfocusedContainerColor = TextEditorBgColor,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continue button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onContinue()
                },
                enabled = !uiState.isLoading && uiState.displayName.isNotBlank(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonGold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
