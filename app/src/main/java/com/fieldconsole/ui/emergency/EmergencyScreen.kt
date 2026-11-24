package com.fieldconsole.ui.emergency

import androidx.compose.ui.res.stringResource
import com.fieldconsole.R
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldconsole.data.emergency.EmergencyNumber
import com.fieldconsole.data.local.entity.EmergencyService
import com.fieldconsole.intent.IntentDispatcher
import com.fieldconsole.intent.EmergencyDialer
import com.fieldconsole.location.GeoContext
import com.fieldconsole.viewmodel.EmergencyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    geo:        GeoContext,
    dispatcher: IntentDispatcher,
    onDismiss:  () -> Unit,
    vm:         EmergencyViewModel = hiltViewModel()
) {
    val state   = vm.state.collectAsState().value
    val context = LocalContext.current

    LaunchedEffect(geo) { vm.load(geo.latitude, geo.longitude, com.fieldconsole.data.emergency.CountryBoundary.current(geo, System.currentTimeMillis()) ?: "UNKNOWN") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null,
                             tint = Color(0xFFCC0000), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.emergency), style = MaterialTheme.typography.titleLarge,
                                 fontWeight = FontWeight.Black, color = Color(0xFFCC0000))
                            Text(geo.shortLabel, style = MaterialTheme.typography.labelSmall,
                                 color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Quick navigation buttons ──────────────────────────────────────
            Text(stringResource(R.string.quick_actions), style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { dispatcher.emergencyNavigation(context, geo) },
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.navigate), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick  = { dispatcher.copyLocation(context, geo) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.copy_gps), fontSize = 12.sp)
                }
            }

            // ── Emergency numbers ─────────────────────────────────────────────
            RegionalPanel(geo)

            // ── Nearby services ───────────────────────────────────────────────
            Text(stringResource(R.string.nearby),
                 style = MaterialTheme.typography.titleSmall,
                 fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            if (state.isLoading && state.services.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.searching_services),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            state.error?.let { err ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(listOfNotNull(err, state.staleWarning).joinToString("\n"), modifier = Modifier.padding(12.dp),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (state.showNoResults) {
                Text(stringResource(R.string.no_services),
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }

            state.services.forEach { svc ->
                EmergencyServiceCard(svc)
            }

            // ── Data source ───────────────────────────────────────────────────
            Text(
                text  = "Services: OpenStreetMap via Overpass API. Locations may be area centers, not entrances. Verify access before travel.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun EmergencyServiceCard(svc: EmergencyService) {
    val context = LocalContext.current
    val typeIcon = when (svc.type) {
        "HOSPITAL"       -> Icons.Default.LocalHospital
        "POLICE"         -> Icons.Default.LocalPolice
        "FIRE"           -> Icons.Default.LocalFireDepartment
        "PHARMACY"       -> Icons.Default.MedicalServices
        "AED"            -> Icons.Default.FavoriteBorder
        "SHELTER"        -> Icons.Default.Home
        else             -> Icons.Default.Place
    }
    val typeColor = when (svc.type) {
        "HOSPITAL"  -> Color(0xFF1B5E20)
        "POLICE"    -> Color(0xFF1565C0)
        "FIRE"      -> Color(0xFFB71C1C)
        "PHARMACY"  -> Color(0xFF4A148C)
        "AED"       -> Color(0xFFE65100)
        else        -> Color(0xFF37474F)
    }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(svc.name, fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.weight(1f))
                if (svc.is24h) {
                    Surface(color = Color(0xFF2E7D32), shape = MaterialTheme.shapes.extraSmall) {
                        Text("Tagged 24/7", color = Color.White, fontSize = 10.sp,
                             modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            if (svc.address.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(svc.address, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%.0f m straight-line".format(svc.distanceM),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.open_status),
                     style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val uri = Uri.parse("geo:${svc.lat},${svc.lon}?q=${svc.lat},${svc.lon}(${svc.name})")
                    if (!com.fieldconsole.intent.MatrixHandoff.launch(context, Intent(Intent.ACTION_VIEW, uri)))
                        android.widget.Toast.makeText(context, R.string.no_receiver, android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.navigate))
                }
            }
        }
    }
}
