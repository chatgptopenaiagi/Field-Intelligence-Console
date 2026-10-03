package com.fieldconsole.ui.main

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.location.GeoContext
import com.fieldconsole.ui.components.ActionMatrixButton
import com.fieldconsole.ui.components.ObservationDialog
import com.fieldconsole.ui.emergency.EmergencyScreen
import com.fieldconsole.ui.theme.*
import com.fieldconsole.viewmodel.MainUiState
import com.fieldconsole.viewmodel.MainViewModel

@Composable
fun MainScreen(
    mainViewModel:   MainViewModel         = hiltViewModel(),
    dispatcher:      IntentDispatcher,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory:  () -> Unit = {},
    onNavigateToSearch:   () -> Unit = {}
) {
    val uiState  by mainViewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // ── Runtime permission launcher ───────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine   = grants[Manifest.permission.ACCESS_FINE_LOCATION]   ?: false
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) mainViewModel.onPermissionGranted()
        else                mainViewModel.onPermissionDenied()
    }

    // Trigger permission request when state demands it
    LaunchedEffect(uiState) {
        if (uiState is MainUiState.RequestingPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ── Emergency screen overlay ──────────────────────────────────────────────
    if (uiState is MainUiState.Emergency) {
        EmergencyScreen(
            geo        = (uiState as MainUiState.Emergency).geo,
            dispatcher = dispatcher,
            onDismiss  = mainViewModel::onEmergencyDismissed
        )
        return
    }

    Scaffold(
        topBar = {
            FicTopBar(
                entryCount = mainViewModel.entryCount.collectAsState().value,
                onSettings = onNavigateToSettings,
                onHistory  = onNavigateToHistory,
                onSearch   = onNavigateToSearch
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState  = uiState,
                transitionSpec = {
                    fadeIn() + slideInVertically { it / 4 } togetherWith
                    fadeOut() + slideOutVertically { -it / 4 }
                },
                label = "main_state"
            ) { state ->
                when (state) {
                    is MainUiState.Idle,
                    is MainUiState.RequestingPermission ->
                        IdleContent(onScan = mainViewModel::onScanPressed)

                    is MainUiState.Scanning ->
                        ScanningContent()

                    is MainUiState.AwaitingObservation -> {
                        IdleContent(onScan = {})
                        ObservationDialog(
                            geo       = state.geo,
                            onConfirm = mainViewModel::onObservationSubmitted,
                            onDismiss = mainViewModel::onObservationDismissed
                        )
                    }

                    is MainUiState.ActionMatrix -> ActionMatrixContent(
                        entry      = state.entry,
                        geo        = state.geo,
                        dispatcher = dispatcher,
                        context    = context,
                        onBack     = mainViewModel::onReset,
                        onEmergency = { mainViewModel.onEmergencyPressed(state.geo) }
                    )

                    is MainUiState.Emergency -> { /* handled above */ }

                    is MainUiState.Error -> {
                        IdleContent(onScan = mainViewModel::onScanPressed)
                        ErrorDialog(
                            message   = state.message,
                            onDismiss = mainViewModel::onErrorDismissed
                        )
                    }
                }
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicTopBar(
    entryCount: Int,
    onSettings: () -> Unit,
    onHistory:  () -> Unit = {},
    onSearch:   () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text("FIELD INTELLIGENCE",
                     style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("CONSOLE",
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.primary, letterSpacing = 4.sp)
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = onHistory) {
                if (entryCount > 0) {
                    BadgedBox(badge = {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$entryCount", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                } else {
                    Icon(Icons.Default.History, contentDescription = "History")
                }
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ── Idle ──────────────────────────────────────────────────────────────────────

@Composable
private fun IdleContent(onScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text      = "ONE TAP.\nINSTANT AWARENESS.",
                style     = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick  = onScan,
                shape    = MaterialTheme.shapes.extraLarge,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(180.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MyLocation, "SCAN",
                         modifier = Modifier.size(56.dp),
                         tint     = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("SCAN", fontSize = 22.sp, fontWeight = FontWeight.Black,
                         color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// ── Scanning ──────────────────────────────────────────────────────────────────

@Composable
private fun ScanningContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color       = MaterialTheme.colorScheme.primary,
                modifier    = Modifier.size(64.dp),
                strokeWidth = 5.dp
            )
            Spacer(Modifier.height(24.dp))
            Text("Acquiring GPS fix\u2026", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Resolving location\u2026",
                 style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        }
    }
}

// ── Action Matrix ─────────────────────────────────────────────────────────────

@Composable
private fun ActionMatrixContent(
    entry:       FieldEntry,
    geo:         GeoContext,
    dispatcher:  IntentDispatcher,
    context:     android.content.Context,
    onBack:      () -> Unit,
    onEmergency: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Location header ───────────────────────────────────────────────────
        Surface(
            color    = MaterialTheme.colorScheme.surfaceVariant,
            shape    = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier          = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocationOn, null,
                     tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = listOfNotNull(geo.fullPlaceName, geo.geocodingNotice).joinToString("\n"),
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "${geo.coordString}  (\u00b1${geo.accuracy.toInt()} m)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.note.isNotBlank()) {
                        Text(entry.note,
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entry.tag.isNotBlank()) {
                        Text("#${entry.tag}",
                             style = MaterialTheme.typography.labelLarge,
                             color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── 8-button matrix ───────────────────────────────────────────────────
        val buttons = listOf(
            Triple("INFO SCAN",      Icons.Default.Search,                ColorInfoScan),
            Triple("HISTORY",        Icons.Default.History,               ColorHistory),
            Triple("NAVIGATION",     Icons.Default.Navigation,            ColorNavigation),
            Triple("AI ANALYSIS",    Icons.Default.AutoAwesome,           ColorAiAnalysis),
            Triple("RISK / SAFETY",  Icons.Default.Warning,               ColorRiskSafety),
            Triple("LOGISTICS",      Icons.Default.LocalShipping,         ColorLogistics),
            Triple("FINANCIAL",      Icons.Default.Business,              ColorFinancial),
            Triple("EMERGENCY EXIT", Icons.AutoMirrored.Filled.ExitToApp, ColorEmergency)
        )

        buttons.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, icon, color) ->
                    ActionMatrixButton(
                        label   = label,
                        icon    = icon,
                        color   = color,
                        onClick = {
                            if (label == "EMERGENCY EXIT") onEmergency()
                            else handleAction(label, entry, geo, dispatcher, context)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Copy Location ─────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = { dispatcher.copyLocation(context, geo, entry.note, entry.tag) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text("COPY LOCATION")
        }

        // ── Share Field Report ────────────────────────────────────────────────
        OutlinedButton(
            onClick  = { dispatcher.shareFieldReport(context, geo, entry.note, entry.tag) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("SHARE FIELD REPORT")
        }

        // ── New Scan ──────────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text("NEW SCAN")
        }
    }
}

// ── Error dialog ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.ErrorOutline, null,
                       tint = MaterialTheme.colorScheme.error) },
        title = { Text("Location Error") },
        text  = { Text(message) },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

// ── Action dispatcher ─────────────────────────────────────────────────────────

private fun handleAction(
    label:      String,
    entry:      FieldEntry,
    geo:        GeoContext,
    dispatcher: IntentDispatcher,
    context:    android.content.Context
) {
    when (label) {
        "INFO SCAN"     -> dispatcher.infoScan(context, geo, entry)
        "HISTORY"       -> dispatcher.history(context, geo, entry)
        "NAVIGATION"    -> dispatcher.navigation(context, geo)
        "AI ANALYSIS"   -> dispatcher.aiAnalysis(context, geo, entry.note, entry.tag)
        "RISK / SAFETY" -> dispatcher.riskSafety(context, geo, entry)
        "LOGISTICS"     -> dispatcher.logistics(context, geo)
        "FINANCIAL"     -> dispatcher.financial(context, geo, entry)
        // EMERGENCY EXIT handled by onEmergency callback above
    }
}
