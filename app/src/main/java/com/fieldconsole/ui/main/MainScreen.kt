package com.fieldconsole.ui.main

import androidx.compose.ui.res.stringResource
import com.fieldconsole.R
import com.fieldconsole.intent.MatrixHandoff
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.BuildConfig
import com.fieldconsole.data.local.entity.FieldEntry
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.location.GeoContext
import com.fieldconsole.ui.components.ActionMatrixButton
import com.fieldconsole.ui.components.ObservationDialog
import com.fieldconsole.ui.emergency.EmergencyScreen
import com.fieldconsole.ui.theme.*
import com.fieldconsole.viewmodel.MainUiState
import com.fieldconsole.viewmodel.MainViewModel
import com.fieldconsole.export.ReportExchange
import com.google.gson.JsonObject

@Composable
fun MainScreen(
    mainViewModel:   MainViewModel         = hiltViewModel(),
    dispatcher:      IntentDispatcher,
    onNavigateToSettings: () -> Unit,
    onNavigateToNetwork: (GeoContext?) -> Unit,
    onNavigateToReports: (JsonObject?) -> Unit,
    onAppearance: () -> Unit,
    onNavigateToHistory:  () -> Unit = {},
    onNavigateToSearch:   () -> Unit = {}
) {
    val uiState  by mainViewModel.uiState.collectAsState()
    val context  = LocalContext.current

    var regional by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var manualPlace by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (manualPlace) CoordinateDialog({ manualPlace = false }) { latitude, longitude ->
        manualPlace = false
        mainViewModel.selectCoordinates(latitude, longitude)
    }
    if (regional) com.fieldconsole.ui.emergency.RegionalSettings({ regional = false }, (uiState as? MainUiState.ActionMatrix)?.geo)
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

    val captured = (uiState as? MainUiState.AwaitingObservation)?.geo
    LaunchedEffect(captured) {
        captured?.let { com.fieldconsole.location.RegionalLanguage.onFix(context, it) }
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
                onSearch   = onNavigateToSearch,
                onNetwork = { onNavigateToNetwork(null) },
                onReports = { onNavigateToReports(null) },
                onManualPlace = { manualPlace = true }, onRegional = { regional = true }, onAppearance = onAppearance
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
                        onEmergency = { mainViewModel.onEmergencyPressed(state.geo) },
                        onNetwork = { onNavigateToNetwork(state.geo) },
                        onReport = { onNavigateToReports(ReportExchange.fromEntry(state.entry, state.geo)) }
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

@Composable
private fun CoordinateDialog(onDismiss: () -> Unit, onSelect: (Double, Double) -> Unit) {
    var latitude by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var longitude by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val lat = latitude.trim().replace(',', '.').toDoubleOrNull()
    val lon = longitude.trim().replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.select_coordinates)) },
        text = { Column {
            OutlinedTextField(latitude, { latitude = it }, singleLine = true,
                label = { Text(stringResource(R.string.latitude)) })
            OutlinedTextField(longitude, { longitude = it }, singleLine = true,
                label = { Text(stringResource(R.string.longitude)) })
        } },
        confirmButton = { TextButton(enabled = lat != null && lat in -90.0..90.0 && lon != null && lon in -180.0..180.0,
            onClick = { if (lat != null && lon != null) onSelect(lat, lon) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FicTopBar(
    entryCount: Int,
    onSettings: () -> Unit,
    onNetwork: () -> Unit,
    onReports: () -> Unit,
    onManualPlace: () -> Unit,
    onRegional: () -> Unit,
    onAppearance: () -> Unit,
    onHistory:  () -> Unit = {},
    onSearch:   () -> Unit = {}
) {
    var toolsExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text("FIELD INTELLIGENCE",
                     style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("CONSOLE · BETA",
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
            }
            IconButton(onClick = onHistory) {
                if (entryCount > 0) {
                    BadgedBox(badge = {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$entryCount", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.action_history))
                    }
                } else {
                    Icon(Icons.Default.History, contentDescription = stringResource(R.string.action_history))
                }
            }
            Box {
                IconButton(onClick = { toolsExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.tools)) }
                DropdownMenu(toolsExpanded, { toolsExpanded = false }) {
                    listOf(stringResource(R.string.regional_settings) to onRegional, stringResource(R.string.select_coordinates) to onManualPlace, stringResource(R.string.known_network) to onNetwork, stringResource(R.string.field_reports) to onReports,
                        stringResource(R.string.appearance) to onAppearance, stringResource(R.string.settings_engines_title) to onSettings).forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { toolsExpanded = false; action() })
                    }
                }
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
                text      = stringResource(R.string.scan_motto),
                style     = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Text("BETA · ${BuildConfig.VERSION_NAME}",
                 style = MaterialTheme.typography.labelLarge,
                 color = MaterialTheme.colorScheme.primary)
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
                    Icon(Icons.Default.MyLocation, null,
                         modifier = Modifier.size(56.dp),
                         tint     = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.scan_button), fontSize = 22.sp, fontWeight = FontWeight.Black,
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
            Text(stringResource(R.string.scan_fix), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.scan_resolve),
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
    onEmergency: () -> Unit,
    onNetwork: () -> Unit,
    onReport: () -> Unit
) {
    var review by androidx.compose.runtime.saveable.rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var options by androidx.compose.runtime.saveable.rememberSaveable(entry.id) { mutableStateOf<String?>(null) }
    var earthFallback by androidx.compose.runtime.saveable.rememberSaveable(entry.id) { mutableStateOf(false) }
    val warn = stringResource(R.string.no_receiver)
    fun unavailable() = android.widget.Toast.makeText(context, warn, android.widget.Toast.LENGTH_LONG).show()
    val offline = stringResource(R.string.offline_web)
    fun external(uri: android.net.Uri) {
        val network = context.getSystemService(android.net.ConnectivityManager::class.java)
        if (network?.getNetworkCapabilities(network.activeNetwork)
                ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            android.widget.Toast.makeText(context, offline, android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!MatrixHandoff.launch(context, android.content.Intent(android.content.Intent.ACTION_VIEW, uri))) unavailable()
    }
    options?.let { function ->
        AlertDialog(onDismissRequest = { options = null }, title = { Text(matrixLabel(function)) },
            confirmButton = { TextButton(onClick = { options = null; handleAction(function, entry, geo, dispatcher, context) }) { Text(stringResource(R.string.open_source)) } },
            dismissButton = { TextButton(onClick = { options = null; review = function }) { Text(stringResource(R.string.review_ai)) } })
    }
    review?.let { function ->
        val prompt = remember(function, entry, geo) { dispatcher.reviewPrompt(function, entry, geo) }
        val valid = MatrixHandoff.completeText(prompt)
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        var fallback by androidx.compose.runtime.saveable.rememberSaveable(function) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { review = null }, title = { Text(stringResource(R.string.review_ai)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(if (valid) R.string.share_review else R.string.share_invalid))
                if (fallback) Text(stringResource(R.string.paste_help))
                if (valid) androidx.compose.foundation.text.selection.SelectionContainer { Text(prompt) }
            } },
            confirmButton = { TextButton(enabled = valid, onClick = {
                if (fallback) {
                    dispatcher.copyToClipboard(context, prompt, "FIC")
                    if (clipboard.primaryClip?.getItemAt(0)?.text?.toString() == prompt) {
                        external(android.net.Uri.parse("https://chatgpt.com/"))
                    } else unavailable()
                } else if (!MatrixHandoff.launch(context, MatrixHandoff.chatIntent(prompt))) fallback = true
            }) { Text(stringResource(if (fallback) R.string.copy_web else R.string.open_chat)) } },
            dismissButton = { TextButton(onClick = { review = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (earthFallback) AlertDialog(onDismissRequest = { earthFallback = false },
        title = { Text("Google Earth") }, text = { Text(stringResource(R.string.earth_help)) },
        confirmButton = { TextButton(onClick = { earthFallback = false; external(MatrixHandoff.earthUrl(geo)) }) { Text(stringResource(R.string.earth_web)) } },
        dismissButton = { TextButton(onClick = { earthFallback = false; external(android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.earth")) }) { Text(stringResource(R.string.install_earth)) } })
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
                        text       = listOfNotNull(geo.fullPlaceName, geo.geocodingNotice?.let { stringResource(R.string.address_unknown) }).joinToString("\n"),
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text  = "${geo.coordString}  (${geo.accuracyText})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    com.fieldconsole.ui.emergency.MeasurementDetails(geo)
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

        // ── Cognitive Matrix ───────────────────────────────────────────────────
        val buttons = listOf(
            Triple("INFO SCAN",      Icons.Default.Search,                ColorInfoScan),
            Triple("HISTORY",        Icons.Default.History,               ColorHistory),
            Triple("NAVIGATION",     Icons.Default.Navigation,            ColorNavigation),
            Triple("AI ANALYSIS",    Icons.Default.AutoAwesome,           ColorAiAnalysis),
            Triple("RISK / SAFETY",  Icons.Default.Warning,               ColorRiskSafety),
            Triple("LOGISTICS",      Icons.Default.LocalShipping,         ColorLogistics),
            Triple("FINANCIAL",      Icons.Default.Business,              ColorFinancial),
            Triple("EMERGENCY EXIT", Icons.AutoMirrored.Filled.ExitToApp, ColorEmergency),
            Triple("GOOGLE EARTH", Icons.Default.Public, Color(0xFF103A75))
        )

        buttons.chunked(2).forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (label, icon, color) ->
                    ActionMatrixButton(
                        label   = matrixLabel(label),
                        icon    = icon,
                        color   = color,
                        onClick = {
                            when (label) {
                                "GOOGLE EARTH" -> if (!geo.validCoordinates) unavailable() else earthFallback = !MatrixHandoff.openEarth(context, geo)
                                "AI ANALYSIS" -> review = label
                                "INFO SCAN", "HISTORY", "RISK / SAFETY", "FINANCIAL" -> options = label
                                "EMERGENCY EXIT" -> onEmergency()
                                else -> handleAction(label, entry, geo, dispatcher, context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(8.dp))

        Card(onClick = onNetwork, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.known_network), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.network_intro), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_exchange)) }

        OutlinedButton(onClick = { dispatcher.copyToClipboard(context, "${geo.latitude}, ${geo.longitude}", "Coordinates") },
            enabled = geo.validCoordinates, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_coordinates)) }
        // ── Copy Location ─────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = { dispatcher.copyLocation(context, geo, entry.note, entry.tag) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.copy_location))
        }

        // ── Share Field Report ────────────────────────────────────────────────
        OutlinedButton(
            onClick  = { dispatcher.shareFieldReport(context, geo, entry.note, entry.tag) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share_report))
        }

        // ── New Scan ──────────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_scan))
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
        title = { Text(stringResource(R.string.location_error)) },
        text  = { Text(when {
            message.contains("permission", ignoreCase = true) -> stringResource(R.string.denied)
            message.contains("disabled", ignoreCase = true) -> stringResource(R.string.location_off)
            message.contains("timed out", ignoreCase = true) -> stringResource(R.string.location_timeout)
            else -> message
        }) },
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
        "RISK / SAFETY" -> dispatcher.riskSafety(context, geo, entry)
        "LOGISTICS"     -> dispatcher.logistics(context, geo)
        "FINANCIAL"     -> dispatcher.financial(context, geo, entry)
        // EMERGENCY EXIT handled by onEmergency callback above
    }
}

@Composable
private fun matrixLabel(key: String): String = when (key) {
    "INFO SCAN" -> stringResource(R.string.action_info_scan)
    "HISTORY" -> stringResource(R.string.action_history)
    "NAVIGATION" -> stringResource(R.string.action_navigation)
    "AI ANALYSIS" -> stringResource(R.string.action_ai_analysis)
    "RISK / SAFETY" -> stringResource(R.string.action_risk_safety)
    "LOGISTICS" -> stringResource(R.string.action_logistics)
    "FINANCIAL" -> stringResource(R.string.action_financial)
    "EMERGENCY EXIT" -> stringResource(R.string.action_emergency)
    "GOOGLE EARTH" -> stringResource(R.string.google_earth)
    else -> key
}
