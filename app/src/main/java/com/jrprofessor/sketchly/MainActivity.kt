package com.jrprofessor.sketchly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.jrprofessor.sketchly.ui.navigation.SketchlyBottomBar
import com.jrprofessor.sketchly.ui.navigation.SketchlyNavGraph
import com.jrprofessor.sketchly.ui.navigation.Screen
import com.jrprofessor.sketchly.ui.theme.SketchlyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if user is currently authenticated
    val startDestination = remember {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) Screen.Dashboard.route else Screen.GetStarted.route
    }

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
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding),
        )
    }
}