package com.jrprofessor.sketchly.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jrprofessor.sketchly.ui.theme.ButtonGold
import com.jrprofessor.sketchly.ui.theme.PaperIvory
import com.jrprofessor.sketchly.ui.theme.TextMuted

/**
 * Shared bottom navigation bar for the Dashboard.
 * Layout: Inbox | Draw FAB (elevated center) | History
 *
 * Circle is accessed from the Dashboard top-bar circle icon.
 * Settings is accessed from the Profile screen.
 *
 * @param isHistorySelected  `true` when the History tab is active, `false` for Inbox.
 * @param onInboxTap          Callback when the user taps the Inbox tab item.
 * @param onHistoryTap        Callback when the user taps the History tab item.
 * @param onDrawTap           Callback when the user taps the center Draw FAB.
 */
@Composable
fun SketchlyBottomBar(
    isHistorySelected: Boolean,
    onInboxTap: () -> Unit,
    onHistoryTap: () -> Unit,
    onDrawTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Background bar
        Surface(
            color = PaperIvory,
            shadowElevation = 16.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Inbox tab
                DashTabItem(
                    icon     = if (!isHistorySelected) Icons.Rounded.Inbox
                               else Icons.Outlined.Inbox,
                    label    = "Inbox",
                    selected = !isHistorySelected,
                    onClick  = onInboxTap,
                )

                // Center spacer for FAB
                Spacer(modifier = Modifier.width(64.dp))

                // History tab
                DashTabItem(
                    icon     = if (isHistorySelected) Icons.Rounded.History
                               else Icons.Outlined.History,
                    label    = "History",
                    selected = isHistorySelected,
                    onClick  = onHistoryTap,
                )
            }
        }

        // Draw FAB — floats above center
        Box(
            modifier = Modifier
                .offset(y = (-20).dp)
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(ButtonGold)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDrawTap() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Draw",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun DashTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 22.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "TabIconSize",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) ButtonGold else TextMuted.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
            ),
            color = if (selected) ButtonGold else TextMuted.copy(alpha = 0.55f),
        )
    }
}
