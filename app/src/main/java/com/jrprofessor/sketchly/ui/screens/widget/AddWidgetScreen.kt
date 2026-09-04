package com.jrprofessor.sketchly.ui.screens.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.theme.AppNameColor
import com.jrprofessor.sketchly.ui.theme.BgColor
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.PillShape
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import com.jrprofessor.sketchly.ui.theme.TextEditorBorderColor
import com.jrprofessor.sketchly.ui.theme.TextMuted
import com.jrprofessor.sketchly.widget.SketchlyWidgetReceiver

@Composable
fun AddWidgetScreen(
    onAddWidget: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // ── Phone mockup illustration ──────────────────────────────────────
            PhoneMockupIllustration(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(340.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Heading ────────────────────────────────────────────────────────
            Text(
                text = "Add Scribble to your\nHome Screen",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                ),
                color = AppNameColor,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Subtitle ───────────────────────────────────────────────────────
            Text(
                text = "See notes from friends instantly\nwithout opening the app.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = TextMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // ── Add Widget button ──────────────────────────────────────────────
            Surface(
                shape = PillShape,
                color = ButtonGold,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(PillShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        // Request widget pin (Android 8.0+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, SketchlyWidgetReceiver::class.java)
                            if (appWidgetManager.isRequestPinAppWidgetSupported) {
                                appWidgetManager.requestPinAppWidget(provider, null, null)
                            }
                        }
                        onAddWidget()
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Add Widget",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        ),
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Maybe later ────────────────────────────────────────────────────
            Text(
                text = "Maybe later",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = TextMuted,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onSkip() }
                    .padding(vertical = 8.dp, horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Phone Mockup — drawn with Canvas to show the widget on a home screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PhoneMockupIllustration(modifier: Modifier = Modifier) {
    val bgColor = BgColor
    val phoneBodyColor = Color(0xFFE8E0CC)
    val screenColor = Color(0xFFF0EAD8)
    val appIconColor = phoneBodyColor.copy(alpha = 0.7f)
    val widgetBg = PaperIvory
    val widgetSketchColor = AppNameColor.copy(alpha = 0.55f)
    val avatarColor = Color(0xFF70A18A)
    val strokeColor = AppNameColor

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val phoneCorner = w * 0.12f
        val phoneLeft = 0f
        val phoneTop = h * 0.04f
        val phoneRight = w
        val phoneBottom = h * 0.96f

        // Phone body shadow
        drawRoundRect(
            color = AppNameColor.copy(alpha = 0.08f),
            topLeft = Offset(phoneLeft + 6f, phoneTop + 6f),
            size = Size(phoneRight - phoneLeft, phoneBottom - phoneTop),
            cornerRadius = CornerRadius(phoneCorner),
        )

        // Phone body
        drawRoundRect(
            color = phoneBodyColor,
            topLeft = Offset(phoneLeft, phoneTop),
            size = Size(phoneRight - phoneLeft, phoneBottom - phoneTop),
            cornerRadius = CornerRadius(phoneCorner),
        )

        // Screen
        val screenPad = w * 0.06f
        val screenTop = phoneTop + h * 0.06f
        val screenBottom = phoneBottom - h * 0.05f
        drawRoundRect(
            color = screenColor,
            topLeft = Offset(phoneLeft + screenPad, screenTop),
            size = Size(phoneRight - phoneLeft - screenPad * 2, screenBottom - screenTop),
            cornerRadius = CornerRadius(w * 0.07f),
        )

        // Status bar dots
        val sbY = screenTop + h * 0.025f
        val sbLeft = phoneLeft + screenPad + w * 0.06f
        drawCircle(color = strokeColor.copy(alpha = 0.3f), radius = w * 0.012f, center = Offset(w / 2f, sbY))

        // App icons row 1 (top row, 4 icons)
        val iconSize = w * 0.10f
        val iconY1 = screenTop + h * 0.06f
        val iconSpacing = (phoneRight - phoneLeft - screenPad * 2 - iconSize * 4) / 5f
        for (i in 0..3) {
            val iconX = phoneLeft + screenPad + iconSpacing + i * (iconSize + iconSpacing)
            drawRoundRect(
                color = appIconColor,
                topLeft = Offset(iconX, iconY1),
                size = Size(iconSize, iconSize),
                cornerRadius = CornerRadius(iconSize * 0.28f),
            )
        }

        // ── Widget card ─────────────────────────────────────────────────────
        val widgetTop = iconY1 + iconSize + h * 0.03f
        val widgetLeft = phoneLeft + screenPad + w * 0.03f
        val widgetRight = phoneRight - screenPad - w * 0.03f
        val widgetBottom = widgetTop + h * 0.32f
        val widgetCorner = w * 0.06f

        // Widget shadow
        drawRoundRect(
            color = AppNameColor.copy(alpha = 0.06f),
            topLeft = Offset(widgetLeft + 3f, widgetTop + 3f),
            size = Size(widgetRight - widgetLeft, widgetBottom - widgetTop),
            cornerRadius = CornerRadius(widgetCorner),
        )

        // Widget background
        drawRoundRect(
            color = widgetBg,
            topLeft = Offset(widgetLeft, widgetTop),
            size = Size(widgetRight - widgetLeft, widgetBottom - widgetTop),
            cornerRadius = CornerRadius(widgetCorner),
        )

        // Widget header: avatar + "From Sarah"
        val headerY = widgetTop + h * 0.025f
        val avatarR = w * 0.055f
        val avatarCx = widgetLeft + w * 0.08f
        val avatarCy = headerY + avatarR
        drawCircle(color = avatarColor, radius = avatarR, center = Offset(avatarCx, avatarCy))

        // "From Sarah" text rendered as a thick line placeholder
        drawRoundRect(
            color = strokeColor.copy(alpha = 0.22f),
            topLeft = Offset(avatarCx + avatarR + w * 0.03f, avatarCy - w * 0.025f),
            size = Size(w * 0.28f, w * 0.04f),
            cornerRadius = CornerRadius(w * 0.02f),
        )

        // Sketch area inside widget
        val sketchLeft = widgetLeft + w * 0.04f
        val sketchTop2 = headerY + avatarR * 2f + h * 0.015f
        val sketchRight2 = widgetRight - w * 0.04f
        val sketchBottom2 = widgetBottom - h * 0.015f

        drawRoundRect(
            color = Color(0xFFF5F0E0),
            topLeft = Offset(sketchLeft, sketchTop2),
            size = Size(sketchRight2 - sketchLeft, sketchBottom2 - sketchTop2),
            cornerRadius = CornerRadius(w * 0.04f),
        )

        // Draw a simple teacup doodle inside the sketch area
        val cx = (sketchLeft + sketchRight2) / 2f
        val cy = (sketchTop2 + sketchBottom2) / 2f + h * 0.01f
        val cupStroke = Stroke(width = w * 0.018f, cap = StrokeCap.Round)

        // Cup body
        val cupW = w * 0.18f
        val cupH = w * 0.12f
        val path = Path().apply {
            moveTo(cx - cupW / 2f, cy - cupH / 2f)
            lineTo(cx - cupW * 0.4f, cy + cupH / 2f)
            lineTo(cx + cupW * 0.4f, cy + cupH / 2f)
            lineTo(cx + cupW / 2f, cy - cupH / 2f)
        }
        drawPath(path, color = widgetSketchColor, style = cupStroke)

        // Cup handle
        val handlePath = Path().apply {
            moveTo(cx + cupW * 0.4f, cy - cupH * 0.1f)
            cubicTo(
                cx + cupW * 0.7f, cy - cupH * 0.1f,
                cx + cupW * 0.7f, cy + cupH * 0.4f,
                cx + cupW * 0.4f, cy + cupH * 0.4f,
            )
        }
        drawPath(handlePath, color = widgetSketchColor, style = cupStroke)

        // Saucer line
        drawLine(
            color = widgetSketchColor,
            start = Offset(cx - cupW * 0.55f, cy + cupH / 2f + w * 0.015f),
            end = Offset(cx + cupW * 0.55f, cy + cupH / 2f + w * 0.015f),
            strokeWidth = w * 0.016f,
            cap = StrokeCap.Round,
        )

        // Steam wisps
        for (i in -1..1) {
            val steamX = cx + i * cupW * 0.22f
            val steamPath = Path().apply {
                moveTo(steamX, cy - cupH / 2f - w * 0.02f)
                cubicTo(
                    steamX - w * 0.02f, cy - cupH / 2f - w * 0.06f,
                    steamX + w * 0.02f, cy - cupH / 2f - w * 0.10f,
                    steamX, cy - cupH / 2f - w * 0.14f,
                )
            }
            drawPath(steamPath, color = widgetSketchColor.copy(alpha = 0.55f), style = Stroke(width = w * 0.012f, cap = StrokeCap.Round))
        }

        // App icons row 2 (below widget)
        val iconY2 = widgetBottom + h * 0.03f
        for (i in 0..3) {
            val iconX = phoneLeft + screenPad + iconSpacing + i * (iconSize + iconSpacing)
            drawRoundRect(
                color = appIconColor,
                topLeft = Offset(iconX, iconY2),
                size = Size(iconSize, iconSize),
                cornerRadius = CornerRadius(iconSize * 0.28f),
            )
        }
    }
}

// ── Previews ──

@Preview(name = "AddWidget Screen", showBackground = true)
@Composable
private fun AddWidgetScreenPreview() {
    SketchlyTheme {
        AddWidgetScreen(
            onAddWidget = {},
            onSkip = {},
        )
    }
}
