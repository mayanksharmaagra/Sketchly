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
import com.jrprofessor.sketchly.ui.screens.auth.AuthScreen
import com.jrprofessor.sketchly.ui.screens.auth.ProfileSetupScreen
import com.jrprofessor.sketchly.ui.screens.circle.FriendsListScreen
import com.jrprofessor.sketchly.ui.screens.dashboard.DashboardScreen
import com.jrprofessor.sketchly.ui.screens.draw.DrawScreen
import com.jrprofessor.sketchly.ui.screens.draw.DrawViewModel
import com.jrprofessor.sketchly.ui.screens.profile.ProfileScreen
import com.jrprofessor.sketchly.ui.screens.send.SendToScreen
import com.jrprofessor.sketchly.ui.screens.settings.SettingsScreen
import com.jrprofessor.sketchly.ui.screens.viewer.ContactHistoryScreen
import com.jrprofessor.sketchly.ui.screens.viewer.SketchlyViewerScreen
import com.jrprofessor.sketchly.ui.screens.contacts.ContactPermissionScreen
import com.jrprofessor.sketchly.ui.screens.started.StartedScreen
import com.jrprofessor.sketchly.ui.screens.widget.AddWidgetScreen
import com.jrprofessor.sketchly.ui.screens.settings.BlockedUsersScreen
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
            val dashboardViewModel = hiltViewModel<com.jrprofessor.sketchly.ui.screens.dashboard.DashboardViewModel>()
            DashboardScreen(
                dashboardViewModel = dashboardViewModel,
                onSketchTap = { sketch ->
                    val currentUserId = dashboardViewModel.currentUserId
                    val isMine = sketch.senderId == currentUserId

                    when {
                        // Unread → always open Viewer (marks as read + replay)
                        !sketch.isRead -> navController.navigate(Screen.Viewer.createRoute(sketch.id))

                        // Already-read sent sketch → open Viewer (NOT ContactHistory — that
                        // would pass own UID and show a blank screen)
                        isMine -> navController.navigate(Screen.Viewer.createRoute(sketch.id))

                        // Already-read received sketch → open ContactHistory for that sender
                        else -> navController.navigate(Screen.ContactHistory.createRoute(sketch.senderId))
                    }
                },
                onDrawTap    = { navController.navigate(Screen.Draw.route) },
                onCircleTap  = { navController.navigate(Screen.FriendsList.route) },
                onProfileTap = { navController.navigate(Screen.Profile.route) },
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
                // Primary: "Add to Widget" gold button — navigate to AddWidgetScreen
                // so the user sees the live scribble preview before pinning
                onContinue = {
                    drawViewModel.dismissSentDialog()
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Draw.route) { inclusive = true }
                    }
                },
                // Secondary: "Maybe later" — skip AddWidgetScreen, go to Dashboard
                onAddToWidget = {
                    val sketchId = uiState.lastSentSketchId
                    drawViewModel.dismissSentDialog()
                    if (sketchId != null) {
                        navController.navigate(Screen.AddWidget.createRoute(sketchId)) {
                            popUpTo(Screen.Draw.route) { inclusive = false }
                        }
                    } else {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Draw.route) { inclusive = true }
                        }
                    }

                },
            )
        }

        composable(
            route = Screen.AddWidget.route,
            arguments = listOf(
                navArgument("sketchId") { type = NavType.StringType },
            ),
        ) {
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

        composable(Screen.FriendsList.route) {
            FriendsListScreen(onBack = { navController.popBackStack() },)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEditProfile = {
                    navController.navigate(Screen.EditProfile.route)
                },
                onNavigateToBlockedUsers = {
                    navController.navigate(Screen.BlockedUsers.route)
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
                onBack   = { navController.popBackStack() },
                onBlock  = { navController.popBackStack() },
            )
        }

        composable(
            route = Screen.ContactHistory.route,
            arguments = listOf(
                navArgument("contactId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
            ContactHistoryScreen(
                contactId  = contactId,
                onBack     = { navController.popBackStack() },
                onSketchTap = { sketchId ->
                    navController.navigate(Screen.Viewer.createRoute(sketchId))
                },
                onBlock = { navController.popBackStack() },
            )
        }

        composable(Screen.BlockedUsers.route) {
            BlockedUsersScreen(onBack = { navController.popBackStack() })
        }
    }
}
