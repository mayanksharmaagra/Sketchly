package com.jrprofessor.sketchly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jrprofessor.sketchly.ui.navigation.SketchlyBottomBar
import com.jrprofessor.sketchly.ui.navigation.SketchlyNavGraph
import com.jrprofessor.sketchly.ui.navigation.Screen
import com.jrprofessor.sketchly.ui.screens.started.StartupViewModel
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Launcher for the POST_NOTIFICATIONS runtime permission dialog (Android 13+ / API 33+).
     * Result is informational only — the app degrades gracefully if denied.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ requires POST_NOTIFICATIONS at runtime (manifest declaration alone is
        // insufficient). Request here so FCM notifications are displayed immediately after login.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            SketchlyTheme {
                SketchlyApp()
            }
        }
    }
}

@Composable
private fun SketchlyApp() {
    // Resolve the correct start destination asynchronously.
    // StartupViewModel checks Firebase Auth AND Firestore profile completeness:
    //   • No auth user         → GetStarted
    //   • Auth but no username → ProfileSetup  (incomplete profile — e.g. closed app mid-onboarding)
    //   • Auth + username set  → Dashboard
    val startupVm: StartupViewModel = hiltViewModel()
    val startDestination by startupVm.startupDestination.collectAsStateWithLifecycle()

    // Show nothing (transparent splash) while the async profile check is in-flight.
    // This is typically <500 ms on a good connection and the OS splash screen covers it.
    val resolvedDestination = startDestination ?: return

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Active bottom tab is inferred from the current route:
    // Dashboard route → Inbox selected; History route → History selected.
    val isHistorySelected = currentRoute == Screen.History.route

    // Bottom bar is shown on Dashboard and History screens
    val showBottomBar = currentRoute == Screen.Dashboard.route ||
                        currentRoute == Screen.History.route

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    SketchlyBottomBar(
                        isHistorySelected = isHistorySelected,
                        onInboxTap = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onHistoryTap = {
                            navController.navigate(Screen.History.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onDrawTap = { navController.navigate(Screen.Draw.route) },
                    )
                }
            }
        },
    ) { innerPadding ->
        SketchlyNavGraph(
            navController    = navController,
            startDestination = resolvedDestination,
            modifier         = Modifier.padding(innerPadding),
        )
    }
}