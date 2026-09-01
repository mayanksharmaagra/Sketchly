package com.jrprofessor.sketchly.ui.screens.auth

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.FrauncesFontFamily
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBgColor
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted

// ─────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun OtpScreenPhonePreview() {
    SketchlyTheme {
        OtpVerificationContent(
            destination = "+91 •••• 4821",
            otpDigits = listOf("4", "2", "8", "1", "", ""),
            isLoading = false,
            errorMessage = null,
            resendCountdown = 42,
            onDigitChanged = { _, _ -> },
            onVerify = {},
            onResend = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OtpScreenEmailPreview() {
    SketchlyTheme {
        OtpVerificationContent(
            destination = "m***@gmail.com",
            otpDigits = listOf("", "", "", "", "", ""),
            isLoading = false,
            errorMessage = "Incorrect code. Please try again.",
            resendCountdown = 0,
            onDigitChanged = { _, _ -> },
            onVerify = {},
            onResend = {},
        )
    }
}

// ─────────────────────────────────────────────
// Main composable (stateless, driven by ViewModel)
// ─────────────────────────────────────────────

@Composable
fun OtpVerificationScreen(
    uiState: AuthUiState,
    onDigitChanged: (index: Int, digit: String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    val destination = if (uiState.isPhoneMode) {
        val full = "${uiState.countryCode} ${uiState.phoneNumber}"
        maskPhone(uiState.countryCode, uiState.phoneNumber)
    } else {
        maskEmail(uiState.email)
    }

    OtpVerificationContent(
        destination = destination,
        otpDigits = uiState.otpDigits,
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        resendCountdown = uiState.resendCountdown,
        onDigitChanged = onDigitChanged,
        onVerify = onVerify,
        onResend = onResend,
    )
}

// ─────────────────────────────────────────────
// Pure UI
// ─────────────────────────────────────────────

@Composable
fun OtpVerificationContent(
    destination: String,
    otpDigits: List<String>,
    isLoading: Boolean,
    errorMessage: String?,
    resendCountdown: Int,
    onDigitChanged: (index: Int, digit: String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Spacer(modifier = Modifier.height(48.dp))

            // ── Heading ──────────────────────────────────
            Text(
                text = "Enter your code",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FrauncesFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                ),
                color = AppNameColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Destination hint ──────────────────────────
            Text(
                text = "Sent to $destination",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(44.dp))

            // ── 6-digit OTP boxes ─────────────────────────
            OtpBoxRow(
                digits = otpDigits,
                hasError = errorMessage != null,
                onDigitChanged = onDigitChanged,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Error message ─────────────────────────────
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Verify button ─────────────────────────────
            val allFilled = otpDigits.all { it.isNotEmpty() }
            Button(
                onClick = onVerify,
                enabled = !isLoading && allFilled,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonGold,
                    disabledContainerColor = ButtonGold.copy(alpha = 0.45f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = "Verify",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Resend ────────────────────────────────────
            if (resendCountdown > 0) {
                Text(
                    text = "Resend code in ${resendCountdown}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            } else {
                TextButton(onClick = onResend) {
                    Text(
                        text = "Resend code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ButtonGold,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// 6-box OTP row
// ─────────────────────────────────────────────

@Composable
private fun OtpBoxRow(
    digits: List<String>,
    hasError: Boolean,
    onDigitChanged: (index: Int, digit: String) -> Unit,
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // Auto-focus first empty box on initial composition
    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        digits.forEachIndexed { index, digit ->
            OtpSingleBox(
                digit = digit,
                hasError = hasError,
                focusRequester = focusRequesters[index],
                onValueChange = { newDigit ->
                    if (newDigit.length <= 1 && (newDigit.isEmpty() || newDigit.last().isDigit())) {
                        onDigitChanged(index, newDigit)
                        // Auto-advance focus
                        if (newDigit.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                onBackspace = {
                    if (digit.isEmpty() && index > 0) {
                        onDigitChanged(index - 1, "")
                        focusRequesters[index - 1].requestFocus()
                    } else {
                        onDigitChanged(index, "")
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────
// Single digit box
// ─────────────────────────────────────────────

@Composable
private fun OtpSingleBox(
    digit: String,
    hasError: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    // Spring-bounce scale when digit is entered
    val scale by animateFloatAsState(
        targetValue = if (digit.isNotEmpty()) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "otpScale",
    )

    val borderColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isFocused -> ButtonGold
        digit.isNotEmpty() -> ButtonGold.copy(alpha = 0.5f)
        else -> TextEditorBorderColor
    }

    BasicTextField(
        value = digit,
        onValueChange = onValueChange,
        modifier = modifier
            .scale(scale)
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TextEditorBgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown) {
                    onBackspace()
                    true
                } else false
            },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next,
        ),
        singleLine = true,
        cursorBrush = SolidColor(ButtonGold),
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = TextColor,
            fontSize = 22.sp,
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                innerTextField()
            }
        },
    )
}

// ─────────────────────────────────────────────
// Mask helpers
// ─────────────────────────────────────────────

private fun maskPhone(countryCode: String, phone: String): String {
    val last4 = phone.takeLast(4).ifEmpty { "XXXX" }
    return "$countryCode •••• $last4"
}

private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    return if (at <= 1) email
    else "${email.first()}***${email.substring(at)}"
}
