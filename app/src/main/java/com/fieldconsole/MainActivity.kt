package com.fieldconsole

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.ui.history.HistoryScreen
import com.fieldconsole.ui.main.MainScreen
import com.fieldconsole.ui.search.SmartSearchScreen
import com.fieldconsole.ui.search.openSearchEntry
import com.fieldconsole.viewmodel.MainViewModel
import com.fieldconsole.ui.settings.EngineSettingsScreen
import com.fieldconsole.ui.theme.FieldIntelligenceTheme
import com.fieldconsole.ui.theme.AppearanceDialog
import com.fieldconsole.ui.network.NetworkScreen
import com.fieldconsole.ui.network.ReportScreen
import com.fieldconsole.location.GeoContext
import com.google.gson.JsonObject
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

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.fieldconsole.location.RegionalLanguage.attach(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences("fic_appearance", MODE_PRIVATE) }
            var theme by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
            var textScale by remember { mutableFloatStateOf(preferences.getFloat("text_scale", 1f)) }
            var appearance by remember { mutableStateOf(false) }
            FieldIntelligenceTheme(theme, textScale) {
                val nav = rememberNavController()
                var networkGeo by remember { mutableStateOf<GeoContext?>(null) }
                var networkSource by remember { mutableStateOf("unavailable") }
                var currentReport by remember { mutableStateOf<JsonObject?>(null) }
                if (appearance) AppearanceDialog(theme, textScale, onClose = { appearance = false }) { selectedTheme, scale ->
                    theme = selectedTheme; textScale = scale
                    preferences.edit().putString("theme", theme).putFloat("text_scale", scale).apply()
                    appearance = false
                }
                NavHost(navController = nav, startDestination = "main") {

                    composable("main") {
                        MainScreen(
                            dispatcher           = dispatcher,
                            onNavigateToSettings = { nav.navigate("settings") },
                            onNavigateToNetwork = { geo -> networkGeo = geo; networkSource = if (geo == null) "unavailable" else "device scan"; nav.navigate("network") },
                            onNavigateToReports = { report -> currentReport = report; nav.navigate("reports") },
                            onAppearance = { appearance = true },
                            onNavigateToHistory  = { nav.navigate("history") },
                            onNavigateToSearch   = { nav.navigate("search") }
                        )
                    }

                    composable("settings") {
                        EngineSettingsScreen(onBack = { nav.popBackStack() })
                    }

                    composable("network") { NetworkScreen(networkGeo, { nav.popBackStack() }, initialPlaceSource = networkSource) }
                    composable("reports") {
                        ReportScreen(currentReport, { nav.popBackStack() }, onSelectPlace = { report ->
                            val location = report.getAsJsonObject("location")
                            networkGeo = if (location.get("latitude").isJsonNull || location.get("longitude").isJsonNull) null
                                else GeoContext(latitude = location.get("latitude").asDouble, longitude = location.get("longitude").asDouble)
                            networkSource = "report-derived (original ${location.get("source").asString})"
                            nav.navigate("network")
                        })
                    }

                    composable("history") { screen ->
                        val mainEntry = remember(screen) { nav.getBackStackEntry("main") }
                        val mainVm: MainViewModel = hiltViewModel(mainEntry)
                        HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { entry -> mainVm.openEntry(entry); nav.popBackStack() })
                    }

                    composable("search") { searchEntry ->
                        val mainEntry = remember(searchEntry) { nav.getBackStackEntry("main") }
                        val mainVm: MainViewModel = hiltViewModel(mainEntry)
                        SmartSearchScreen(
                            onBack = { nav.popBackStack() },
                            onEntrySelect = { entry -> openSearchEntry(nav, mainVm, entry) }
                        )
                    }
                }
            }
        }
    }
}
