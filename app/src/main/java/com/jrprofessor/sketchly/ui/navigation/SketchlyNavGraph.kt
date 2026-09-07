package com.jrprofessor.sketchly.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jrprofessor.sketchly.ui.screens.archive.ArchiveScreen
import com.jrprofessor.sketchly.ui.screens.auth.AuthScreen
import com.jrprofessor.sketchly.ui.screens.auth.ProfileSetupScreen
import com.jrprofessor.sketchly.ui.screens.circle.CircleScreen
import com.jrprofessor.sketchly.ui.screens.dashboard.DashboardScreen
import com.jrprofessor.sketchly.ui.screens.draw.DrawScreen
import com.jrprofessor.sketchly.ui.screens.draw.DrawViewModel
import com.jrprofessor.sketchly.ui.screens.inbox.InboxScreen
import com.jrprofessor.sketchly.ui.screens.profile.ProfileScreen
import com.jrprofessor.sketchly.ui.screens.send.SendToScreen
import com.jrprofessor.sketchly.ui.screens.settings.SettingsScreen
import com.jrprofessor.sketchly.ui.screens.viewer.SketchlyViewerScreen
import com.jrprofessor.sketchly.ui.screens.preview.ScreenPreviewScreen
import com.jrprofessor.sketchly.ui.screens.contacts.ContactPermissionScreen
import com.jrprofessor.sketchly.ui.screens.started.StartedScreen
import com.jrprofessor.sketchly.ui.screens.widget.AddWidgetScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jrprofessor.sketchly.ui.screens.history.HistoryScreen
import com.jrprofessor.sketchly.ui.screens.profile.EditProfileScreen

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
                onNewUser = {
                    // New account → must complete Full Name + Username before continuing
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onReturningUser = {
                    // Existing account (username already set) → skip setup, go to Dashboard
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ProfileSetup.route) {
            // ProfileSetupScreen shares the AuthViewModel via hiltViewModel() —
            // it retains the phone/countryCode state set during AuthScreen.
            ProfileSetupScreen(
                onContinue = {
                    // Profile saved → proceed to contact sync
                    navController.navigate(Screen.ContactPermission.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.ContactPermission.route) {
            ContactPermissionScreen(
                onSyncComplete = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.ContactPermission.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.ContactPermission.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onSketchTap  = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
                onDrawTap    = { navController.navigate(Screen.Draw.route) },
                onCircleTap  = { navController.navigate(Screen.Circle.route) },
                onProfileTap = { navController.navigate(Screen.Profile.route) },
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSendTo = {
                    navController.navigate(Screen.SendTo.route)
                },
            )
        }

        composable(Screen.SendTo.route) {
            // Share the same DrawViewModel instance from the Draw back-stack entry
            // so contact selection and send state are unified.
            // `remember(it)` is required to avoid the "getBackStackEntry during composition"
            // warning — it caches the entry and re-resolves only when the back-stack changes.
            val drawEntry = remember(it) {
                navController.getBackStackEntry(Screen.Draw.route)
            }
            val drawViewModel: DrawViewModel = hiltViewModel(drawEntry)
            val uiState by drawViewModel.uiState.collectAsStateWithLifecycle()
            val contacts by drawViewModel.contacts.collectAsStateWithLifecycle()

            SendToScreen(
                contacts = contacts,
                selectedContactIds = uiState.selectedContactIds,
                isSending = uiState.isSending,
                showSentDialog = uiState.showSentDialog,
                sentToNames = uiState.sentToNames,
                onContactToggle = { drawViewModel.toggleContactSelection(it) },
                onSendConfirmed = { drawViewModel.sendSketch() },
                onBack = { navController.popBackStack() },
                onContinue = {
                    drawViewModel.dismissSentDialog()
                    navController.navigate(Screen.AddWidget.route) {
                        popUpTo(Screen.Draw.route) { inclusive = false }
                    }
                },
            )
        }

        composable(Screen.AddWidget.route) {
            AddWidgetScreen(
                onAddWidget = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Draw.route) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Draw.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.Circle.route) {
            CircleScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCircle = {
                    navController.navigate(Screen.Circle.route)
                },
                onSignedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNavigateToScreenPreview = {
                    navController.navigate(Screen.ScreenPreview.route)
                },
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route) {
                        popUpTo(Screen.Profile.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(
                onSketchTap = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
            )
        }

        // Dedicated History screen — wired to bottom-nav History tab
        composable(Screen.History.route) {
            HistoryScreen(
                onSketchTap   = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
                onProfileTap  = { navController.navigate(Screen.Profile.route) },
                onSettingsTap = {
                    navController.navigate(Screen.Settings.route)
                },
                onDrawTap     = { navController.navigate(Screen.Draw.route) },
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
