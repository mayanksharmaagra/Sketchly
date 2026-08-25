package com.jrprofessor.sketchly.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jrprofessor.sketchly.ui.theme.BottomNavShape
import com.jrprofessor.sketchly.ui.theme.FabShape
import com.jrprofessor.sketchly.ui.theme.SecondaryContainer

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Inbox.route,
        label = "Inbox",
        selectedIcon = Icons.Rounded.Mail,
        unselectedIcon = Icons.Outlined.Mail,
    ),
    // Draw is rendered as a special elevated FAB — handled separately
    BottomNavItem(
        route = Screen.Circle.route,
        label = "Circle",
        selectedIcon = Icons.Rounded.Groups,
        unselectedIcon = Icons.Outlined.Groups,
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        label = "Settings",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
)

/**
 * Custom floating bottom navigation bar matching the DESIGN.md spec:
 * - Floating dock with high roundedness, not edge-attached
 * - Draw tab is an elevated FAB in the center
 * - Organic, warm styling
 */
@Composable
fun SketchlyBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // The floating dock background
        Surface(
            shape = BottomNavShape,
            color = SecondaryContainer,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Inbox tab
                NavItem(
                    item = bottomNavItems[0],
                    isSelected = currentRoute == bottomNavItems[0].route,
                    onClick = { onNavigate(bottomNavItems[0].route) },
                )

                // Draw FAB (center, elevated)
                DrawFabNavItem(
                    isSelected = currentRoute == Screen.Draw.route,
                    onClick = { onNavigate(Screen.Draw.route) },
                )

                // Circle tab
                NavItem(
                    item = bottomNavItems[1],
                    isSelected = currentRoute == bottomNavItems[1].route,
                    onClick = { onNavigate(bottomNavItems[1].route) },
                )

                // Settings tab
                NavItem(
                    item = bottomNavItems[2],
                    isSelected = currentRoute == bottomNavItems[2].route,
                    onClick = { onNavigate(bottomNavItems[2].route) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "nav_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .scale(scale),
    ) {
        Icon(
            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun DrawFabNavItem(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fab_scale",
    )

    Surface(
        shape = FabShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        modifier = Modifier
            .offset(y = (-8).dp)
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Draw,
                contentDescription = "Draw",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Draw",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
