package com.jrprofessor.sketchly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jrprofessor.sketchly.ui.theme.BottomNavShape
import com.jrprofessor.sketchly.ui.theme.PaperIvory

/**
 * Floating pen toolbar for the Draw screen.
 *
 * Shows color swatches, thickness slider, undo and clear buttons.
 * Matches the mockup: horizontal row of color dots | thickness slider | undo icon.
 */
@Composable
fun PenToolbar(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    thickness: Float,
    minThickness: Float = 2f,
    maxThickness: Float = 22f,
    onThicknessChanged: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = BottomNavShape,
            color = PaperIvory.copy(alpha = 0.95f),
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // ── Color swatches ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors.forEach { color ->
                        ColorSwatch(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { onColorSelected(color) },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ── Vertical divider ──
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // ── Thickness slider ──
                Slider(
                    value = thickness,
                    onValueChange = onThicknessChanged,
                    valueRange = minThickness..maxThickness,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "Pen thickness slider" },
                )

                Spacer(modifier = Modifier.width(4.dp))

                // ── Vertical divider ──
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // ── Undo ──
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Undo last stroke",
                        tint = if (canUndo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                }

                // ── Clear ──
                IconButton(
                    onClick = onClear,
                    enabled = canUndo,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Clear canvas",
                        tint = if (canUndo) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "swatch_scale",
    )

    // Resolve a human-readable name for TalkBack (UIUX §9)
    val colorName = when {
        color.red < 0.15f && color.green < 0.15f && color.blue < 0.15f -> "Near-black ink"
        color.red > 0.7f && color.green < 0.35f -> "Terracotta"
        color.blue > 0.55f && color.red < 0.35f -> "Denim blue"
        color.green > 0.4f && color.red < 0.4f -> "Sage green"
        color.red > 0.6f && color.green > 0.3f && color.blue < 0.2f -> "Ochre"
        color.red > 0.7f && color.green > 0.7f && color.blue > 0.65f -> "Warm grey"
        color.red > 0.65f && color.blue > 0.35f -> "Rose"
        else -> "Ink colour"
    }
    val selectedLabel = if (isSelected) "$colorName, selected" else colorName

    // 48dp outer tap target (UIUX §9 — minimum 48×48dp) wrapping the 28dp visible dot
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClickLabel = "Select $colorName",
            ) { onClick() }
            .semantics { contentDescription = selectedLabel },
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color, CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
