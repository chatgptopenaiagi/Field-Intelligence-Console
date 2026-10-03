package com.fieldconsole

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.ui.history.HistoryScreen
import com.fieldconsole.ui.main.MainScreen
import com.fieldconsole.ui.search.SmartSearchScreen
import com.fieldconsole.ui.settings.EngineSettingsScreen
import com.fieldconsole.ui.theme.FieldIntelligenceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MainActivity — single-activity host for Field Intelligence Console v3.
 *
 * Navigation graph:
 *   "main"      → MainScreen (SCAN + Action Matrix)
 *   "settings"  → EngineSettingsScreen (API Keys)
 *   "history"   → HistoryScreen (all saved observations)
 *   "search"    → SmartSearchScreen (full-text search)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var dispatcher: IntentDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldIntelligenceTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "main") {

                    composable("main") {
                        MainScreen(
                            dispatcher           = dispatcher,
                            onNavigateToSettings = { nav.navigate("settings") },
                            onNavigateToHistory  = { nav.navigate("history") },
                            onNavigateToSearch   = { nav.navigate("search") }
                        )
                    }

                    composable("settings") {
                        EngineSettingsScreen(onBack = { nav.popBackStack() })
                    }

                    composable("history") {
                        HistoryScreen(onBack = { nav.popBackStack() })
                    }

                    composable("search") {
                        SmartSearchScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
