package com.jrprofessor.sketchly.ui.screens.auth

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Auth entry point: Phone number entry → OTP verification.
 *
 * V1 flow: Phone → OTP → [NavGraph routes to ProfileSetupScreen or Draw]
 *
 * [onNewUser]       — called after OTP success for brand-new accounts; NavGraph
 *                     navigates to ProfileSetupScreen.
 * [onReturningUser] — called after OTP success for returning users; NavGraph
 *                     navigates directly to Draw/Inbox.
 *
 * Email auth UI is HIDDEN in V1 but all email logic (EmailView, toggleAuthMode,
 * submitEmailAuth, etc.) is preserved in the codebase for V2 re-enablement.
 */
@Composable
fun AuthScreen(
    onNewUser: () -> Unit,
    onReturningUser: () -> Unit,
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
                // Phone mode → send SMS OTP. State update (isOtpSent=true) drives
                // the transition to OtpVerificationScreen automatically.
                viewModel.sendPhoneOtp(activity!!)
            } else {
                // Email mode (V2 — not reachable from V1 UI but logic kept)
                viewModel.submitEmailAuth(
                    onSuccess = onReturningUser,
                    onNeedsOtp = { /* state update shows OTP screen */ },
                )
            }
        },
        onVerifyOtp = {
            viewModel.verifyPhoneOtp(
                onNewUser = onNewUser,
                onReturningUser = onReturningUser,
            )
        },
        onOtpDigitChanged = viewModel::onOtpDigitChanged,
        onResendOtp = {
            // State update inside ViewModel drives OTP screen refresh
            viewModel.resendOtp(activity = if (uiState.isPhoneMode) activity else null)
        },
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
    // Kept for API compat with previews; not used in V1 direct flow
    onSaveDisplayName: () -> Unit = {},
) {
    // Two steps: 0 = phone entry, 1 = OTP
    val step = when {
        uiState.isOtpSent -> 1
        else              -> 0
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
                // ── Auth form (phone only in V1) ──
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
// Auth form (phone entry — V1 default)
// Email section kept but not rendered in V1 UI.
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

                    // V1: Phone only.
                    // V2: uncomment `else` branch below and restore the toggle button.
                    if (uiState.isPhoneMode) {
                        PhoneNumberView(
                            uiState.phoneNumber, uiState.countryCode, onPhoneNumberChanged
                        )
                    } else {
                        // V2: Email flow (not reachable in V1 — toggle button is hidden)
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

                    // ── V1: Email toggle button is HIDDEN.
                    // The toggle logic (toggleAuthMode) and EmailView composable are
                    // preserved below for V2 re-enablement. Simply un-comment this block:
                    //
                    // TextButton(onClick = toggleAuthMode) {
                    //     Text(
                    //         text = if (uiState.isPhoneMode) "Use email instead"
                    //         else "Use phone number instead",
                    //         style = MaterialTheme.typography.bodyMedium,
                    //         textDecoration = TextDecoration.Underline,
                    //         color = TextMuted,
                    //         fontWeight = FontWeight.Bold,
                    //     )
                    // }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Email form (Sign Up / Sign In tabs)
// V2 — not shown in V1 UI. Preserved for re-enablement.
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
