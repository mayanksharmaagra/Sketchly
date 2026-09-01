package com.jrprofessor.sketchly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jrprofessor.sketchly.ui.screens.archive.ArchiveScreen
import com.jrprofessor.sketchly.ui.screens.auth.AuthScreen
import com.jrprofessor.sketchly.ui.screens.circle.CircleScreen
import com.jrprofessor.sketchly.ui.screens.draw.DrawScreen
import com.jrprofessor.sketchly.ui.screens.inbox.InboxScreen
import com.jrprofessor.sketchly.ui.screens.settings.SettingsScreen
import com.jrprofessor.sketchly.ui.screens.viewer.SketchlyViewerScreen
import com.jrprofessor.sketchly.ui.screens.preview.ScreenPreviewScreen
import com.jrprofessor.sketchly.ui.screens.contacts.ContactPermissionScreen
import com.jrprofessor.sketchly.ui.screens.started.StartedScreen

@Composable
fun SketchlyNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Screen.GetStarted.route) {
            StartedScreen(onGetStarted = {
                navController.navigate(Screen.Auth.route)
            })
        }
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    // Navigate to contact permission gate instead of directly to Draw
                    navController.navigate(Screen.ContactPermission.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ContactPermission.route) {
            ContactPermissionScreen(
                onContinue = {
                    navController.navigate(Screen.Draw.route) {
                        popUpTo(Screen.ContactPermission.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Inbox.route) {
            InboxScreen(
                onSketchTap = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
                onDrawTap = { navController.navigate(Screen.Draw.route) },
            )
        }

        composable(Screen.Draw.route) {
            DrawScreen(
                onNavigateToCircle = {
                    navController.navigate(Screen.Circle.route)
                },
            )
        }

        composable(Screen.Circle.route) {
            CircleScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToCircle = {
                    navController.navigate(Screen.Circle.route)
                },
                onSignedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToScreenPreview = {
                    navController.navigate(Screen.ScreenPreview.route)
                },
            )
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(
                onSketchTap = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
            )
        }

        composable(
            route = Screen.Viewer.route,
            arguments = listOf(
                navArgument("sketchId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val sketchId = backStackEntry.arguments?.getString("sketchId").orEmpty()
            SketchlyViewerScreen(
                sketchId = sketchId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ScreenPreview.route) {
            ScreenPreviewScreen(
                onNavigateTo = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
